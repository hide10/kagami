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
        val frozen = MirrorState().moveTo(dragged).request(FacePart.NOSE).freeze(Crop.full(PHONE), PHONE)
        assertTrue(frozen.frozen)
        assertEquals(dragged, frozen.target)
        assertNull(frozen.pendingPart)
        assertEquals(dragged, frozen.faceFound(f, PHONE).target)
    }

    @Test
    fun partButtonsDoNothingWhileFrozen() {
        val frozen = MirrorState().moveTo(dragged).freeze(Crop.full(PHONE), PHONE)
        assertEquals(frozen, frozen.request(FacePart.MOUTH))
        assertEquals(dragged, frozen.request(FacePart.MOUTH).faceFound(f, PHONE).target)
    }

    @Test
    fun fingersAndShowAllStillMoveTheFrozenView() {
        val frozen = MirrorState().moveTo(dragged).freeze(Crop.full(PHONE), PHONE)

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
        val live = MirrorState().moveTo(dragged).freeze(Crop.full(PHONE), PHONE).unfreeze()
        assertFalse(live.frozen)
        assertEquals(dragged, live.target)
        val zoomed = live.request(FacePart.NOSE).faceFound(f, PHONE)
        assertPt(PHONE.center, screenOf(f.nose, zoomed.target!!, PHONE))
    }

    @Test
    fun freezingTwiceOrGoingLiveWhileLiveChangesNothing() {
        val frozen = MirrorState().moveTo(dragged).freeze(Crop.full(PHONE), PHONE)
        assertEquals(frozen, frozen.freeze(Crop.full(PHONE), PHONE))
        val live = MirrorState().moveTo(dragged)
        assertEquals(live, live.unfreeze())
    }

    /** カメラが 2 倍に切り出していた一枚を止めた。止めた一枚に映っているのは画面の (270..810, 480..1440) だけ。 */
    private val frame = Crop(540f, 960f, 2f)

    @Test
    fun aFrozenFrameIsAllThereIsToShow() {
        val frozen = MirrorState().moveTo(dragged).freeze(frame, PHONE)
        assertEquals(frame, frozen.extent(PHONE))
        assertEquals(frame, frozen.showAll().extent(PHONE))
        assertNull(frozen.showAll().target)
        assertEquals(Crop.full(PHONE), frozen.unfreeze().extent(PHONE))
    }

    @Test
    fun freezingPullsTheWantedViewInsideTheFrozenFrame() {
        val wide = MirrorState().moveTo(Crop(300f, 600f, 1.5f)).freeze(frame, PHONE)
        assertEquals(frame, wide.target)
        val inside = Crop(600f, 1000f, 4f)
        assertEquals(inside, MirrorState().moveTo(inside).freeze(frame, PHONE).target)
    }

    @Test
    fun goingLiveKeepsTheViewChosenWhileFrozen() {
        val chosen = Crop(600f, 1000f, 5f)
        val live = MirrorState().freeze(frame, PHONE).moveTo(chosen).unfreeze()
        assertEquals(chosen, live.target)
        assertNull(live.still)
    }

    @Test
    fun aPartWhoseFaceWasNotFoundLeavesTheViewAlone() {
        val held = MirrorState().moveTo(dragged)
        val missed = held.request(FacePart.NOSE).partMissed()
        assertEquals(held, missed)
        assertEquals(dragged, missed.faceFound(f, PHONE).target)
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
