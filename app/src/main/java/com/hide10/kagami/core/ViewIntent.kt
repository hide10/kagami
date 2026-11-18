package com.hide10.kagami.core

/** 寄る部位。eyeFrac は「両目の間隔が画面幅の何割に見えるか」。 */
enum class FacePart(val eyeFrac: Float) {
    EYES(0.55f),
    NOSE(0.9f),
    MOUTH(0.7f);

    fun landmark(face: FaceLandmarks): Pt = when (this) {
        EYES -> face.eyeMid
        NOSE -> face.nose
        MOUTH -> face.mouth
    }
}

/** 何を見せたいか。zoomMul はピンチで掛けた倍率。 */
sealed interface ViewIntent {
    val zoomMul: Float

    fun withZoomMul(zoomMul: Float): ViewIntent

    data class FaceLock(override val zoomMul: Float = 1f) : ViewIntent {
        override fun withZoomMul(zoomMul: Float) = copy(zoomMul = zoomMul)
    }

    data class Part(val part: FacePart, override val zoomMul: Float = 1f) : ViewIntent {
        override fun withZoomMul(zoomMul: Float) = copy(zoomMul = zoomMul)
    }

    /** center は元座標(タップ位置を toSource で戻した点)。 */
    data class Point(val center: Pt, override val zoomMul: Float = 1f) : ViewIntent {
        override fun withZoomMul(zoomMul: Float) = copy(zoomMul = zoomMul)
    }
}
