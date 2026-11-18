package com.hide10.kagami.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    fun warmTintsRedAndCoolTintsBlue() {
        val warm = gains(ColorAdjust(kelvin = 3000f).matrix())
        val cool = gains(ColorAdjust(kelvin = 9000f).matrix())
        assertTrue("$warm", warm.r > warm.g && warm.g > warm.b)
        assertTrue("$cool", cool.b > cool.r)
        assertTrue(!ColorAdjust(kelvin = 3000f).isNeutral)
    }

    @Test
    fun exposureDoublesPerStop() {
        assertEquals(2f, gains(ColorAdjust(exposureEv = 1f).matrix()).g, 1e-5f)
        assertEquals(0.5f, gains(ColorAdjust(exposureEv = -1f).matrix()).b, 1e-5f)
    }

    @Test
    fun matrixNeverTouchesAlphaOrAddsOffsets() {
        val m = ColorAdjust(exposureEv = 1.3f, kelvin = 4000f).matrix()
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
    fun lightBrightnessScalesTheStrongestChannel() {
        assertEquals(0.4f, ScreenLight(brightness = 0.4f, kelvin = 4000f).color().max, 1e-6f)
    }
}
