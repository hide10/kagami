package com.hide10.kagami.camera

import android.content.Context
import android.graphics.Rect
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.hide10.kagami.core.SensorRect
import com.hide10.kagami.core.SensorSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import java.util.concurrent.Executors

private const val TAG = "FrontCamera"

/** 頼む範囲を変えるのは、多くても 1 フレーム(30fps)に 1 度。 */
private const val REQUEST_INTERVAL_MS = 33L

/**
 * 前面カメラをプレビューと analyzer につなぎ、キャンセルされるまで保つ。前面カメラが無いときだけ戻る。
 * ImageMapping のためにプレビューと解析を同じ 4:3 にそろえる。
 * プレビューは 1920x1440 までのいちばん細かい 4:3 にし、端末が持っていればノイズ除去と輪郭強調を高品質にする。
 * CameraX が解析とのあいだで 1920x1440 を保証するのは手ブレ補正の組み合わせだけで、それ以外は 1440x1080 ほどに下がる。
 * 拡大は crop.requested の SCALER_CROP_REGION をカメラに頼んで行い、撮れた一枚ごとに実際の範囲を crop に記録する。
 * Camera2CameraControl の値は CameraX 自身の拡大より後に上書きで重なるので、CameraX の拡大(倍率 1 のまま)とぶつからない。
 */
