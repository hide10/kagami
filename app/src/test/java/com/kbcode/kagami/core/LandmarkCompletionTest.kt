package com.kbcode.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LandmarkCompletionTest {
    private val box = FaceBox(300f, 400f, 700f, 900f)
    private val leftEye = Pt(580f, 560f)
    private val rightEye = Pt(420f, 560f)
    private val nose = Pt(500f, 680f)
    private val mouthLeft = Pt(560f, 780f)
    private val mouthRight = Pt(440f, 780f)
    private val full = RawLandmarks(leftEye, rightEye, nose, mouthLeft, mouthRight)

    private fun assertPt(expected: Pt, actual: Pt, eps: Float = 1e-3f) {
        assertEquals(expected.x, actual.x, eps)
        assertEquals(expected.y, actual.y, eps)
    }

    @Test
    fun allLandmarksPresentAreUsedAsIs() {
        val f = completeLandmarks(full, box)!!
        assertPt(leftEye, f.leftEye)
        assertPt(rightEye, f.rightEye)
        assertPt(nose, f.nose)
        assertPt(Pt(500f, 780f), f.mouth)
    }

    @Test
    fun missingMouthIsPlacedBelowTheNoseAndKeepsTheOthers() {
        val f = completeLandmarks(full.copy(mouthLeft = null, mouthRight = null), box)!!
        assertPt(leftEye, f.leftEye)
        assertPt(rightEye, f.rightEye)
        assertPt(nose, f.nose)
        assertEquals(500f, f.mouth.x, 1e-3f)
        assertTrue("口は鼻より下", f.mouth.y > nose.y)
        assertTrue("口は箱の中", f.mouth.y < box.bottom)
    }

    @Test
    fun oneMissingEyeIsMirroredAcrossTheNose() {
        val f = completeLandmarks(full.copy(rightEye = null), box)!!
        assertPt(leftEye, f.leftEye)
        assertPt(Pt(420f, 560f), f.rightEye)
        val g = completeLandmarks(full.copy(leftEye = null), box)!!
        assertPt(Pt(580f, 560f), g.leftEye)
        assertPt(rightEye, g.rightEye)
    }

    @Test
    fun missingNoseSitsBetweenEyesAndMouth() {
        val f = completeLandmarks(full.copy(nose = null), box)!!
        assertEquals(500f, f.nose.x, 1e-3f)
        assertTrue(f.nose.y > 560f && f.nose.y < 780f)
    }

    @Test
    fun onlyOneMouthCornerPutsTheMouthOnTheAxis() {
        val f = completeLandmarks(full.copy(mouthRight = null), box)!!
        assertPt(Pt(500f, 780f), f.mouth)
    }

    @Test
    fun nothingButTheBoxStillGivesAnUprightFaceInsideIt() {
        val f = completeLandmarks(RawLandmarks(null, null, null, null, null), box)!!
        assertEquals(500f, f.nose.x, 1e-3f)
        assertEquals(500f, f.mouth.x, 1e-3f)
        assertEquals(f.leftEye.y, f.rightEye.y, 1e-3f)
        assertTrue(f.leftEye.x > f.rightEye.x)
        assertTrue(f.leftEye.y < f.nose.y && f.nose.y < f.mouth.y)
        for (p in listOf(f.leftEye, f.rightEye, f.nose, f.mouth)) {
            assertTrue(p.x in box.left..box.right && p.y in box.top..box.bottom)
        }
    }

    @Test
    fun noEyesButNoseAndMouthStillPlacesEyesAboveTheNose() {
        val f = completeLandmarks(RawLandmarks(null, null, nose, mouthLeft, mouthRight), box)!!
        assertPt(nose, f.nose)
        assertTrue(f.leftEye.y < nose.y)
    }

    @Test
    fun emptyBoxWithNothingGivesNull() {
        assertNull(completeLandmarks(RawLandmarks(null, null, null, null, null), FaceBox(0f, 0f, 0f, 0f)))
    }

    @Test
    fun aMirroredEyeThatCollapsesOntoTheNoseIsRedrawnFromTheBox() {
        // 片目の x が鼻の x と一致すると、折り返した目も同じ点になる。
        val f = completeLandmarks(RawLandmarks(Pt(500f, 560f), null, Pt(500f, 680f), null, null), box)!!
        assertTrue("目の間隔が確保される", f.leftEye.distTo(f.rightEye) >= box.width * 0.15f)
        assertEquals(f.leftEye.y, f.rightEye.y, 1e-3f)
        assertTrue("鼻は目の下", f.nose.y > f.leftEye.y)
        assertTrue("口は鼻の下", f.mouth.y > f.nose.y)
        assertTrue("口は目の下で、目から離れる", f.mouth.distTo(f.leftEye.midTo(f.rightEye)) > f.leftEye.distTo(f.rightEye) * 0.5f)
    }

    @Test
    fun anEmptyBoxIsIgnoredWhenEveryLandmarkIsPresent() {
        val f = completeLandmarks(full, FaceBox(0f, 0f, 0f, 0f))!!
        assertPt(leftEye, f.leftEye)
        assertPt(rightEye, f.rightEye)
        assertPt(nose, f.nose)
        assertPt(Pt(500f, 780f), f.mouth)
    }
}
