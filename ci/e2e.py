#!/usr/bin/env python3
"""Drives both TotoGo apps on an emulator through their core flows and reports crashes."""
import os, re, subprocess, sys, time, xml.etree.ElementTree as ET

OUT = "e2e-out"; os.makedirs(OUT, exist_ok=True)
failures = []

def sh(cmd, check=False):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True)
    return r.stdout + r.stderr

def adb(c): return sh(f"adb {c}")

def shot(name):
    sh(f"adb exec-out screencap -p > {OUT}/{name}.png")
    print(f"  [screenshot] {name}")

def dismiss_system_dialogs(x):
    if "isn't responding" in x or "keeps stopping" in x:
        for label in ("Close app", "Wait"):
            m = re.search(r'text="%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % label, x)
            if m:
                a = list(map(int, m.groups())); adb(f"shell input tap {(a[0]+a[2])//2} {(a[1]+a[3])//2}"); print(f"  dismissed system dialog ({label})"); time.sleep(1); return True
    return False

def nodes():
    for _ in range(4):
        out = adb("shell uiautomator dump /sdcard/ui.xml")
        if "ERROR" in out: print("  uiautomator:", out.strip()[:120])
        x = adb("shell cat /sdcard/ui.xml")
        if "<hierarchy" in x:
            if dismiss_system_dialogs(x): continue
            try:
                return list(ET.fromstring(x[x.index("<?xml"):] if "<?xml" in x else x).iter("node"))
            except Exception:
                pass
        time.sleep(1)
    return []

def center(n):
    a = list(map(int, re.findall(r"\d+", n.get("bounds"))))
    return (a[0] + a[2]) // 2, (a[1] + a[3]) // 2, a

def find(text, exact=False):
    for n in nodes():
        for attr in ("text", "content-desc"):
            v = n.get(attr) or ""
            if (v == text) if exact else (text.lower() in v.lower()):
                return n
    return None

def tap(text, timeout=15, required=True):
    end = time.time() + timeout
    while time.time() < end:
        n = find(text)
        if n is not None:
            x, y, _ = center(n); adb(f"shell input tap {x} {y}"); print(f"  tap '{text}'"); time.sleep(1.2); return True
        time.sleep(1)
    if required: failures.append(f"could not find '{text}'"); print(f"  !! missing '{text}'")
    return False

def wait_for(text, timeout):
    end = time.time() + timeout
    while time.time() < end:
        if find(text) is not None: print(f"  saw '{text}'"); return True
        if crashed(): return False
        time.sleep(2)
    failures.append(f"timeout waiting for '{text}'"); print(f"  !! timeout '{text}'"); return False

def edit_texts():
    return [n for n in nodes() if "EditText" in (n.get("class") or "")]

def type_into(index, value):
    e = edit_texts()
    if len(e) <= index: failures.append(f"no text field #{index}"); return
    x, y, _ = center(e[index]); adb(f"shell input tap {x} {y}"); time.sleep(0.6)
    adb("shell input text " + value.replace(" ", "%s")); time.sleep(0.6); adb("shell input keyevent 111")  # ESC hides IME
    print(f"  typed '{value}' into field #{index}")

def swipe_up(): adb("shell input swipe 540 1700 540 500 300"); time.sleep(1)

def slide(text):
    n = find(text)
    if n is None: failures.append(f"no slider '{text}'"); return
    _, y, a = center(n); adb(f"shell input swipe {a[0] + 45} {y} {a[2] - 10} {y} 700"); print(f"  slid '{text}'"); time.sleep(2)

def crashed():
    log = adb("logcat -d -b crash")
    return "FATAL EXCEPTION" in log

def login(name):
    tap("Mobile number", 20, required=False) or type_into(0, "")
    type_into(0, "9876543210"); tap("Send OTP")
    type_into(0, "123456"); tap("Verify")
    type_into(0, name); tap("Let's go", 10)

def run_app(pkg, flow):
    print(f"\n=== {pkg} ===")
    adb("shell settings put global hide_error_dialogs 1")
    adb("logcat -c"); adb("logcat -b crash -c")
    adb(f"shell pm clear {pkg}")
    for p in ["POST_NOTIFICATIONS", "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION"]:
        adb(f"shell pm grant {pkg} android.permission.{p}")
    adb(f"shell monkey -p {pkg} -c android.intent.category.LAUNCHER 1"); time.sleep(6)
    shot(f"{pkg}-01-start")
    try:
        flow()
    except Exception as e:
        failures.append(f"{pkg}: script error {e}")
    time.sleep(2)
    if crashed():
        failures.append(f"{pkg} CRASHED")
        print("\n----- CRASH LOG -----"); print(adb("logcat -d -b crash")[-6000:]); print("---------------------")
    print("----- AndroidRuntime/TotoGo errors -----")
    print("\n".join(l for l in adb("logcat -d *:E").splitlines() if "AndroidRuntime" in l or "totogo" in l.lower())[-3000:])

def passenger():
    p = "com.totogo.passenger"
    login("Abhijit Test"); time.sleep(4); shot(f"{p}-02-home")
    tap("Nalhati Bazar"); time.sleep(2); shot(f"{p}-03-destination")
    tap("Request Toto", 10); time.sleep(2); shot(f"{p}-04-fare")
    tap("Request Toto at", 10); time.sleep(4); shot(f"{p}-05-finding")
    if wait_for("Live distance", 120): shot(f"{p}-06-accepted")
    if wait_for("Arriving at", 150): shot(f"{p}-07-tracking")
    if wait_for("arrived!", 200): shot(f"{p}-08-complete"); tap("Done")
    time.sleep(2); tap("Translate", 3, required=False); shot(f"{p}-09-home-after")

def driver():
    p = "com.totogo.driver"
    login("Abhijit Driver"); time.sleep(3); shot(f"{p}-02-onboarding")
    type_into(2, "WB53E1234"); type_into(3, "WBDL12345"); swipe_up(); swipe_up()
    tap("Submit for verification"); time.sleep(2); shot(f"{p}-03-verification")
    if wait_for("OFFLINE", 30): shot(f"{p}-04-dashboard")
    tap("OFFLINE"); time.sleep(2); shot(f"{p}-05-online")
    if wait_for("ACCEPT", 40):
        shot(f"{p}-06-request"); tap("ACCEPT", exact=False) if False else tap("ACCEPT")
        time.sleep(4); shot(f"{p}-07-after-accept")
        if find("Mark Arrived") is None and find("OFFLINE") is not None:
            print("  lost the race (DEMO) — waiting for another request")
            if wait_for("ACCEPT", 40): tap("ACCEPT"); time.sleep(4)
        slide("Mark Arrived"); slide("Start Ride"); time.sleep(3); shot(f"{p}-08-trip"); slide("Complete Ride"); time.sleep(3)
        shot(f"{p}-09-done")
    adb("shell input keyevent 3"); time.sleep(25); shot(f"{p}-10-background")

run_app("com.totogo.passenger", passenger)
run_app("com.totogo.driver", driver)
print("\n==== RESULT ====")
print("PASS" if not failures else "FAILURES:\n- " + "\n- ".join(failures))
open(f"{OUT}/result.txt", "w").write("\n".join(failures) or "PASS")