@OptIn(ExperimentalCamera2Interop::class)
suspend fun runFrontCamera(
    context: Context,
    owner: LifecycleOwner,
    view: PreviewView,
    analyzer: ImageAnalysis.Analyzer,
    crop: CameraCrop,
) {
    val provider = ProcessCameraProvider.awaitInstance(context)
    val selector = CameraSelector.DEFAULT_FRONT_CAMERA
    if (!provider.hasCamera(selector)) return
    val info = provider.getCameraInfo(selector)

    val fourThree = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
    val stabilized = Preview.getPreviewCapabilities(info).isStabilizationSupported
    val characteristics = Camera2CameraInfo.from(info)
    val highQualityNr = offers(
        characteristics.getCameraCharacteristic(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES),
        CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY,
    )
    val highQualityEdge = offers(
        characteristics.getCameraCharacteristic(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES),
        CameraMetadata.EDGE_MODE_HIGH_QUALITY,
    )
    val previewBuilder = Preview.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(fourThree)
                .setResolutionStrategy(
                    ResolutionStrategy(android.util.Size(1920, 1440), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                )
                .build(),
        )
        .setPreviewStabilizationEnabled(stabilized)
    val extender = Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(AppliedCropRecorder(crop))
    if (highQualityNr) {
        extender.setCaptureRequestOption(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
    }
    if (highQualityEdge) {
        extender.setCaptureRequestOption(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY)
    }
    val preview = previewBuilder.build()
    preview.setSurfaceProvider(view.surfaceProvider)
    val analysis = ImageAnalysis.Builder()
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setAspectRatioStrategy(fourThree)
                .setResolutionStrategy(
                    ResolutionStrategy(android.util.Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                )
                .build(),
        )
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    // 解析は撮影結果を少し待つことがあるので、メインスレッドでは回さない。
    val analysisThread = Executors.newSingleThreadExecutor()
    analysis.setAnalyzer(analysisThread, analyzer)
    val camera = provider.bindToLifecycle(owner, selector, preview, analysis)
    val stream = preview.resolutionInfo?.resolution
    Log.i(
        TAG,
        "preview $stream analysis ${analysis.resolutionInfo?.resolution} " +
            "stabilization $stabilized noiseReduction ${if (highQualityNr) "HIGH_QUALITY" else "default"} " +
            "edge ${if (highQualityEdge) "HIGH_QUALITY" else "default"}",
    )
    val active = characteristics.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
    val maxDigitalZoom = characteristics.getCameraCharacteristic(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM)
    val cropping = characteristics.getCameraCharacteristic(CameraCharacteristics.SCALER_CROPPING_TYPE)
    val spec = SensorSpec.of(
        activeWidth = active?.width(),
        activeHeight = active?.height(),
        maxDigitalZoom = maxDigitalZoom,
        freeform = cropping == CameraMetadata.SCALER_CROPPING_TYPE_FREEFORM,
        streamWidth = stream?.width,
        streamHeight = stream?.height,
        rotationDegrees = info.getSensorRotationDegrees(preview.targetRotation),
        mirror = true,
    )
    Log.i(
        TAG,
        "crop activeArray ${active?.width()}x${active?.height()} maxDigitalZoom $maxDigitalZoom " +
            "zoomStateMax ${info.zoomState.value?.maxZoomRatio} cropping ${if (cropping == CameraMetadata.SCALER_CROPPING_TYPE_FREEFORM) "FREEFORM" else "CENTER_ONLY"} " +
            "rotation ${info.getSensorRotationDegrees(preview.targetRotation)} " +
            (spec?.let { "base ${it.baseWidth}x${it.baseHeight} cameraZoomLimit ${it.zoomLimit}" } ?: "unsupported: the app magnifies everything"),
    )
    crop.spec = spec
    val control = Camera2CameraControl.from(camera.cameraControl)
    try {
        // StateFlow は同じ値を流さず、待っているあいだに来た値は最後の一つだけ残る。動いている最中は 1 フレームに 1 度、止まれば最後の範囲が必ず届く。
        crop.requested.filterNotNull().collect { rect ->
            control.setCaptureRequestOptions(
                CaptureRequestOptions.Builder()
                    .setCaptureRequestOption(CaptureRequest.SCALER_CROP_REGION, Rect(rect.left, rect.top, rect.right, rect.bottom))
                    .build(),
            )
            delay(REQUEST_INTERVAL_MS)
        }
    } finally {
        crop.spec = null
        provider.unbind(preview, analysis)
        analysisThread.shutdown()
    }
}

/** 撮れた一枚ごとに、カメラが実際に切り出した範囲を記録する。頼んだ範囲と実際の範囲は、変わったときだけ 0.25 秒に一度までログに出す。 */
private class AppliedCropRecorder(private val crop: CameraCrop) : CameraCaptureSession.CaptureCallback() {
    private var loggedAt = 0L
    private var loggedRequested: SensorRect? = null
    private var loggedApplied: SensorRect? = null

    override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
        val r = result.get(CaptureResult.SCALER_CROP_REGION) ?: return
        val timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP) ?: return
        if (r.width() <= 0 || r.height() <= 0) return
        val applied = crop.record(timestamp, r.left, r.top, r.right, r.bottom)
        val requested = crop.requested.value
        val now = SystemClock.uptimeMillis()
        if ((applied != loggedApplied || requested != loggedRequested) && now - loggedAt >= LOG_INTERVAL_MS) {
            val spec = crop.spec
            Log.i(
                TAG,
                "crop requested ${requested?.text(spec) ?: "none"} applied ${applied.text(spec)} " +
                    "stabilization ${result.get(CaptureResult.CONTROL_VIDEO_STABILIZATION_MODE)} zoomRatio ${zoomRatio(result)}",
            )
            loggedAt = now
            loggedRequested = requested
            loggedApplied = applied
        }
    }

    private fun SensorRect.text(spec: SensorSpec?) =
        "[$left,$top,$right,$bottom]" + (spec?.let { " x%.2f".format(it.zoomOf(this)) } ?: "")

    private fun zoomRatio(result: TotalCaptureResult) =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) result.get(CaptureResult.CONTROL_ZOOM_RATIO) else null

    private companion object {
        const val LOG_INTERVAL_MS = 250L
    }
}

/** カメラが持つモードの一覧(取れなければ null)に mode が入っているか。 */
internal fun offers(available: IntArray?, mode: Int) = available?.contains(mode) == true
