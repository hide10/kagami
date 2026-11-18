package com.hide10.kagami.camera

import android.hardware.camera2.CameraMetadata.EDGE_MODE_FAST
import android.hardware.camera2.CameraMetadata.EDGE_MODE_HIGH_QUALITY
import android.hardware.camera2.CameraMetadata.EDGE_MODE_OFF
import android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_FAST
import android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
import android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL
import android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_OFF
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityModeTest {
    @Test
    fun asksForHighQualityNoiseReductionOnlyWhenTheCameraListsIt() {
        val hq = NOISE_REDUCTION_MODE_HIGH_QUALITY
        assertTrue(offers(intArrayOf(NOISE_REDUCTION_MODE_OFF, NOISE_REDUCTION_MODE_FAST, hq), hq))
        assertTrue(offers(intArrayOf(hq), hq))
        assertFalse(offers(intArrayOf(NOISE_REDUCTION_MODE_OFF, NOISE_REDUCTION_MODE_FAST, NOISE_REDUCTION_MODE_MINIMAL), hq))
        assertFalse(offers(intArrayOf(), hq))
        assertFalse(offers(null, hq))
    }

    @Test
    fun asksForHighQualityEdgesOnlyWhenTheCameraListsIt() {
        assertTrue(offers(intArrayOf(EDGE_MODE_OFF, EDGE_MODE_FAST, EDGE_MODE_HIGH_QUALITY), EDGE_MODE_HIGH_QUALITY))
        assertFalse(offers(intArrayOf(EDGE_MODE_OFF, EDGE_MODE_FAST), EDGE_MODE_HIGH_QUALITY))
        assertFalse(offers(null, EDGE_MODE_HIGH_QUALITY))
    }
}
