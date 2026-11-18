package com.hide10.kagami.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorStateTest {
    @Test
    fun opensFollowingTheFaceWithNothingAdjusted() {
        val s = MirrorState()
        assertEquals(ViewIntent.FaceLock(), s.intent)
        assertTrue(s.color.isNeutral)
        assertFalse(s.light.on)
        assertNull(s.panel)
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
}
