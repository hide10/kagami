package com.hide10.kagami.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.awaitCancellation

/**
 * 前面カメラをプレビューと analyzer につなぎ、キャンセルされるまで保つ。前面カメラが無いときだけ戻る。
 * ImageMapping のためにプレビューと解析を同じ 4:3 にそろえる。
 * 拡大はプレビューを切り出すだけなので、プレビューは 1920x1440 までのいちばん細かい 4:3 にし、端末が持っていればノイズ除去と輪郭強調を高品質にする。
 * CameraX が解析とのあいだで 1920x1440 を保証するのは手ブレ補正の組み合わせだけで、それ以外は 1440x1080 ほどに下がる。
 */
@OptIn(ExperimentalCamera2Interop::class)
suspend fun runFrontCamera(
    context: Context,
    owner: LifecycleOwner,
    view: PreviewView,
    analyzer: ImageAnalysis.Analyzer,
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
    val extender = Camera2Interop.Extender(previewBuilder)
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

    analysis.setAnalyzer(ContextCompat.getMainExecutor(context), analyzer)
    provider.bindToLifecycle(owner, selector, preview, analysis)
    Log.i(
        "FrontCamera",
        "preview ${preview.resolutionInfo?.resolution} analysis ${analysis.resolutionInfo?.resolution} " +
            "stabilization $stabilized noiseReduction ${if (highQualityNr) "HIGH_QUALITY" else "default"} " +
            "edge ${if (highQualityEdge) "HIGH_QUALITY" else "default"}",
    )
    try {
        awaitCancellation()
    } finally {
        provider.unbind(preview, analysis)
    }
}

/** カメラが持つモードの一覧(取れなければ null)に mode が入っているか。 */
internal fun offers(available: IntArray?, mode: Int) = available?.contains(mode) == true
