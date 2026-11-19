package com.kbcode.kagami.ui

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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import com.kbcode.kagami.core.FacePart

// アイコンの絵。24×24 の格子に描いてキャンバスの大きさまで広げ、選んでいる所や点いているものは ACCENT で描く。

internal val INK = Color.White
internal val ACCENT = Color(0xFFFFD54F)
private val BLUSH = Color(0xCCFF8FA3)
private val PUPIL = Color(0xFF2B2B2B)

/** 寄らない方の目の濃さ。 */
private const val FADED = 0.4f

internal inline fun DrawScope.grid(block: DrawScope.() -> Unit) = scale(size.minDimension / 24f, Offset.Zero, block)

private fun svgPath(d: String) = PathParser().parsePathString(d).toPath()

// 画面の左に映る目。右の目はこれを x = 12 で左右に返して描く。
private val EYE = svgPath("M1.4 11.2 C3.3 6 8.9 6 10.6 11.2 C8.9 15.6 3.3 15.6 1.4 11.2 Z")
private val LASHES = svgPath("M2.5 9.1 L0.9 7.6 M4.1 7.7 L3.2 5.8")
private val SMILE = svgPath("M9.6 15.6 Q12 18 14.4 15.6")
private val SPARKLE = svgPath("M20.8 1.2 Q21.47 2.93 23.2 3.6 Q21.47 4.27 20.8 6 Q20.13 4.27 18.4 3.6 Q20.13 2.93 20.8 1.2 Z")

// 正面から見た鼻。鼻筋の 2 本が下で小鼻へ広がり、鼻の穴の上で折り返して真ん中の鼻先へ下りる。
private val NOSE = svgPath(
    "M10.2 3.2 C10.2 7.4 9.6 9.8 7.6 12.4 C5.4 15.2 4.6 17 5.8 18.6 C6.9 19.9 8.9 19.5 9.7 18.4 " +
        "C10.3 19.6 11 20.2 12 20.2 C13 20.2 13.7 19.6 14.3 18.4 C15.1 19.5 17.1 19.9 18.2 18.6 " +
        "C19.4 17 18.6 15.2 16.4 12.4 C14.4 9.8 13.8 7.4 13.8 3.2",
)

// 上唇は真ん中がくぼむ山形、下唇は丸く膨らむ。2 枚のすき間が口の合わせ目になる。
private val UPPER_LIP = svgPath(
    "M2.6 11.4 C5 8.2 7.4 6.2 9.4 6.4 C10.6 6.5 11.2 7.4 12 7.4 C12.8 7.4 13.4 6.5 14.6 6.4 " +
        "C16.6 6.2 19 8.2 21.4 11.4 C17.6 11.2 15.2 10.4 12 11 C8.8 10.4 6.4 11.2 2.6 11.4 Z",
)
private val LOWER_LIP = svgPath(
    "M3 12.6 C6.6 12.6 8.8 12.2 12 12.8 C15.2 12.2 17.4 12.6 21 12.6 C18.8 16.6 15.8 18.4 12 18.4 C8.2 18.4 5.2 16.6 3 12.6 Z",
)
private val GLOSS = svgPath("M14.6 15.6 Q16 15.2 16.8 14.2")

/** 部位のボタンの絵。寄る所を ACCENT で描く。null は顔全体。左目は画面の左に映る目で、左右は入れ替えない。 */
internal fun DrawScope.partGlyph(part: FacePart?) = grid {
    when (part) {
        null -> wholeFace()
        FacePart.LEFT_EYE, FacePart.RIGHT_EYE -> {
            eye(target = part == FacePart.LEFT_EYE)
            scale(-1f, 1f, Offset(12f, 0f)) { eye(target = part == FacePart.RIGHT_EYE) }
        }
        FacePart.NOSE -> drawPath(NOSE, ACCENT, style = Stroke(1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        FacePart.MOUTH -> {
            for (lip in listOf(UPPER_LIP, LOWER_LIP)) {
                drawPath(lip, ACCENT)
                drawPath(lip, ACCENT, style = Stroke(0.8f, join = StrokeJoin.Round))
            }
            drawPath(GLOSS, INK, 0.9f, Stroke(1f, cap = StrokeCap.Round))
        }
    }
}

/** 丸い笑顔に、ほおの赤みと小さなきらめき。 */
private fun DrawScope.wholeFace() {
    drawCircle(ACCENT, 8.5f, Offset(12f, 12.6f), style = Stroke(1.8f))
    for (x in listOf(9f, 15f)) drawOval(ACCENT, Offset(x - 1.25f, 10f), Size(2.5f, 3.2f))
    for (x in listOf(7f, 17f)) drawOval(BLUSH, Offset(x - 1.6f, 14.05f), Size(3.2f, 1.9f))
    drawPath(SMILE, ACCENT, style = Stroke(1.6f, cap = StrokeCap.Round))
    drawPath(SPARKLE, ACCENT)
}

/** まつげと瞳のある目と、その下のほおの赤み。寄る目は ACCENT で、もう片方は薄く描く。 */
private fun DrawScope.eye(target: Boolean) {
    val color = if (target) ACCENT else INK
    val alpha = if (target) 1f else FADED
    drawPath(EYE, color, alpha, Stroke(1.6f, join = StrokeJoin.Round))
    drawPath(LASHES, color, alpha, Stroke(1.4f, cap = StrokeCap.Round))
    drawCircle(color, 2.9f, Offset(6f, 10.9f), alpha)
    drawCircle(PUPIL, 0.95f, Offset(6.9f, 10f), alpha)
    drawOval(BLUSH, Offset(3.7f, 16.25f), Size(4.6f, 2.3f), alpha)
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

/** 一時停止の 2 本の棒。端は丸い。 */
internal fun DrawScope.pauseGlyph(active: Boolean) = grid {
    for (x in listOf(6.5f, 13.5f)) drawRoundRect(if (active) ACCENT else INK, Offset(x, 5f), Size(4f, 14f), CornerRadius(2f))
}

/** 丸の中の i。 */
internal fun DrawScope.infoGlyph(active: Boolean) = grid {
    val c = if (active) ACCENT else INK
    drawCircle(c, 9f, Offset(12f, 12f), style = Stroke(1.8f))
    drawCircle(c, 1.3f, Offset(12f, 7.7f))
    drawLine(c, Offset(12f, 11f), Offset(12f, 16.8f), 2f, StrokeCap.Round)
}

/** 丸と 8 本の光の太陽。露出の帯の端に添える。 */
internal fun DrawScope.sunGlyph() = grid {
    val c = Offset(12f, 12f)
    drawCircle(INK, 4.4f, c, style = Stroke(1.8f))
    for (i in 0 until 8) rotate(i * 45f, c) { drawLine(INK, Offset(12f, 2.6f), Offset(12f, 4.8f), 1.8f, StrokeCap.Round) }
}
