package com.hide10.kagami.core

import kotlin.math.exp
import kotlin.math.ln

/**
 * 時間ベースの指数平滑。zoom は log 空間、中心は線形で寄せる。
 * 両端が clamp 済みなら途中も表示領域が画面内に収まる(1/zoom の幾何補間は線形補間以下なので)。
 */
object CropSmoother {
    const val TAU_SEC = 0.25f

    /** フレームが落ちてもこれ以上は一度に進めない。 */
    const val MAX_DT_SEC = 0.1f

    fun step(current: Crop, target: Crop, dtSec: Float): Crop {
        val dt = dtSec.coerceIn(0f, MAX_DT_SEC)
        val a = 1f - exp(-dt / TAU_SEC)
        return Crop(
            cx = lerp(current.cx, target.cx, a),
            cy = lerp(current.cy, target.cy, a),
            zoom = exp(lerp(ln(current.zoom), ln(target.zoom), a)),
        )
    }

    private fun lerp(from: Float, to: Float, a: Float) = from + (to - from) * a
}
