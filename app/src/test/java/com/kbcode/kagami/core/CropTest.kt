package com.kbcode.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CropTest {
    private val rnd = Random(7)

    private fun randomCrop() = Crop(
        cx = rnd.nextFloat() * 3000f - 1000f,
        cy = rnd.nextFloat() * 4000f - 1000f,
        zoom = rnd.nextFloat() * 16f - 2f,
    )

    @Test
    fun clampNeverShowsOutsideTheImage() {
        repeat(2000) { assertShowsOnlyImage(randomCrop().clamp(PHONE), PHONE) }
    }

    @Test
    fun clampKeepsACropThatAlreadyFits() {
        val fits = Crop(300f, 500f, 2f)
        assertEquals(fits, fits.clamp(PHONE))
    }

    @Test
    fun clampLimitsZoomToOneThroughMax() {
        assertEquals(1f, Crop(540f, 960f, 0.3f).clamp(PHONE).zoom)
        assertEquals(8f, Crop(540f, 960f, 8f).clamp(PHONE).zoom)
        assertEquals(10f, Crop(540f, 960f, 10f).clamp(PHONE).zoom)
        assertEquals(10f, Crop(540f, 960f, 40f).clamp(PHONE).zoom)
    }

    @Test
    fun layerTransformDrawsTheCropCenterAtTheScreenCenterScaledByZoom() {
        val crop = Crop(300f, 700f, 3f)
        assertPt(PHONE.center, screenOf(crop.center, crop, PHONE))
        val oneRight = screenOf(Pt(301f, 700f), crop, PHONE)
        assertEquals(PHONE.center.x + 3f, oneRight.x, 1e-3f)
    }

    @Test
    fun toSourceUndoesLayerTransform() {
        repeat(500) {
            val crop = randomCrop().clamp(PHONE)
            val tap = Pt(rnd.nextFloat() * PHONE.w, rnd.nextFloat() * PHONE.h)
            assertPt(tap, screenOf(crop.toSource(tap, PHONE), crop, PHONE), eps = 0.05f)
        }
    }

    @Test
    fun toScreenFindsWhereTheLayerDrawsAPoint() {
        repeat(200) {
            val crop = randomCrop().clamp(PHONE)
            val p = Pt(rnd.nextFloat() * PHONE.w, rnd.nextFloat() * PHONE.h)
            assertPt(screenOf(p, crop, PHONE), crop.toScreen(p, PHONE), eps = 1e-2f)
        }
    }

    @Test
    fun aTappedPointEndsUpInTheMiddleOnceTheViewHasZoomedToIt() {
        val shown = Crop(400f, 900f, 1.5f)
        val tapped = shown.toSource(Pt(800f, 500f), PHONE)
        val target = CropPlanner.tap(Pt(800f, 500f), shown, PHONE)
        assertPt(Pt(800f, 500f), shown.toScreen(tapped, PHONE))
        assertPt(PHONE.center, target.toScreen(tapped, PHONE))
    }

    @Test
    fun clampInsideAFrameKeepsTheCropWithinThatFrame() {
        val frame = Crop(300f, 700f, 2.5f)
        repeat(2000) {
            val c = randomCrop().clamp(PHONE, frame)
            assertTrue("$c zoom", c.zoom in frame.zoom..Crop.MAX_ZOOM)
            for (corner in listOf(Pt(0f, 0f), Pt(PHONE.w, PHONE.h))) {
                val onFrame = screenOf(c.toSource(corner, PHONE), frame, PHONE)
                assertTrue("$c leaves $frame at $corner -> $onFrame", onFrame.x in -0.01f..PHONE.w + 0.01f && onFrame.y in -0.01f..PHONE.h + 0.01f)
            }
        }
        assertEquals(frame, Crop.full(PHONE).clamp(PHONE, frame))
    }

    /** カメラが frame に切り出した映像へ、残りの拡大 residual を重ねたときに画面のどこへ描かれるか。 */
    private fun throughCamera(p: Pt, frame: Crop, residual: Crop) = screenOf(screenOf(p, frame, PHONE), residual, PHONE)

    @Test
    fun theCameraCropPlusTheRemainderLooksLikeTheShownCrop() {
        repeat(500) {
            val shown = randomCrop().clamp(PHONE)
            val frame = Crop(shown.cx, shown.cy, 1f + rnd.nextFloat() * (shown.zoom - 1f)).clamp(PHONE)
            val p = Pt(rnd.nextFloat() * PHONE.w, rnd.nextFloat() * PHONE.h)
            assertPt(screenOf(p, shown, PHONE), throughCamera(p, frame, shown.within(frame, PHONE)), eps = 0.05f)
        }
    }

    @Test
    fun nothingIsLeftForTheAppWhenTheCameraCroppedExactlyTheShownCrop() {
        val shown = Crop(300f, 700f, 6f)
        val rest = shown.within(shown, PHONE)
        assertPt(PHONE.center, rest.center, eps = 1e-3f)
        assertEquals(1f, rest.zoom, 1e-6f)
    }

    @Test
    fun aCameraThatDidNotCropLeavesTheWholeZoomToTheApp() {
        val shown = Crop(300f, 700f, 6f)
        val rest = shown.within(Crop.full(PHONE), PHONE)
        assertPt(shown.center, rest.center, eps = 1e-3f)
        assertEquals(shown.zoom, rest.zoom, 1e-6f)
    }

    @Test
    fun fullShowsTheWholeImage() {
        val full = Crop.full(PHONE)
        assertPt(Pt(0f, 0f), full.toSource(Pt(0f, 0f), PHONE))
        assertPt(Pt(PHONE.w, PHONE.h), full.toSource(Pt(PHONE.w, PHONE.h), PHONE))
    }
}
