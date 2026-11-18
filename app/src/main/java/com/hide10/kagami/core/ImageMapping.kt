package com.hide10.kagami.core

import kotlin.math.max

/**
 * ML Kit の座標(回転済みの正立画像、非ミラー)をビュー空間へ移す。
 * PreviewView の FILL_CENTER(短辺を合わせて中央で切る)と、前面カメラの左右反転を再現する。
 * プレビューと解析が同じ画角(同じアスペクト比)であることが前提。
 */
class ImageMapping(private val image: Size, view: Size, private val mirror: Boolean) {
    private val scale = max(view.w / image.w, view.h / image.h)
    private val offset = Pt((view.w - image.w * scale) / 2, (view.h - image.h * scale) / 2)

    fun toView(p: Pt): Pt {
        val x = if (mirror) image.w - p.x else p.x
        return Pt(x * scale + offset.x, p.y * scale + offset.y)
    }

    fun toView(face: FaceLandmarks): FaceLandmarks = face.map(::toView)
}
