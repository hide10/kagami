package com.kbcode.kagami.ui

import kotlin.math.PI
import kotlin.math.sin

/**
 * 指の操作の絵の、ループのある瞬間の形。長さは絵の 96×96 の格子の単位(56dp の絵で 1 が約 0.58dp)。
 * lift は手が触れた位置から浮いている高さで、0 が触れている。
 * ringGrow は触れた輪が下絵の大きさから広がった幅、ringAlpha はその輪の濃さ。長押しでは、満ちていく輪とその下地の濃さ。
 * fill は長押しの輪の満ち具合(0..1)。slide は手の横のずれ、spread は 2 本の指の開き(0 がそろい、1 が V の字)。
 */
internal data class GesturePose(
    val lift: Float = 0f,
    val ringGrow: Float = 0f,
    val ringAlpha: Float = 0f,
    val fill: Float = 0f,
    val slide: Float = 0f,
    val spread: Float = 0f,
)

/**
 * 使い方の絵の小さな動き。t はループの進み(0..1)で、1 は 0 に戻る。
 * t = 0 がいちばん分かりやすい形(承認済みの下絵と同じ形)で、動きを止めた端末ではこの形のまま見える。
 * タップは指の数によらず手が 1 回下りて輪が 1 つ広がり、長押しは指が触れたまま輪が満ちる。
 */
internal object GestureMotion {
    /** 浮いた手の高さ。56dp の絵で 3dp ほど。 */
    private const val LIFT = 5f

    /** 輪が広がりきって消えるまでの、ループに対する長さ。 */
    private const val RIPPLE_LIFE = 0.4f

    /** 輪が広がる幅。56dp の絵で 3dp ほどで、広がっても絵の上端からほとんど出ない。 */
    private const val RIPPLE_GROW = 5f

    /** 露出の手が左右へ動く幅。 */
    private const val SLIDE = 9f

    private val TAP_LIFT = listOf(Key(0f, 0f), Key(0.12f, 0f), Key(0.4f, LIFT), Key(0.78f, LIFT), Key(1f, 0f))

    /** 輪は EMPTY_FROM で消えきってから空になり、FILL_FROM で指が触れて、そこから FILL_LENGTH(2 秒のループで 1 秒)かけて満ちる。 */
    private const val EMPTY_FROM = 0.45f
    private const val FILL_FROM = 0.675f
    private const val FILL_LENGTH = 0.5f

    /** 輪が満ちたら少し待って指を上げ、輪を消す。FILL_FROM で触れ直すまで浮いている。 */
    private val LONG_PRESS_LIFT = listOf(Key(0f, 0f), Key(0.25f, 0f), Key(0.38f, LIFT), Key(0.55f, LIFT), Key(FILL_FROM, 0f), Key(1f, 0f))
    private val LONG_PRESS_RING = listOf(Key(0f, 1f), Key(0.25f, 1f), Key(0.4f, 0f), Key(0.6f, 0f), Key(FILL_FROM, 1f), Key(1f, 1f))

    private val PINCH_SPREAD = listOf(Key(0f, 1f), Key(0.12f, 1f), Key(0.45f, 0f), Key(0.6f, 0f), Key(0.92f, 1f), Key(1f, 1f))

    fun pose(icon: GestureIcon, t: Float): GesturePose {
        val at = t.mod(1f)
        return when (icon) {
            GestureIcon.TAP, GestureIcon.THREE_FINGER_TAP, GestureIcon.TWO_FINGER_TAP -> {
                val x = (at / RIPPLE_LIFE).coerceAtMost(1f)
                GesturePose(lift = TAP_LIFT.at(at), ringGrow = RIPPLE_GROW * (1 - (1 - x) * (1 - x)), ringAlpha = 1 - x * x * x)
            }
            GestureIcon.LONG_PRESS -> GesturePose(
                lift = LONG_PRESS_LIFT.at(at),
                ringAlpha = LONG_PRESS_RING.at(at),
                fill = if (at in EMPTY_FROM..FILL_FROM) 0f else ((at - FILL_FROM).mod(1f) / FILL_LENGTH).coerceAtMost(1f),
            )
            GestureIcon.PINCH -> GesturePose(spread = PINCH_SPREAD.at(at))
            GestureIcon.TWO_FINGER_HORIZONTAL -> GesturePose(slide = SLIDE * sin(2 * PI * at).toFloat())
        }
    }

    private class Key(val at: Float, val value: Float)

    /** キーのあいだを、両端でゆっくりになる曲線でつなぐ。 */
    private fun List<Key>.at(t: Float): Float {
        val next = indexOfFirst { it.at >= t }.coerceAtLeast(1)
        val a = this[next - 1]
        val b = this[next]
        val x = ((t - a.at) / (b.at - a.at)).coerceIn(0f, 1f)
        return a.value + (b.value - a.value) * x * x * (3 - 2 * x)
    }
}
