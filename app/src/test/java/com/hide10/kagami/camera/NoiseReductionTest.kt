package com.hide10.kagami.camera

import android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_FAST
import android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY
import android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_MINIMAL
import android.hardware.camera2.CameraMetadata.NOISE_REDUCTION_MODE_OFF
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoiseReductionTest {
    @Test
    fun asksForHighQualityOnlyWhenTheCameraListsIt() {
        assertTrue(hasHighQualityNoiseReduction(intArrayOf(NOISE_REDUCTION_MODE_OFF, NOISE_REDUCTION_MODE_FAST, NOISE_REDUCTION_MODE_HIGH_QUALITY)))
        assertTrue(hasHighQualityNoiseReduction(intArrayOf(NOISE_REDUCTION_MODE_HIGH_QUALITY)))
        assertFalse(hasHighQualityNoiseReduction(intArrayOf(NOISE_REDUCTION_MODE_OFF, NOISE_REDUCTION_MODE_FAST, NOISE_REDUCTION_MODE_MINIMAL)))
        assertFalse(hasHighQualityNoiseReduction(intArrayOf()))
        assertFalse(hasHighQualityNoiseReduction(null))
    }
}
