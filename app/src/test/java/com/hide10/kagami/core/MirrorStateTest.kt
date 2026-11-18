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
        assertFalse(s.frozen)
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
    fun freezingKeepsTheViewAndCancelsAPress() {
        val frozen = MirrorState().moveTo(dragged).request(FacePart.NOSE).freeze()
        assertTrue(frozen.frozen)
        assertEquals(dragged, frozen.target)
        assertNull(frozen.pendingPart)
        assertEquals(dragged, frozen.faceFound(f, PHONE).target)
    }

    @Test
    fun partButtonsDoNothingWhileFrozen() {
        val frozen = MirrorState().moveTo(dragged).freeze()
        assertEquals(frozen, frozen.request(FacePart.MOUTH))
        assertEquals(dragged, frozen.request(FacePart.MOUTH).faceFound(f, PHONE).target)
    }

    @Test
    fun fingersAndShowAllStillMoveTheFrozenView() {
        val frozen = MirrorState().moveTo(dragged).freeze()

        val pinched = frozen.moveTo(CropPlanner.transform(dragged, PHONE.center, Pt(40f, 0f), 2f, PHONE))
        assertTrue(pinched.frozen)
        assertEquals(6f, pinched.target!!.zoom, 1e-4f)

        val tapped = frozen.moveTo(CropPlanner.tap(Pt(700f, 1000f), dragged, PHONE))
        assertTrue(tapped.frozen)
        assertPt(PHONE.center, screenOf(dragged.toSource(Pt(700f, 1000f), PHONE), tapped.target!!, PHONE))

        val whole = frozen.showAll()
        assertTrue(whole.frozen)
        assertNull(whole.target)
    }

    @Test
    fun goingLiveKeepsTheViewAndPartButtonsWorkAgain() {
        val live = MirrorState().moveTo(dragged).freeze().unfreeze()
        assertFalse(live.frozen)
        assertEquals(dragged, live.target)
        val zoomed = live.request(FacePart.NOSE).faceFound(f, PHONE)
        assertPt(PHONE.center, screenOf(f.nose, zoomed.target!!, PHONE))
    }

    @Test
    fun freezingTwiceOrGoingLiveWhileLiveChangesNothing() {
        val frozen = MirrorState().moveTo(dragged).freeze()
        assertEquals(frozen, frozen.freeze())
        val live = MirrorState().moveTo(dragged)
        assertEquals(live, live.unfreeze())
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
