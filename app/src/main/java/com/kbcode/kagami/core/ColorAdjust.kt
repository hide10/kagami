package com.kbcode.kagami.core

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

    /** 露出のスライダーで今の露出がある位置。左端(EV の下限)が 0、真ん中(無加工)が 0.5、右端が 1。 */
    val exposureFraction get() = (exposureEv - EV.start) / (EV.endInclusive - EV.start)

    /** 1/3 段刻みに丸めるので、真ん中付近へ戻せば無加工に戻る。 */
    fun withExposure(ev: Float) = copy(exposureEv = (ev.coerceIn(EV) * STOPS_PER_EV).roundToInt() / STOPS_PER_EV)

    /** 動かし始めに fromEv だった露出を、指で横へ dx 動かしたあと。幅 width いっぱいで EV の端から端まで動き、右へ動かすと明るい。 */
    fun withExposureDragged(fromEv: Float, dx: Float, width: Float) = withExposure(fromEv + dx / width * (EV.endInclusive - EV.start))

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

        /** 露出を丸める刻み。1 段を 3 つに分ける。 */
        private const val STOPS_PER_EV = 3f

        /** 露出のスライダーの両端のあいだに置く目盛りの数。目盛りは withExposure が丸める刻みごとに並ぶ。 */
        val EV_TICKS = ((EV.endInclusive - EV.start) * STOPS_PER_EV).roundToInt() - 1
    }
}

/** 画面ライトの光らせ方。BAND は画面のふちを帯で光らせ、SHRINK はプレビューを角の丸いカードに縮めてその周りを光らせる。 */
enum class LightMode { BAND, SHRINK }

/**
 * 画面ライト。光の量はひとつのスライダー(thicknessDp)で決め、BAND では帯の太さ、SHRINK ではカードの小ささになる。色は暗くしない。
 * 光らせ方は点けても消しても変わらない。
 */
data class ScreenLight(
    val on: Boolean = false,
    val thicknessDp: Float = 64f,
    val kelvin: Float = 5000f,
    val mode: LightMode = LightMode.BAND,
) {
    /** 4dp 刻みに丸め、範囲の外へは出さない。 */
    fun withThickness(dp: Float) = copy(thicknessDp = (dp.coerceIn(THICKNESS_DP) / 4f).roundToInt() * 4f)

    /** 色温度の色を、いちばん強いチャンネルが 1 になるまで明るくしたもの。 */
    fun color(): Rgb {
        val c = Kelvin.rgb(kelvin)
        return c * (1f / c.max)
    }

    fun glows(m: LightMode) = on && mode == m

    /** プレビューを映す大きさ(画面に対する比)。SHRINK で光っているときだけ、光の量が最小で 0.95、最大で 0.6 になるよう比例して縮める。 */
    val previewScale: Float
        get() {
            if (!glows(LightMode.SHRINK)) return 1f
            val amount = (thicknessDp - THICKNESS_DP.start) / (THICKNESS_DP.endInclusive - THICKNESS_DP.start)
            return LARGEST_CARD + (SMALLEST_CARD - LARGEST_CARD) * amount
        }

    companion object {
        val THICKNESS_DP = 16f..120f
        val KELVIN = 2700f..8000f
        private const val LARGEST_CARD = 0.95f
        private const val SMALLEST_CARD = 0.6f
    }
}
