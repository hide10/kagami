package com.kbcode.kagami.core

import com.kbcode.kagami.core.Crop.Companion.MAX_ZOOM
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 画面の右端を上下になぞって寄り引きする帯。上へなぞると寄り、下へなぞると引く。
 * 倍率は対数で動くので、同じ距離なら同じ比だけ変わり、帯の長さいっぱいで 1 倍から MAX_ZOOM 倍まで動く。
 */
object ZoomStrip {
    /** 長さ length の帯の上を dy(下向きが正)なぞったあとの切り出し。見えている真ん中を保ち、bounds(既定は画像全体)の外は見せない。 */
    fun drag(crop: Crop, dy: Float, length: Float, size: Size, bounds: Crop = Crop.full(size)): Crop =
        CropPlanner.transform(crop, size.center, Pt(0f, 0f), MAX_ZOOM.pow(-dy / length), size, bounds)

    /** zoom 倍を示すつまみの位置。0 が下端(1 倍)、1 が上端(MAX_ZOOM 倍)で、なぞった指と同じだけ動く。 */
    fun thumb(zoom: Float): Float = (ln(zoom) / ln(MAX_ZOOM)).coerceIn(0f, 1f)

    /** つまみの横に出す倍率。0.1 倍刻みで、全体を映しているあいだは出さない。端末の言語によらず小数点で書く。 */
    fun label(zoom: Float): String? {
        val tenths = (zoom * 10).roundToInt()
        return if (tenths <= 10) null else "×${tenths / 10}.${tenths % 10}"
    }
}
