package com.hide10.kagami.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** センサー上の長方形。単位はアクティブ領域の画素で、(0, 0) がその左上。回転もミラーもしていない向き。 */
data class SensorRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * カメラ側で切り出すための前提。activeWidth/Height は SENSOR_INFO_ACTIVE_ARRAY_SIZE、maxDigitalZoom は SCALER_AVAILABLE_MAX_DIGITAL_ZOOM。
 * freeform は SCALER_CROPPING_TYPE が FREEFORM か(違えば中央の切り出ししかできない)。streamWidth/Height はプレビューのストリームの大きさ(センサーの向き)。
 * rotationDegrees はセンサーの像を画面で正立させる時計回りの角度、mirror は画面で左右反転して見せるか。
 */
data class SensorSpec(
    val activeWidth: Int,
    val activeHeight: Int,
    val maxDigitalZoom: Float,
    val freeform: Boolean,
    val streamWidth: Int,
    val streamHeight: Int,
    val rotationDegrees: Int,
    val mirror: Boolean,
) {
    init {
        require(activeWidth > 0 && activeHeight > 0 && streamWidth > 0 && streamHeight > 0 && rotationDegrees in ROTATIONS) { "$this" }
    }

    // 比は整数のまま掛けて比べ、割るのは最後の一度だけにする。4:3 のセンサーなら base はちょうどセンサー全体になる。
    private val widthLimited = activeWidth.toLong() * streamHeight <= activeHeight.toLong() * streamWidth

    /** 倍率 1 のときストリームに映る範囲。アクティブ領域に収まる、ストリームと同じ比のいちばん大きな中央の長方形。 */
    val baseWidth = if (widthLimited) activeWidth.toFloat() else (activeHeight.toLong() * streamWidth).toFloat() / streamHeight
    val baseHeight = if (widthLimited) (activeWidth.toLong() * streamHeight).toFloat() / streamWidth else activeHeight.toFloat()
    val baseLeft = (activeWidth - baseWidth) / 2
    val baseTop = (activeHeight - baseHeight) / 2

    /** カメラが寄れる上限(base に対する倍率)。切り出しはアクティブ領域の 1/maxDigitalZoom より小さくできない。 */
    val zoomLimit = max(1f, maxDigitalZoom * min(baseWidth / activeWidth, baseHeight / activeHeight))

    /** カメラがこの範囲を切り出したときの倍率。ストリームは範囲の中央からストリームと同じ比で切るので、その幅で決まる。 */
    fun zoomOf(rect: SensorRect): Float = baseWidth / min(rect.width.toFloat(), (rect.height.toLong() * streamWidth).toFloat() / streamHeight)

    /** この範囲を切り出した映像が、丸め誤差を除いて全体を映しているか。 */
    fun showsAll(rect: SensorRect) = zoomOf(rect) <= 1f + FULL_TOLERANCE

    companion object {
        val ROTATIONS = setOf(0, 90, 180, 270)
        const val FULL_TOLERANCE = 0.02f

        /** カメラ側で切り出せないとき(アクティブ領域や拡大の上限やストリームの大きさが取れない、1 倍より寄れない、向きが 90 度刻みでない)は null。 */
        fun of(
            activeWidth: Int?,
            activeHeight: Int?,
            maxDigitalZoom: Float?,
            freeform: Boolean,
            streamWidth: Int?,
            streamHeight: Int?,
            rotationDegrees: Int,
            mirror: Boolean,
        ): SensorSpec? {
            if (activeWidth == null || activeHeight == null || maxDigitalZoom == null || streamWidth == null || streamHeight == null) return null
            if (activeWidth <= 0 || activeHeight <= 0 || maxDigitalZoom <= 1f || streamWidth <= 0 || streamHeight <= 0) return null
            val rotation = (rotationDegrees % 360 + 360) % 360
            if (rotation !in ROTATIONS) return null
            return SensorSpec(activeWidth, activeHeight, maxDigitalZoom, freeform, streamWidth, streamHeight, rotation, mirror)
        }
    }
}

/**
 * ビュー空間の Crop と、カメラに頼む SCALER_CROP_REGION(センサー座標)を行き来する。
 * ビューは PreviewView の FILL_CENTER で、センサーの base を rotationDegrees だけ回し、mirror なら左右反転して映す。
 * カメラが切り出した映像は、ビュー座標で見ると Crop と同じ形(中心と倍率)の拡大になるので、カメラ側の切り出しも Crop で表す。
 */
