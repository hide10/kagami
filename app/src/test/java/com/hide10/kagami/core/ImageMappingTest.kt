package com.hide10.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageMappingTest {
    /** 縦持ちの 4:3 解析画像を 9:16 のビューへ。高さで合わせ(×3)、左右 180px ずつ切れる。 */
    private val image = Size(480f, 640f)
    private val mirrored = ImageMapping(image, PHONE, mirror = true)

    @Test
    fun imageCenterLandsOnViewCenter() {
        assertPt(PHONE.center, mirrored.toView(Pt(240f, 320f)))
    }

    @Test
    fun frontCameraMirrorsLeftAndRight() {
        assertPt(Pt(960f, 150f), mirrored.toView(Pt(100f, 50f)))
        assertPt(Pt(120f, 150f), mirrored.toView(Pt(380f, 50f)))
    }

    @Test
    fun fillCenterScalesToFillAndCropsTheSides() {
        val plain = ImageMapping(image, PHONE, mirror = false)
        assertPt(Pt(-180f, 0f), plain.toView(Pt(0f, 0f)))
        assertPt(Pt(1260f, 1920f), plain.toView(Pt(480f, 640f)))
    }

    @Test
    fun fillCenterCropsTopAndBottomForATallView() {
        val tall = ImageMapping(Size(480f, 640f), Size(1000f, 1000f), mirror = false)
        assertPt(Pt(0f, -166.67f), tall.toView(Pt(0f, 0f)), eps = 0.01f)
        assertPt(Pt(1000f, 1166.67f), tall.toView(Pt(480f, 640f)), eps = 0.01f)
    }

    @Test
    fun aSensorFrameTurnedAQuarterIsSeenUpright() {
        assertEquals(Size(480f, 640f), uprightSize(640, 480, 270))
        assertEquals(Size(480f, 640f), uprightSize(640, 480, 90))
        assertEquals(Size(640f, 480f), uprightSize(640, 480, 180))
        assertEquals(Size(640f, 480f), uprightSize(640, 480, 0))
    }

    @Test
    fun mapsEveryLandmarkOfAFace() {
        val f = face(Pt(240f, 300f), 60f)
        val v = mirrored.toView(f)
        assertPt(mirrored.toView(f.leftEye), v.leftEye)
        assertPt(mirrored.toView(f.rightEye), v.rightEye)
        assertPt(mirrored.toView(f.nose), v.nose)
        assertPt(mirrored.toView(f.mouth), v.mouth)
        assertEquals(60f * 3f, v.eyeDist, 1e-3f)
    }
}
