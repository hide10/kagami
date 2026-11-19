package com.kbcode.kagami.core

import kotlin.math.sqrt

/** 顔の外接矩形。正立画像の座標で、y は下向き。 */
data class FaceBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2
}

/** ML Kit が返したままのランドマーク。取れなかったものは null。 */
data class RawLandmarks(
    val leftEye: Pt?,
    val rightEye: Pt?,
    val nose: Pt?,
    val mouthLeft: Pt?,
    val mouthRight: Pt?,
)

/**
 * 取れなかったランドマークを、取れたものと顔の外接矩形から推定して補う。取れたものはそのまま使う。
 * 顔は正立画像でほぼ縦向きとし、傾きは両目の線と、目から鼻への向きから拾う。矩形が空で何も決められなければ null。
 */
fun completeLandmarks(raw: RawLandmarks, box: FaceBox): FaceLandmarks? {
    // 全部取れているときは外接矩形を見ない。
    if (raw.leftEye != null && raw.rightEye != null && raw.nose != null && raw.mouthLeft != null && raw.mouthRight != null) {
        return FaceLandmarks(raw.leftEye, raw.rightEye, raw.nose, raw.mouthLeft.midTo(raw.mouthRight))
    }
    if (box.width <= 0f || box.height <= 0f) return null
    // 顔の中心線の x。鼻、両目の中点、矩形の中心の順に頼る。
    val axisX = raw.nose?.x
        ?: if (raw.leftEye != null && raw.rightEye != null) raw.leftEye.midTo(raw.rightEye).x else box.centerX

    // 片目だけなら中心線で折り返す。ML Kit の LEFT_EYE は画像の右側に出るので、どちらも無いときもそれに合わせる。
    var leftEye = raw.leftEye
    var rightEye = raw.rightEye
    if (leftEye == null && rightEye != null) leftEye = Pt(2 * axisX - rightEye.x, rightEye.y)
    if (rightEye == null && leftEye != null) rightEye = Pt(2 * axisX - leftEye.x, leftEye.y)
    // 片目と鼻の x がほぼ一致すると、折り返した目が同じ点に潰れる。目の間隔が矩形の幅に比べて極端に狭ければ、両目とも矩形から取り直す。
    if (leftEye != null && rightEye != null && leftEye.distTo(rightEye) < box.width * MIN_EYE_SPAN) {
        leftEye = null
        rightEye = null
    }
    if (leftEye == null || rightEye == null) {
        val half = box.width * EYE_HALF_SPAN
        val y = raw.nose?.let { it.y - NOSE_BELOW_EYES * 2 * half } ?: (box.top + box.height * EYE_LINE)
        leftEye = Pt(axisX + half, y)
        rightEye = Pt(axisX - half, y)
    }

    val eyeMid = leftEye.midTo(rightEye)
    val span = leftEye.distTo(rightEye)
    // 目の線に垂直で、下向き(y が増える向き)の単位ベクトル。
    val down = run {
        val dx = rightEye.x - leftEye.x
        val dy = rightEye.y - leftEye.y
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 1e-6f) Pt(0f, 1f) else (Pt(-dy, dx) / len).let { if (it.y < 0) it * -1f else it }
    }

    val mouthRaw = when {
        raw.mouthLeft != null && raw.mouthRight != null -> raw.mouthLeft.midTo(raw.mouthRight)
        // 口の端が片方だけなら、中心線の上の同じ高さ。
        raw.mouthLeft != null -> Pt(axisX, raw.mouthLeft.y)
        raw.mouthRight != null -> Pt(axisX, raw.mouthRight.y)
        else -> null
    }
    val nose = raw.nose
        ?: mouthRaw?.let { eyeMid + (it - eyeMid) * NOSE_ON_EYE_TO_MOUTH }
        ?: (eyeMid + down * (span * NOSE_BELOW_EYES))
    val mouth = mouthRaw ?: (nose + (nose - eyeMid) * MOUTH_BELOW_NOSE)
    return FaceLandmarks(leftEye, rightEye, nose, mouth)
}

/** 外接矩形の幅に対する、両目の間隔の半分。 */
private const val EYE_HALF_SPAN = 0.22f

/** 両目の間隔が、外接矩形の幅のこれ未満なら潰れているとみなす。 */
private const val MIN_EYE_SPAN = 0.15f

/** 外接矩形の上端から目の線までの、高さに対する割合。 */
private const val EYE_LINE = 0.38f

/** 目の間隔に対する、目の線から鼻までの距離。 */
private const val NOSE_BELOW_EYES = 0.8f

/** 目から口までの距離に対する、目から鼻までの距離。 */
private const val NOSE_ON_EYE_TO_MOUTH = 0.65f

/** 目から鼻までのベクトルに対する、鼻から口までのベクトル。 */
private const val MOUTH_BELOW_NOSE = 0.45f
