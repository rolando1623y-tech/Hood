package com.panahoops.next

/** Camera2's advertised normal-session AE ranges are hints, not guaranteed FPS. */
object FpsPolicy {
    @JvmStatic
    fun best(advertised: List<Pair<Int, Int>>, desired: Int): Pair<Int, Int>? {
        if (desired <= 0 || advertised.isEmpty()) return null
        val candidates = advertised.filter { it.first > 0 && it.second >= it.first && it.second <= desired }
        return candidates.maxWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenBy { it.first })
    }
}
