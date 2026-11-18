package com.hide10.kagami.core

import kotlin.math.abs
import kotlin.math.max

/** ある瞬間の指 1 本。離した指は、離した位置が pressed = false で一度だけ届く。 */
data class Finger(val id: Long, val at: Pt, val pressed: Boolean = true)

/** 指の動きから読み取った操作。 */
sealed interface Gesture {
    /** 1 本指を動かさずに、長押しになる前に離した。at は離した位置。ダブルタップの 1 回目もすぐにタップとして届く。 */
    data class Tap(val at: Pt) : Gesture

    /** タップのすぐあとに、近くをもう一度タップした。2 回目はタップとしては届かない。 */
    data object DoubleTap : Gesture

    /** 1 本指を動かさずに押し続けた。離す前に届き、そのあと動かせば Move になる。 */
    data object LongPress : Gesture

    /** 2 本指をほぼ同時に置き、ほとんど動かさずにすぐ離した。 */
    data object TwoFingerTap : Gesture

    /** 1 本指のドラッグとピンチ。CropPlanner.transform に渡す値で、centroid は動く前の指の重心、pan はその移動、zoom は指の間隔の比。 */
    data class Move(val centroid: Pt, val pan: Pt, val zoom: Float) : Gesture

    /** 2 本指を間隔を保ったまま上下に動かした。dy は前に届けてからの重心の移動で、下向きが正。 */
    data class ZoomDrag(val dy: Float) : Gesture

    /** 2 本指を間隔を保ったまま左右に動かした。dx は横へ動かし始めてからの重心の移動の合計で、右が正。starts はその最初の一回。 */
    data class ExposureDrag(val dx: Float, val starts: Boolean) : Gesture
}

/** 読み分けの閾値。slop は指が動いたとみなす距離(px)で、時間はミリ秒。 */
data class TouchRules(
    val slop: Float,
    val longPressMs: Long,
    /** ダブルタップの 2 回目は、1 回目を離してからこの時間のうちに置く。 */
    val doubleTapMs: Long,
    /** ダブルタップの 2 回目は、1 回目を離した位置からこの距離(px)のうちで離す。 */
    val doubleTapSlop: Float,
    /** 2 本指タップの 2 本目は、1 本目を置いてからこの時間のうちに置く。 */
    val twoFingerGapMs: Long = 150,
    /** 2 本指タップは、1 本目を置いてからこの時間のうちに全部離す。 */
    val twoFingerTapMs: Long = 400,
)

/**
 * 画面に届いた指を、最初の指を置いてから全部離すまでを 1 回として読み分ける。
 * 置いた位置から slop を越えて動いたときに一度だけ動き方を決め、指の数が変わっても全部離すまで変えない。
 * 1 本指なら Move。2 本指以上なら、指の間隔の変化が重心の移動より大きければピンチの Move、
 * そうでなければ重心の動きの縦横の大きいほうで、縦は ZoomDrag、横は ExposureDrag。
 * 動かずに離したときは、1 本指ならタップかダブルタップ、2 本指をほぼ同時に置いてすぐ離したなら 2 本指タップ。
 * 1 本指を動かさずに longPressMs たてば長押し。
 */
class TouchReader(private val rules: TouchRules) {
    /** 置いている指の、置いた位置と今の位置。 */
    private val start = HashMap<Long, Pt>()
    private val now = HashMap<Long, Pt>()
    private var touch: Touch? = null
    private var lastTap: LastTap? = null

    /** 長押しを待っているあいだ、それが決まる時刻。それまでに指の動きが届かなければ、その時刻に tick を呼ぶ。 */
    val deadline: Long?
        get() = touch?.takeIf { it.waiting }?.let { it.since + rules.longPressMs }

    /** timeMs が deadline に届いていれば長押し。 */
    fun tick(timeMs: Long): Gesture? {
        val t = touch?.takeIf { it.waiting && timeMs >= it.since + rules.longPressMs } ?: return null
        t.held = true
        return Gesture.LongPress
    }

