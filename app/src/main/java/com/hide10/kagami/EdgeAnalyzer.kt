package com.hide10.kagami

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

/**
 * 前面カメラの映像からエッジ画像を作るアナライザ。
 * Y 成分だけを使い、間引いてから回転・左右反転・Sobel 検出を行う。
 * 映像そのものは保存も送信もしない（エッジ画像も画面表示のためだけに使う）。
 */
class EdgeAnalyzer(
    private val onBitmap: (Bitmap) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val step = 2
            val w = image.width / step
            val h = image.height / step
            if (w < 3 || h < 3) return
            val luma = IntArray(w * h)
            for (y in 0 until h) {
                val srcRow = y * step * rowStride
                val outRow = y * w
                for (x in 0 until w) {
                    luma[outRow + x] =
                        buffer.get(srcRow + x * step * pixelStride).toInt() and 0xFF
                }
            }
            val (rotated, rw, rh) =
                EdgeProcessor.rotate(luma, w, h, image.imageInfo.rotationDegrees)
            val mirrored = EdgeProcessor.mirror(rotated, rw, rh)
            val edges = EdgeProcessor.sobelEdges(mirrored, rw, rh)
            val bitmap = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(edges, 0, rw, 0, 0, rw, rh)
            onBitmap(bitmap)
        } finally {
            image.close()
        }
    }
}
