package com.panahoops.next

/** Pure data-selection policy, separated from database I/O for deterministic testing. */
object ResetPolicy {
    @JvmStatic
    fun matches(scope: String, from: Long, until: Long, value: String,
                kind: String, timestamp: Long, id: String, player: String, mode: String): Boolean {
        require(from >= 0 && until > from)
        val inPeriod = timestamp >= from && timestamp < until
        return when (scope) {
            "ALL" -> true
            "DAY", "RANGE" -> inPeriod
            "LAST_SESSION" -> kind == "session" && id == value
            "PLAYER" -> kind == "match" && player.trim().equals(value.trim(), ignoreCase = true)
            "MODE" -> kind == "session" && mode == value
            "SHOTS" -> kind == "shot" || kind == "match"
            "EXERCISES" -> kind == "session"
            "FIT" -> kind == "fit"
            else -> false
        }
    }
}
