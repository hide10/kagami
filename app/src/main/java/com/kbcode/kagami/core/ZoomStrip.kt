package com.kbcode.kagami.core

import com.kbcode.kagami.core.Crop.Companion.MAX_ZOOM
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 画面の右端を上下になぞって寄り引きする帯。上へなぞると寄り、下へなぞると引く。
 * 倍率は対数で動くので、同じ距離なら同じ比だけ変わり、帯の長さいっぱいで 1 倍から MAX_ZOOM 倍まで動く。
 */
object ZoomStrip {
    /** 長さ length の帯の上を dy(下向きが正)なぞったあとの切り出し。見えている真ん中を保ち、bounds(既定は画像全体)の外は見せない。 */
    fun drag(crop: Crop, dy: Float, length: Float, size: Size, bounds: Crop = Crop.full(size)): Crop =
        // 帯の長さが 0 以下だと dy / length が NaN や無限大になり、coerceIn をすり抜けて切り出しが壊れる。動かさない。
        if (length <= 0f) crop else CropPlanner.transform(crop, size.center, Pt(0f, 0f), MAX_ZOOM.pow(-dy / length), size, bounds)

    /** zoom 倍を示すつまみの位置。0 が下端(1 倍)、1 が上端(MAX_ZOOM 倍)で、なぞった指と同じだけ動く。 */
    fun thumb(zoom: Float): Float = (ln(zoom) / ln(MAX_ZOOM)).coerceIn(0f, 1f)

    /**
     * 高さ height の帯のうち、戻るジェスチャーから外す高さ span の範囲の上端。つまみの中心 thumbY を真ん中にとり、
     * 帯からはみ出すなら内側へずらす。帯が span より短ければ帯全体になる。
     */
    fun guardTop(thumbY: Float, span: Float, height: Float): Float = (thumbY - span / 2).coerceIn(0f, max(0f, height - span))

    /** つまみの横に出す倍率。0.1 倍刻みで、全体を映しているあいだは出さない。端末の言語によらず小数点で書く。 */
    fun label(zoom: Float): String? {
        val tenths = (zoom * 10).roundToInt()
        return if (tenths <= 10) null else "×${tenths / 10}.${tenths % 10}"
    }
}
