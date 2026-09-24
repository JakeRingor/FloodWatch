package com.example.floodwatch

import kotlin.math.exp

internal object WheelDetections {
    data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float, val score: Float)

    // COCO annotations use category 1. Slot 0 is unused; slot 2 is background.
    fun decode(coordinates: Array<FloatArray>, logits: Array<FloatArray>): List<Box> {
        require(coordinates.size == logits.size)
        val candidates = coordinates.indices.mapNotNull { i ->
            val b = coordinates[i]
            require(b.size == 4 && logits[i].size == 3)
            val score = (1.0 / (1.0 + exp(-logits[i][1].toDouble()))).toFloat()
            if (!score.isFinite() || score < .4f || b.any { !it.isFinite() } || b[2] <= 0 || b[3] <= 0) return@mapNotNull null
            val box = Box((b[0] - b[2]/2).coerceIn(0f,1f), (b[1] - b[3]/2).coerceIn(0f,1f),
                (b[0] + b[2]/2).coerceIn(0f,1f), (b[1] + b[3]/2).coerceIn(0f,1f), score)
            box.takeIf { it.right > it.left && it.bottom > it.top }
        }.sortedByDescending { it.score }
        val kept = mutableListOf<Box>()
        for (box in candidates) if (kept.none { iou(it, box) > .5f }) kept.add(box)
        return kept
    }

    private fun iou(a: Box, b: Box): Float {
        val intersection = (minOf(a.right,b.right)-maxOf(a.left,b.left)).coerceAtLeast(0f) *
            (minOf(a.bottom,b.bottom)-maxOf(a.top,b.top)).coerceAtLeast(0f)
        return intersection / ((a.right-a.left)*(a.bottom-a.top)+(b.right-b.left)*(b.bottom-b.top)-intersection)
    }
}
