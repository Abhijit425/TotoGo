/**
 * TotoGo Cloud Functions — secure ride matching & booking operations.
 *
 * The client is never trusted for: fare, driver availability, or ride acceptance.
 *  - createRide recomputes the suggested fare and clamps the passenger's offer.
 *  - dispatchRound picks eligible drivers server-side (online + verified + fresh GPS + in radius).
 *  - acceptRide runs in a Firestore transaction: exactly one driver can win a ride.
 *  - Radius expansion (2 → 4 → 6 km by default) is driven by Cloud Tasks (expandSearch).
 */
import { initializeApp } from "firebase-admin/app";
import { getFirestore, FieldValue, Transaction } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { getAuth } from "firebase-admin/auth";
import { getFunctions } from "firebase-admin/functions";
import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import { onTaskDispatched } from "firebase-functions/v2/tasks";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { setGlobalOptions, logger } from "firebase-functions/v2";

initializeApp();
setGlobalOptions({ region: "asia-south1", maxInstances: 20 });
const db = getFirestore();

// ---------------------------------------------------------------- types & config

type Place = { id: string; nameEn: string; nameBn: string; lat: number; lng: number };
type RideStatus = "SEARCHING" | "ACCEPTED" | "ARRIVED" | "IN_PROGRESS" | "COMPLETED" | "CANCELLED" | "NO_DRIVERS";
const ACTIVE: RideStatus[] = ["SEARCHING", "ACCEPTED", "ARRIVED", "IN_PROGRESS"];

interface DispatchConfig { radiiKm: number[]; roundTimeoutSec: number; requestExpirySec: number; maxDriversPerRound: number; locationFreshSec: number }
interface FareConfig { baseFare: number; perKm: number; minimumFare: number; minOfferRatio: number; maxOfferRatio: number; roadFactor: number }
interface ServiceArea { centerLat: number; centerLng: number; radiusKm: number; name: string }

const DEFAULT_DISPATCH: DispatchConfig = { radiiKm: [2, 4, 6], roundTimeoutSec: 20, requestExpirySec: 20, maxDriversPerRound: 15, locationFreshSec: 120 };
const DEFAULT_FARE: FareConfig = { baseFare: 10, perKm: 10, minimumFare: 20, minOfferRatio: 0.7, maxOfferRatio: 3, roadFactor: 1.3 };
const DEFAULT_AREA: ServiceArea = { centerLat: 24.2958, centerLng: 87.8232, radiusKm: 25, name: "Nalhati" };

async function cfg<T>(doc: string, fallback: T): Promise<T> {
  const snap = await db.collection("config").doc(doc).get();
  return { ...fallback, ...(snap.exists ? (snap.data() as Partial<T>) : {}) };
}

// ---------------------------------------------------------------- helpers

