package com.kbcode.kagami.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GestureMotionTest {
    private fun pose(icon: GestureIcon, t: Float) = GestureMotion.pose(icon, t)

    private val steps = (0 until 2000).map { it / 2000f }

    /** 56dp の絵での 1dp を、絵の格子(96×96)の単位で。 */
    private val dp = 96f / 56f

    private val taps = listOf(GestureIcon.TAP, GestureIcon.THREE_FINGER_TAP, GestureIcon.TWO_FINGER_TAP)

    /** 輪が生まれた(広がりが戻った)瞬間。 */
    private fun births(icon: GestureIcon) = steps.indices.filter { i ->
        pose(icon, steps[i]).ringGrow < pose(icon, steps[(i + steps.size - 1) % steps.size]).ringGrow
    }.map { steps[it] }

    @Test
    fun theStillFrameIsTheApprovedPicture() {
        for (icon in taps) {
            val p = pose(icon, 0f)
            assertEquals("$icon", 0f, p.lift, 0f)
            assertEquals("$icon", 0f, p.ringGrow, 0f)
            assertEquals("$icon", 1f, p.ringAlpha, 0f)
        }

        val long = pose(GestureIcon.LONG_PRESS, 0f)
        assertEquals(0f, long.lift, 0f)
        assertEquals(1f, long.ringAlpha, 0f)
        // 下絵の輪は 12 時から 234 度まで満ちている。
        assertEquals(234f / 360f, long.fill, 0.01f)

        assertEquals(1f, pose(GestureIcon.PINCH, 0f).spread, 0f)
        assertEquals(0f, pose(GestureIcon.TWO_FINGER_HORIZONTAL, 0f).slide, 0f)
    }

    @Test
    fun theLoopEndsWhereItStartsSoTheStillFrameIsTheSameAtEitherEnd() {
        for (icon in GestureIcon.entries) {
            assertEquals("$icon", pose(icon, 0f), pose(icon, 1f))
            val start = pose(icon, 0f)
            val end = pose(icon, 0.9995f)
            // 最後の一歩で 0.05dp より大きく跳ばない。
            assertEquals("$icon", start.lift, end.lift, 0.05f * dp)
            assertEquals("$icon", start.slide, end.slide, 0.05f * dp)
            assertEquals("$icon", start.spread, end.spread, 0.01f)
            assertEquals("$icon", start.fill, end.fill, 0.01f)
        }
    }

    @Test
    fun everyTapTouchesOnceAndItsRingStartsWhileTheFingersAreDown() {
        for (icon in taps) {
            val births = births(icon)
            assertEquals("$icon", listOf(0f), births)
            assertTrue("$icon", steps.any { pose(icon, it).lift > 1.5f * dp })
        }
    }

    @Test
    fun theLongPressHoldsTheFingerDownWhileTheRingFillsAndLetsGoOnlyOnceItIsFull() {
        var last = 0f
        for (t in steps) {
            val p = pose(GestureIcon.LONG_PRESS, t)
            if (p.fill in 0.01f..0.99f) assertEquals("$t", 0f, p.lift, 0.01f)
            if (p.lift > 0.01f) assertTrue("$t", p.fill == 0f || p.fill == 1f)
            if (p.fill < last) assertEquals("$t", 0f, p.ringAlpha, 0.01f)
            last = p.fill
        }
        // 空から満ちるまで、2 秒のループで 1 秒ほど。
        val filling = steps.count { pose(GestureIcon.LONG_PRESS, it).fill in 0.001f..0.999f } / steps.size.toFloat()
        assertEquals(1000f, filling * GestureIcon.LONG_PRESS.loopMs, 50f)
    }

    @Test
    fun theMovesStayModestAndGoBothWays() {
        for (icon in GestureIcon.entries) {
            assertTrue("$icon", icon.loopMs in 1200..2000)
            for (t in steps) {
                val p = pose(icon, t)
                assertTrue("$icon $t", p.lift in 0f..3 * dp && abs(p.slide) <= 6 * dp && p.ringGrow in 0f..3 * dp)
                assertTrue("$icon $t", p.ringAlpha in 0f..1f && p.fill in 0f..1f)
            }
        }
        val slides = steps.map { pose(GestureIcon.TWO_FINGER_HORIZONTAL, it).slide }
        assertTrue(slides.min() < -4 * dp && slides.max() > 4 * dp)
        val spreads = steps.map { pose(GestureIcon.PINCH, it).spread }
        assertEquals(0f, spreads.min(), 0f)
        assertEquals(1f, spreads.max(), 0f)
    }
}
