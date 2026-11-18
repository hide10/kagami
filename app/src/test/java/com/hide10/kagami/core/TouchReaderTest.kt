package com.hide10.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val RULES = TouchRules(slop = 20f, longPressMs = 500, twoFingerGapMs = 150, twoFingerTapMs = 400)

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
    fun aThirdFingerIsNoTwoFingerTap() {
        val h = Hand()
        h.down(0, 1L to Pt(300f, 900f), 2L to Pt(500f, 900f), 3L to Pt(700f, 900f))
        h.up(100, 1, 2, 3)
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
}
