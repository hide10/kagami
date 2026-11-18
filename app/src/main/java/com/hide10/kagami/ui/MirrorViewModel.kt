package com.hide10.kagami.ui

import androidx.lifecycle.ViewModel
import com.hide10.kagami.core.Crop
import com.hide10.kagami.core.CropPlanner
import com.hide10.kagami.core.FaceLandmarks
import com.hide10.kagami.core.MirrorState
import com.hide10.kagami.core.Size
import com.hide10.kagami.core.ViewIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MirrorViewModel : ViewModel() {
    private val _state = MutableStateFlow(MirrorState())
    val state: StateFlow<MirrorState> = _state.asStateFlow()

    fun show(intent: ViewIntent) = _state.update { it.copy(intent = intent) }

    fun pinch(factor: Float, face: FaceLandmarks?, shown: Crop, size: Size) =
        _state.update { it.copy(intent = CropPlanner.pinch(it.intent, factor, face, shown, size)) }
}
