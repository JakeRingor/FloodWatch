package com.example.floodwatch

import kotlin.math.abs

/** Experimental row-boundary heuristic adapted from combined v2.ipynb, final cell.
 * This finds a visual boundary, not proof of water. Operates on unannotated pixels. */
object WheelWaterline {
    fun candidate(width: Int, height: Int, gray: FloatArray, saturation: FloatArray): Int? {
        require(gray.size == width * height && saturation.size == gray.size)
        if (width < 16 || height < 16) return null
        fun meanRow(data: FloatArray, row: Int): Double =
            (0 until width).sumOf { data[row * width + it].toDouble() } / width
        val scores = mutableListOf<Pair<Double, Int>>()
        val start = (height * .45).toInt()
        val end = (height * .95).toInt()
        for (row in start until end) {
            if (row < 3 || row + 3 >= height) continue
            val brightness = abs((1..3).sumOf { meanRow(gray, row-it) - meanRow(gray, row+it) } / 3)
            val sat = abs((1..3).sumOf { meanRow(saturation, row-it) - meanRow(saturation, row+it) } / 3)
            val gradient = (0 until width).sumOf { abs(gray[row*width+it] - gray[(row-1)*width+it]).toDouble() } / width
            val strong = (0 until width).count { abs(gray[(row-2)*width+it] - gray[(row+2)*width+it]) > 20 }.toDouble() / width
            val score = (brightness*.4 + sat*.2 + gradient*.2 + strong*20) *
                (.75 + .50*(row-start)/(end-start).coerceAtLeast(1))
            // Unlike the notebook, reject featureless crops instead of inventing a waterline.
            if (score >= 12.0 && strong >= .2) scores.add(score to row)
        }
        val groups = mutableListOf<MutableList<Int>>()
        for ((_, row) in scores.sortedByDescending { it.first }.take(40)) {
            val group = groups.firstOrNull { abs(row - it.average()) <= 10 }
            if (group == null) groups.add(mutableListOf(row)) else group.add(row)
        }
        return groups.maxByOrNull { it.size*10 + it.average()/height*20 }?.average()?.toInt()
    }

    fun fraction(top: Float, bottom: Float, waterline: Float): Float? {
        if (!top.isFinite() || !bottom.isFinite() || !waterline.isFinite() || bottom <= top ||
            waterline !in top..bottom) return null
        return (bottom-waterline)/(bottom-top)
    }
}
