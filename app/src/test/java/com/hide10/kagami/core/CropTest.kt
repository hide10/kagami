package com.hide10.kagami.core

import org.junit.Assert.assertEquals
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
    fun fullShowsTheWholeImage() {
        val full = Crop.full(PHONE)
        assertPt(Pt(0f, 0f), full.toSource(Pt(0f, 0f), PHONE))
        assertPt(Pt(PHONE.w, PHONE.h), full.toSource(Pt(PHONE.w, PHONE.h), PHONE))
    }
}
