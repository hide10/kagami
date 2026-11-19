package com.kbcode.kagami.ui

import kotlin.math.PI
import kotlin.math.sin

/** 触れたところから広がる輪ひとつ。半径は 24×24 の格子の単位。 */
internal data class Ripple(val radius: Float, val alpha: Float)

/**
 * 指の操作の絵の、ループのある瞬間の形。
 * lift は手が触れた位置から浮いている高さ(格子の単位)で、0 が触れている、負は押し込んでいる。
 * fill は長押しの輪の満ち具合(0..1)、fillAlpha はその輪の濃さ。slide は手の横のずれ、spread は 2 本の指の開き(0 がそろい、1 が V の字)。
 */
internal data class GesturePose(
    val lift: Float = 0f,
    val ripples: List<Ripple> = emptyList(),
    val fill: Float = 0f,
    val fillAlpha: Float = 0f,
    val slide: Float = 0f,
    val spread: Float = 0f,
)

/**
 * 使い方の絵の小さな動き。t はループの進み(0..1)で、1 は 0 に戻る。
 * t = 0 がいちばん分かりやすい形で、動きを止めた端末ではこの形のまま見える。
 * タップは指が 1 回下りて輪が 1 つ広がり、ダブルタップは続けて 2 回下りて輪が 2 つ重なり、長押しは指が沈んだまま輪が満ちる。
 */
internal object GestureMotion {
    /** 浮いた指の高さ。56dp の絵で 3dp ほど。 */
    private const val LIFT = 1.4f

    /** 長押しで指が沈む深さ。 */
    private const val SINK = 0.7f

    /** 輪が広がりきって消えるまでの、ループに対する長さ。 */
    private const val RIPPLE_LIFE = 0.4f

    /** 輪が広がる最大の半径。指先の中心から絵の上端までに収まる。 */
    private const val RIPPLE_MAX = 6.6f

    /** 露出の手が左右へ動く幅。 */
    private const val SLIDE = 2.4f

    private val TAP_LIFT = listOf(Key(0f, 0f), Key(0.12f, 0f), Key(0.4f, LIFT), Key(0.78f, LIFT), Key(1f, 0f))

    /** 0.8 と 1(= 0)で 2 回触れ、2 回目のすぐあとが t = 0。 */
    private val DOUBLE_TAP_LIFT = listOf(
        Key(0f, 0f), Key(0.06f, 0f), Key(0.24f, LIFT), Key(0.68f, LIFT), Key(0.8f, 0f), Key(0.84f, 0f), Key(0.9f, LIFT * 0.6f), Key(1f, 0f),
    )
    private const val DOUBLE_TAP_FIRST = 0.8f

    /** 0.62 で触れて 0.7 までに沈み、輪が満ちるまで沈んだまま。満ちたら指を上げて輪を消す。 */
    private val LONG_PRESS_LIFT = listOf(
        Key(0f, -SINK), Key(0.3f, -SINK), Key(0.42f, LIFT), Key(0.55f, LIFT), Key(0.62f, 0f), Key(0.7f, -SINK), Key(1f, -SINK),
    )
    private val LONG_PRESS_RING = listOf(Key(0f, 1f), Key(0.3f, 1f), Key(0.45f, 0f), Key(0.64f, 0f), Key(0.7f, 1f), Key(1f, 1f))

    /** 輪は EMPTY_FROM で消えきってから空になり、FILL_FROM から FILL_LENGTH かけて満ちる。 */
    private const val EMPTY_FROM = 0.45f
    private const val FILL_FROM = 0.7f
    private const val FILL_LENGTH = 0.4f

    private val PINCH_SPREAD = listOf(Key(0f, 1f), Key(0.12f, 1f), Key(0.45f, 0f), Key(0.6f, 0f), Key(0.92f, 1f), Key(1f, 1f))

    fun pose(icon: GestureIcon, t: Float): GesturePose {
        val at = t.mod(1f)
        return when (icon) {
            GestureIcon.TAP -> GesturePose(lift = TAP_LIFT.at(at), ripples = listOf(ripple(at, 3.6f)))
            GestureIcon.DOUBLE_TAP -> GesturePose(
                lift = DOUBLE_TAP_LIFT.at(at),
                ripples = listOf(ripple(at - DOUBLE_TAP_FIRST, 3.4f), ripple(at, 3.4f)),
            )
            GestureIcon.LONG_PRESS -> GesturePose(
                lift = LONG_PRESS_LIFT.at(at),
                fill = if (at in EMPTY_FROM..FILL_FROM) 0f else ((at - FILL_FROM).mod(1f) / FILL_LENGTH).coerceAtMost(1f),
                fillAlpha = LONG_PRESS_RING.at(at),
            )
            GestureIcon.PINCH -> GesturePose(spread = PINCH_SPREAD.at(at))
            GestureIcon.TWO_FINGER_HORIZONTAL -> GesturePose(slide = SLIDE * sin(2 * PI * at).toFloat())
            GestureIcon.TWO_FINGER_TAP -> GesturePose(lift = TAP_LIFT.at(at), ripples = listOf(ripple(at, 4.8f)))
        }
    }

    /** 経過 since(ループの単位、負なら 1 周前に生まれた)の輪。from の半径で生まれ、速く広がってから薄れて消える。 */
    private fun ripple(since: Float, from: Float): Ripple {
        val x = (since.mod(1f) / RIPPLE_LIFE).coerceAtMost(1f)
        val grow = 1 - (1 - x) * (1 - x)
        return Ripple(from + (RIPPLE_MAX - from) * grow, 1 - x * x * x)
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
