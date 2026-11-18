package com.hide10.kagami.core

import org.junit.Assert.assertEquals

val PHONE = Size(1080f, 1920f)

/** graphicsLayer(pivot 中心)が元座標 p を画面のどこに描くか。 */
fun screenOf(p: Pt, crop: Crop, size: Size): Pt {
    val t = crop.layerTransform(size)
    val pivot = size.center
    return Pt(pivot.x + t.scale * (p.x - pivot.x) + t.translationX, pivot.y + t.scale * (p.y - pivot.y) + t.translationY)
}

fun assertPt(expected: Pt, actual: Pt, eps: Float = 0.5f) {
    assertEquals("x of $actual", expected.x, actual.x, eps)
    assertEquals("y of $actual", expected.y, actual.y, eps)
}

/** 両目が eyeDist 離れ、目の中点が eyeMid にある正面の顔。 */
fun face(eyeMid: Pt, eyeDist: Float) = FaceLandmarks(
    leftEye = Pt(eyeMid.x - eyeDist / 2, eyeMid.y),
    rightEye = Pt(eyeMid.x + eyeDist / 2, eyeMid.y),
    nose = Pt(eyeMid.x, eyeMid.y + 0.6f * eyeDist),
    mouth = Pt(eyeMid.x, eyeMid.y + 1.1f * eyeDist),
)

/** 60fps で seconds 秒ぶん target へ寄せる。 */
fun settle(from: Crop, target: Crop, seconds: Float = 3f): Crop {
    var c = from
    repeat((seconds * 60).toInt()) { c = CropSmoother.step(c, target, 1f / 60) }
    return c
}

/** 画面の四隅が元画像の内側を指していれば、表示に空白は出ない。 */
fun assertShowsOnlyImage(crop: Crop, size: Size) {
    val eps = 0.01f
    for (corner in listOf(Pt(0f, 0f), Pt(size.w, size.h))) {
        val src = crop.toSource(corner, size)
        check(src.x >= -eps && src.x <= size.w + eps && src.y >= -eps && src.y <= size.h + eps) {
            "$crop shows outside the image at screen $corner -> $src"
        }
    }
    check(crop.zoom in 1f..Crop.MAX_ZOOM) { "$crop zoom out of range" }
}
