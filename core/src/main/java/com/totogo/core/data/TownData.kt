package com.totogo.core.data

import com.totogo.core.geo.Geo
import com.totogo.core.model.GeoPoint
import com.totogo.core.model.Place
import com.totogo.core.model.PlaceCategory
import java.util.PriorityQueue

/**
 * Manually maintained local landmark database for Nalhati, Birbhum (demo seed).
 *
 * Coordinates are APPROXIMATE and laid out for the demo map. Before launch, walk the town
 * and correct each point (or edit them from the admin dashboard -> Service areas).
 */
object TownData {
    val CENTER = GeoPoint(24.2958, 87.8232)

    val landmarks: List<Place> = listOf(
        p("station", "Nalhati Junction Railway Station", "নলহাটি জংশন রেল স্টেশন", PlaceCategory.STATION, 24.2960, 87.8225),
        p("busstand", "Nalhati Bus Stand", "নলহাটি বাস স্ট্যান্ড", PlaceCategory.BUS_STAND, 24.2934, 87.8263),
        p("bazar", "Nalhati Bazar (Market)", "নলহাটি বাজার", PlaceCategory.MARKET, 24.2948, 87.8246),
        p("haat", "Haat Tala", "হাটতলা", PlaceCategory.MARKET, 24.2919, 87.8282),
        p("temple", "Nalateswari Temple", "নলাটেশ্বরী মন্দির", PlaceCategory.TEMPLE, 24.3012, 87.8152),
        p("hospital", "Nalhati Rural Hospital", "নলহাটি গ্রামীণ হাসপাতাল", PlaceCategory.HOSPITAL, 24.2903, 87.8236),
        p("college", "Hiralal Bhakat College", "হীরালাল ভকত কলেজ", PlaceCategory.SCHOOL, 24.2992, 87.8288),
        p("highschool", "Nalhati High School", "নলহাটি উচ্চ বিদ্যালয়", PlaceCategory.SCHOOL, 24.2978, 87.8193),
        p("municipality", "Nalhati Municipality Office", "নলহাটি পৌরসভা", PlaceCategory.OFFICE, 24.2940, 87.8204),
        p("police", "Nalhati Police Station", "নলহাটি থানা", PlaceCategory.OFFICE, 24.2924, 87.8214),
        p("postoffice", "Nalhati Post Office", "নলহাটি ডাকঘর", PlaceCategory.OFFICE, 24.2946, 87.8229),
        p("bank", "SBI Nalhati Branch", "এসবিআই নলহাটি শাখা", PlaceCategory.BANK, 24.2953, 87.8257),
        p("nhmore", "Nalhati More (NH-14)", "নলহাটি মোড় (এনএইচ-১৪)", PlaceCategory.BUS_STAND, 24.2879, 87.8306),
        p("lohapur", "Lohapur", "লোহাপুর", PlaceCategory.VILLAGE, 24.3185, 87.8105),
        p("moregram", "Moregram", "মোড়গ্রাম", PlaceCategory.VILLAGE, 24.2722, 87.8412),
        p("bhadrapur", "Bhadrapur", "ভদ্রপুর", PlaceCategory.VILLAGE, 24.3204, 87.8418),
        p("kalitala", "Kali Mandir Para", "কালী মন্দির পাড়া", PlaceCategory.VILLAGE, 24.3060, 87.8240),
    )

    /** Road network used for demo routing and for drawing the stylised town map. */
    val roads: List<Pair<String, String>> = listOf(
        "station" to "bazar", "bazar" to "busstand", "bazar" to "bank", "bank" to "busstand",
        "bazar" to "postoffice", "postoffice" to "station", "station" to "highschool",
        "highschool" to "temple", "temple" to "lohapur", "station" to "municipality",
        "municipality" to "police", "police" to "hospital", "hospital" to "haat",
        "haat" to "busstand", "busstand" to "nhmore", "nhmore" to "moregram", "haat" to "nhmore",
        "bank" to "college", "college" to "bhadrapur", "highschool" to "municipality",
        "postoffice" to "police", "station" to "kalitala", "kalitala" to "college",
        "kalitala" to "lohapur", "kalitala" to "bhadrapur", "postoffice" to "hospital",
    )

