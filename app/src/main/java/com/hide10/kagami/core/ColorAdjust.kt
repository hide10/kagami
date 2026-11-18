package com.hide10.kagami.core

import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

data class Rgb(val r: Float, val g: Float, val b: Float) {
    operator fun times(k: Float) = Rgb(r * k, g * k, b * k)
    operator fun div(o: Rgb) = Rgb(r / o.r, g / o.g, b / o.b)

    val max get() = maxOf(r, g, b)
}

object Kelvin {
    const val NEUTRAL = 6500f

    /** 色温度の光の色(各 0..1)。Tanner Helland の黒体近似。 */
    fun rgb(kelvin: Float): Rgb {
        val t = kelvin.coerceIn(1000f, 40000f) / 100f
        val r = if (t <= 66f) 255f else 329.69873f * (t - 60f).pow(-0.13320476f)
        val g = if (t <= 66f) 99.4708f * ln(t) - 161.11957f else 288.12217f * (t - 60f).pow(-0.07551485f)
        val b = when {
            t >= 66f -> 255f
            t <= 19f -> 0f
            else -> 138.51773f * ln(t - 10f) - 305.0448f
        }
        return Rgb(channel(r), channel(g), channel(b))
    }

    /** 6500K を (1,1,1) とした RGB ゲイン。低い色温度ほど赤く、高いほど青い。 */
    fun gain(kelvin: Float): Rgb = rgb(kelvin) / rgb(NEUTRAL)

    private fun channel(v: Float) = (v / 255f).coerceIn(0f, 1f)
}

/** プレビューの色調。既定値は無加工。 */
data class ColorAdjust(val exposureEv: Float = 0f, val kelvin: Float = Kelvin.NEUTRAL) {
    val isNeutral get() = exposureEv == 0f && kelvin == Kelvin.NEUTRAL

    /** 1/3 段刻みに丸めるので、真ん中付近へ戻せば無加工に戻る。 */
    fun withExposure(ev: Float) = copy(exposureEv = (ev.coerceIn(EV) * 3f).roundToInt() / 3f)

    /** 100K 刻みに丸めるので、6500K 付近へ戻せば無加工に戻る。 */
    fun withKelvin(k: Float) = copy(kelvin = (k.coerceIn(KELVIN) / 100f).roundToInt() * 100f)

    /** android.graphics.ColorMatrix と同じ 4x5 行優先の配列。 */
    fun matrix(): FloatArray {
        val g = Kelvin.gain(kelvin) * 2f.pow(exposureEv)
        return floatArrayOf(
            g.r, 0f, 0f, 0f, 0f,
            0f, g.g, 0f, 0f, 0f,
            0f, 0f, g.b, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    companion object {
        val EV = -2f..2f
        val KELVIN = 3000f..10000f
    }
}

/** 画面ライト。画面を光らせて顔を照らす。 */
data class ScreenLight(val on: Boolean = false, val brightness: Float = 1f, val kelvin: Float = 5000f) {
    /** いちばん強いチャンネルが brightness になる色。 */
    fun color(): Rgb {
        val c = Kelvin.rgb(kelvin)
        return c * (brightness / c.max)
    }

    companion object {
        val BRIGHTNESS = 0.1f..1f
        val KELVIN = 2700f..8000f
    }
}
