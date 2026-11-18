package com.hide10.kagami.ui

import androidx.lifecycle.ViewModel
import com.hide10.kagami.core.ColorAdjust
import com.hide10.kagami.core.Crop
import com.hide10.kagami.core.FaceLandmarks
import com.hide10.kagami.core.FacePart
import com.hide10.kagami.core.MirrorState
import com.hide10.kagami.core.Panel
import com.hide10.kagami.core.ScreenLight
import com.hide10.kagami.core.Size
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

class MirrorViewModel : ViewModel() {
    private val _state = MutableStateFlow(MirrorState())
    val state: StateFlow<MirrorState> = _state.asStateFlow()

    fun showAll() = _state.update { it.showAll() }

    fun moveTo(crop: Crop) = _state.update { it.moveTo(crop) }

    /** 部位を待ち始めたら true。止めているあいだは受け付けないので false で、顔を探さなくてよい。 */
    fun request(part: FacePart) = _state.updateAndGet { it.request(part) }.pendingPart == part

    fun freeze() = _state.update { it.freeze() }

    fun unfreeze() = _state.update { it.unfreeze() }

    fun faceFound(face: FaceLandmarks, size: Size) = _state.update { it.faceFound(face, size) }

    fun toggle(panel: Panel) = _state.update { it.toggle(panel) }

    fun toggleLight() = _state.update { it.toggleLight() }

    fun adjustColor(change: (ColorAdjust) -> ColorAdjust) = _state.update { it.copy(color = change(it.color)) }

    fun adjustLight(change: (ScreenLight) -> ScreenLight) = _state.update { it.copy(light = change(it.light)) }
}
