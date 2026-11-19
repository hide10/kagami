package com.kbcode.kagami.core

import com.kbcode.kagami.core.Crop.Companion.MAX_ZOOM

object CropPlanner {
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
