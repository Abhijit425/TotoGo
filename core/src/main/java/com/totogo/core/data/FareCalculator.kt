package com.totogo.core.data

import com.totogo.core.model.FareConfig
import kotlin.math.roundToInt

/**
 * Client-side fare SUGGESTION only. The backend recomputes the suggestion and clamps the
 * offered fare (createRide Cloud Function) — the client value is never trusted.
 */
object FareCalculator {
    fun suggested(km: Double, cfg: FareConfig = FareConfig()): Int {
        val raw = cfg.baseFare + cfg.perKm * km
        val rounded = ((raw / 5.0).roundToInt() * 5)
        return maxOf(cfg.minimumFare, rounded)
    }

    fun minOffer(suggested: Int, cfg: FareConfig = FareConfig()) =
        maxOf(cfg.minimumFare, ((suggested * cfg.minOfferRatio) / 5.0).roundToInt() * 5)

    fun maxOffer(suggested: Int, cfg: FareConfig = FareConfig()) =
        ((suggested * cfg.maxOfferRatio) / 5.0).roundToInt() * 5

    fun clamp(offer: Int, suggested: Int, cfg: FareConfig = FareConfig()) =
        offer.coerceIn(minOffer(suggested, cfg), maxOffer(suggested, cfg))
}
