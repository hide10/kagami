package com.kbcode.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val RULES = TouchRules(slop = 20f, longPressMs = 500, fingersGapMs = 150, fingersTapMs = 400)

/** 画面と同じく、毎回いま置いている指と、離したばかりの指を届ける。届いた操作を seen にためる。 */
private class Hand(rules: TouchRules = RULES) {
    val reader = TouchReader(rules)
    val seen = mutableListOf<Gesture>()
    private val fingers = LinkedHashMap<Long, Pt>()

    fun down(t: Long, vararg f: Pair<Long, Pt>) = move(t, *f)

    fun move(t: Long, vararg f: Pair<Long, Pt>) {
        f.forEach { fingers[it.first] = it.second }
        send(t, emptyList())
    }

    fun up(t: Long, vararg ids: Long) = send(t, ids.map { Finger(it, fingers.remove(it)!!, pressed = false) })

    /** 画面の時計。deadline を過ぎていれば長押しが届く。 */
    fun clock(t: Long) {
        reader.tick(t)?.let(seen::add)
    }

    private fun send(t: Long, lifted: List<Finger>) {
        reader.read(t, fingers.map { Finger(it.key, it.value) } + lifted)?.let(seen::add)
    }

    val moves get() = seen.filterIsInstance<Gesture.Move>()
}

class TouchReaderTest {
    @Test
    fun aStillQuickTouchIsATapWhereTheFingerLifted() {
        val h = Hand()
        h.down(0, 1L to Pt(100f, 200f))
        h.move(60, 1L to Pt(108f, 205f))
        h.up(120, 1)
        assertEquals(listOf(Gesture.Tap(Pt(108f, 205f))), h.seen)
    }

    @Test
    fun holdingStillUntilTheDeadlineIsALongPressAndNoTap() {
        val h = Hand()
        h.down(1000, 1L to Pt(100f, 200f))
        assertEquals(1500L, h.reader.deadline)
        h.clock(1499)
        assertTrue(h.seen.isEmpty())
        h.clock(1500)
        assertNull(h.reader.deadline)
        h.up(1900, 1)
        assertEquals(listOf(Gesture.LongPress), h.seen)
    }

    @Test
    fun aLongPressStillCountsWhenTheNextEventArrivesLate() {
        val h = Hand()
        h.down(0, 1L to Pt(100f, 200f))
        h.up(700, 1)
        assertEquals(listOf(Gesture.LongPress), h.seen)
    }

    @Test
    fun aOneFingerDragMovesTheViewAndIsNeverATapOrLongPress() {
        val h = Hand()
        h.down(0, 1L to Pt(100f, 200f))
        h.move(30, 1L to Pt(110f, 200f))
        assertTrue("within the slop nothing moves yet", h.seen.isEmpty())
        h.move(60, 1L to Pt(130f, 190f))
        h.move(90, 1L to Pt(150f, 180f))
        assertNull(h.reader.deadline)
        h.clock(2000)
        h.up(2100, 1)
        assertEquals(
            listOf(
                Gesture.Move(Pt(110f, 200f), Pt(20f, -10f), 1f),
                Gesture.Move(Pt(130f, 190f), Pt(20f, -10f), 1f),
            ),
            h.seen,
        )
    }

    @Test
    fun draggingAfterALongPressStillMovesTheView() {
        val h = Hand()
        h.down(0, 1L to Pt(100f, 200f))
        h.clock(500)
        h.move(600, 1L to Pt(100f, 260f))
        h.up(700, 1)
        assertEquals(listOf(Gesture.LongPress, Gesture.Move(Pt(100f, 200f), Pt(0f, 60f), 1f)), h.seen)
    }

