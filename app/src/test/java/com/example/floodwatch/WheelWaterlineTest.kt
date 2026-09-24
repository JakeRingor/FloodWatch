package com.example.floodwatch

import org.junit.Assert.*
import org.junit.Test

class WheelWaterlineTest {
    @Test fun flatCropDoesNotInventWater() {
        assertNull(WheelWaterline.candidate(40,100,FloatArray(4000){100f},FloatArray(4000)))
    }
    @Test fun clearBoundaryProducesNearbyCandidate() {
        val gray = FloatArray(4000) { if (it/40 < 70) 20f else 180f }
        val row = WheelWaterline.candidate(40,100,gray,FloatArray(4000))
        assertNotNull(row)
        assertTrue(row!! in 67..73)
    }
    @Test fun tinyCropHasNoEstimate() {
        assertNull(WheelWaterline.candidate(4,4,FloatArray(16),FloatArray(16)))
    }
    @Test fun invalidGeometryIsUnknownNotZeroDepth() {
        assertNull(WheelWaterline.fraction(10f,10f,10f))
        assertNull(WheelWaterline.fraction(10f,100f,120f))
        assertNull(WheelWaterline.fraction(10f,100f,Float.NaN))
    }
    @Test fun fractionUsesFullHeight() {
        assertEquals(.25f,WheelWaterline.fraction(100f,300f,250f)!!,.001f)
    }
}