    private val byId = landmarks.associateBy { it.id }
    fun place(id: String): Place = byId.getValue(id)

    private fun p(id: String, en: String, bn: String, c: PlaceCategory, lat: Double, lng: Double) =
        Place(id, en, bn, c, GeoPoint(lat, lng))

    fun nearest(point: GeoPoint): Place = landmarks.minBy { Geo.distanceKm(it.point, point) }

    /** Dijkstra over the town graph; endpoints are snapped to the nearest landmark node. */
    fun route(from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        val start = nearest(from).id
        val goal = nearest(to).id
        val adj = HashMap<String, MutableList<Pair<String, Double>>>()
        roads.forEach { (a, b) ->
            val d = Geo.distanceKm(place(a).point, place(b).point)
            adj.getOrPut(a) { mutableListOf() }.add(b to d)
            adj.getOrPut(b) { mutableListOf() }.add(a to d)
        }
        val dist = HashMap<String, Double>().apply { put(start, 0.0) }
        val prev = HashMap<String, String>()
        val pq = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        pq.add(start to 0.0)
        while (pq.isNotEmpty()) {
            val (u, du) = pq.poll()!!
            if (u == goal) break
            if (du > (dist[u] ?: Double.MAX_VALUE)) continue
            for ((v, w) in adj[u].orEmpty()) {
                val nd = du + w
                if (nd < (dist[v] ?: Double.MAX_VALUE)) {
                    dist[v] = nd; prev[v] = u; pq.add(v to nd)
                }
            }
        }
        val nodes = ArrayList<String>()
        var cur: String? = goal
        while (cur != null) { nodes.add(cur); cur = prev[cur] }
        nodes.reverse()
        if (nodes.firstOrNull() != start) return listOf(from, to)
        val pts = ArrayList<GeoPoint>()
        pts.add(from)
        nodes.forEach { pts.add(place(it).point) }
        pts.add(to)
        return pts.zipWithNext().filter { (a, b) -> Geo.distanceKm(a, b) > 0.005 }.map { it.first } + to
    }

    fun roadDistanceKm(from: GeoPoint, to: GeoPoint): Double = Geo.pathLengthKm(route(from, to))
}

/**
 * Search that accepts Bengali script or English (including common romanised spellings).
 */
object PlaceSearch {
    private val aliases: Map<String, List<String>> = mapOf(
        "station" to listOf("rail", "train", "junction", "steshon", "stesan", "ষ্টেশন", "স্টেশন", "রেল"),
        "busstand" to listOf("bus", "stand", "bas", "বাস"),
        "bazar" to listOf("bazaar", "market", "hat", "বাজার"),
        "haat" to listOf("hat", "haat tola", "hattala", "হাট"),
        "temple" to listOf("nalateshwari", "mandir", "maa", "shaktipeeth", "মন্দির"),
        "hospital" to listOf("haspatal", "doctor", "medical", "hospatal", "হাসপাতাল"),
        "college" to listOf("hiralal", "bhakat", "kolej", "কলেজ"),
        "highschool" to listOf("school", "iskul", "বিদ্যালয়", "স্কুল"),
        "municipality" to listOf("pouroshova", "pourasabha", "office", "পৌরসভা"),
        "police" to listOf("thana", "থানা"),
        "postoffice" to listOf("dakghar", "post", "ডাকঘর"),
        "bank" to listOf("sbi", "atm", "ব্যাংক"),
        "nhmore" to listOf("highway", "nh14", "more", "মোড়"),
        "kalitala" to listOf("kali", "para", "কালী"),
    )

    fun search(query: String, extra: List<Place> = emptyList()): List<Place> {
        val q = query.trim().lowercase()
        val all = extra + TownData.landmarks
        if (q.isEmpty()) return all
        return all.map { p ->
            val score = when {
                p.nameEn.lowercase().startsWith(q) || p.nameBn.startsWith(q) -> 3
                p.nameEn.lowercase().contains(q) || p.nameBn.contains(q) -> 2
                aliases[p.id].orEmpty().any { it.lowercase().contains(q) || q.contains(it.lowercase()) } -> 1
                else -> 0
            }
            p to score
        }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
    }
}
