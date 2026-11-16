package com.hide10.kagami

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeProcessorTest {

    private fun gray(pixel: Int): Int = pixel and 0xFF

    @Test
    fun sobel_uniformImageHasNoEdges() {
        val luma = IntArray(25) { 128 }
        val out = EdgeProcessor.sobelEdges(luma, 5, 5, threshold = 40)
        assertTrue(out.all { gray(it) == 0 })
    }

    @Test
    fun sobel_verticalStepProducesEdge() {
        // 左半分が黒、右半分が白の 5x5。境界（x=2 付近）にエッジが出る。
        val luma = IntArray(25) { i -> if (i % 5 < 2) 0 else 255 }
        val out = EdgeProcessor.sobelEdges(luma, 5, 5, threshold = 40)
        assertTrue(gray(out[2 * 5 + 2]) > 0)
        assertEquals(0, gray(out[2 * 5 + 4])) // 端の列は処理対象外で黒のまま
    }

    @Test
    fun rotate90_swapsDimensionsAndMovesMarker() {
        // 2x3（幅2, 高さ3）、左上 (0,0) に目印。
        val luma = intArrayOf(255, 0, 0, 0, 0, 0)
        val (out, w, h) = EdgeProcessor.rotate(luma, 2, 3, 90)
        assertEquals(3, w)
        assertEquals(2, h)
        // 時計回り90度で左上は右上 (x=2,y=0) へ。
        assertEquals(255, out[0 * 3 + 2])
    }

    @Test
    fun mirror_flipsHorizontally() {
        val luma = intArrayOf(255, 0, 0, 0)
        val out = EdgeProcessor.mirror(luma, 2, 2)
        assertEquals(255, out[1])
        assertEquals(0, out[0])
    }
}
