package com.hide10.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CropPlannerTest {
    private val anchor = Pt(0.5f * PHONE.w, 0.45f * PHONE.h)
    private val lock = ViewIntent.FaceLock()

    private fun focusOf(f: FaceLandmarks) = f.eyeMid.midTo(f.nose)

    @Test
    fun faceLockSettlesEveryFaceAtTheSameSpotAndSize() {
        val faces = listOf(
            face(Pt(540f, 900f), 120f),
            face(Pt(400f, 700f), 90f),
            face(Pt(700f, 1200f), 150f),
            face(Pt(600f, 1000f), 200f),
        )
        for (f in faces) {
            val shown = settle(Crop.full(PHONE), CropPlanner.target(lock, f, PHONE))
            assertPt(anchor, screenOf(focusOf(f), shown, PHONE), eps = 1f)
            val eyesOnScreen = screenOf(f.rightEye, shown, PHONE).x - screenOf(f.leftEye, shown, PHONE).x
            assertEquals("face $f", 0.30f * PHONE.w, eyesOnScreen, 0.01f * PHONE.w)
        }
    }

    @Test
    fun faceLockNearTheEdgeStaysInsideTheImage() {
        val rnd = Random(3)
        repeat(1000) {
            val f = face(Pt(rnd.nextFloat() * PHONE.w, rnd.nextFloat() * PHONE.h), 20f + rnd.nextFloat() * 600f)
            assertShowsOnlyImage(CropPlanner.target(lock, f, PHONE), PHONE)
        }
    }

    @Test
    fun aFaceTooBigForTheTargetShowsTheWholeFrame() {
        val huge = face(PHONE.center, 0.6f * PHONE.w)
        assertEquals(1f, CropPlanner.target(lock, huge, PHONE).zoom)
    }

    @Test
    fun withoutAFaceFaceIntentsShowTheWholeFrame() {
        val full = Crop.full(PHONE)
        assertEquals(full, CropPlanner.target(lock, null, PHONE))
        assertEquals(full, CropPlanner.target(ViewIntent.FaceLock(zoomMul = 2f), null, PHONE))
        for (part in FacePart.entries) {
            assertEquals(full, CropPlanner.target(ViewIntent.Part(part), null, PHONE))
        }
    }

    @Test
    fun partBringsThatLandmarkToTheCenter() {
        val f = face(Pt(540f, 900f), 250f)
        val expected = mapOf(FacePart.EYES to f.eyeMid, FacePart.NOSE to f.nose, FacePart.MOUTH to f.mouth)
        for ((part, landmark) in expected) {
            val crop = CropPlanner.target(ViewIntent.Part(part), f, PHONE)
            assertPt(PHONE.center, screenOf(landmark, crop, PHONE))
            assertEquals(part.eyeFrac * PHONE.w / 250f, crop.zoom, 1e-3f)
        }
    }

    @Test
    fun partsZoomCloserThanTheWholeFace() {
        val f = face(Pt(540f, 900f), 120f)
        val faceZoom = CropPlanner.target(lock, f, PHONE).zoom
        for (part in FacePart.entries) {
            assertTrue("$part", CropPlanner.target(ViewIntent.Part(part), f, PHONE).zoom > faceZoom)
        }
    }

    @Test
    fun pointCentersTheTappedSpotAtPointZoomWithoutAFace() {
        val spot = Pt(500f, 1100f)
        val crop = CropPlanner.target(ViewIntent.Point(spot), null, PHONE)
        assertPt(PHONE.center, screenOf(spot, crop, PHONE))
        assertEquals(CropPlanner.POINT_ZOOM, crop.zoom)
    }

    @Test
    fun pointNearACornerStaysInsideTheImage() {
        val crop = CropPlanner.target(ViewIntent.Point(Pt(5f, 5f)), null, PHONE)
        assertShowsOnlyImage(crop, PHONE)
        assertEquals(CropPlanner.POINT_ZOOM, crop.zoom)
    }

    @Test
    fun zoomMulScalesTheZoomAndKeepsTheFaceAtTheAnchor() {
        val f = face(Pt(540f, 900f), 120f)
        val base = CropPlanner.target(lock, f, PHONE)
        val closer = CropPlanner.target(ViewIntent.FaceLock(zoomMul = 1.5f), f, PHONE)
        assertEquals(base.zoom * 1.5f, closer.zoom, 1e-3f)
        assertPt(anchor, screenOf(focusOf(f), closer, PHONE))
    }

    @Test
    fun pinchingPastMaxThenBackZoomsOutRightAway() {
        val f = face(Pt(540f, 900f), 120f)
        var intent: ViewIntent = lock
        repeat(20) { intent = CropPlanner.pinch(intent, 1.5f, f, Crop.full(PHONE), PHONE) }
        assertEquals(Crop.MAX_ZOOM, CropPlanner.target(intent, f, PHONE).zoom, 1e-3f)
        intent = CropPlanner.pinch(intent, 0.5f, f, Crop.full(PHONE), PHONE)
        assertEquals(Crop.MAX_ZOOM / 2, CropPlanner.target(intent, f, PHONE).zoom, 1e-3f)
        assertTrue(intent is ViewIntent.FaceLock)
    }

    @Test
    fun pinchingAHugeFaceStillZoomsIn() {
        val huge = face(PHONE.center, 0.6f * PHONE.w)
        val intent = CropPlanner.pinch(lock, 2f, huge, Crop.full(PHONE), PHONE)
        assertEquals(2f, CropPlanner.target(intent, huge, PHONE).zoom, 1e-3f)
    }

    @Test
    fun pinchingWithoutAFaceZoomsAroundWhatIsShown() {
        val shown = Crop.full(PHONE)
        val intent = CropPlanner.pinch(lock, 2f, null, shown, PHONE)
        val crop = CropPlanner.target(intent, null, PHONE)
        assertEquals(2f, crop.zoom, 1e-3f)
        assertPt(PHONE.center, screenOf(shown.center, crop, PHONE))
    }

    @Test
    fun tapBringsTheTouchedContentToTheCenter() {
        val shown = Crop(400f, 800f, 2f)
        val finger = Pt(900f, 300f)
        val touched = shown.toSource(finger, PHONE)
        val crop = CropPlanner.target(CropPlanner.tap(finger, shown, PHONE), null, PHONE)
        assertPt(PHONE.center, screenOf(touched, crop, PHONE))
        assertEquals(CropPlanner.POINT_ZOOM, crop.zoom, 1e-3f)
    }

    @Test
    fun tapDoesNotZoomOutWhenAlreadyCloser() {
        val shown = Crop(540f, 960f, 4f)
        val crop = CropPlanner.target(CropPlanner.tap(PHONE.center, shown, PHONE), null, PHONE)
        assertEquals(4f, crop.zoom, 1e-3f)
    }
}
