package com.hide10.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceHoldTest {
    private val ms = 1_000_000L
    private val a = face(Pt(500f, 900f), 100f)
    private val b = face(Pt(600f, 1000f), 120f)

    @Test
    fun nothingBeforeTheFirstFace() {
        assertNull(FaceHold().get(0L))
    }

    @Test
    fun keepsTheLastFaceForSixHundredMilliseconds() {
        val hold = FaceHold()
        hold.seen(a, 1000 * ms)
        assertEquals(a, hold.get(1000 * ms))
        assertEquals(a, hold.get(1600 * ms))
        assertNull(hold.get(1601 * ms))
    }

    @Test
    fun aNewSightingReplacesTheFaceAndRestartsTheClock() {
        val hold = FaceHold()
        hold.seen(a, 0L)
        hold.seen(b, 500 * ms)
        assertEquals(b, hold.get(1000 * ms))
        assertNull(hold.get(1101 * ms))
    }
}