class SensorMapping(private val spec: SensorSpec, private val view: Size) {
    private val turned = spec.rotationDegrees % 180 != 0
    private val uprightW = if (turned) spec.baseHeight else spec.baseWidth
    private val uprightH = if (turned) spec.baseWidth else spec.baseHeight
    private val scale = max(view.w / uprightW, view.h / uprightH)
    private val offset = Pt((view.w - uprightW * scale) / 2, (view.h - uprightH * scale) / 2)

    /**
     * shown を見せるとき、カメラに切り出させる範囲(ビュー座標)。カメラの上限まではカメラが寄り、残りはアプリが拡大する。
     * 中央しか切り出せないカメラでは、shown の表示領域を含む中央の範囲のうちいちばん寄ったもの。どちらも shown の表示領域を必ず含む。
     */
    fun cameraPart(shown: Crop): Crop {
        val zoom = min(shown.zoom, spec.zoomLimit)
        if (spec.freeform) return Crop(shown.cx, shown.cy, zoom).clamp(view)
        val mid = view.center
        val fitX = view.w / 2 / (abs(shown.cx - mid.x) + view.w / (2 * shown.zoom))
        val fitY = view.h / 2 / (abs(shown.cy - mid.y) + view.h / (2 * shown.zoom))
        return Crop(mid.x, mid.y, max(1f, minOf(zoom, fitX, fitY)))
    }

    /** カメラに切り出させる範囲を SCALER_CROP_REGION にする。ストリームと同じ比なので、カメラは余計に切らない。 */
    fun toSensor(camera: Crop): SensorRect {
        val c = sensorOf(camera.center)
        val halfW = spec.baseWidth / (2 * camera.zoom)
        val halfH = spec.baseHeight / (2 * camera.zoom)
        return SensorRect(
            (c.x - halfW).roundToInt().coerceIn(0, spec.activeWidth),
            (c.y - halfH).roundToInt().coerceIn(0, spec.activeHeight),
            (c.x + halfW).roundToInt().coerceIn(0, spec.activeWidth),
            (c.y + halfH).roundToInt().coerceIn(0, spec.activeHeight),
        )
    }

    /** カメラが実際に切り出した範囲(撮影結果の SCALER_CROP_REGION)を、ビュー座標の Crop にする。 */
    fun toView(applied: SensorRect): Crop {
        val c = viewOf(Pt((applied.left + applied.right) / 2f, (applied.top + applied.bottom) / 2f))
        return Crop(c.x, c.y, spec.zoomOf(applied))
    }

    /** ビューの点 p に映っているセンサー上の点を、base の中の比(センサーの向きで 0..1)で表す。ピントと露出を測る点に使う。 */
    fun baseFraction(p: Pt): Pt {
        val s = sensorOf(p)
        return Pt((s.x - spec.baseLeft) / spec.baseWidth, (s.y - spec.baseTop) / spec.baseHeight)
    }

    private fun sensorOf(p: Pt): Pt {
        val shownX = (p.x - offset.x) / scale
        val uy = (p.y - offset.y) / scale
        val ux = if (spec.mirror) uprightW - shownX else shownX
        val w = spec.baseWidth
        val h = spec.baseHeight
        val b = when (spec.rotationDegrees) {
            0 -> Pt(ux, uy)
            90 -> Pt(uy, h - ux)
            180 -> Pt(w - ux, h - uy)
            else -> Pt(w - uy, ux)
        }
        return Pt(spec.baseLeft + b.x, spec.baseTop + b.y)
    }

    private fun viewOf(s: Pt): Pt {
        val bx = s.x - spec.baseLeft
        val by = s.y - spec.baseTop
        val w = spec.baseWidth
        val h = spec.baseHeight
        val u = when (spec.rotationDegrees) {
            0 -> Pt(bx, by)
            90 -> Pt(h - by, bx)
            180 -> Pt(w - bx, h - by)
            else -> Pt(by, w - bx)
        }
        val shownX = if (spec.mirror) uprightW - u.x else u.x
        return Pt(shownX * scale + offset.x, u.y * scale + offset.y)
    }
}
