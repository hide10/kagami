package com.hide10.kagami.core

/** 最後に見えた顔を holdNanos のあいだ返し続ける。検出の取りこぼしで全体表示へ戻らないための遅延。 */
class FaceHold(private val holdNanos: Long = 600_000_000L) {
    private var face: FaceLandmarks? = null
    private var seenAtNanos = 0L

    fun seen(face: FaceLandmarks, nowNanos: Long) {
        this.face = face
        seenAtNanos = nowNanos
    }

    fun get(nowNanos: Long): FaceLandmarks? = face?.takeIf { nowNanos - seenAtNanos <= holdNanos }
}
