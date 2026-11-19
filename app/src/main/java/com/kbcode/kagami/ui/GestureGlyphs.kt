package com.kbcode.kagami.ui

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.kbcode.kagami.R

// 指の操作の絵。手の甲から見た白い右手で、立てた指の数がそのまま操作の指になる。触れた輪と動きの矢印は ACCENT で描く。
// 1 本指の手は人差し指を真ん中より左に立て、握った指の節と親指を見せる。真ん中に指 1 本だけが立つと中指に見えるため。
// 形は承認済みの下絵(help-icons-mock.svg)の数をそのまま使うので、格子は Glyphs.kt の 24 ではなく下絵の 96×96。

/** 使い方に出す指の操作を、出す順に。hint はそれでできること、how は操作のしかた、loopMs は絵の動きの 1 周の長さ。 */
enum class GestureIcon(@StringRes val hint: Int, @StringRes val how: Int, val loopMs: Int) {
    TAP(R.string.hint_focus, R.string.gesture_tap, 1600),
    THREE_FINGER_TAP(R.string.hint_light, R.string.gesture_three_finger_tap, 1600),
    LONG_PRESS(R.string.hint_pause, R.string.gesture_long_press, 2000),
    PINCH(R.string.hint_zoom, R.string.gesture_pinch, 1800),
    TWO_FINGER_HORIZONTAL(R.string.hint_exposure, R.string.gesture_two_finger_horizontal, 2000),
    TWO_FINGER_TAP(R.string.hint_full_screen, R.string.gesture_two_finger_tap, 1600),
}

/** 白い右手。fingers は立てた指、palm は手のひら、knuckles は握った指の節、thumb は親指の楕円の外枠、tips は指先の爪の点。 */
private class Hand(val fingers: List<Rect>, val palm: Rect, val knuckles: List<Rect>, val thumb: Rect, val tips: List<Offset> = emptyList())

/** 下絵の rect(左上 x, y と幅、高さ)。 */
private fun bar(x: Float, y: Float, width: Float, height: Float) = Rect(Offset(x, y), Size(width, height))

/** 下絵の ellipse(中心と半径)。 */
private fun oval(cx: Float, cy: Float, rx: Float, ry: Float = rx) = Rect(cx - rx, cy - ry, cx + rx, cy + ry)

/** 人差し指を左寄りに立て、ほかの 3 本を握った手。 */
private val POINTING = Hand(
    fingers = listOf(bar(35f, 8f, 13f, 44f)),
    palm = bar(30f, 44f, 40f, 42f),
    knuckles = listOf(oval(55f, 47f, 6.8f), oval(63f, 51f, 6.2f), oval(69f, 56f, 5.6f)),
    thumb = oval(27f, 62f, 6f, 11f),
    tips = listOf(Offset(41.5f, 22f)),
)

/** 人差し指・中指・薬指を立て、小指を握った手。 */
private val THREE = Hand(
    fingers = listOf(bar(32f, 10f, 12f, 40f), bar(47f, 6f, 12f, 44f), bar(62f, 12f, 12f, 38f)),
    palm = bar(28f, 44f, 44f, 42f),
    knuckles = listOf(oval(73f, 50f, 5.5f)),
    thumb = oval(25f, 62f, 6f, 11f),
    tips = listOf(Offset(38f, 17f), Offset(53f, 13f), Offset(68f, 19f)),
)

/** 人差し指と中指をそろえて立てた手。 */
private val TWO = Hand(
    fingers = listOf(bar(37f, 10f, 12.5f, 42f), bar(52.5f, 10f, 12.5f, 42f)),
    palm = bar(31f, 44f, 38f, 42f),
    knuckles = listOf(oval(61f, 47f, 6f), oval(68f, 53f, 5.5f)),
    thumb = oval(29f, 62f, 5.5f, 10.5f),
    tips = listOf(Offset(43f, 17f), Offset(58.5f, 17f)),
)

/** 上に左右の矢印を置くため、指と手のひらを短くした 2 本指の手。 */
private val TWO_LOW = Hand(
    fingers = listOf(bar(37f, 26f, 12.5f, 38f), bar(52.5f, 26f, 12.5f, 38f)),
    palm = bar(31f, 56f, 38f, 34f),
    knuckles = listOf(oval(61f, 59f, 6f), oval(68f, 64f, 5.5f)),
    thumb = oval(29f, 70f, 5.5f, 10f),
)

/** ピンチの手。2 本の指を、spread で V の字に開く。 */
private val PINCH_HAND = Hand(
    fingers = listOf(bar(33.5f, 12f, 13f, 44f), bar(50.5f, 12f, 13f, 44f)),
    palm = bar(29f, 48f, 39f, 40f),
    knuckles = emptyList(),
    thumb = oval(27f, 64f, 6f, 11f),
)

