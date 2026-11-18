package com.hide10.kagami.core

enum class Panel { COLOR, LIGHT }

data class MirrorState(
    val intent: ViewIntent = ViewIntent.FaceLock(),
    val color: ColorAdjust = ColorAdjust(),
    val light: ScreenLight = ScreenLight(),
    val panel: Panel? = null,
) {
    fun toggle(p: Panel) = copy(panel = p.takeUnless { it == panel })
}
