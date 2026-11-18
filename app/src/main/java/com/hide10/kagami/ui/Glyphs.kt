package com.hide10.kagami.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import com.hide10.kagami.core.FacePart

// アイコンの絵。24×24 の格子に描いてキャンバスの大きさまで広げ、選んでいる所や点いているものは ACCENT で描く。

internal val INK = Color.White
private val FAINT = Color.White.copy(alpha = 0.55f)
internal val ACCENT = Color(0xFFFFD54F)

internal inline fun DrawScope.grid(block: DrawScope.() -> Unit) = scale(size.minDimension / 24f, Offset.Zero, block)

/** 顔の輪郭と目・鼻・口。part を ACCENT で大きく描き、null なら輪郭を ACCENT にして全体を表す。左目は画面の左に映る目で、左右は入れ替えない。 */
internal fun DrawScope.faceGlyph(part: FacePart?) = grid {
    fun ink(p: FacePart) = when (part) {
        null -> INK
        p -> ACCENT
        else -> FAINT
    }
    fun width(p: FacePart) = if (part == p) 2.4f else 1.5f
    drawOval(if (part == null) ACCENT else INK, Offset(4f, 2.5f), Size(16f, 19f), style = Stroke(if (part == null) 2.2f else 1.5f))
    for ((eye, x) in listOf(FacePart.LEFT_EYE to 9f, FacePart.RIGHT_EYE to 15f)) {
        drawCircle(ink(eye), if (part == eye) 2.3f else 1.4f, Offset(x, 10f))
    }
    drawLine(ink(FacePart.NOSE), Offset(12f, 11.5f), Offset(12f, 15f), width(FacePart.NOSE), StrokeCap.Round)
    val mouth = Path().apply {
        moveTo(9f, 17.5f)
        quadraticTo(12f, 20f, 15f, 17.5f)
    }
    drawPath(mouth, ink(FacePart.MOUTH), style = Stroke(width(FacePart.MOUTH), cap = StrokeCap.Round))
}

/** 電球。lit なら中を塗る。 */
internal fun DrawScope.bulbGlyph(lit: Boolean) = grid {
    val bulb = Path().apply {
        arcTo(Rect(Offset(12f, 9.5f), 6f), 125f, 290f, forceMoveTo = true)
        lineTo(14.5f, 17.2f)
        lineTo(9.5f, 17.2f)
        close()
    }
    if (lit) drawPath(bulb, ACCENT)
    drawPath(bulb, INK, style = Stroke(1.8f, join = StrokeJoin.Round))
    drawLine(INK, Offset(10f, 20.2f), Offset(14f, 20.2f), 1.8f, StrokeCap.Round)
}

/** 一時停止の 2 本の棒。 */
internal fun DrawScope.pauseGlyph(active: Boolean) = grid {
    for (x in listOf(6.5f, 13.5f)) drawRoundRect(if (active) ACCENT else INK, Offset(x, 5f), Size(4f, 14f), CornerRadius(1f))
}

/** 丸の中の i。 */
internal fun DrawScope.infoGlyph(active: Boolean) = grid {
    val c = if (active) ACCENT else INK
    drawCircle(c, 9f, Offset(12f, 12f), style = Stroke(1.8f))
    drawCircle(c, 1.3f, Offset(12f, 7.7f))
    drawLine(c, Offset(12f, 11f), Offset(12f, 16.8f), 2f, StrokeCap.Round)
}

/** つまみの付いた 3 本のスライダー。 */
internal fun DrawScope.slidersGlyph(active: Boolean) = grid {
    val c = if (active) ACCENT else INK
    for ((y, knob) in listOf(6f to 15f, 12f to 8f, 18f to 13f)) {
        drawLine(c, Offset(3.5f, y), Offset(20.5f, y), 1.6f, StrokeCap.Round)
        drawCircle(c, 2.4f, Offset(knob, y))
    }
}
