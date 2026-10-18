package com.example.floodwatch.util

data class FloodReferenceCandidate<T>(
    val value: T,
    val severity: Int,
    val depthCentimeters: Double,
    val elevationMeters: Double?
)

object FloodReferenceSelector {
    /** Deepest severity/depth wins; the lower elevation breaks equal-depth ties. */
    fun <T> select(candidates: List<FloodReferenceCandidate<T>>): T? = candidates
        .maxWithOrNull(
            compareBy<FloodReferenceCandidate<T>> { it.severity.coerceIn(1, 4) }
                .thenBy { it.depthCentimeters }
                .thenBy { -(it.elevationMeters ?: Double.MAX_VALUE) }
        )
        ?.value

    fun depthForRange(range: String?, severity: Int): Double {
        val normalized = range.orEmpty().replace(" ", "")
        return when {
            normalized.startsWith("0-5") -> 5.0
            normalized.startsWith("6-15") -> 15.0
            normalized.startsWith("16-30") -> 30.0
            normalized.startsWith("31-50") -> 50.0
            normalized.startsWith("50+") -> 75.0
            else -> when (severity.coerceIn(1, 4)) {
                4 -> 75.0
                3 -> 50.0
                2 -> 30.0
                else -> 15.0
            }
        }
    }
}