private val PINCH_ARROWS = svgPath("M14 26 L5 15 M3 24 L4 13 L14 14 M82 26 L91 15 M93 24 L92 13 L82 14")
private val SLIDE_ARROW = svgPath("M10 14 L86 14 M19 5 L9 14 L19 23 M77 5 L87 14 L77 23")

private val NAIL = Color(0xFFC9C9D2)

/** 長押しの、まだ満ちていない輪の下地。 */
private val TRACK = Color(0xFF4A4A55)

private const val PALM_CORNER = 13f
private const val TIP_RADIUS = 3.2f
private const val THUMB_TILT = -18f

/** V の字に開ききった指の傾き。 */
private const val SPREAD_ANGLE = 17f

/** 浮いた高さ 1 あたりに手を大きくする割合。浮くと見る人へ近づく。 */
private const val LIFT_GROW = 0.0075f

/**
 * 指の操作 icon の、ループの進み t(0..1、GestureMotion)での絵。t = 0 が承認済みの下絵と同じ形。
 * 輪は触れた面に残り、手だけが浮く。
 */
internal fun DrawScope.gestureGlyph(icon: GestureIcon, t: Float = 0f) = mockGrid {
    val pose = GestureMotion.pose(icon, t)
    when (icon) {
        GestureIcon.TAP -> {
            ring(oval(41.5f, 15f, 13f), pose)
            hand(POINTING, lift = pose.lift)
        }
        GestureIcon.THREE_FINGER_TAP -> {
            ring(oval(53f, 14f, 25f, 12.5f), pose)
            hand(THREE, lift = pose.lift)
        }
        GestureIcon.LONG_PRESS -> {
            val track = oval(41.5f, 17f, 13.5f)
            drawOval(TRACK.copy(alpha = pose.ringAlpha), track.topLeft, track.size, style = Stroke(5f))
            drawArc(
                ACCENT.copy(alpha = pose.ringAlpha), -90f, 360f * pose.fill, false, track.topLeft, track.size,
                style = Stroke(5f, cap = StrokeCap.Round),
            )
            translate(top = 3f) { hand(POINTING, lift = pose.lift) }
        }
        GestureIcon.PINCH -> {
            drawPath(PINCH_ARROWS, ACCENT, style = Stroke(4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            hand(PINCH_HAND, spread = SPREAD_ANGLE * pose.spread)
        }
        GestureIcon.TWO_FINGER_HORIZONTAL -> {
            drawPath(SLIDE_ARROW, ACCENT, style = Stroke(4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            translate(left = pose.slide) { hand(TWO_LOW) }
        }
        GestureIcon.TWO_FINGER_TAP -> {
            ring(oval(49f, 17f, 21f, 12.5f), pose)
            hand(TWO, lift = pose.lift)
        }
    }
}

/** 下絵の 96×96 の格子に描く。下絵の絵は高さ 90 ほどなので、3 下げて上下の真ん中に置く。 */
private inline fun DrawScope.mockGrid(block: DrawScope.() -> Unit) = scale(size.minDimension / 96f, Offset.Zero) { translate(top = 3f, block = block) }

/** タップの輪。at は下絵の輪で、pose の分だけ広がって薄れる。 */
private fun DrawScope.ring(at: Rect, pose: GesturePose) {
    val r = at.inflate(pose.ringGrow)
    drawOval(ACCENT.copy(alpha = pose.ringAlpha), r.topLeft, r.size, style = Stroke(4f))
}

/**
 * hand を lift だけ浮かせて描く。浮いた分だけ手のひらの上端を中心に大きくする。
 * spread は、手のひらの真ん中より左の指を左へ、右の指を右へ傾ける角度。指は手のひらの上端から 4 下を軸に傾く。
 */
private fun DrawScope.hand(hand: Hand, lift: Float = 0f, spread: Float = 0f) = withTransform({
    val grown = 1 + LIFT_GROW * lift
    translate(0f, -lift)
    scale(grown, grown, Offset(hand.palm.center.x, hand.palm.top))
}) {
    rotate(THUMB_TILT, hand.thumb.center) { drawOval(INK, hand.thumb.topLeft, hand.thumb.size) }
    drawRoundRect(INK, hand.palm.topLeft, hand.palm.size, CornerRadius(PALM_CORNER))
    for (k in hand.knuckles) drawOval(INK, k.topLeft, k.size)
    for (f in hand.fingers) {
        val angle = if (f.center.x < hand.palm.center.x) -spread else spread
        rotate(angle, Offset(f.center.x, hand.palm.top + 4f)) { drawRoundRect(INK, f.topLeft, f.size, CornerRadius(f.width / 2)) }
    }
    for (tip in hand.tips) drawCircle(NAIL, TIP_RADIUS, tip)
}
