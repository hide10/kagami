package com.hide10.kagami

/**
 * 輝度データに対する Sobel エッジ検出。カメラ映像の Y 成分だけを受け取り、
 * エッジの強さをグレースケールの ARGB ピクセルとして返す純粋な処理。
 * （CameraX 1.6.2 では SurfaceProcessor を使う CameraEffect のコンストラクタが
 * ライブラリ内部専用でアプリから使えないため、エッジ表示はこの方式で実現する。）
 */
object EdgeProcessor {

    /**
     * @param luma 行優先の輝度（0..255）、サイズは width*height
     * @param threshold この値以下の勾配はエッジとみなさない（ノイズ除け）
     * @return ARGB ピクセル（黒地に白いエッジ、強さで明るさが変わる）
     */
    fun sobelEdges(luma: IntArray, width: Int, height: Int, threshold: Int = 40): IntArray {
        val out = IntArray(width * height) { 0xFF000000.toInt() }
        if (width < 3 || height < 3) return out
        for (y in 1 until height - 1) {
            val row = y * width
            val up = row - width
            val down = row + width
            for (x in 1 until width - 1) {
                val gx = -luma[up + x - 1] - 2 * luma[row + x - 1] - luma[down + x - 1] +
                    luma[up + x + 1] + 2 * luma[row + x + 1] + luma[down + x + 1]
                val gy = -luma[up + x - 1] - 2 * luma[up + x] - luma[up + x + 1] +
                    luma[down + x - 1] + 2 * luma[down + x] + luma[down + x + 1]
                val mag = kotlin.math.abs(gx) + kotlin.math.abs(gy)
                if (mag > threshold) {
                    val v = (mag - threshold).coerceAtMost(255)
                    out[row + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
                }
            }
        }
        return out
    }

    /** 輝度データを時計回りに回す。degrees は 0/90/180/270 のいずれか。 */
    fun rotate(luma: IntArray, width: Int, height: Int, degrees: Int): Triple<IntArray, Int, Int> {
        return when (((degrees % 360) + 360) % 360) {
            90 -> {
                val out = IntArray(width * height)
                for (y in 0 until height) for (x in 0 until width) {
                    out[x * height + (height - 1 - y)] = luma[y * width + x]
                }
                Triple(out, height, width)
            }
            180 -> {
                val out = IntArray(width * height)
                for (i in luma.indices) out[luma.size - 1 - i] = luma[i]
                Triple(out, width, height)
            }
            270 -> {
                val out = IntArray(width * height)
                for (y in 0 until height) for (x in 0 until width) {
                    out[(width - 1 - x) * height + y] = luma[y * width + x]
                }
                Triple(out, height, width)
            }
            else -> Triple(luma, width, height)
        }
    }

    /** 左右を反転する（前面カメラの鏡像に合わせる）。 */
    fun mirror(luma: IntArray, width: Int, height: Int): IntArray {
        val out = IntArray(width * height)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) out[row + (width - 1 - x)] = luma[row + x]
        }
        return out
    }
}
