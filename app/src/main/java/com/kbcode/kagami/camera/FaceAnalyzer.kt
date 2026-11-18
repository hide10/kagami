package com.kbcode.kagami.camera

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import com.kbcode.kagami.core.FaceLandmarks
import com.kbcode.kagami.core.Pt
import com.kbcode.kagami.core.SensorRect
import com.kbcode.kagami.core.Size
import com.kbcode.kagami.core.uprightSize
import java.util.concurrent.atomic.AtomicReference

/** 顔を探した一枚。face と image は正立画像(非ミラー)の座標、crop はその一枚にカメラが切り出していた範囲(分からなければ null)。 */
class FaceFrame(val face: FaceLandmarks, val image: Size, val crop: SensorRect?)

/**
 * 頼まれたときだけ顔を探す。頼まれていないフレームは ML Kit に渡さずに閉じる。
 * カメラが寄っている一枚では顔が収まっていないことがあるので、カメラが全体を切り出した一枚を待ってから探す。
 */
class FaceAnalyzer(private val crop: CameraCrop) : ImageAnalysis.Analyzer, AutoCloseable {
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build(),
    )
    private val main = Handler(Looper.getMainLooper())

    private class Request(val onResult: (FaceFrame?) -> Unit, val deadline: Long) {
        /** この頼みのあとに届いた最初の一枚のセンサー時刻。解析スレッドだけが触る。 */
        var firstFrame = NO_FRAME
    }

    private val request = AtomicReference<Request?>()

    /**
     * 全体を映した一枚でいちばん大きな顔を一度だけ探し、onResult に渡す。顔が無いか、TIMEOUT_MS までに使える一枚が来なければ null。
     * 使える一枚は、撮影結果がカメラの全体を示す一枚。手ブレ補正などで全体と報告されない端末でも、頼んでから SETTLE_NS 経った一枚は使う。
     * 届く前に呼び直すと前の onResult は呼ばれない。onResult はメインスレッドで呼ばれる。
     */
    fun requestFace(onResult: (FaceFrame?) -> Unit) = request.set(Request(onResult, SystemClock.uptimeMillis() + TIMEOUT_MS))

    /** まだフレームに渡していない requestFace を取り消す。探し始めた顔は止められず、onResult は呼ばれる。 */
    fun cancel() = request.set(null)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image ?: return proxy.close()
        val r = request.get() ?: return proxy.close()
        if (SystemClock.uptimeMillis() > r.deadline) {
            if (request.compareAndSet(r, null)) main.post { r.onResult(null) }
            return proxy.close()
        }
        val timestamp = proxy.imageInfo.timestamp
        if (r.firstFrame == NO_FRAME) r.firstFrame = timestamp
        val applied = crop.appliedAt(timestamp, RESULT_WAIT_MS)
        val whole = crop.spec?.let { applied != null && it.showsAll(applied) } ?: true
        if (!whole && timestamp - r.firstFrame < SETTLE_NS) return proxy.close()
        if (!request.compareAndSet(r, null)) return proxy.close()
        val frameCrop = applied ?: crop.latest
        val rotation = proxy.imageInfo.rotationDegrees
        val image = uprightSize(proxy.width, proxy.height, rotation)
        detector.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }?.landmarks()
                r.onResult(face?.let { FaceFrame(it, image, frameCrop) })
            }
            .addOnFailureListener { r.onResult(null) }
            .addOnCompleteListener { proxy.close() }
    }

    override fun close() = detector.close()

    private companion object {
        const val NO_FRAME = Long.MIN_VALUE

        /** 使える一枚を待つ上限。ML Kit が探し始めてからは待たない。 */
        const val TIMEOUT_MS = 600L

        /** 全体と報告されなくても、全体を頼んでからこれだけ経てばカメラは頼みを反映している。 */
        const val SETTLE_NS = 400_000_000L

        /** 一枚の撮影結果が解析より遅れて届くとき、待つ上限。 */
        const val RESULT_WAIT_MS = 50L
    }
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