    /** 時刻 timeMs に届いた指。届かなかった指は前の位置に置いたままとみなす。 */
    fun read(timeMs: Long, fingers: List<Finger>): Gesture? {
        val due = tick(timeMs)
        val before = HashMap(now)
        for (f in fingers) {
            if (f.pressed) {
                start.putIfAbsent(f.id, f.at)
                now[f.id] = f.at
            } else {
                start.remove(f.id)
                now.remove(f.id)
            }
        }
        val t = touch ?: if (now.isEmpty()) return due else Touch(timeMs).also { touch = it }
        t.most = max(t.most, now.size)
        if (now.size >= 2 && t.second == null) t.second = timeMs
        if (now.isEmpty()) {
            touch = null
            return due ?: finish(t, timeMs, fingers.first { !it.pressed }.at)
        }
        val kind = t.kind ?: decide()?.also { t.kind = it } ?: return due
        val m = move(before) ?: return due
        return due ?: when (kind) {
            Kind.MOVE -> m
            Kind.ZOOM -> Gesture.ZoomDrag(m.pan.y)
            Kind.EXPOSURE -> {
                val starts = t.dx == null
                val dx = (t.dx ?: 0f) + m.pan.x
                t.dx = dx
                Gesture.ExposureDrag(dx, starts)
            }
        }
    }

    /** 置いた位置から、重心の移動か指の間隔の変化が slop を越えていれば、その動き方。 */
    private fun decide(): Kind? {
        val ids = now.keys.toList()
        val from = ids.map(start::getValue)
        val to = ids.map(now::getValue)
        val c0 = centroid(from)
        val c1 = centroid(to)
        val travel = c1 - c0
        val moved = c0.distTo(c1)
        // spread は指の間隔の半分なので、2 倍して間隔の変化にする。片方の指だけを動かすピンチでも、間隔は重心の 2 倍動く。
        val stretch = abs(spread(to, c1) - spread(from, c0))
        return when {
            max(moved, stretch) <= rules.slop -> null
            ids.size == 1 || 2 * stretch > moved -> Kind.MOVE
            abs(travel.y) >= abs(travel.x) -> Kind.ZOOM
            else -> Kind.EXPOSURE
        }
    }

    /** 前の位置から続けて置いている指だけで測るので、指を足しても離しても重心は跳ばない。何も変わらなければ null。 */
    private fun move(before: Map<Long, Pt>): Gesture.Move? {
        val ids = now.keys.filter { it in before }
        if (ids.isEmpty()) return null
        val from = ids.map(before::getValue)
        val to = ids.map(now::getValue)
        val c0 = centroid(from)
        val c1 = centroid(to)
        val s0 = spread(from, c0)
        val zoom = if (s0 == 0f) 1f else spread(to, c1) / s0
        return if (c0 == c1 && zoom == 1f) null else Gesture.Move(c0, c1 - c0, zoom)
    }

    private fun finish(t: Touch, timeMs: Long, at: Pt): Gesture? {
        val previous = lastTap
        lastTap = null
        val second = t.second
        return when {
            t.kind != null || t.held -> null
            t.most == 1 && previous != null && t.since - previous.upMs <= rules.doubleTapMs && previous.at.distTo(at) <= rules.doubleTapSlop ->
                Gesture.DoubleTap
            t.most == 1 -> Gesture.Tap(at).also { lastTap = LastTap(timeMs, at) }
            t.most == 2 && second != null && second - t.since <= rules.twoFingerGapMs && timeMs - t.since <= rules.twoFingerTapMs ->
                Gesture.TwoFingerTap
            else -> null
        }
    }

    private enum class Kind { MOVE, ZOOM, EXPOSURE }

    private class LastTap(val upMs: Long, val at: Pt)

    /**
     * 1 回の操作。most は同時に置いた指のいちばん多い数、second は 2 本目を置いた時刻、held は長押しを知らせたか、
     * kind は決まった動き方、dx は ExposureDrag で届けた横の移動の合計。
     */
    private class Touch(val since: Long) {
        var most = 0
        var second: Long? = null
        var held = false
        var kind: Kind? = null
        var dx: Float? = null
        val waiting get() = most == 1 && !held && kind == null
    }
}

private fun centroid(ps: List<Pt>) = ps.fold(Pt(0f, 0f), Pt::plus) / ps.size.toFloat()

/** 重心からの平均の距離。2 本なら指の間隔の半分。 */
private fun spread(ps: List<Pt>, c: Pt) = ps.sumOf { it.distTo(c).toDouble() }.toFloat() / ps.size
