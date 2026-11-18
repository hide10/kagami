package com.kbcode.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

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
        assertEquals(LightMode.BAND, s.light.mode)
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
    fun turningTheLightOnOpensNoPanel() {
        val on = MirrorState().toggleLight()
        assertTrue(on.light.on)
        assertNull(on.panel)
        assertFalse(on.toggleLight().light.on)
    }

    @Test
    fun turningTheLightOffClosesItsPanelAndTurningItOnAgainDoesNotReopenIt() {
        val open = MirrorState().toggleLight().toggle(Panel.LIGHT)
        assertEquals(Panel.LIGHT, open.panel)
        val off = open.toggleLight()
        assertFalse(off.light.on)
        assertNull(off.panel)
        assertNull(off.toggleLight().panel)
    }

    @Test
    fun turningTheLightOffLeavesTheColorPanelOpen() {
        val coloring = MirrorState().toggleLight().toggle(Panel.COLOR)
        assertEquals(Panel.COLOR, coloring.toggleLight().panel)
        assertEquals(Panel.COLOR, coloring.toggleLight().toggleLight().panel)
    }

    @Test
    fun openingTheLightPanelWhileTheLightIsOffTurnsItOn() {
        val opened = MirrorState().toggle(Panel.LIGHT)
        assertTrue(opened.light.on)
        assertEquals(Panel.LIGHT, opened.panel)
        assertEquals(Panel.LIGHT, MirrorState().toggle(Panel.COLOR).toggle(Panel.LIGHT).panel)
    }

    @Test
    fun closingOrSwitchingAwayFromTheLightPanelLeavesTheLightOn() {
        val lit = MirrorState().toggleLight()
        assertTrue(lit.toggle(Panel.LIGHT).light.on)
        assertTrue(lit.toggle(Panel.LIGHT).toggle(Panel.LIGHT).light.on)
        assertTrue(lit.toggle(Panel.LIGHT).toggle(Panel.COLOR).light.on)
        assertTrue(lit.toggle(Panel.COLOR).light.on)
    }

    @Test
    fun theLightPanelNeverShowsWhileTheLightIsOff() {
        val rnd = Random(3)
        val steps = listOf<(MirrorState) -> MirrorState>(
            { it.toggleLight() },
            { it.toggle(Panel.LIGHT) },
            { it.toggle(Panel.COLOR) },
        )
        var s = MirrorState()
        repeat(2000) {
            s = steps[rnd.nextInt(steps.size)](s)
            assertTrue("$s", s.panel != Panel.LIGHT || s.light.on)
        }
    }

    @Test
    fun theLightKeepsItsAmountAndColorAcrossOffAndOn() {
        val set = MirrorState(light = ScreenLight(thicknessDp = 100f, kelvin = 3000f))
        val again = set.toggleLight().toggleLight().toggleLight()
        assertTrue(again.light.on)
        assertEquals(100f, again.light.thicknessDp, 0f)
        assertEquals(3000f, again.light.kelvin, 0f)
    }

    @Test
    fun theLightKeepsItsModeAcrossOffOnAndThePanels() {
        val shrink = MirrorState(light = ScreenLight(mode = LightMode.SHRINK))
        val again = shrink.toggleLight().toggle(Panel.LIGHT).toggleLight().toggle(Panel.LIGHT)
        assertTrue(again.light.on)
        assertEquals(LightMode.SHRINK, again.light.mode)
        assertEquals(LightMode.SHRINK, again.toggle(Panel.COLOR).toggle(Panel.COLOR).light.mode)
        assertEquals(1f, again.toggleLight().light.previewScale, 0f)
    }

    @Test
    fun fullScreenComesAndGoesWithoutChangingAnythingElse() {
        val s = MirrorState(target = dragged, color = ColorAdjust(exposureEv = 1f), light = ScreenLight(on = true), panel = Panel.COLOR)
            .freeze(Crop(540f, 960f, 2f), PHONE)
        val full = s.toggleFullScreen()
        assertTrue(full.fullScreen)
        assertTrue(full.frozen)
        assertEquals(s.copy(fullScreen = true), full)
        assertEquals(s, full.toggleFullScreen())
    }

    @Test
    fun inFullScreenOnlyTheTwoFingerTapGetsThrough() {
        val full = MirrorState().toggleFullScreen()
        assertTrue(full.allows(Gesture.TwoFingerTap))
        assertTrue(MirrorState().allows(Gesture.TwoFingerTap))
        for (g in listOf(Gesture.Tap(Pt(1f, 2f)), Gesture.LongPress, Gesture.Move(Pt(0f, 0f), Pt(5f, 5f), 1.2f))) {
            assertFalse("$g", full.allows(g))
            assertTrue("$g", MirrorState().allows(g))
        }
    }

    @Test
    fun aShrinkingLightGlowsAtTheEdgesInFullScreenAndShrinksAgainAfter() {
        val shrink = MirrorState(light = ScreenLight(on = true, thicknessDp = 120f, mode = LightMode.SHRINK))
        assertEquals(LightMode.SHRINK, shrink.glowing)
        assertEquals(0.6f, shrink.previewScale, 1e-6f)
        val full = shrink.toggleFullScreen()
        assertEquals(LightMode.BAND, full.glowing)
        assertEquals(1f, full.previewScale, 0f)
        assertEquals(LightMode.SHRINK, full.light.mode)
        assertEquals(shrink, full.toggleFullScreen())
    }

    @Test
    fun fullScreenKeepsTheBandLightAndNothingGlowsWhileTheLightIsOff() {
        assertEquals(LightMode.BAND, MirrorState(light = ScreenLight(on = true)).toggleFullScreen().glowing)
        assertNull(MirrorState().toggleFullScreen().glowing)
        val off = MirrorState(light = ScreenLight(thicknessDp = 120f, mode = LightMode.SHRINK))
        assertNull(off.glowing)
        assertEquals(1f, off.previewScale, 0f)
    }
}
