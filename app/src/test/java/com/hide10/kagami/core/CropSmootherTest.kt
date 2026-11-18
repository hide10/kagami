package com.hide10.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

class CropSmootherTest {
    private val from = Crop.full(PHONE)
    private val to = Crop(300f, 600f, 4f)

    @Test
    fun reachesTheTargetWithinTwoSeconds() {
        val c = settle(from, to, seconds = 2f)
        assertEquals(to.cx, c.cx, 0.5f)
        assertEquals(to.cy, c.cy, 0.5f)
        assertEquals(to.zoom, c.zoom, 0.01f)
    }

    @Test
    fun approachesWithoutOvershoot() {
        var c = from
        repeat(200) {
            val next = CropSmoother.step(c, to, 1f / 60)
            assertTrue(abs(to.cx - next.cx) <= abs(to.cx - c.cx))
            assertTrue(abs(to.cy - next.cy) <= abs(to.cy - c.cy))
            assertTrue(abs(to.zoom - next.zoom) <= abs(to.zoom - c.zoom))
            assertTrue((next.cx - c.cx) * (to.cx - from.cx) >= 0)
            c = next
        }
    }

    @Test
    fun aLongStallStillMovesOnlyAFractionOfTheWay() {
        val maxFrac = 1f - exp(-CropSmoother.MAX_DT_SEC / CropSmoother.TAU_SEC)
        val c = CropSmoother.step(from, to, dtSec = 5f)
        assertTrue(abs(c.cx - from.cx) <= maxFrac * abs(to.cx - from.cx) + 1e-3f)
        assertTrue(abs(c.cy - from.cy) <= maxFrac * abs(to.cy - from.cy) + 1e-3f)
        assertTrue(ln(c.zoom) <= maxFrac * ln(to.zoom) + 1e-4f)
        assertTrue(maxFrac < 0.4f)
    }

    @Test
    fun zeroOrNegativeTimeDoesNotMove() {
        assertEquals(from, CropSmoother.step(from, to, 0f))
        assertEquals(from, CropSmoother.step(from, to, -1f))
    }

    @Test
    fun zoomingInAndOutTakesTheSameTime() {
        val inStep = CropSmoother.step(Crop(540f, 960f, 1f), Crop(540f, 960f, 4f), 1f / 60)
        val outStep = CropSmoother.step(Crop(540f, 960f, 4f), Crop(540f, 960f, 1f), 1f / 60)
        assertEquals(ln(inStep.zoom), ln(4f) - ln(outStep.zoom), 1e-5f)
    }

    @Test
    fun theWayBetweenTwoValidCropsNeverShowsOutsideTheImage() {
        val corner = Crop(0f, 0f, 5f).clamp(PHONE)
        val far = Crop(PHONE.w, PHONE.h, 1.2f).clamp(PHONE)
        for ((a, b) in listOf(corner to far, far to corner, Crop.full(PHONE) to corner)) {
            var c = a
            repeat(300) {
                c = CropSmoother.step(c, b, 1f / 60)
                assertShowsOnlyImage(c, PHONE)
            }
        }
    }
}
