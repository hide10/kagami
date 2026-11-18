package com.hide10.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class ZoomStripTest {
    private val length = 1200f

    @Test
    fun theWholeStripUpGoesFromTheWholeViewToTenTimesAndBack() {
        val up = ZoomStrip.drag(Crop.full(PHONE), -length, length, PHONE)
        assertEquals(10f, up.zoom, 1e-3f)
        assertEquals(1f, ZoomStrip.drag(up, length, length, PHONE).zoom, 1e-3f)
    }

    @Test
    fun equalDistancesGiveEqualRatios() {
        val a = ZoomStrip.drag(Crop.full(PHONE), -length / 4, length, PHONE)
        val b = ZoomStrip.drag(a, -length / 4, length, PHONE)
        val c = ZoomStrip.drag(b, -length / 4, length, PHONE)
        assertEquals(10f.pow(0.25f), a.zoom, 1e-4f)
        assertEquals(a.zoom, b.zoom / a.zoom, 1e-4f)
        assertEquals(a.zoom, c.zoom / b.zoom, 1e-4f)
    }

    @Test
    fun manySmallStepsAddUpToOneLongDrag() {
        var c = Crop.full(PHONE)
        repeat(60) { c = ZoomStrip.drag(c, -length / 120, length, PHONE) }
        assertEquals(10f.pow(0.5f), c.zoom, 1e-3f)
    }

    @Test
    fun zoomingKeepsWhatIsInTheMiddleOfTheView() {
        val shown = Crop(450f, 1000f, 3f)
        val closer = ZoomStrip.drag(shown, -length / 5, length, PHONE)
        assertTrue("$closer", closer.zoom > shown.zoom)
        assertPt(PHONE.center, screenOf(shown.center, closer, PHONE))
        val wider = ZoomStrip.drag(shown, length / 10, length, PHONE)
        assertTrue("$wider", wider.zoom < shown.zoom)
        assertPt(PHONE.center, screenOf(shown.center, wider, PHONE))
    }

    @Test
    fun theZoomStopsAtTheEndsAndNeverShowsOutsideTheImage() {
        assertEquals(1f, ZoomStrip.drag(Crop.full(PHONE), length / 3, length, PHONE).zoom, 0f)
        assertEquals(10f, ZoomStrip.drag(Crop(540f, 960f, 8f), -length, length, PHONE).zoom, 0f)
        assertShowsOnlyImage(ZoomStrip.drag(Crop(150f, 200f, 6f), length / 2, length, PHONE), PHONE)
    }

    @Test
    fun aFrozenFrameStopsTheZoomOutAtItsEdge() {
        val frame = Crop(540f, 960f, 2f)
        assertEquals(2f, ZoomStrip.drag(Crop(540f, 960f, 4f), length, length, PHONE, frame).zoom, 1e-4f)
    }

    @Test
    fun theThumbRunsFromBottomAtOneTimeToTopAtTheLimit() {
        assertEquals(0f, ZoomStrip.thumb(1f), 1e-6f)
        assertEquals(0.5f, ZoomStrip.thumb(10f.pow(0.5f)), 1e-6f)
        assertEquals(1f, ZoomStrip.thumb(10f), 1e-6f)
    }

    @Test
    fun theThumbMovesAsFarAsTheFinger() {
        val from = Crop(540f, 960f, 2f)
        val to = ZoomStrip.drag(from, -0.3f * length, length, PHONE)
        assertEquals(0.3f, ZoomStrip.thumb(to.zoom) - ZoomStrip.thumb(from.zoom), 1e-4f)
    }
}