    @Test
    fun twoFingersPutDownTogetherAndLiftedQuicklyAreATwoFingerTap() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 900f))
        h.down(80, 2L to Pt(600f, 900f))
        h.move(150, 1L to Pt(405f, 902f), 2L to Pt(596f, 899f))
        h.up(250, 1)
        h.up(300, 2)
        assertEquals(listOf(Gesture.TwoFingerTap), h.seen)
    }

    @Test
    fun twoFingersAtOnceInOneEventAreATwoFingerTap() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 900f), 2L to Pt(600f, 900f))
        h.up(200, 1, 2)
        assertEquals(listOf(Gesture.TwoFingerTap), h.seen)
    }

    @Test
    fun aSecondFingerThatComesLateIsNoTwoFingerTapAndNoTap() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 900f))
        h.down(200, 2L to Pt(600f, 900f))
        h.up(300, 1, 2)
        assertTrue(h.seen.isEmpty())
    }

    @Test
    fun twoFingersHeldTooLongAreNoTwoFingerTapAndNoLongPress() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 900f), 2L to Pt(600f, 900f))
        assertNull(h.reader.deadline)
        h.clock(1000)
        h.up(1000, 1, 2)
        assertTrue(h.seen.isEmpty())
    }

    @Test
    fun aQuickPinchZoomsAndIsNoTwoFingerTap() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 900f), 2L to Pt(600f, 900f))
        h.move(50, 1L to Pt(350f, 900f), 2L to Pt(650f, 900f))
        h.up(100, 1, 2)
        assertEquals(listOf(Gesture.Move(Pt(500f, 900f), Pt(0f, 0f), 1.5f)), h.seen)
    }

    @Test
    fun aSmallPinchWithinTheSlopIsStillATwoFingerTap() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 900f), 2L to Pt(600f, 900f))
        h.move(50, 1L to Pt(392f, 900f), 2L to Pt(608f, 900f))
        h.up(100, 1, 2)
        assertEquals(listOf(Gesture.TwoFingerTap), h.seen)
    }

    @Test
    fun threeFingersPutDownTogetherAndLiftedQuicklyAreAThreeFingerTapAndNothingElse() {
        val h = Hand()
        h.down(0, 1L to Pt(300f, 900f))
        h.down(70, 2L to Pt(500f, 880f))
        h.down(140, 3L to Pt(700f, 900f))
        assertNull("no long press while three fingers are down", h.reader.deadline)
        h.move(200, 1L to Pt(306f, 903f), 2L to Pt(497f, 884f), 3L to Pt(695f, 898f))
        h.up(260, 2)
        h.up(300, 1)
        h.up(340, 3)
        assertEquals(listOf(Gesture.ThreeFingerTap), h.seen)
    }

    @Test
    fun threeFingersAtOnceInOneEventAreAThreeFingerTap() {
        val h = Hand()
        h.down(0, 1L to Pt(300f, 900f), 2L to Pt(500f, 900f), 3L to Pt(700f, 900f))
        h.up(100, 1, 2, 3)
        assertEquals(listOf(Gesture.ThreeFingerTap), h.seen)
    }

    @Test
    fun aThirdFingerThatComesLateIsNoThreeFingerTapAndNoTwoFingerTap() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 900f), 2L to Pt(600f, 900f))
        h.down(151, 3L to Pt(800f, 900f))
        h.up(250, 1, 2, 3)
        assertTrue(h.seen.isEmpty())
    }

    @Test
    fun threeFingersHeldTooLongAreNoThreeFingerTapAndNoLongPress() {
        val h = Hand()
        h.down(0, 1L to Pt(300f, 900f), 2L to Pt(500f, 900f), 3L to Pt(700f, 900f))
        h.clock(1000)
        h.up(401, 1, 2, 3)
        assertTrue(h.seen.isEmpty())
    }

    @Test
    fun threeFingersThatMoveAreAPinchOrADragAndNoThreeFingerTap() {
        val pinch = Hand()
        pinch.down(0, 1L to Pt(300f, 900f), 2L to Pt(500f, 900f), 3L to Pt(700f, 900f))
        pinch.move(50, 1L to Pt(250f, 900f), 2L to Pt(500f, 900f), 3L to Pt(750f, 900f))
        pinch.up(100, 1, 2, 3)
        assertEquals(1, pinch.moves.size)
        assertTrue(Gesture.ThreeFingerTap !in pinch.seen)

        val drag = Hand()
        drag.down(0, 1L to Pt(300f, 900f), 2L to Pt(500f, 900f), 3L to Pt(700f, 900f))
        drag.move(50, 1L to Pt(300f, 860f), 2L to Pt(500f, 860f), 3L to Pt(700f, 860f))
        drag.up(100, 1, 2, 3)
        assertEquals(listOf(Gesture.ZoomDrag(-40f)), drag.seen)
    }

    @Test
    fun fourFingersAreNoTap() {
        val h = Hand()
        h.down(0, 1L to Pt(200f, 900f), 2L to Pt(400f, 900f), 3L to Pt(600f, 900f), 4L to Pt(800f, 900f))
        h.up(100, 1, 2, 3, 4)
        assertTrue(h.seen.isEmpty())
    }

    @Test
    fun addingAFingerDuringADragTurnsItIntoAPinchWithoutAJump() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 900f))
        h.move(50, 1L to Pt(400f, 950f))
        h.down(100, 1L to Pt(400f, 950f), 2L to Pt(600f, 950f))
        h.move(150, 1L to Pt(300f, 950f), 2L to Pt(700f, 950f))
        h.up(200, 2)
        h.move(250, 1L to Pt(300f, 1000f))
        h.up(300, 1)
        assertEquals(
            listOf(
                Gesture.Move(Pt(400f, 900f), Pt(0f, 50f), 1f),
                Gesture.Move(Pt(500f, 950f), Pt(0f, 0f), 2f),
                Gesture.Move(Pt(300f, 950f), Pt(0f, 50f), 1f),
            ),
            h.seen,
        )
    }

    @Test
    fun aTouchAfterADragStartsFresh() {
        val h = Hand()
        h.down(0, 1L to Pt(100f, 200f))
        h.move(50, 1L to Pt(300f, 200f))
        h.up(100, 1)
        h.down(400, 1L to Pt(300f, 200f))
        h.up(450, 1)
        assertEquals(Gesture.Tap(Pt(300f, 200f)), h.seen.last())
        assertEquals(1, h.moves.size)
    }

    @Test
    fun pointerMovesWithNoFingerDownAreIgnored() {
        val h = Hand()
        assertNull(h.reader.read(0, listOf(Finger(9, Pt(10f, 10f), pressed = false))))
        assertNull(h.reader.deadline)
        h.down(100, 1L to Pt(100f, 200f))
        h.up(150, 1)
        assertEquals(listOf(Gesture.Tap(Pt(100f, 200f))), h.seen)
    }

    private fun Hand.tap(t: Long, at: Pt) {
        down(t, 1L to at)
        up(t + 60, 1)
    }

    @Test
    fun quickTapsInTheSameSpotAreEachATap() {
        val h = Hand()
        h.tap(0, Pt(500f, 900f))
        h.tap(150, Pt(510f, 890f))
        h.tap(300, Pt(500f, 900f))
        assertEquals(listOf(Gesture.Tap(Pt(500f, 900f)), Gesture.Tap(Pt(510f, 890f)), Gesture.Tap(Pt(500f, 900f))), h.seen)
    }

    @Test
    fun holdingRightAfterATapIsALongPress() {
        val h = Hand()
        h.tap(0, Pt(500f, 900f))
        h.down(200, 1L to Pt(500f, 900f))
        h.clock(700)
        h.up(800, 1)
        assertEquals(listOf(Gesture.Tap(Pt(500f, 900f)), Gesture.LongPress), h.seen)
    }

    @Test
    fun twoFingersMovedTogetherUpOrDownZoomByHowFarTheyMoved() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 1000f), 2L to Pt(600f, 1000f))
        h.move(20, 1L to Pt(400f, 990f), 2L to Pt(600f, 990f))
        h.move(40, 1L to Pt(400f, 970f), 2L to Pt(600f, 970f))
        h.move(60, 1L to Pt(403f, 940f), 2L to Pt(598f, 940f))
        h.move(80, 1L to Pt(400f, 1000f), 2L to Pt(600f, 1000f))
        h.up(100, 1, 2)
        assertEquals(listOf(Gesture.ZoomDrag(-20f), Gesture.ZoomDrag(-30f), Gesture.ZoomDrag(60f)), h.seen)
    }

    @Test
    fun aTwoFingerDragWithOneFingerLaggingIsStillADrag() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 1000f), 2L to Pt(600f, 1000f))
        h.move(30, 1L to Pt(400f, 960f), 2L to Pt(600f, 975f))
        assertEquals(listOf(Gesture.ZoomDrag(-32.5f)), h.seen)
    }

    @Test
    fun twoFingersMovedTogetherSidewaysReportTheWholeWayFromWhereTheyStarted() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 1000f), 2L to Pt(600f, 1000f))
        h.move(30, 1L to Pt(430f, 1002f), 2L to Pt(630f, 1001f))
        h.move(60, 1L to Pt(470f, 1000f), 2L to Pt(670f, 1000f))
        h.move(90, 1L to Pt(450f, 1000f), 2L to Pt(650f, 1000f))
        h.up(120, 1, 2)
        assertEquals(
            listOf(Gesture.ExposureDrag(30f, starts = true), Gesture.ExposureDrag(70f, starts = false), Gesture.ExposureDrag(50f, starts = false)),
            h.seen,
        )
        h.down(500, 1L to Pt(400f, 1000f), 2L to Pt(600f, 1000f))
        h.move(530, 1L to Pt(370f, 1000f), 2L to Pt(570f, 1000f))
        assertEquals(Gesture.ExposureDrag(-30f, starts = true), h.seen.last())
    }

    @Test
    fun theDirectionIsDecidedOnceAndKeptUntilTheFingersLift() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 1000f), 2L to Pt(600f, 1000f))
        h.move(30, 1L to Pt(400f, 960f), 2L to Pt(600f, 960f))
        h.move(60, 1L to Pt(480f, 960f), 2L to Pt(680f, 960f))
        h.move(90, 1L to Pt(300f, 1000f), 2L to Pt(700f, 1000f))
        h.up(120, 1, 2)
        assertTrue("${h.seen}", h.seen.all { it is Gesture.ZoomDrag })
        assertEquals(3, h.seen.size)
    }

    @Test
    fun spreadingTheFingersIsAPinchEvenWhenOnlyOneFingerMoves() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 1000f), 2L to Pt(600f, 1000f))
        h.move(30, 1L to Pt(400f, 1000f), 2L to Pt(660f, 1000f))
        assertEquals(listOf(Gesture.Move(Pt(500f, 1000f), Pt(30f, 0f), 1.3f)), h.seen)
        h.move(60, 1L to Pt(400f, 950f), 2L to Pt(660f, 950f))
        assertEquals(Gesture.Move(Pt(530f, 1000f), Pt(0f, -50f), 1f), h.seen.last())
    }

    @Test
    fun liftingOneFingerDuringATwoFingerZoomKeepsZoomingWithoutAJump() {
        val h = Hand()
        h.down(0, 1L to Pt(400f, 1000f), 2L to Pt(600f, 1000f))
        h.move(30, 1L to Pt(400f, 960f), 2L to Pt(600f, 960f))
        h.up(60, 2)
        h.move(90, 1L to Pt(400f, 950f))
        assertEquals(listOf(Gesture.ZoomDrag(-40f), Gesture.ZoomDrag(-10f)), h.seen)
    }
}
