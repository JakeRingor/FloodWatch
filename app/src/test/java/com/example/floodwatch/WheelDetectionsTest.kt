package com.example.floodwatch

import org.junit.Assert.*
import org.junit.Test

class WheelDetectionsTest {
    @Test fun ignoresUnusedAndBackgroundScores() {
        val result = WheelDetections.decode(arrayOf(floatArrayOf(.5f,.5f,.2f,.2f)),
            arrayOf(floatArrayOf(10f,-10f,10f)))
        assertTrue(result.isEmpty())
    }
    @Test fun decodesNormalizedCenterBoxesAndSuppressesDuplicates() {
        val result = WheelDetections.decode(
            arrayOf(floatArrayOf(.5f,.5f,.2f,.4f), floatArrayOf(.5f,.5f,.2f,.4f), floatArrayOf(.1f,.1f,.1f,.1f)),
            arrayOf(floatArrayOf(-10f,2f,-10f),floatArrayOf(-10f,1f,-10f),floatArrayOf(-10f,1f,-10f)))
        assertEquals(2,result.size)
        assertEquals(.4f,result[0].left,.0001f)
        assertEquals(.3f,result[0].top,.0001f)
        assertEquals(.880797f,result[0].score,.0001f)
    }
    @Test fun rejectsInvalidBoxes() {
        assertTrue(WheelDetections.decode(arrayOf(floatArrayOf(Float.NaN,.5f,.2f,.2f)),
            arrayOf(floatArrayOf(0f,5f,0f))).isEmpty())
    }
}
