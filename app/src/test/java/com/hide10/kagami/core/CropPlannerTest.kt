package com.hide10.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CropPlannerTest {
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
        }
    }

    @Test
    fun eyesAndNoseShowTheEyeDistanceAtTheirShareOfTheScreen() {
        val f = face(Pt(540f, 900f), 250f)
        for ((part, share) in listOf(FacePart.LEFT_EYE to 1.1f, FacePart.RIGHT_EYE to 1.1f, FacePart.NOSE to 0.9f)) {
            val shown = f.eyeDist * CropPlanner.zoomOnPart(part, f, PHONE).zoom
            assertEquals("$part", share * PHONE.w, shown, 0.1f)
        }
    }

    /** 口の両端の間隔を両目の 0.8 倍として、画面に映る口の幅。 */
    private fun mouthShown(f: FaceLandmarks) = 0.8f * f.eyeDist * CropPlanner.zoomOnPart(FacePart.MOUTH, f, PHONE).zoom

    @Test
    fun theMouthNearlyFillsTheWidthAndIsTheClosestPart() {
        val f = face(Pt(540f, 900f), 250f)
        assertEquals(0.9f * PHONE.w, mouthShown(f), 0.5f)
        val zoom = FacePart.entries.associateWith { CropPlanner.zoomOnPart(it, f, PHONE).zoom }
        assertTrue("$zoom", zoom.getValue(FacePart.MOUTH) > zoom.getValue(FacePart.LEFT_EYE))
        assertTrue("$zoom", zoom.getValue(FacePart.LEFT_EYE) > zoom.getValue(FacePart.NOSE))
    }

    @Test
    fun theMouthStopsShortOfTheWidthOnlyWhenTheFaceIsFarAway() {
        // 10 倍で 9 割に届くのは、両目が画面幅の 0.9 / 0.8 / 10 = 11.25% 以上に映るとき。
        for (eyeShare in listOf(0.12f, 0.2f, 0.3f, 0.45f)) {
            assertEquals("$eyeShare", 0.9f * PHONE.w, mouthShown(face(PHONE.center, eyeShare * PHONE.w)), 0.5f)
        }
        val far = face(PHONE.center, 0.08f * PHONE.w)
        assertEquals(10f, CropPlanner.zoomOnPart(FacePart.MOUTH, far, PHONE).zoom)
        assertEquals(0.64f * PHONE.w, mouthShown(far), 0.5f)
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
    fun partNearTheEdgeStaysInsideTheImage() {
        val rnd = Random(3)
        repeat(1000) {
            val f = face(Pt(rnd.nextFloat() * PHONE.w, rnd.nextFloat() * PHONE.h), 20f + rnd.nextFloat() * 600f)
            for (part in FacePart.entries) assertShowsOnlyImage(CropPlanner.zoomOnPart(part, f, PHONE), PHONE)
        }
    }

    @Test
    fun tapBringsTheTouchedContentToTheCenter() {
        val shown = Crop(400f, 800f, 2f)
        val finger = Pt(900f, 300f)
        val touched = shown.toSource(finger, PHONE)
        val crop = CropPlanner.tap(finger, shown, PHONE)
        assertPt(PHONE.center, screenOf(touched, crop, PHONE))
        assertEquals(CropPlanner.POINT_ZOOM, crop.zoom, 1e-3f)
    }

    @Test
    fun tapDoesNotZoomOutWhenAlreadyCloser() {
        val crop = CropPlanner.tap(PHONE.center, Crop(540f, 960f, 4f), PHONE)
        assertEquals(4f, crop.zoom, 1e-3f)
    }

    @Test
    fun tapNearACornerStaysInsideTheImage() {
        val crop = CropPlanner.tap(Pt(5f, 5f), Crop.full(PHONE), PHONE)
        assertShowsOnlyImage(crop, PHONE)
        assertEquals(CropPlanner.POINT_ZOOM, crop.zoom)
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
    fun pinchZoomStopsAtTenTimesAndTurnsBackRightAway() {
        val still = Pt(0f, 0f)
        var crop = Crop(540f, 960f, 4f)
        repeat(5) { crop = CropPlanner.transform(crop, PHONE.center, still, 1.5f, PHONE) }
        assertEquals(10f, crop.zoom)
        crop = CropPlanner.transform(crop, PHONE.center, still, 0.5f, PHONE)
        assertEquals(5f, crop.zoom, 1e-4f)
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
    fun draggingAtTenTimesStopsAtEveryEdgeOfTheImage() {
        val atLimit = Crop(540f, 960f, 10f)
        // 10 倍では 108 x 192 px ぶんが映るので、中心は端から 54 px と 96 px までしか寄れない。
        val pushes = mapOf(
            Pt(30000f, 0f) to Pt(54f, 960f),
            Pt(-30000f, 0f) to Pt(1026f, 960f),
            Pt(0f, 30000f) to Pt(540f, 96f),
            Pt(0f, -30000f) to Pt(540f, 1824f),
        )
        for ((pan, edge) in pushes) {
            val pushed = CropPlanner.transform(atLimit, PHONE.center, pan, 1f, PHONE)
            assertEquals(10f, pushed.zoom)
            assertPt(edge, pushed.center, eps = 1e-3f)
        }
    }

    @Test
    fun gesturesAtTenTimesNeverShowOutsideTheImage() {
        val rnd = Random(5)
        var crop = Crop(540f, 960f, 10f)
        repeat(2000) {
            val centroid = Pt(rnd.nextFloat() * PHONE.w, rnd.nextFloat() * PHONE.h)
            val pan = Pt(rnd.nextFloat() * 600f - 300f, rnd.nextFloat() * 600f - 300f)
            crop = CropPlanner.transform(crop, centroid, pan, 1f + rnd.nextFloat(), PHONE)
            assertEquals(10f, crop.zoom)
            assertShowsOnlyImage(crop, PHONE)
        }
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

    /** 止めた一枚(カメラが画面の左上寄りを 3 倍に切り出していた)。 */
    private val frozen = Crop(300f, 500f, 3f)

    @Test
    fun whileFrozenPinchingOutStopsAtTheFrozenFrame() {
        var crop = Crop(320f, 520f, 6f)
        repeat(10) { crop = CropPlanner.transform(crop, PHONE.center, Pt(0f, 0f), 0.5f, PHONE, frozen) }
        assertEquals(frozen, crop)
    }

    @Test
    fun whileFrozenGesturesAndTapsNeverLeaveTheFrozenFrame() {
        val rnd = Random(13)
        var crop = frozen
        repeat(2000) {
            val centroid = Pt(rnd.nextFloat() * PHONE.w, rnd.nextFloat() * PHONE.h)
            val pan = Pt(rnd.nextFloat() * 600f - 300f, rnd.nextFloat() * 600f - 300f)
            crop = if (it % 7 == 0) {
                CropPlanner.tap(centroid, crop, PHONE, frozen)
            } else {
                CropPlanner.transform(crop, centroid, pan, 0.5f + rnd.nextFloat(), PHONE, frozen)
            }
            assertShowsOnlyImage(crop.within(frozen, PHONE), PHONE)
        }
    }
}
