package com.hide10.kagami.camera

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.hide10.kagami.core.FaceLandmarks
import com.hide10.kagami.core.Pt
import com.hide10.kagami.core.Size
import com.hide10.kagami.core.uprightSize
import java.util.concurrent.atomic.AtomicReference

/** 頼まれたときだけ顔を探す。頼まれていないフレームは ML Kit に渡さずに閉じる。 */
class FaceAnalyzer : ImageAnalysis.Analyzer, AutoCloseable {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build(),
    )
    private val request = AtomicReference<((FaceLandmarks, Size) -> Unit)?>()

    /**
     * 次に届くフレームでいちばん大きな顔を一度だけ探し、正立画像(非ミラー)の座標で onFace に渡す。顔が無ければ何も呼ばない。
     * フレームが届く前に呼び直すと、前の onFace は呼ばれない。onFace はメインスレッドで呼ばれる。
     */
    fun requestFace(onFace: (face: FaceLandmarks, image: Size) -> Unit) = request.set(onFace)

    /** まだフレームに渡していない requestFace を取り消す。探し始めた顔は止められず、見つかれば onFace が呼ばれる。 */
    fun cancel() = request.set(null)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image ?: return proxy.close()
        val onFace = request.getAndSet(null) ?: return proxy.close()
        val rotation = proxy.imageInfo.rotationDegrees
        val image = uprightSize(proxy.width, proxy.height, rotation)
        detector.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { faces ->
                faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }?.landmarks()?.let { onFace(it, image) }
            }
            .addOnCompleteListener { proxy.close() }
    }

    override fun close() = detector.close()
}

private fun Face.landmarks(): FaceLandmarks? {
    fun at(type: Int) = getLandmark(type)?.position?.let { Pt(it.x, it.y) }
    val leftEye = at(FaceLandmark.LEFT_EYE) ?: return null
    val rightEye = at(FaceLandmark.RIGHT_EYE) ?: return null
    val nose = at(FaceLandmark.NOSE_BASE) ?: return null
    val mouthLeft = at(FaceLandmark.MOUTH_LEFT) ?: return null
    val mouthRight = at(FaceLandmark.MOUTH_RIGHT) ?: return null
    return FaceLandmarks(leftEye, rightEye, nose, mouthLeft.midTo(mouthRight))
}
