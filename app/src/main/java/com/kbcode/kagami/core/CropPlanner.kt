package com.kbcode.kagami.core

import com.kbcode.kagami.core.Crop.Companion.MAX_ZOOM
import kotlin.math.max

object CropPlanner {
    /** タップした点へ寄るときの倍率。 */
    const val POINT_ZOOM = 2.5f

    /** タップした点を真ん中へ運び、POINT_ZOOM まで寄る。いまそれより寄っていれば倍率は保つ。bounds(既定は画像全体)の外は見せない。 */
    fun tap(screen: Pt, shown: Crop, size: Size, bounds: Crop = Crop.full(size)): Crop {
        val p = shown.toSource(screen, size)
        return Crop(p.x, p.y, max(shown.zoom, POINT_ZOOM)).clamp(size, bounds)
    }

    /** 部位を画面の真ん中に置き、両目の間隔が画面幅の part.eyeFrac に見えるまで寄る。face はビュー座標。 */
    fun zoomOnPart(part: FacePart, face: FaceLandmarks, size: Size): Crop {
        val p = part.landmark(face)
        return Crop(p.x, p.y, part.eyeFrac * size.w / face.eyeDist).clamp(size)
    }

    /**
     * 指の動きを切り出しに映す。引数は detectTransformGestures の値で、centroid は動く前の指の重心、pan はその移動、
     * zoom は指の間隔の比。centroid の下にあった像を centroid + pan へ運び、bounds(既定は画像全体)の外は見せない。
     */
    fun transform(crop: Crop, centroid: Pt, pan: Pt, zoom: Float, size: Size, bounds: Crop = Crop.full(size)): Crop {
        val held = crop.toSource(centroid, size)
        val z = (crop.zoom * zoom).coerceIn(bounds.zoom, MAX_ZOOM)
        val c = held - (centroid + pan - size.center) / z
        return Crop(c.x, c.y, z).clamp(size, bounds)
    }
}
