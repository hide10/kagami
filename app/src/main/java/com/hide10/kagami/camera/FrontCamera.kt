package com.hide10.kagami.camera

import android.content.Context
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
 */
suspend fun runFrontCamera(
    context: Context,
    owner: LifecycleOwner,
    view: PreviewView,
    analyzer: ImageAnalysis.Analyzer,
) {
    val provider = ProcessCameraProvider.awaitInstance(context)
    val selector = CameraSelector.DEFAULT_FRONT_CAMERA
    if (!provider.hasCamera(selector)) return

    val fourThree = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
    val preview = Preview.Builder()
        .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(fourThree).build())
        .setPreviewStabilizationEnabled(Preview.getPreviewCapabilities(provider.getCameraInfo(selector)).isStabilizationSupported)
        .build()
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
    try {
        awaitCancellation()
    } finally {
        provider.unbind(preview, analysis)
    }
}
