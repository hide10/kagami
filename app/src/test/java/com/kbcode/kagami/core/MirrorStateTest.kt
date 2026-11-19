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
        assertFalse(s.lightPanel)
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

    /** 選んでいるボタン。null は全体で、どれも選んでいなければ空。 */
    private fun MirrorState.selected() = (listOf(null) + FacePart.entries).filter { selects(it) }

    @Test
    fun theWholeButtonIsSelectedAtTheStart() {
        assertEquals(listOf(null), MirrorState().selected())
    }

    @Test
    fun aPartIsSelectedOnceItsFaceIsFoundAndNotWhileWaiting() {
        val pressed = MirrorState().request(FacePart.LEFT_EYE)
        assertEquals(listOf(null), pressed.selected())
        assertEquals(listOf(FacePart.LEFT_EYE), pressed.faceFound(f, PHONE).selected())
    }

    @Test
    fun aPartWhoseFaceWasNotFoundKeepsTheSelection() {
        assertEquals(listOf(null), MirrorState().request(FacePart.NOSE).partMissed().selected())
        val nose = MirrorState().request(FacePart.NOSE).faceFound(f, PHONE)
        assertEquals(listOf(FacePart.NOSE), nose.request(FacePart.MOUTH).selected())
        assertEquals(listOf(FacePart.NOSE), nose.request(FacePart.MOUTH).partMissed().selected())
        assertEquals(listOf(FacePart.MOUTH), nose.request(FacePart.MOUTH).faceFound(f, PHONE).selected())
    }

    @Test
    fun movingByHandSelectsNothingUntilTheWholeViewShowsAgain() {
        val nose = MirrorState().request(FacePart.NOSE).faceFound(f, PHONE)
        assertEquals(emptyList<FacePart?>(), nose.moveTo(dragged).selected())
        assertEquals(emptyList<FacePart?>(), nose.moveTo(nose.target!!).selected())
        assertEquals(listOf(null), nose.moveTo(Crop.full(PHONE)).selected())
        assertEquals(listOf(null), nose.showAll().selected())
        assertEquals(listOf(null), MirrorState().moveTo(dragged).showAll().selected())
    }

    @Test
    fun theSelectionStaysWhileFrozenAndFollowsTheFingersThere() {
        val eye = MirrorState().request(FacePart.RIGHT_EYE).faceFound(f, PHONE)
        val frozen = eye.freeze(frame, PHONE)
        assertEquals(listOf(FacePart.RIGHT_EYE), frozen.selected())
        assertEquals(listOf(FacePart.RIGHT_EYE), frozen.unfreeze().selected())
        assertEquals(emptyList<FacePart?>(), frozen.moveTo(Crop(600f, 1000f, 4f)).selected())
        assertEquals(listOf(null), frozen.moveTo(frame).selected())
        assertEquals(listOf(null), frozen.showAll().selected())
        assertEquals(emptyList<FacePart?>(), frozen.moveTo(frame).unfreeze().selected())
    }

    @Test
    fun atMostOneButtonIsEverSelected() {
        val rnd = Random(5)
        val steps = listOf<(MirrorState) -> MirrorState>(
            { it.request(FacePart.entries[rnd.nextInt(FacePart.entries.size)]) },
            { it.faceFound(f, PHONE) },
            { it.partMissed() },
            { it.moveTo(Crop(540f, 960f, 1f + rnd.nextInt(3))) },
            { it.showAll() },
            { it.freeze(frame, PHONE) },
            { it.unfreeze() },
        )
        var s = MirrorState()
        repeat(2000) {
            s = steps[rnd.nextInt(steps.size)](s)
            assertTrue("$s", s.selected().size <= 1)
            assertTrue("$s", s.shownPart == null || s.target != null)
        }
    }

    @Test
    fun pressingTheLightPanelOpensItAndPressingItAgainClosesIt() {
        val open = MirrorState().toggleLightPanel()
        assertTrue(open.lightPanel)
        assertFalse(open.toggleLightPanel().lightPanel)
    }

    @Test
    fun turningTheLightOnOpensNoPanel() {
        val on = MirrorState().toggleLight()
        assertTrue(on.light.on)
        assertFalse(on.lightPanel)
        assertFalse(on.toggleLight().light.on)
    }

    @Test
    fun turningTheLightOffClosesItsPanelAndTurningItOnAgainDoesNotReopenIt() {
        val open = MirrorState().toggleLight().toggleLightPanel()
        assertTrue(open.lightPanel)
        val off = open.toggleLight()
        assertFalse(off.light.on)
        assertFalse(off.lightPanel)
        assertFalse(off.toggleLight().lightPanel)
    }

    @Test
    fun openingTheLightPanelWhileTheLightIsOffTurnsItOn() {
        val opened = MirrorState().toggleLightPanel()
        assertTrue(opened.light.on)
        assertTrue(opened.lightPanel)
    }

    @Test
    fun openingOrClosingTheLightPanelLeavesTheLightOn() {
        val lit = MirrorState().toggleLight()
        assertTrue(lit.toggleLightPanel().light.on)
        assertTrue(lit.toggleLightPanel().toggleLightPanel().light.on)
    }

    @Test
    fun theLightPanelNeverShowsWhileTheLightIsOff() {
        val rnd = Random(3)
        val steps = listOf<(MirrorState) -> MirrorState>(
            { it.toggleLight() },
            { it.toggleLightPanel() },
            { it.toggleFullScreen() },
        )
        var s = MirrorState()
        repeat(2000) {
            s = steps[rnd.nextInt(steps.size)](s)
            assertTrue("$s", !s.lightPanel || s.light.on)
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
    fun theLightKeepsItsModeAcrossOffOnAndItsPanel() {
        val shrink = MirrorState(light = ScreenLight(mode = LightMode.SHRINK))
        val again = shrink.toggleLight().toggleLightPanel().toggleLight().toggleLightPanel()
        assertTrue(again.light.on)
        assertEquals(LightMode.SHRINK, again.light.mode)
        assertEquals(LightMode.SHRINK, again.toggleLightPanel().light.mode)
        assertEquals(1f, again.toggleLight().light.previewScale, 0f)
    }

    @Test
    fun fullScreenComesAndGoesWithoutChangingAnythingElse() {
        val s = MirrorState(target = dragged, color = ColorAdjust(exposureEv = 1f), light = ScreenLight(on = true), lightPanel = true)
            .freeze(Crop(540f, 960f, 2f), PHONE)
        val full = s.toggleFullScreen()
        assertTrue(full.fullScreen)
        assertTrue(full.frozen)
        assertEquals(s.copy(fullScreen = true), full)
        assertEquals(s, full.toggleFullScreen())
    }

    @Test
    fun theFingersMoveLightAndFreezeInFullScreenWithoutLeavingIt() {
        val full = MirrorState().toggleFullScreen()
        val moved = full.moveTo(dragged)
        assertEquals(dragged, moved.target)
        assertTrue(moved.fullScreen)
        val lit = moved.toggleLight()
        assertEquals(LightMode.BAND, lit.glowing)
        val frozen = lit.freeze(Crop(540f, 960f, 2f), PHONE)
        assertTrue(frozen.frozen)
        assertEquals(LightMode.BAND, frozen.glowing)
        assertTrue(frozen.fullScreen)
        val back = frozen.unfreeze().toggleLight()
        assertFalse(back.frozen)
        assertNull(back.glowing)
        assertTrue(back.fullScreen)
    }

    @Test
    fun turningTheLightOffInFullScreenClosesItsPanelForWhenFullScreenEnds() {
        val off = MirrorState().toggleLightPanel().toggleFullScreen().toggleLight()
        assertNull(off.glowing)
        assertFalse(off.toggleFullScreen().lightPanel)
        val on = off.toggleLight()
        assertEquals(LightMode.BAND, on.glowing)
        assertFalse(on.toggleFullScreen().lightPanel)
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
