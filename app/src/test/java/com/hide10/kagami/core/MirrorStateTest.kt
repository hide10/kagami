package com.hide10.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorStateTest {
    private val f = face(Pt(540f, 900f), 250f)
    private val dragged = Crop(300f, 600f, 3f)

    @Test
    fun opensOnTheWholeViewWithNothingAdjusted() {
        val s = MirrorState()
        assertNull(s.target)
        assertNull(s.pendingPart)
        assertTrue(s.color.isNeutral)
        assertFalse(s.light.on)
        assertNull(s.panel)
    }

    @Test
    fun aPartButtonZoomsOnThatPartOnceTheFaceIsFound() {
        val pressed = MirrorState().request(FacePart.NOSE)
        assertNull(pressed.target)
        val zoomed = pressed.faceFound(f, PHONE)
        assertPt(PHONE.center, screenOf(f.nose, zoomed.target!!, PHONE))
    }

    @Test
    fun afterZoomingTheViewStaysPutWhenTheFaceMoves() {
        val zoomed = MirrorState().request(FacePart.NOSE).faceFound(f, PHONE)
        assertEquals(zoomed, zoomed.faceFound(face(Pt(400f, 1000f), 200f), PHONE))
    }

    @Test
    fun aFaceNobodyAskedForChangesNothing() {
        assertEquals(MirrorState(), MirrorState().faceFound(f, PHONE))
        val held = MirrorState().moveTo(dragged)
        assertEquals(held, held.faceFound(f, PHONE))
    }

    @Test
    fun theLastPartPressedWins() {
        val s = MirrorState().request(FacePart.LEFT_EYE).request(FacePart.MOUTH).faceFound(f, PHONE)
        assertPt(PHONE.center, screenOf(f.mouth, s.target!!, PHONE))
    }

    @Test
    fun touchingAfterAPressCancelsIt() {
        val s = MirrorState().request(FacePart.NOSE).moveTo(dragged).faceFound(f, PHONE)
        assertEquals(dragged, s.target)
    }

    @Test
    fun showAllReturnsToTheWholeViewAndCancelsAPress() {
        val zoomed = MirrorState().request(FacePart.NOSE).faceFound(f, PHONE)
        assertNull(zoomed.showAll().target)
        assertNull(zoomed.request(FacePart.MOUTH).showAll().faceFound(f, PHONE).target)
    }

    @Test
    fun pressingAPanelOpensItAndPressingItAgainClosesIt() {
        val open = MirrorState().toggle(Panel.COLOR)
        assertEquals(Panel.COLOR, open.panel)
        assertNull(open.toggle(Panel.COLOR).panel)
    }

    @Test
    fun pressingTheOtherPanelSwitchesToIt() {
        assertEquals(Panel.LIGHT, MirrorState().toggle(Panel.COLOR).toggle(Panel.LIGHT).panel)
    }

    @Test
    fun theLightTurnsOnAndOffWithoutOpeningOrClosingAPanel() {
        val on = MirrorState().toggleLight()
        assertTrue(on.light.on)
        assertNull(on.panel)
        assertFalse(on.toggleLight().light.on)

        val withPanel = MirrorState().toggle(Panel.LIGHT)
        assertEquals(Panel.LIGHT, withPanel.toggleLight().panel)
        assertEquals(Panel.LIGHT, withPanel.toggleLight().toggleLight().panel)
    }

    @Test
    fun theLightPanelOpensAndClosesWithoutSwitchingTheLight() {
        assertFalse(MirrorState().toggle(Panel.LIGHT).light.on)
        val lit = MirrorState().toggleLight()
        assertTrue(lit.toggle(Panel.LIGHT).light.on)
        assertTrue(lit.toggle(Panel.LIGHT).toggle(Panel.LIGHT).light.on)
        assertTrue(lit.toggle(Panel.COLOR).light.on)
    }

    @Test
    fun theLightKeepsItsAmountAndColorAcrossOffAndOn() {
        val set = MirrorState(light = ScreenLight(thicknessDp = 100f, kelvin = 3000f))
        val again = set.toggleLight().toggleLight().toggleLight()
        assertTrue(again.light.on)
        assertEquals(100f, again.light.thicknessDp, 0f)
        assertEquals(3000f, again.light.kelvin, 0f)
    }
}
