package com.kbcode.kagami.core

import kotlin.math.max

/**
 * ML Kit の座標(回転済みの正立画像、非ミラー)をビュー空間へ移す。
 * PreviewView の FILL_CENTER(短辺を合わせて中央で切る)と、前面カメラの左右反転を再現する。
 * frame はその解析画像にカメラが切り出していた範囲(ビュー座標)で、既定は全体。
 * プレビューと解析が同じ画角(同じアスペクト比)であることが前提。
 */
class ImageMapping(private val image: Size, private val view: Size, private val mirror: Boolean, private val frame: Crop = Crop.full(view)) {
    private val scale = max(view.w / image.w, view.h / image.h)
    private val offset = Pt((view.w - image.w * scale) / 2, (view.h - image.h * scale) / 2)

    fun toView(p: Pt): Pt {
        val x = if (mirror) image.w - p.x else p.x
        return frame.toSource(Pt(x * scale + offset.x, p.y * scale + offset.y), view)
    }

    fun toView(face: FaceLandmarks): FaceLandmarks = face.map(::toView)
}

/** センサー画像の幅・高さと回転角から、ML Kit が座標を返す正立画像の大きさ。 */
fun uprightSize(width: Int, height: Int, rotationDegrees: Int): Size =
    if (rotationDegrees % 180 == 0) Size(width.toFloat(), height.toFloat()) else Size(height.toFloat(), width.toFloat())
