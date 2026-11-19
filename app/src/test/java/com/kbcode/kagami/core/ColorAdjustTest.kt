package com.kbcode.kagami.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorAdjustTest {
    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun gains(m: FloatArray) = Rgb(m[0], m[6], m[12])

    @Test
    fun defaultIsUntouched() {
        assertTrue(ColorAdjust().isNeutral)
        assertArrayEquals(identity, ColorAdjust().matrix(), 1e-6f)
    }

    @Test
    fun exposureDoublesPerStopAndLeavesTheColorAlone() {
        assertEquals(Rgb(2f, 2f, 2f), gains(ColorAdjust(exposureEv = 1f).matrix()))
        assertEquals(Rgb(0.5f, 0.5f, 0.5f), gains(ColorAdjust(exposureEv = -1f).matrix()))
    }

    @Test
    fun slidingBackNearTheMiddleLeavesThePreviewUntouched() {
        val adjusted = ColorAdjust().withExposure(1.2f)
        assertTrue(!adjusted.isNeutral)
        val back = adjusted.withExposure(-0.1f)
        assertTrue("$back", back.isNeutral)
        assertArrayEquals(identity, back.matrix(), 0f)
    }

    @Test
    fun theExposureBarRunsFromDarkestOnTheLeftThroughUntouchedInTheMiddle() {
        assertEquals(0f, ColorAdjust().withExposure(-2f).exposureFraction, 1e-6f)
        assertEquals(0.5f, ColorAdjust().exposureFraction, 1e-6f)
        assertEquals(0.75f, ColorAdjust().withExposure(1f).exposureFraction, 1e-6f)
        assertEquals(1f, ColorAdjust().withExposure(2f).exposureFraction, 1e-6f)
    }

    @Test
    fun theExposureBarStopsOnEveryThirdOfAStopAndOnUntouched() {
        // スライダーは両端と EV_TICKS 個の目盛りを等しい間隔で並べる。
        val positions = ColorAdjust.EV_TICKS + 2
        val stops = (0 until positions).map { -2f + 4f * it / (positions - 1) }
        assertEquals(1f / 3f, stops[1] - stops[0], 1e-6f)
        for (ev in stops) assertEquals(ev, ColorAdjust().withExposure(ev).exposureEv, 1e-6f)
        assertTrue(stops.any { ColorAdjust().withExposure(it).isNeutral })
    }

    @Test
    fun draggingRightBrightensAndAcrossTheWholeWidthCoversTheWholeRange() {
        val width = 1080f
        assertEquals(1f, ColorAdjust().withExposureDragged(0f, width / 4, width).exposureEv, 1e-6f)
        assertEquals(-1f, ColorAdjust().withExposureDragged(0f, -width / 4, width).exposureEv, 1e-6f)
        assertEquals(2f, ColorAdjust().withExposureDragged(-2f, width, width).exposureEv, 1e-6f)
        assertEquals(2f, ColorAdjust().withExposureDragged(1f, width, width).exposureEv, 1e-6f)
    }

    @Test
    fun aDragMovesTheExposureInThirdsCountedFromWhereItStarted() {
        val width = 1080f
        val third = width / 12
        val c = ColorAdjust()
        assertEquals(1f / 3, c.withExposureDragged(0f, third, width).exposureEv, 1e-6f)
        assertEquals(0f, c.withExposureDragged(0f, third * 0.4f, width).exposureEv, 0f)
        assertEquals(-1f / 3, c.withExposureDragged(1f / 3, -third * 1.6f, width).exposureEv, 1e-6f)
    }

    @Test
    fun exposureStopsAtTheEndsOfItsRange() {
        assertEquals(2f, ColorAdjust().withExposure(5f).exposureEv, 0f)
        assertEquals(-2f, ColorAdjust().withExposure(-5f).exposureEv, 0f)
    }

    @Test
    fun matrixNeverTouchesAlphaOrAddsOffsets() {
        val m = ColorAdjust(exposureEv = 1.3f).matrix()
        assertEquals(20, m.size)
        assertEquals(1f, m[18])
        for (i in listOf(4, 9, 14, 19)) assertEquals(0f, m[i])
    }

    @Test
    fun neutralLightIsWhiteAndWarmLightIsOrange() {
        val white = ScreenLight(kelvin = 6500f).color()
        assertTrue("$white", white.r > 0.97f && white.g > 0.97f && white.b > 0.97f)
        val warm = ScreenLight(kelvin = 3000f).color()
        assertEquals(1f, warm.r, 1e-6f)
        assertTrue("$warm", warm.r > warm.g && warm.g > warm.b)
    }

    @Test
    fun lightColorIsAlwaysFullIntensity() {
        for (k in listOf(2700f, 4000f, 6500f, 8000f)) {
            val c = ScreenLight(thicknessDp = 16f, kelvin = k).color()
            assertEquals("$k", 1f, c.max, 1e-6f)
        }
    }

    @Test
    fun lightColorFollowsKelvin() {
        val warm = ScreenLight(kelvin = 2700f).color()
        val cool = ScreenLight(kelvin = 8000f).color()
        assertTrue("$warm $cool", cool.b > warm.b && cool.g > warm.g)
        assertEquals(1f, cool.b, 1e-6f)
    }

    @Test
    fun lightThicknessStaysInRangeAndSnapsToFourDp() {
        assertEquals(120f, ScreenLight().withThickness(500f).thicknessDp, 0f)
        assertEquals(16f, ScreenLight().withThickness(0f).thicknessDp, 0f)
        assertEquals(48f, ScreenLight().withThickness(49.9f).thicknessDp, 0f)
        assertEquals(52f, ScreenLight().withThickness(50.1f).thicknessDp, 0f)
        assertTrue(ScreenLight().thicknessDp in ScreenLight.THICKNESS_DP)
    }

    @Test
    fun theShrinkLightMakesThePreviewSmallerTheMoreLightItGives() {
        val shrink = ScreenLight(on = true, mode = LightMode.SHRINK)
        assertEquals(0.95f, shrink.withThickness(16f).previewScale, 1e-6f)
        assertEquals(0.775f, shrink.withThickness(68f).previewScale, 1e-6f)
        assertEquals(0.6f, shrink.withThickness(120f).previewScale, 1e-6f)
        val scales = (16..120 step 4).map { shrink.withThickness(it.toFloat()).previewScale }
        assertTrue("$scales", scales.zipWithNext().all { (a, b) -> b < a })
    }

    @Test
    fun onlyTheShrinkLightShrinksThePreview() {
        assertEquals(1f, ScreenLight(on = false, thicknessDp = 120f, mode = LightMode.SHRINK).previewScale, 0f)
        assertEquals(1f, ScreenLight(on = true, thicknessDp = 120f, mode = LightMode.BAND).previewScale, 0f)
    }

    @Test
    fun eachModeGlowsOnlyWhileTheLightIsOnInThatMode() {
        val band = ScreenLight(on = true, mode = LightMode.BAND)
        assertTrue(band.glows(LightMode.BAND))
        assertFalse(band.glows(LightMode.SHRINK))
        val shrink = band.copy(mode = LightMode.SHRINK)
        assertTrue(shrink.glows(LightMode.SHRINK))
        assertFalse(shrink.glows(LightMode.BAND))
        assertFalse(band.copy(on = false).glows(LightMode.BAND))
        assertFalse(shrink.copy(on = false).glows(LightMode.SHRINK))
    }
}
