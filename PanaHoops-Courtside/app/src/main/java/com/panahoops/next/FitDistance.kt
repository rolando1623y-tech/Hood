package com.panahoops.next

/** Distance is estimated from observed steps; there is no GPS claim. */
object FitDistance {
    @JvmStatic
    fun meters(steps: Long, strideCentimeters: Int): Double {
        require(steps >= 0) { "No se admiten pasos negativos" }
        require(strideCentimeters in 40..150) { "Zancada fuera del rango permitido" }
        return steps.toDouble() * strideCentimeters / 100.0
    }
}
