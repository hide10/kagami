package com.hide10.kagami.camera

import android.os.SystemClock
import com.hide10.kagami.core.SensorRect
import com.hide10.kagami.core.SensorSpec
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 画面・カメラ・顔の解析が分け合う、カメラ側の切り出し。
 * 画面が requested に頼みたい範囲を置き、カメラはそれを頼んで、撮れた一枚ごとに実際に切り出した範囲を記録する。画面と解析はそれを読み戻す。
 */
class CameraCrop {
    /** カメラをつないだら決まる。null のあいだ(切り出しに対応していない端末も)は、画面がすべて自分で拡大する。 */
    @Volatile
    var spec: SensorSpec? = null
        internal set

    /** 画面が頼みたい SCALER_CROP_REGION。null のあいだは何も頼まない。 */
    val requested = MutableStateFlow<SensorRect?>(null)

    /** いちばん新しい一枚にカメラが実際に切り出していた範囲。 */
    @Volatile
    var latest: SensorRect? = null
        private set

    private val lock = ReentrantLock()
    private val arrived = lock.newCondition()
    private val stamps = LongArray(HISTORY)
    private val rects = arrayOfNulls<SensorRect>(HISTORY)
    private var next = 0

    /** センサー時刻 timestamp の一枚の撮影結果。前の一枚と同じ範囲なら同じインスタンスを使い、毎フレーム作らない。 */
    internal fun record(timestamp: Long, left: Int, top: Int, right: Int, bottom: Int): SensorRect = lock.withLock {
        val last = latest
        val rect = if (last != null && last.left == left && last.top == top && last.right == right && last.bottom == bottom) {
            last
        } else {
            SensorRect(left, top, right, bottom)
        }
        stamps[next] = timestamp
        rects[next] = rect
        next = (next + 1) % HISTORY
        latest = rect
        arrived.signalAll()
        rect
    }

    /** センサー時刻 timestamp の一枚にカメラが切り出していた範囲。撮影結果がまだなら waitMs まで待ち、来なければ null。 */
    fun appliedAt(timestamp: Long, waitMs: Long): SensorRect? = lock.withLock {
        val deadline = SystemClock.uptimeMillis() + waitMs
        var found = find(timestamp)
        var left = deadline - SystemClock.uptimeMillis()
        while (found == null && left > 0) {
            arrived.await(left, TimeUnit.MILLISECONDS)
            found = find(timestamp)
            left = deadline - SystemClock.uptimeMillis()
        }
        found
    }

    private fun find(timestamp: Long): SensorRect? {
        for (i in 0 until HISTORY) if (stamps[i] == timestamp) return rects[i]
        return null
    }

    private companion object {
        /** 30fps で 0.5 秒ぶん。解析が遅れても、その一枚の結果はまだ残っている。 */
        const val HISTORY = 16
    }
}
