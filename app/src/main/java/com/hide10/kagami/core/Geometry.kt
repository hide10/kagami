package com.hide10.kagami.core

import kotlin.math.hypot

/** ビュー空間の点。単位はプレビュー領域の px(zoom=1 のとき)。 */
data class Pt(val x: Float, val y: Float) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Float) = Pt(x * k, y * k)
    operator fun div(k: Float) = Pt(x / k, y / k)

    fun midTo(o: Pt) = Pt((x + o.x) / 2, (y + o.y) / 2)
    fun distTo(o: Pt) = hypot(x - o.x, y - o.y)
}

data class Size(val w: Float, val h: Float) {
    val center get() = Pt(w / 2, h / 2)
}

data class FaceLandmarks(val leftEye: Pt, val rightEye: Pt, val nose: Pt, val mouth: Pt) {
    val eyeMid get() = leftEye.midTo(rightEye)
    val eyeDist get() = leftEye.distTo(rightEye)

    fun map(f: (Pt) -> Pt) = FaceLandmarks(f(leftEye), f(rightEye), f(nose), f(mouth))
}

/** graphicsLayer に渡す値。pivot はレイヤー中心。 */
data class LayerTransform(val scale: Float, val translationX: Float, val translationY: Float)

/** 表示領域は (W/zoom × H/zoom) で、中心が (cx, cy)。 */
data class Crop(val cx: Float, val cy: Float, val zoom: Float) {
    val center get() = Pt(cx, cy)

    fun clamp(size: Size): Crop {
        val z = zoom.coerceIn(1f, MAX_ZOOM)
        val halfW = size.w / (2 * z)
        val halfH = size.h / (2 * z)
        return Crop(cx.coerceIn(halfW, size.w - halfW), cy.coerceIn(halfH, size.h - halfH), z)
    }

    /** 元座標 c を画面中心へ運ぶ: screen = pivot + zoom*(p - pivot) + t で t = zoom*(center - c)。 */
    fun layerTransform(size: Size): LayerTransform {
        val mid = size.center
        return LayerTransform(zoom, zoom * (mid.x - cx), zoom * (mid.y - cy))
    }

    /** layerTransform の逆。画面上の点(タップ位置)を元座標へ。 */
    fun toSource(screen: Pt, size: Size): Pt = center + (screen - size.center) / zoom

    companion object {
        const val MAX_ZOOM = 5f

        fun full(size: Size) = Crop(size.w / 2, size.h / 2, 1f)
    }
}
