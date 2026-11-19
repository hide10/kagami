package com.kbcode.kagami.ui

import androidx.annotation.StringRes
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.util.lerp
import com.kbcode.kagami.R

// 指の操作の絵。手の甲から見た白い手で、伸ばした指の数と向きがそのまま操作の指になる。触れた輪と動きの矢印は ACCENT で描く。
// 手の部品は、下地の色 gap の縁取りを敷いてから白く塗る。後から描いた指が前に重なり、そのあいだに切れ目が見える。
// 格子は Glyphs.kt と同じ 24×24。

/** 使い方に出す指の操作を、出す順に。label は絵の読み上げに使う操作の名前、hint はそれでできること、loopMs は絵の動きの 1 周の長さ。 */
enum class GestureIcon(@StringRes val label: Int, @StringRes val hint: Int, val loopMs: Int) {
    TAP(R.string.gesture_tap, R.string.hint_focus, 1600),
    DOUBLE_TAP(R.string.gesture_double_tap, R.string.hint_light, 1800),
    LONG_PRESS(R.string.gesture_long_press, R.string.hint_pause, 2000),
    PINCH(R.string.gesture_pinch, R.string.hint_zoom, 1800),
    TWO_FINGER_HORIZONTAL(R.string.gesture_two_finger_horizontal, R.string.hint_exposure, 2000),
    TWO_FINGER_TAP(R.string.gesture_two_finger_tap, R.string.hint_full_screen, 1600),
}

/** 指 1 本。付け根 (x, y) から角度 angle(0 が真上で、正が時計回り)へ、長さ length・太さ width の両端の丸い棒で伸びる。 */
private class Digit(val x: Float, val length: Float, val width: Float, val angle: Float, val y: Float = 0f)

/** 手の甲から見た右手。指の付け根の並びが y = 0。extended は伸ばして爪を見せる指、curled は握った指、tip は絵の中で位置を合わせる指先。 */
private class Hand(val extended: List<Digit>, val curled: List<Digit>, val tip: Offset)

private val THUMB = Digit(0.6f, 9.6f, 3.4f, -38f, y = 6.4f)
private val PALM_TOP_LEFT = Offset(-2.2f, -1.2f)
private val PALM = Size(11.6f, 9.4f)
private const val PALM_CORNER = 3.4f

/** 人差し指 1 本を立てた手。 */
private val POINTING = Hand(
    extended = listOf(Digit(0.2f, 11f, 3.6f, 0f)),
    curled = listOf(Digit(3.2f, 3.8f, 3.2f, 0f), Digit(6f, 3.2f, 3f, 4f), Digit(8.4f, 2.4f, 2.6f, 10f)),
    tip = Offset(0.2f, -9.2f),
)

private val CURLED_TWO = listOf(Digit(6.6f, 3.2f, 3f, 6f), Digit(8.8f, 2.4f, 2.6f, 12f))

/** 人差し指と中指をそろえて立てた手。tip は 2 本の指先のあいだ。 */
private val TWO = Hand(listOf(Digit(0f, 10.6f, 3.4f, -4f), Digit(3.6f, 11.4f, 3.4f, 4f)), CURLED_TWO, Offset(1.8f, -9f))

/** 2 本の指を V の字に開いた手。 */
private val SPREAD = Hand(listOf(Digit(-0.6f, 10.4f, 3.4f, -20f), Digit(3.8f, 11f, 3.4f, 18f)), CURLED_TWO, Offset(1.8f, -9f))

/** そろえた 2 本(open = 0)から V の字(open = 1)までのあいだの手。 */
private fun opened(open: Float) = Hand(
    TWO.extended.zip(SPREAD.extended) { a, b -> Digit(lerp(a.x, b.x, open), lerp(a.length, b.length, open), a.width, lerp(a.angle, b.angle, open)) },
    CURLED_TWO,
    TWO.tip,
)

/** 指 1 本の絵で、触れている指先。 */
private val TIP = Offset(10.5f, 7f)

/** 縁取りの、部品の外へはみ出す幅。 */
private const val CUT = 1f
private const val RIPPLE = 1.5f
private const val ARROW = 1.7f

/** 浮いた高さ 1 あたりに手を大きくする割合。浮くと見る人へ近づき、押し込むと遠のく。 */
private const val LIFT_GROW = 0.03f
private val NAIL = Color(0xFFC9C5CE)

/**
 * 指の操作 icon の、ループの進み t(0..1、GestureMotion)での絵。gap は絵を置く下地の色で、指のあいだの切れ目に使う。
 * タップの輪は 1 回なら 1 重、ダブルタップなら 2 重。長押しは押している長さを、満ちていく輪で表す。
 */
