package com.kbcode.kagami.camera

import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPointFactory
import androidx.camera.view.PreviewView
import com.kbcode.kagami.core.Pt
import com.kbcode.kagami.core.SensorMapping
import java.util.concurrent.TimeUnit

/** 合わせたピントと露出を、CameraX が連続 AF に戻すまでの秒数。 */
private const val AUTO_CANCEL_S = 3L

/**
 * 映像のタップした点にピントと露出を合わせる。カメラをつないでいるあいだだけ働き、どれもメインスレッドで呼ぶ。
 *
 * CameraX は測る点を、自分の切り出しの中の比としてセンサー座標へ直す。拡大は SCALER_CROP_REGION を Camera2CameraControl で
 * 上書きして行い、CameraX の倍率は 1 のままなので、CameraX にとっての切り出しはいつもセンサー全体になる。
 * PreviewView の MeteringPointFactory が返すのは映っているストリームの中の比なので、カメラが切り出しているあいだは別の所を測ってしまう。
 * そこでカメラが切り出せるときは、指の下に映っているセンサー上の点を base に対する比(SensorMapping.baseFraction)で求め、
 * Preview と同じ比を持つ点で渡す。CameraX はその比をセンサー全体の中央にある base へ戻すので、カメラの切り出しによらず指の下を測る。
 * 切り出せないカメラでは映っているのが base 全体なので、PreviewView の点をそのまま使う。
 */
class CameraFocus {
    private class Bound(val camera: Camera, val view: PreviewView, val sensor: MeteringPointFactory?)

    private var bound: Bound? = null

    /** sensor は base に対する比を Preview と同じ比の点にする factory で、カメラが切り出せるときだけ渡す。 */
    internal fun attach(camera: Camera, view: PreviewView, sensor: MeteringPointFactory?) {
        bound = Bound(camera, view, sensor)
    }

    internal fun detach() {
        bound = null
    }

    /**
     * point はビュー座標で、画像全体を倍率 1 で見たときの位置。mapping はカメラが切り出せるあいだの対応、zoom はいま見せている倍率。
     * 測る広さは見えている範囲に対して同じになるよう、倍率で割る。頼めたら true。
     * つないでいないときや、AF・AE・AWB のどの領域も持たないカメラ(固定焦点の前面カメラに多い)では、何もせず false。
     */
    fun at(point: Pt, mapping: SensorMapping?, zoom: Float): Boolean {
        val b = bound ?: return false
        val size = MeteringPointFactory.getDefaultPointSize() / zoom
        val metering = if (mapping != null && b.sensor != null) {
            val f = mapping.baseFraction(point)
            b.sensor.createPoint(f.x, f.y, size)
        } else {
            b.view.meteringPointFactory.createPoint(point.x, point.y, size)
        }
        val action = FocusMeteringAction.Builder(metering).setAutoCancelDuration(AUTO_CANCEL_S, TimeUnit.SECONDS).build()
        if (!b.camera.cameraInfo.isFocusMeteringSupported(action)) return false
        b.camera.cameraControl.startFocusAndMetering(action)
        return true
    }
}
