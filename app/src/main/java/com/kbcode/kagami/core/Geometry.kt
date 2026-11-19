package com.kbcode.kagami.core

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

    /**
     * 画面の左右どちらに映る目か。鏡像なので、本人が自分の右目と思う目は画面の右に映る。
     * ミラー後は leftEye/rightEye の名前と画面の左右が一致するとは限らないので、x で決める。
     */
    val screenLeftEye get() = if (leftEye.x <= rightEye.x) leftEye else rightEye
    val screenRightEye get() = if (leftEye.x <= rightEye.x) rightEye else leftEye

    fun map(f: (Pt) -> Pt) = FaceLandmarks(f(leftEye), f(rightEye), f(nose), f(mouth))
}

/** graphicsLayer に渡す値。pivot はレイヤー中心。 */
data class LayerTransform(val scale: Float, val translationX: Float, val translationY: Float)

/** 表示領域は (W/zoom × H/zoom) で、中心が (cx, cy)。 */
data class Crop(val cx: Float, val cy: Float, val zoom: Float) {
    val center get() = Pt(cx, cy)

    /**
     * 表示領域を bounds の表示領域の内側に収める。既定の bounds は画像全体。倍率は bounds.zoom から MAX_ZOOM まで。
     * bounds 自身は clamp 済みであること。
     */
    fun clamp(size: Size, bounds: Crop = full(size)): Crop {
        val z = zoom.coerceIn(bounds.zoom, MAX_ZOOM)
        val slackW = size.w / (2 * bounds.zoom) - size.w / (2 * z)
        val slackH = size.h / (2 * bounds.zoom) - size.h / (2 * z)
        return Crop(cx.coerceIn(bounds.cx - slackW, bounds.cx + slackW), cy.coerceIn(bounds.cy - slackH, bounds.cy + slackH), z)
    }

    /**
     * カメラがすでに frame へ切り出した映像の上で、この切り出しを見せるための残りの拡大。どちらもこのビューの座標。
     * カメラの映像は p を pivot + frame.zoom*(p - frame.center) に描くので、それに重ねて同じ見え方になる Crop を返す。frame と同じなら全体(恒等)。
     */
    fun within(frame: Crop, size: Size): Crop {
        val mid = size.center
        return Crop(mid.x + frame.zoom * (cx - frame.cx), mid.y + frame.zoom * (cy - frame.cy), zoom / frame.zoom)
    }

    /** 元座標 c を画面中心へ運ぶ: screen = pivot + zoom*(p - pivot) + t で t = zoom*(center - c)。 */
    fun layerTransform(size: Size): LayerTransform {
        val mid = size.center
        return LayerTransform(zoom, zoom * (mid.x - cx), zoom * (mid.y - cy))
    }

    /** layerTransform の逆。画面上の点(タップ位置)を元座標へ。 */
    fun toSource(screen: Pt, size: Size): Pt = center + (screen - size.center) / zoom

    /** toSource の逆。元座標の点が画面のどこに映るか。 */
    fun toScreen(source: Pt, size: Size): Pt = size.center + (source - center) * zoom

    companion object {
        const val MAX_ZOOM = 10f

        fun full(size: Size) = Crop(size.w / 2, size.h / 2, 1f)
    }
}
