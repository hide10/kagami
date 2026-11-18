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

/** いちばん大きな顔を、正立画像(非ミラー)の座標で onFace に渡す。onFace はメインスレッドで呼ばれる。 */
class FaceAnalyzer(private val onFace: (face: FaceLandmarks, image: Size) -> Unit) : ImageAnalysis.Analyzer, AutoCloseable {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image ?: return proxy.close()
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
