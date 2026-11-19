package com.kbcode.kagami.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class GestureMotionTest {
    private fun pose(icon: GestureIcon, t: Float) = GestureMotion.pose(icon, t)

    private val steps = (0 until 2000).map { it / 2000f }

    /** 輪が生まれた(半径が小さく戻った)瞬間。 */
    private fun births(icon: GestureIcon) = steps.indices.filter { i ->
        pose(icon, steps[i]).ripples.zip(pose(icon, steps[(i + steps.size - 1) % steps.size]).ripples).any { (now, before) -> now.radius < before.radius }
    }.map { steps[it] }

    @Test
    fun theStillFrameShowsEachGestureAtItsClearest() {
        val tap = pose(GestureIcon.TAP, 0f)
        assertEquals(0f, tap.lift, 0f)
        assertEquals(1, tap.ripples.count { it.alpha > 0.5f })

        val double = pose(GestureIcon.DOUBLE_TAP, 0f)
        assertEquals(0f, double.lift, 0f)
        assertEquals(2, double.ripples.count { it.alpha > 0.5f })
        assertTrue(double.ripples.maxOf { it.radius } > double.ripples.minOf { it.radius } + 2f)

        val long = pose(GestureIcon.LONG_PRESS, 0f)
        assertTrue(long.lift < 0f)
        assertTrue(long.fill in 0.6f..0.9f)
        assertEquals(1f, long.fillAlpha, 0f)

        assertEquals(1f, pose(GestureIcon.PINCH, 0f).spread, 0f)
        assertEquals(0f, pose(GestureIcon.TWO_FINGER_HORIZONTAL, 0f).slide, 0f)
        assertEquals(0f, pose(GestureIcon.TWO_FINGER_TAP, 0f).lift, 0f)
    }

    @Test
    fun theLoopEndsWhereItStartsSoTheStillFrameIsTheSameAtEitherEnd() {
        for (icon in GestureIcon.entries) {
            assertEquals("$icon", pose(icon, 0f), pose(icon, 1f))
            val start = pose(icon, 0f)
            val end = pose(icon, 0.9995f)
            assertEquals("$icon", start.lift, end.lift, 0.01f)
            assertEquals("$icon", start.slide, end.slide, 0.01f)
            assertEquals("$icon", start.spread, end.spread, 0.01f)
            assertEquals("$icon", start.fill, end.fill, 0.01f)
        }
    }

    @Test
    fun aTapTouchesOnceADoubleTapTwiceAndEveryRippleStartsUnderTheFinger() {
        val counts = mapOf(GestureIcon.TAP to 1, GestureIcon.DOUBLE_TAP to 2, GestureIcon.TWO_FINGER_TAP to 1)
        for ((icon, count) in counts) {
            val births = births(icon)
            assertEquals("$icon", count, births.size)
            for (t in births) assertEquals("$icon $t", 0f, pose(icon, t).lift, 0.01f)
        }
    }

    @Test
    fun theLongPressHoldsTheFingerDownWhileTheRingFillsAndLetsGoOnlyOnceItIsFull() {
        var last = 0f
        for (t in steps) {
            val p = pose(GestureIcon.LONG_PRESS, t)
            if (p.fill in 0.01f..0.99f) assertTrue("$t", p.lift < 0f)
            if (p.lift > 0f) assertTrue("$t", p.fill == 0f || p.fill == 1f)
            if (p.fill < last) assertEquals("$t", 0f, p.fillAlpha, 0.01f)
            last = p.fill
        }
    }

    @Test
    fun theMovesStayModestAndGoBothWays() {
        for (icon in GestureIcon.entries) {
            for (t in steps) {
                val p = pose(icon, t)
                assertTrue("$icon $t", abs(p.lift) <= 1.5f && abs(p.slide) <= 2.5f)
                assertTrue("$icon $t", p.ripples.all { it.radius <= 6.6f && it.alpha in 0f..1f })
            }
        }
        val slides = steps.map { pose(GestureIcon.TWO_FINGER_HORIZONTAL, it).slide }
        assertTrue(slides.min() < -2f && slides.max() > 2f)
        val spreads = steps.map { pose(GestureIcon.PINCH, it).spread }
        assertEquals(0f, spreads.min(), 0f)
        assertEquals(1f, spreads.max(), 0f)
    }
}
