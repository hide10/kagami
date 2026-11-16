package com.hide10.kagami

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MirrorViewModelTest {

    @Test
    fun clamp_keepsValueInsideRange() {
        assertEquals(2.0f, MirrorViewModel.clampZoomRatio(2.0f, 1.0f, 4.0f), 0.001f)
    }

    @Test
    fun clamp_limitsBelowMinAndAboveMax() {
        assertEquals(1.0f, MirrorViewModel.clampZoomRatio(0.5f, 1.0f, 4.0f), 0.001f)
        assertEquals(4.0f, MirrorViewModel.clampZoomRatio(9.0f, 1.0f, 4.0f), 0.001f)
    }

    @Test
    fun clamp_withDegenerateRangeReturnsMin() {
        assertEquals(1.0f, MirrorViewModel.clampZoomRatio(3.0f, 1.0f, 1.0f), 0.001f)
    }

    @Test
    fun zoomSupported_onlyWhenRangeIsOpen() {
        assertTrue(MirrorViewModel.isZoomSupported(1.0f, 4.0f))
        assertFalse(MirrorViewModel.isZoomSupported(1.0f, 1.0f))
    }

    @Test
    fun uiState_toggles() {
        val vm = MirrorViewModel()
        assertFalse(vm.uiState.value.edgeMode)
        vm.setEdgeMode(true)
        assertTrue(vm.uiState.value.edgeMode)
        vm.togglePanel()
        assertFalse(vm.uiState.value.panelVisible)
        vm.setStabilizationEnabled(true)
        assertTrue(vm.uiState.value.stabilizationEnabled)
    }
}
