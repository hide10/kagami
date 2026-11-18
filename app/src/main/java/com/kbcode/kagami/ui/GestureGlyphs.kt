package com.kbcode.kagami.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

// 指の操作の絵。指先は薄い縁のついた白い丸で、指の数がそのまま丸の数になる。動きの矢印と触れた輪は ACCENT で描く。
// 格子は Glyphs.kt と同じ 24×24。

/** 使い方に出す指の操作。ja と en は絵の読み上げに使う操作の名前。 */
enum class GestureIcon(val ja: String, val en: String) {
    TAP("タップ", "Tap"),
    DOUBLE_TAP("ダブルタップ", "Double tap"),
    LONG_PRESS("長押し", "Long press"),
    EDGE_DRAG("右端の上下ドラッグ", "Drag up or down on the right edge"),
    PINCH("ピンチ", "Pinch"),
    TWO_FINGER_VERTICAL("2本指の上下ドラッグ", "Two-finger drag up or down"),
    TWO_FINGER_HORIZONTAL("2本指の水平ドラッグ", "Two-finger drag sideways"),
    TWO_FINGER_TAP("2本指タップ", "Two-finger tap"),
}

private val MID = Offset(12f, 12f)

/** タップの輪は 1 回なら 1 重、ダブルタップなら 2 重。長押しは押している長さを、満ちていく輪で表す。 */
internal fun DrawScope.gestureGlyph(icon: GestureIcon) = grid {
    when (icon) {
        GestureIcon.TAP -> {
            ring(MID, 6.5f)
            finger(MID)
        }
        GestureIcon.DOUBLE_TAP -> {
            ring(MID, 6f)
            ring(MID, 9.5f)
            finger(MID)
        }
        GestureIcon.LONG_PRESS -> {
            ring(MID, 7f, alpha = 0.3f)
            drawArc(ACCENT, -90f, 270f, false, Offset(5f, 5f), Size(14f, 14f), style = Stroke(1.8f, cap = StrokeCap.Round))
            finger(MID)
        }
        GestureIcon.EDGE_DRAG -> {
            drawLine(INK.copy(alpha = 0.45f), Offset(20.5f, 3f), Offset(20.5f, 21f), 1.6f, StrokeCap.Round)
            upAndDown(13f)
        }
        GestureIcon.PINCH -> {
            arrow(Offset(5.9f, 18.1f), Offset(2.8f, 21.2f))
            arrow(Offset(18.1f, 5.9f), Offset(21.2f, 2.8f))
            finger(Offset(9f, 15f))
            finger(Offset(15f, 9f))
        }
        GestureIcon.TWO_FINGER_VERTICAL -> twoUpAndDown()
        GestureIcon.TWO_FINGER_HORIZONTAL -> rotate(90f, MID) { twoUpAndDown() }
        GestureIcon.TWO_FINGER_TAP -> {
            drawRoundRect(ACCENT, Offset(1.8f, 6f), Size(20.4f, 12f), CornerRadius(6f), style = Stroke(1.6f))
            finger(Offset(7.7f, 12f))
            finger(Offset(16.3f, 12f))
        }
    }
}

private fun DrawScope.finger(at: Offset) {
    drawCircle(INK.copy(alpha = 0.25f), 3.6f, at)
    drawCircle(INK, 2.3f, at)
}

private fun DrawScope.ring(at: Offset, radius: Float, alpha: Float = 1f) =
    drawCircle(ACCENT.copy(alpha = alpha), radius, at, style = Stroke(1.6f))

private fun DrawScope.arrow(from: Offset, to: Offset) {
    val d = to - from
    val along = d / d.getDistance()
    val back = to - along * 2.3f
    val side = Offset(-along.y, along.x) * 2f
    val head = Path().apply {
        moveTo(back.x + side.x, back.y + side.y)
        lineTo(to.x, to.y)
        lineTo(back.x - side.x, back.y - side.y)
    }
    drawLine(ACCENT, from, to, 1.8f, StrokeCap.Round)
    drawPath(head, ACCENT, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** x の列に置いた指 1 本と、その上下の矢印。 */
private fun DrawScope.upAndDown(x: Float) {
    arrow(Offset(x, 7.6f), Offset(x, 3f))
    arrow(Offset(x, 16.4f), Offset(x, 21f))
    finger(Offset(x, 12f))
}

private fun DrawScope.twoUpAndDown() {
    upAndDown(7.5f)
    upAndDown(16.5f)
}