function km(aLat: number, aLng: number, bLat: number, bLng: number): number {
  const R = 6371, toR = (d: number) => (d * Math.PI) / 180;
  const dLat = toR(bLat - aLat), dLng = toR(bLng - aLng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toR(aLat)) * Math.cos(toR(bLat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}
const round5 = (v: number) => Math.round(v / 5) * 5;
function suggestedFare(tripKm: number, f: FareConfig) { return Math.max(f.minimumFare, round5(f.baseFare + f.perKm * tripKm)); }

function requireAuth(req: CallableRequest): string {
  if (!req.auth) throw new HttpsError("unauthenticated", "Sign in first");
  return req.auth.uid;
}
function requireAdmin(req: CallableRequest): string {
  const uid = requireAuth(req);
  if (req.auth?.token.admin !== true) throw new HttpsError("permission-denied", "Admin only");
  return uid;
}
function parsePlace(p: unknown, area: ServiceArea): Place {
  const o = p as Record<string, unknown>;
  const lat = Number(o?.lat), lng = Number(o?.lng);
  if (!Number.isFinite(lat) || !Number.isFinite(lng) || Math.abs(lat) > 90 || Math.abs(lng) > 180) throw new HttpsError("invalid-argument", "Bad location");
  if (km(lat, lng, area.centerLat, area.centerLng) > area.radiusKm) throw new HttpsError("out-of-range", `Outside the ${area.name} service area`);
  return {
    id: String(o.id ?? "custom").slice(0, 40), nameEn: String(o.nameEn ?? "Pinned location").slice(0, 80),
    nameBn: String(o.nameBn ?? "নির্বাচিত স্থান").slice(0, 80), lat, lng,
  };
}
function mask(phone: string | undefined): string {
  if (!phone) return "";
  const d = phone.replace(/\D/g, "");
  return `+91 ${d.slice(-10, -8)}•••• ••${d.slice(-2)}`;
}
async function tokensOf(uid: string): Promise<string[]> {
  const u = await db.collection("users").doc(uid).get();
  return (u.get("fcmTokens") as string[] | undefined) ?? [];
}
async function push(uid: string, data: Record<string, string>, channel = "ride_updates") {
  const tokens = await tokensOf(uid);
  if (!tokens.length) return;
  try {
    const res = await getMessaging().sendEachForMulticast({
      tokens, data,
      android: { priority: "high", ttl: 30_000, notification: channel === "ride_requests_v1" ? undefined : { channelId: channel, title: data.title, body: data.body } },
    });
    // Prune dead tokens.
    const dead = res.responses.map((r, i) => (!r.success ? tokens[i] : null)).filter((t): t is string => !!t);
    if (dead.length) await db.collection("users").doc(uid).update({ fcmTokens: FieldValue.arrayRemove(...dead) });
  } catch (e) { logger.warn("push failed", uid, e); }
}

// ---------------------------------------------------------------- passenger: create ride

export const createRide = onCall(async (req) => {
  const uid = requireAuth(req);
  const [fare, area, dispatch] = await Promise.all([cfg("fare", DEFAULT_FARE), cfg("serviceArea", DEFAULT_AREA), cfg("dispatch", DEFAULT_DISPATCH)]);
  const pickup = parsePlace(req.data?.pickup, area);
  const destination = parsePlace(req.data?.destination, area);
  const straight = km(pickup.lat, pickup.lng, destination.lat, destination.lng);
  if (straight < 0.15) throw new HttpsError("invalid-argument", "Pickup and destination are too close");
  const tripKm = Math.round(straight * fare.roadFactor * 100) / 100;
  const suggested = suggestedFare(tripKm, fare);
  const minOffer = Math.max(fare.minimumFare, round5(suggested * fare.minOfferRatio));
  const maxOffer = round5(suggested * fare.maxOfferRatio);
  const offered = Math.min(maxOffer, Math.max(minOffer, Math.round(Number(req.data?.offeredFare) || suggested)));

  const userRef = db.collection("users").doc(uid);
  const lockRef = db.collection("passengerLocks").doc(uid);
  const rideRef = db.collection("rides").doc();
  const now = Date.now();

  await db.runTransaction(async (tx) => {
    const [user, lock] = await Promise.all([tx.get(userRef), tx.get(lockRef)]);
    if (user.get("suspended") === true) throw new HttpsError("permission-denied", "Account suspended. Contact support.");
    const activeId = lock.get("activeRideId") as string | undefined;
    if (activeId) {
      const active = await tx.get(db.collection("rides").doc(activeId));
      if (active.exists && ACTIVE.includes(active.get("status"))) throw new HttpsError("already-exists", "You already have an active booking");
    }
    tx.set(rideRef, {
      passengerId: uid, driverId: null, status: "SEARCHING" as RideStatus,
      pickup, destination, tripKm, suggestedFare: suggested, offeredFare: offered, finalFare: null,
      passenger: { name: user.get("name") ?? "Passenger", phoneMasked: mask(user.get("phone") ?? req.auth?.token.phone_number), rating: user.get("rating") ?? 5 },
      searchRound: 0, searchRounds: dispatch.radiiKm.length, searchRadiusKm: dispatch.radiiKm[0], roundTimeoutSec: dispatch.roundTimeoutSec,
      roundStartedAt: now, driversNotified: 0, notifiedDriverIds: [],
      createdAt: now, acceptedAt: null, completedAt: null,
    });
    tx.set(lockRef, { activeRideId: rideRef.id, updatedAt: now });
  });

  await dispatchRound(rideRef.id, 0);
  return { rideId: rideRef.id, suggestedFare: suggested, offeredFare: offered };
});

// ---------------------------------------------------------------- matching

/** Idempotently claims round [round] for a ride, notifies eligible drivers and schedules the next round. */
async function dispatchRound(rideId: string, round: number): Promise<void> {
  const dispatch = await cfg("dispatch", DEFAULT_DISPATCH);
  const rideRef = db.collection("rides").doc(rideId);

  const claimed = await db.runTransaction(async (tx) => {
    const ride = await tx.get(rideRef);
    if (!ride.exists || ride.get("status") !== "SEARCHING") return null;
    if ((ride.get("searchRound") as number) > round) return null; // round already ran
    if (round >= dispatch.radiiKm.length) {
      tx.update(rideRef, { status: "NO_DRIVERS" });
      tx.set(db.collection("passengerLocks").doc(ride.get("passengerId")), { activeRideId: null }, { merge: true });
      return { done: true as const, passengerId: ride.get("passengerId") as string };
    }
    tx.update(rideRef, { searchRound: round + 1, searchRadiusKm: dispatch.radiiKm[round], roundStartedAt: Date.now(), roundTimeoutSec: dispatch.roundTimeoutSec });
    return { done: false as const, ride: ride.data()! };
  });
  if (!claimed) return;
  if (claimed.done) {
    await push(claimed.passengerId, { type: "no_drivers", rideId, title: "No Toto available right now", body: "Try again or raise your offer a little." });
    return;
  }

  const ride = claimed.ride;
  const pickup = ride.pickup as Place;
  const radius = dispatch.radiiKm[round];
  const already = new Set<string>(ride.notifiedDriverIds ?? []);
  const freshAfter = Date.now() - dispatch.locationFreshSec * 1000;

  // Eligible = online + verified + not suspended + no active ride + fresh GPS within radius + not notified before.
  const online = await db.collection("drivers").where("isOnline", "==", true).where("isVerified", "==", true).get();
  const candidates = online.docs.filter((d) => !d.get("suspended") && !d.get("activeRideId") && !already.has(d.id));
  const locs = candidates.length ? await db.getAll(...candidates.map((d) => db.collection("driverLocations").doc(d.id))) : [];
  const eligible = locs
    .filter((l) => l.exists && (l.get("updatedAt")?.toMillis?.() ?? 0) >= freshAfter)
    .map((l) => ({ id: l.id, dist: km(pickup.lat, pickup.lng, l.get("lat"), l.get("lng")) }))
    .filter((x) => x.dist <= radius)
    .sort((a, b) => a.dist - b.dist)
    .slice(0, dispatch.maxDriversPerRound);

  const expiresAt = Date.now() + dispatch.requestExpirySec * 1000;
  const batch = db.batch();
  for (const d of eligible) {
    batch.set(db.collection("rideRequests").doc(`${rideId}_${d.id}`), {
      rideId, driverId: d.id, status: "PENDING", expiresAt, round: round + 1,
      pickup, destination: ride.destination, offeredFare: ride.offeredFare, suggestedFare: ride.suggestedFare,
      tripKm: ride.tripKm, pickupKm: Math.round(d.dist * 130) / 100,
      passengerName: ride.passenger?.name ?? "Passenger", passengerRating: ride.passenger?.rating ?? 5, createdAt: Date.now(),
    });
  }
  batch.update(rideRef, { driversNotified: FieldValue.increment(eligible.length), notifiedDriverIds: FieldValue.arrayUnion(...eligible.map((e) => e.id), "_") });
  await batch.commit();

  // High-priority data message → driver app plays the TotoGo alarm with a full-screen intent.
  await Promise.all(eligible.map((d) => push(d.id, {
    type: "ride_request", rideId, requestId: `${rideId}_${d.id}`, offeredFare: String(ride.offeredFare),
    pickup: pickup.nameEn, destination: (ride.destination as Place).nameEn, expiresAt: String(expiresAt),
  }, "ride_requests_v1")));

  // Expand the radius if nobody accepts within the round timeout.
  await getFunctions().taskQueue("expandSearch").enqueue({ rideId, round: round + 1 }, { scheduleDelaySeconds: eligible.length ? dispatch.roundTimeoutSec : 2 });
  logger.info(`ride ${rideId} round ${round + 1}: notified ${eligible.length} drivers within ${radius} km`);
}

export const expandSearch = onTaskDispatched({ retryConfig: { maxAttempts: 3, minBackoffSeconds: 5 }, rateLimits: { maxConcurrentDispatches: 20 } }, async (req) => {
  const { rideId, round } = req.data as { rideId: string; round: number };
  const pending = await db.collection("rideRequests").where("rideId", "==", rideId).where("status", "==", "PENDING").get();
  const batch = db.batch();
  pending.docs.filter((d) => d.get("expiresAt") <= Date.now()).forEach((d) => batch.update(d.ref, { status: "EXPIRED" }));
  await batch.commit();
  await dispatchRound(rideId, round);
});

// ---------------------------------------------------------------- driver: accept / reject (atomic)

export const acceptRide = onCall(async (req) => {
  const uid = requireAuth(req);
  const requestId = String(req.data?.requestId ?? "");
  const reqRef = db.collection("rideRequests").doc(requestId);
  const driverRef = db.collection("drivers").doc(uid);
  const telephony = await cfg("telephony", { proxyNumber: "" });

  const result = await db.runTransaction(async (tx: Transaction) => {
    const r = await tx.get(reqRef);
    if (!r.exists || r.get("driverId") !== uid) throw new HttpsError("not-found", "Request not found");
    if (r.get("status") !== "PENDING") throw new HttpsError("failed-precondition", "Request is no longer open");
    if ((r.get("expiresAt") as number) < Date.now()) throw new HttpsError("deadline-exceeded", "Request expired");
    const rideRef = db.collection("rides").doc(r.get("rideId"));
    const [ride, driver] = await Promise.all([tx.get(rideRef), tx.get(driverRef)]);
    const passenger = ride.exists ? await tx.get(db.collection("users").doc(ride.get("passengerId"))) : null;
    if (!driver.get("isVerified") || driver.get("suspended")) throw new HttpsError("permission-denied", "Driver not allowed");
    if (!driver.get("isOnline")) throw new HttpsError("failed-precondition", "Go online first");
    if (driver.get("activeRideId")) throw new HttpsError("failed-precondition", "Finish your current ride first");
    // The lock: only the first transaction to see SEARCHING can claim the ride.
    if (!ride.exists || ride.get("status") !== "SEARCHING") throw new HttpsError("already-exists", "Another driver accepted first");

    const now = Date.now();
    tx.update(rideRef, {
      status: "ACCEPTED", driverId: uid, acceptedAt: now, finalFare: ride.get("offeredFare"),
      driver: {
        name: driver.get("name"), phoneMasked: mask(driver.get("phone")),
        // Masked calling: route through a call-proxy number when configured, else share the number after acceptance only.
        dialNumber: telephony.proxyNumber || driver.get("phone"),
        vehicleNumber: driver.get("vehicleNumber"), totoId: driver.get("totoId") ?? `TG-${uid.slice(0, 5).toUpperCase()}`,
        rating: driver.get("rating") ?? 5, totalRides: driver.get("totalRides") ?? 0,
      },
    });
    tx.update(rideRef, { "passenger.dialNumber": telephony.proxyNumber || passenger?.get("phone") || "" });
    tx.update(reqRef, { status: "ACCEPTED", respondedAt: now });
    tx.update(driverRef, { activeRideId: rideRef.id });
    return { rideId: rideRef.id, passengerId: ride.get("passengerId") as string, driverName: driver.get("name") as string, vehicle: driver.get("vehicleNumber") as string };
  });

  // Close every other open offer for this ride and tell those drivers.
  const others = await db.collection("rideRequests").where("rideId", "==", result.rideId).where("status", "==", "PENDING").get();
  const batch = db.batch();
  others.docs.forEach((d) => batch.update(d.ref, { status: "TAKEN" }));
  await batch.commit();
  await Promise.all([
    ...others.docs.map((d) => push(d.get("driverId"), { type: "ride_taken", rideId: result.rideId }, "ride_requests_v1")),
    push(result.passengerId, { type: "ride_accepted", rideId: result.rideId, title: "Toto accepted!", body: `${result.driverName} · ${result.vehicle} is on the way` }),
  ]);
  return { ok: true, rideId: result.rideId };
});

export const rejectRide = onCall(async (req) => {
  const uid = requireAuth(req);
  const ref = db.collection("rideRequests").doc(String(req.data?.requestId ?? ""));
  const r = await ref.get();
  if (!r.exists || r.get("driverId") !== uid) throw new HttpsError("not-found", "Request not found");
  if (r.get("status") !== "PENDING") return { ok: true };
  await ref.update({ status: "REJECTED", respondedAt: Date.now() });
  // If everyone in this round has said no, expand immediately instead of waiting.
  const open = await db.collection("rideRequests").where("rideId", "==", r.get("rideId")).where("status", "==", "PENDING").limit(1).get();
  if (open.empty) await dispatchRound(r.get("rideId"), r.get("round"));
  return { ok: true };
});

// ---------------------------------------------------------------- ride lifecycle

const DRIVER_TRANSITIONS: Record<string, RideStatus[]> = {
  ACCEPTED: ["ARRIVED", "CANCELLED"],
  ARRIVED: ["IN_PROGRESS", "CANCELLED"],
  IN_PROGRESS: ["COMPLETED"],
};

export const updateRideStatus = onCall(async (req) => {
  const uid = requireAuth(req);
  const rideRef = db.collection("rides").doc(String(req.data?.rideId ?? ""));
  const next = String(req.data?.status) as RideStatus;
  const out = await db.runTransaction(async (tx) => {
    const ride = await tx.get(rideRef);
    if (!ride.exists || ride.get("driverId") !== uid) throw new HttpsError("permission-denied", "Not your ride");
    const cur = ride.get("status") as RideStatus;
    if (!(DRIVER_TRANSITIONS[cur] ?? []).includes(next)) throw new HttpsError("failed-precondition", `Cannot go from ${cur} to ${next}`);
    const now = Date.now();
    const update: Record<string, unknown> = { status: next, [`${next.toLowerCase()}At`]: now };
    const driverRef = db.collection("drivers").doc(uid);
    if (next === "COMPLETED") {
      update.completedAt = now;
      tx.set(db.collection("payments").doc(rideRef.id), { paymentId: rideRef.id, rideId: rideRef.id, amount: ride.get("finalFare"), method: "CASH", status: "PENDING", passengerId: ride.get("passengerId"), driverId: uid, createdAt: now });
      tx.update(driverRef, { activeRideId: null, totalRides: FieldValue.increment(1), lifetimeEarnings: FieldValue.increment(ride.get("finalFare") ?? 0) });
      tx.set(db.collection("passengerLocks").doc(ride.get("passengerId")), { activeRideId: null }, { merge: true });
    }
    if (next === "CANCELLED") {
      update.cancelReason = String(req.data?.reason ?? "Driver cancelled").slice(0, 120);
      update.cancelledBy = "DRIVER";
      tx.update(driverRef, { activeRideId: null, cancelCount: FieldValue.increment(1) });
      tx.set(db.collection("passengerLocks").doc(ride.get("passengerId")), { activeRideId: null }, { merge: true });
    }
    tx.update(rideRef, update);
    return { passengerId: ride.get("passengerId") as string };
  });
  const msg: Record<string, [string, string]> = {
    ARRIVED: ["Your Toto has arrived", "Your driver is waiting at the pickup point"],
    IN_PROGRESS: ["Ride started", "Enjoy your ride — share live status from the app"],
    COMPLETED: ["Ride completed", "Please pay the driver and rate your ride"],
    CANCELLED: ["Driver cancelled", "Sorry! Please request again — we'll find another Toto"],
  };
  const [title, body] = msg[next];
  await push(out.passengerId, { type: `ride_${next.toLowerCase()}`, rideId: rideRef.id, title, body });
  return { ok: true };
});

export const cancelRide = onCall(async (req) => {
  const uid = requireAuth(req);
  const rideRef = db.collection("rides").doc(String(req.data?.rideId ?? ""));
  const info = await db.runTransaction(async (tx) => {
    const ride = await tx.get(rideRef);
    if (!ride.exists || ride.get("passengerId") !== uid) throw new HttpsError("permission-denied", "Not your ride");
    const cur = ride.get("status") as RideStatus;
    if (!["SEARCHING", "ACCEPTED", "ARRIVED"].includes(cur)) throw new HttpsError("failed-precondition", "This ride can no longer be cancelled");
    tx.update(rideRef, { status: "CANCELLED", cancelledBy: "PASSENGER", cancelReason: String(req.data?.reason ?? "").slice(0, 120), cancelledAt: Date.now() });
    tx.set(db.collection("passengerLocks").doc(uid), { activeRideId: null }, { merge: true });
    // Cancellation rule: free until the driver arrives; late cancellations are counted.
    if (cur === "ARRIVED") tx.update(db.collection("users").doc(uid), { lateCancelCount: FieldValue.increment(1) });
    const driverId = ride.get("driverId") as string | null;
    if (driverId) tx.update(db.collection("drivers").doc(driverId), { activeRideId: null });
    return { driverId };
  });
  const open = await db.collection("rideRequests").where("rideId", "==", rideRef.id).where("status", "==", "PENDING").get();
  const batch = db.batch();
  open.docs.forEach((d) => batch.update(d.ref, { status: "CANCELLED" }));
  await batch.commit();
  await Promise.all([
    ...open.docs.map((d) => push(d.get("driverId"), { type: "ride_cancelled", rideId: rideRef.id }, "ride_requests_v1")),
    info.driverId ? push(info.driverId, { type: "ride_cancelled", rideId: rideRef.id, title: "Passenger cancelled", body: "You're available for new requests" }) : Promise.resolve(),
  ]);
  return { ok: true };
});

export const submitRating = onCall(async (req) => {
  const uid = requireAuth(req);
  const stars = Math.round(Number(req.data?.stars));
  if (!(stars >= 1 && stars <= 5)) throw new HttpsError("invalid-argument", "1–5 stars");
  const method = req.data?.paymentMethod === "UPI" ? "UPI" : "CASH";
  const rideRef = db.collection("rides").doc(String(req.data?.rideId ?? ""));
  await db.runTransaction(async (tx) => {
    const ride = await tx.get(rideRef);
    if (!ride.exists || ride.get("passengerId") !== uid) throw new HttpsError("permission-denied", "Not your ride");
    if (ride.get("status") !== "COMPLETED") throw new HttpsError("failed-precondition", "Ride not completed");
    if (ride.get("rating")) return; // rate once
    tx.update(rideRef, { rating: stars, feedback: String(req.data?.feedback ?? "").slice(0, 300) });
    tx.update(db.collection("payments").doc(rideRef.id), { method });
    tx.update(db.collection("drivers").doc(ride.get("driverId")), { ratingSum: FieldValue.increment(stars), ratingCount: FieldValue.increment(1) });
  });
  // Recompute the displayed average outside the transaction.
  const d = await db.collection("drivers").doc((await rideRef.get()).get("driverId")).get();
  const avg = (d.get("ratingSum") ?? 0) / Math.max(1, d.get("ratingCount") ?? 1);
  await d.ref.update({ rating: Math.round(avg * 10) / 10 });
  return { ok: true };
});

// ---------------------------------------------------------------- driver availability & passenger map

export const setDriverAvailability = onCall(async (req) => {
  const uid = requireAuth(req);
  const online = req.data?.online === true;
  const ref = db.collection("drivers").doc(uid);
  const d = await ref.get();
  if (!d.exists || !d.get("isVerified")) throw new HttpsError("permission-denied", "Your account is not verified yet");
  if (d.get("suspended")) throw new HttpsError("permission-denied", "Account suspended");
  if (!online && d.get("activeRideId")) throw new HttpsError("failed-precondition", "Finish the active ride before going offline");
  await ref.update({ isOnline: online, lastAvailabilityChange: Date.now() });
  if (!online) await db.collection("driverLocations").doc(uid).delete(); // stop sharing when offline
  return { ok: true };
});

/** Coarse, privacy-preserving driver positions for the passenger home map. */
export const nearbyDrivers = onCall(async (req) => {
  requireAuth(req);
  const lat = Number(req.data?.lat), lng = Number(req.data?.lng);
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) throw new HttpsError("invalid-argument", "lat/lng");
  const fresh = new Date(Date.now() - 120_000);
  const snap = await db.collection("driverLocations").where("updatedAt", ">=", fresh).limit(200).get();
  const drivers = snap.docs
    .filter((d) => !d.get("activeRideId") && km(lat, lng, d.get("lat"), d.get("lng")) <= 3)
    .map((d) => ({ lat: Math.round(d.get("lat") * 1000) / 1000, lng: Math.round(d.get("lng") * 1000) / 1000 }));
  return { count: drivers.length, drivers };
});

// ---------------------------------------------------------------- admin

export const reviewDriverApplication = onCall(async (req) => {
  requireAdmin(req);
  const driverId = String(req.data?.driverId ?? "");
  const decision = String(req.data?.decision) as "VERIFIED" | "REJECTED" | "SUSPENDED";
  if (!["VERIFIED", "REJECTED", "SUSPENDED"].includes(decision)) throw new HttpsError("invalid-argument", "decision");
  const app = await db.collection("driverApplications").doc(driverId).get();
  if (!app.exists) throw new HttpsError("not-found", "Application not found");
  await db.collection("driverApplications").doc(driverId).update({ status: decision, reviewedAt: Date.now(), reviewedBy: req.auth?.uid });
  await db.collection("drivers").doc(driverId).set({
    driverId, name: app.get("name"), phone: app.get("phone"), vehicleNumber: app.get("vehicleNumber"),
    isVerified: decision === "VERIFIED", suspended: decision === "SUSPENDED", verificationStatus: decision,
    isOnline: false, activeRideId: null, rating: 5, totalRides: 0,
  }, { merge: true });
  await db.collection("users").doc(driverId).set({ role: "DRIVER" }, { merge: true });
  await push(driverId, { type: "verification", title: decision === "VERIFIED" ? "You're verified!" : "Application update", body: decision === "VERIFIED" ? "Go online to start receiving rides" : `Status: ${decision}` });
  return { ok: true };
});

export const updateConfig = onCall(async (req) => {
  requireAdmin(req);
  const which = String(req.data?.doc);
  if (!["dispatch", "fare", "serviceArea", "telephony"].includes(which)) throw new HttpsError("invalid-argument", "doc");
  const v = req.data?.value as Record<string, unknown>;
  if (which === "dispatch") {
    const radii = (v.radiiKm as number[] | undefined)?.map(Number).filter((x) => x > 0 && x <= 20);
    if (radii && (!radii.length || radii.some((x, i) => i > 0 && x <= radii[i - 1]))) throw new HttpsError("invalid-argument", "Radii must increase");
    const t = Number(v.roundTimeoutSec ?? 20);
    if (t < 5 || t > 120) throw new HttpsError("invalid-argument", "Timeout 5–120 s");
  }
  await db.collection("config").doc(which).set({ ...v, updatedAt: Date.now(), updatedBy: req.auth?.uid }, { merge: true });
  return { ok: true };
});

/** Bootstrap: run once from the Firebase shell for the first admin, then admins can promote others. */
export const setAdmin = onCall(async (req) => {
  requireAdmin(req);
  const target = String(req.data?.uid ?? "");
  await getAuth().setCustomUserClaims(target, { admin: req.data?.admin === true });
  return { ok: true };
});

// ---------------------------------------------------------------- safety net

export const sweepStale = onSchedule("every 5 minutes", async () => {
  const now = Date.now();
  const expired = await db.collection("rideRequests").where("status", "==", "PENDING").where("expiresAt", "<", now).limit(400).get();
  const b1 = db.batch();
  expired.docs.forEach((d) => b1.update(d.ref, { status: "EXPIRED" }));
  await b1.commit();
  const stuck = await db.collection("rides").where("status", "==", "SEARCHING").where("createdAt", "<", now - 10 * 60_000).limit(200).get();
  const b2 = db.batch();
  stuck.docs.forEach((d) => {
    b2.update(d.ref, { status: "NO_DRIVERS" });
    b2.set(db.collection("passengerLocks").doc(d.get("passengerId")), { activeRideId: null }, { merge: true });
  });
  await b2.commit();
  logger.info(`sweep: ${expired.size} requests expired, ${stuck.size} rides closed`);
});
