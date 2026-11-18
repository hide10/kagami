package com.hide10.kagami.core

import com.hide10.kagami.core.Crop.Companion.MAX_ZOOM
import kotlin.math.max

object CropPlanner {
    /** 顔追従のとき、両目の間隔が画面幅に占める割合。 */
    const val TARGET_EYE_FRAC = 0.30f

    /** タップした点へ寄るときの倍率。 */
    const val POINT_ZOOM = 2.5f

    /** 顔追従で目と鼻の中点を置く画面上の位置(幅・高さに対する割合)。 */
    const val LOCK_ANCHOR_X = 0.5f
    const val LOCK_ANCHOR_Y = 0.45f

    /** 元座標の focus を画面上の anchor に置き、zoom 倍で見せる。zoom は 1..MAX_ZOOM に収めた値。 */
    private class Framing(val focus: Pt, val anchor: Pt, val zoom: Float)

    private fun framing(intent: ViewIntent, face: FaceLandmarks?, size: Size): Framing? = when (intent) {
        is ViewIntent.FaceLock -> face?.let {
            Framing(
                focus = it.eyeMid.midTo(it.nose),
                anchor = Pt(LOCK_ANCHOR_X * size.w, LOCK_ANCHOR_Y * size.h),
                zoom = clampZoom(TARGET_EYE_FRAC * size.w / it.eyeDist),
            )
        }
        is ViewIntent.Part -> face?.let {
            Framing(intent.part.landmark(it), size.center, clampZoom(intent.part.eyeFrac * size.w / it.eyeDist))
        }
        is ViewIntent.Point -> Framing(intent.center, size.center, POINT_ZOOM)
    }

    /** 顔が要る意図で顔が無ければ全体を見せる。 */
    fun target(intent: ViewIntent, face: FaceLandmarks?, size: Size): Crop {
        val f = framing(intent, face, size) ?: return Crop.full(size)
        val zoom = clampZoom(f.zoom * intent.zoomMul)
        val c = f.focus - (f.anchor - size.center) / zoom
        return Crop(c.x, c.y, zoom).clamp(size)
    }

    /**
     * ピンチで倍率を factor 倍する。zoomMul は実際の倍率が 1..MAX_ZOOM に収まる範囲に留め、
     * 上限で指を広げ続けても戻すとすぐ縮むようにする。
     * 顔待ちで全体を出しているときは、いま見えている中心を Point にして寄る。
     */
    fun pinch(intent: ViewIntent, factor: Float, face: FaceLandmarks?, shown: Crop, size: Size): ViewIntent {
        val f = framing(intent, face, size)
            ?: return ViewIntent.Point(shown.center, clampZoom(shown.zoom * factor) / POINT_ZOOM)
        return intent.withZoomMul((intent.zoomMul * factor).coerceIn(1f / f.zoom, MAX_ZOOM / f.zoom))
    }

    /** タップした画面上の点へ寄る。いま POINT_ZOOM より寄っていれば倍率は保つ。 */
    fun tap(screen: Pt, shown: Crop, size: Size): ViewIntent.Point =
        ViewIntent.Point(shown.toSource(screen, size), max(1f, shown.zoom / POINT_ZOOM))

    /** 部位を画面の真ん中に置き、両目の間隔が画面幅の part.eyeFrac に見えるまで寄る。face はビュー座標。 */
    fun zoomOnPart(part: FacePart, face: FaceLandmarks, size: Size): Crop {
        val p = part.landmark(face)
        return Crop(p.x, p.y, part.eyeFrac * size.w / face.eyeDist).clamp(size)
    }

    /**
     * 指の動きを切り出しに映す。引数は detectTransformGestures の値で、centroid は動く前の指の重心、pan はその移動、
     * zoom は指の間隔の比。centroid の下にあった像を centroid + pan へ運び、画像の外は見せない。
     */
    fun transform(crop: Crop, centroid: Pt, pan: Pt, zoom: Float, size: Size): Crop {
        val held = crop.toSource(centroid, size)
        val z = clampZoom(crop.zoom * zoom)
        val c = held - (centroid + pan - size.center) / z
        return Crop(c.x, c.y, z).clamp(size)
    }

    private fun clampZoom(z: Float) = z.coerceIn(1f, MAX_ZOOM)
}