internal fun DrawScope.gestureGlyph(icon: GestureIcon, gap: Color, t: Float = 0f) = grid {
    val pose = GestureMotion.pose(icon, t)
    when (icon) {
        GestureIcon.TAP, GestureIcon.DOUBLE_TAP -> {
            for (r in pose.ripples) ring(TIP, r.radius, r.alpha)
            hand(POINTING, TIP, gap, lift = pose.lift)
        }
        GestureIcon.LONG_PRESS -> {
            ring(TIP, 4.4f, alpha = 0.3f * pose.fillAlpha)
            drawArc(
                ACCENT.copy(alpha = pose.fillAlpha), -90f, 360f * pose.fill, false, TIP - Offset(4.4f, 4.4f), Size(8.8f, 8.8f),
                style = Stroke(RIPPLE, cap = StrokeCap.Round),
            )
            hand(POINTING, TIP, gap, lift = pose.lift)
        }
        GestureIcon.PINCH -> {
            arrow(Offset(7.6f, 4.2f), Offset(3.4f, 1.4f))
            arrow(Offset(16.4f, 4.2f), Offset(20.6f, 1.4f))
            hand(opened(pose.spread), Offset(12.2f, 7.9f), gap, scale = 0.86f)
        }
        GestureIcon.TWO_FINGER_HORIZONTAL -> {
            arrow(Offset(4.2f, 2.4f), Offset(19.8f, 2.4f), both = true)
            hand(TWO, Offset(12f + pose.slide, 7.6f), gap, scale = 0.9f)
        }
        GestureIcon.TWO_FINGER_TAP -> {
            for (r in pose.ripples) ring(Offset(12f, 7.2f), r.radius, r.alpha)
            hand(TWO, Offset(12f, 7.6f), gap, scale = 0.9f, lift = pose.lift)
        }
    }
}

/**
 * hand を、指先 tip が at に来るよう scale 倍で描く。lift だけ浮かせ、浮いた分だけ指先を中心に大きくする。
 * 親指を手のひらの奥に、指を小指から順に前へ重ねる。
 */
private fun DrawScope.hand(hand: Hand, at: Offset, gap: Color, scale: Float = 1f, lift: Float = 0f) = withTransform({
    val grown = scale * (1 + LIFT_GROW * lift)
    translate(at.x, at.y - lift)
    scale(grown, grown, Offset.Zero)
    translate(-hand.tip.x, -hand.tip.y)
}) {
    digit(THUMB, gap)
    part(PALM_TOP_LEFT, PALM, PALM_CORNER, gap)
    for (d in hand.curled.asReversed()) digit(d, gap)
    for (d in hand.extended.asReversed()) digit(d, gap, nail = true)
}

private fun DrawScope.digit(d: Digit, gap: Color, nail: Boolean = false) = withTransform({
    translate(d.x, d.y)
    rotate(d.angle, Offset.Zero)
}) {
    part(Offset(-d.width / 2, -d.length), Size(d.width, d.length), d.width / 2, gap)
    if (nail) {
        val size = Size(d.width * 0.48f, d.width * 0.6f)
        drawRoundRect(NAIL, Offset(-size.width / 2, -d.length + d.width * 0.32f), size, CornerRadius(size.width / 2))
    }
}

/** 手の部品ひとつ。縁取りを先に敷くので、前の部品との境に gap の色の切れ目が残る。 */
private fun DrawScope.part(topLeft: Offset, size: Size, corner: Float, gap: Color) {
    drawRoundRect(gap, topLeft, size, CornerRadius(corner), style = Stroke(CUT * 2))
    drawRoundRect(INK, topLeft, size, CornerRadius(corner))
}

private fun DrawScope.ring(at: Offset, radius: Float, alpha: Float = 1f) =
    drawCircle(ACCENT.copy(alpha = alpha), radius, at, style = Stroke(RIPPLE))

/** from から to への矢印。both なら from の側にも矢じりを付ける。 */
private fun DrawScope.arrow(from: Offset, to: Offset, both: Boolean = false) {
    drawLine(ACCENT, from, to, ARROW, StrokeCap.Round)
    head(to, to - from)
    if (both) head(from, from - to)
}

private fun DrawScope.head(at: Offset, direction: Offset) {
    val along = direction / direction.getDistance()
    val back = at - along * 2f
    val side = Offset(-along.y, along.x) * 1.8f
    val path = Path().apply {
        moveTo(back.x + side.x, back.y + side.y)
        lineTo(at.x, at.y)
        lineTo(back.x - side.x, back.y - side.y)
    }
    drawPath(path, ACCENT, style = Stroke(ARROW, cap = StrokeCap.Round, join = StrokeJoin.Round))
}
