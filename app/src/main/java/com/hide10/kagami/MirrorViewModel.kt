package com.hide10.kagami

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 「鏡」の画面状態と、ズームまわりの純粋な計算。
 * ズームの正本は CameraX の ZoomState で、ここでは UI の状態とクランプだけを持つ。
 */
class MirrorViewModel : ViewModel() {

    data class UiState(
        val edgeMode: Boolean = false,
        val stabilizationEnabled: Boolean = false,
        val panelVisible: Boolean = true,
        val showInfo: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun setEdgeMode(enabled: Boolean) = _uiState.update { it.copy(edgeMode = enabled) }

    fun setStabilizationEnabled(enabled: Boolean) =
        _uiState.update { it.copy(stabilizationEnabled = enabled) }

    fun togglePanel() = _uiState.update { it.copy(panelVisible = !it.panelVisible) }

    fun setShowInfo(show: Boolean) = _uiState.update { it.copy(showInfo = show) }

    companion object {
        /** ズーム倍率を端末の対応範囲に収める。 */
        fun clampZoomRatio(ratio: Float, minRatio: Float, maxRatio: Float): Float {
            if (minRatio > maxRatio) return minRatio
            return ratio.coerceIn(minRatio, maxRatio)
        }

        /** 最小と最大が等しい端末ではズーム操作を無効にする。 */
        fun isZoomSupported(minRatio: Float, maxRatio: Float): Boolean = maxRatio > minRatio
    }
}
