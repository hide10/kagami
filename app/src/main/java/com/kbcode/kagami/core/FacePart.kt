package com.kbcode.kagami.core

/**
 * 寄る部位。eyeFrac は「両目の間隔が画面幅の何割に見えるか」。
 * LEFT_EYE と RIGHT_EYE は画面の左と右に映る目。1.1 だと、倍率が上限に届かない限りもう片方の目は画面の外に出る。
 */
enum class FacePart(val eyeFrac: Float) {
    LEFT_EYE(1.1f),
    RIGHT_EYE(1.1f),
    NOSE(0.9f),

    // 口の両端の間隔は両目の間隔のおよそ 0.8 倍なので、口が画面幅の 9 割に見えるのは両目が 0.9 / 0.8 ≒ 1.13 のとき。
    MOUTH(0.9f / 0.8f);

    fun landmark(face: FaceLandmarks): Pt = when (this) {
        LEFT_EYE -> face.screenLeftEye
        RIGHT_EYE -> face.screenRightEye
        NOSE -> face.nose
        MOUTH -> face.mouth
    }
}
