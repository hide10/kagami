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
        val expected = mapOf(
            FacePart.LEFT_EYE to Pt(415f, 900f),
            FacePart.RIGHT_EYE to Pt(665f, 900f),
            FacePart.NOSE to f.nose,
            FacePart.MOUTH to f.mouth,
        )
        for ((part, landmark) in expected) {
            val crop = CropPlanner.zoomOnPart(part, f, PHONE)
            assertPt(PHONE.center, screenOf(landmark, crop, PHONE))
            assertEquals(part.eyeFrac * PHONE.w / 250f, crop.zoom, 1e-3f)
        }
    }

    /** 前面カメラの像はミラーで左右が入れ替わり、leftEye が画面の右に来る。 */
    private val mirroredFace = ImageMapping(Size(480f, 640f), PHONE, mirror = true).toView(face(Pt(240f, 300f), 60f))

    @Test
    fun eyeButtonsFollowTheScreenSideNotTheLandmarkName() {
        assertPt(Pt(630f, 900f), mirroredFace.leftEye)
        val right = CropPlanner.zoomOnPart(FacePart.RIGHT_EYE, mirroredFace, PHONE)
        val left = CropPlanner.zoomOnPart(FacePart.LEFT_EYE, mirroredFace, PHONE)
        assertPt(PHONE.center, screenOf(Pt(630f, 900f), right, PHONE))
        assertPt(PHONE.center, screenOf(Pt(450f, 900f), left, PHONE))
    }

    @Test
    fun swappingTheEyeLandmarksDoesNotChangeWhichEyeIsShown() {
        val swapped = mirroredFace.copy(leftEye = mirroredFace.rightEye, rightEye = mirroredFace.leftEye)
        for (part in listOf(FacePart.LEFT_EYE, FacePart.RIGHT_EYE)) {
            assertEquals(
                "$part",
                CropPlanner.zoomOnPart(part, mirroredFace, PHONE),
                CropPlanner.zoomOnPart(part, swapped, PHONE),
            )
        }
    }

    @Test
    fun oneEyeLeavesTheOtherOffScreen() {
        val f = face(Pt(540f, 900f), 250f)
        for ((part, other) in listOf(FacePart.LEFT_EYE to Pt(665f, 900f), FacePart.RIGHT_EYE to Pt(415f, 900f))) {
            val crop = CropPlanner.zoomOnPart(part, f, PHONE)
            val x = screenOf(other, crop, PHONE).x
            assertTrue("$part shows the other eye at $x", x < 0f || x > PHONE.w)
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

    @Test
    fun partNearTheEdgeStaysInsideTheImage() {
        val rnd = Random(3)
        repeat(1000) {
            val f = face(Pt(rnd.nextFloat() * PHONE.w, rnd.nextFloat() * PHONE.h), 20f + rnd.nextFloat() * 600f)
            for (part in FacePart.entries) assertShowsOnlyImage(CropPlanner.zoomOnPart(part, f, PHONE), PHONE)
        }
    }

    @Test
    fun theContentUnderTheFingersFollowsThem() {
        val shown = Crop(540f, 960f, 2f)
        val moves = listOf(
            Triple(Pt(500f, 900f), Pt(60f, -40f), 1f),
            Triple(Pt(300f, 700f), Pt(0f, 0f), 1.5f),
            Triple(Pt(700f, 1100f), Pt(-30f, 45f), 1.2f),
        )
        for ((centroid, pan, zoom) in moves) {
            val under = shown.toSource(centroid, PHONE)
            val next = CropPlanner.transform(shown, centroid, pan, zoom, PHONE)
            assertPt(centroid + pan, screenOf(under, next, PHONE))
            assertEquals(2f * zoom, next.zoom, 1e-4f)
        }
    }

    @Test
    fun pinchZoomStopsAtItsLimitsAndTurnsBackRightAway() {
        val still = Pt(0f, 0f)
        var crop = Crop(540f, 960f, 4f)
        repeat(5) { crop = CropPlanner.transform(crop, PHONE.center, still, 1.5f, PHONE) }
        assertEquals(Crop.MAX_ZOOM, crop.zoom)
        crop = CropPlanner.transform(crop, PHONE.center, still, 0.5f, PHONE)
        assertEquals(Crop.MAX_ZOOM / 2, crop.zoom, 1e-4f)
        repeat(5) { crop = CropPlanner.transform(crop, PHONE.center, still, 0.5f, PHONE) }
        assertEquals(Crop.full(PHONE), crop)
    }

    @Test
    fun draggingPastTheEdgeStopsThereAndComesBackRightAway() {
        val atLeftEdge = Crop(270f, 960f, 2f)
        val pushed = CropPlanner.transform(atLeftEdge, PHONE.center, Pt(400f, 0f), 1f, PHONE)
        assertEquals(atLeftEdge, pushed)
        val back = CropPlanner.transform(pushed, PHONE.center, Pt(-100f, 0f), 1f, PHONE)
        assertEquals(320f, back.cx, 1e-3f)
    }

    @Test
    fun gesturesNeverShowOutsideTheImage() {
        val rnd = Random(11)
        var crop = Crop.full(PHONE)
        repeat(2000) {
            val centroid = Pt(rnd.nextFloat() * PHONE.w, rnd.nextFloat() * PHONE.h)
            val pan = Pt(rnd.nextFloat() * 400f - 200f, rnd.nextFloat() * 400f - 200f)
            crop = CropPlanner.transform(crop, centroid, pan, 0.5f + rnd.nextFloat(), PHONE)
            assertShowsOnlyImage(crop, PHONE)
        }
    }
}
