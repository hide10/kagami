package com.hide10.kagami.core

import kotlin.math.abs
import kotlin.math.max

/** ある瞬間の指 1 本。離した指は、離した位置が pressed = false で一度だけ届く。 */
data class Finger(val id: Long, val at: Pt, val pressed: Boolean = true)

/** 指の動きから読み取った操作。 */
sealed interface Gesture {
    /** 1 本指を動かさずに、長押しになる前に離した。at は離した位置。 */
    data class Tap(val at: Pt) : Gesture

    /** 1 本指を動かさずに押し続けた。離す前に届き、そのあと動かせば Move になる。 */
    data object LongPress : Gesture

    /** 2 本指をほぼ同時に置き、ほとんど動かさずにすぐ離した。 */
    data object TwoFingerTap : Gesture

    /** ドラッグとピンチ。CropPlanner.transform に渡す値で、centroid は動く前の指の重心、pan はその移動、zoom は指の間隔の比。 */
    data class Move(val centroid: Pt, val pan: Pt, val zoom: Float) : Gesture
}

/** 読み分けの閾値。slop は指が動いたとみなす距離(px)で、時間はミリ秒。 */
data class TouchRules(
    val slop: Float,
    val longPressMs: Long,
    /** 2 本指タップの 2 本目は、1 本目を置いてからこの時間のうちに置く。 */
    val twoFingerGapMs: Long = 150,
    /** 2 本指タップは、1 本目を置いてからこの時間のうちに全部離す。 */
    val twoFingerTapMs: Long = 400,
)

/**
 * 画面に届いた指を、最初の指を置いてから全部離すまでを 1 回として読み分ける。
 * 置いた位置から slop を越えて動けば Move で、そのあとは指の数が変わっても全部離すまで Move が続く。
 * 1 本指は、動かずに longPressMs たてば長押し、その前に離せばタップ。2 本指をほぼ同時に置いて動かさずにすぐ離せば 2 本指タップ。
 */
class TouchReader(private val rules: TouchRules) {
    /** 置いている指の、置いた位置と今の位置。 */
    private val start = HashMap<Long, Pt>()
    private val now = HashMap<Long, Pt>()
    private var touch: Touch? = null

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
        if (!t.moving) t.moving = movedPastSlop()
        return due ?: if (t.moving) move(before) else null
    }

    /** 置いた位置から、重心の移動か指の間隔の変化が slop を越えたか。1 本ならその指の移動。 */
    private fun movedPastSlop(): Boolean {
        val ids = now.keys.toList()
        val from = ids.map(start::getValue)
        val to = ids.map(now::getValue)
        val c0 = centroid(from)
        val c1 = centroid(to)
        return max(c0.distTo(c1), abs(spread(to, c1) - spread(from, c0))) > rules.slop
    }

    /** 前の位置から続けて置いている指だけで測るので、指を足しても離しても重心は跳ばない。何も変わらなければ null。 */
    private fun move(before: Map<Long, Pt>): Gesture? {
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
        val second = t.second
        return when {
            t.moving || t.held -> null
            t.most == 1 -> Gesture.Tap(at)
            t.most == 2 && second != null && second - t.since <= rules.twoFingerGapMs && timeMs - t.since <= rules.twoFingerTapMs ->
                Gesture.TwoFingerTap
            else -> null
        }
    }

    /** 1 回の操作。most は同時に置いた指のいちばん多い数、second は 2 本目を置いた時刻、held は長押しを知らせたか。 */
    private class Touch(val since: Long) {
        var most = 0
        var second: Long? = null
        var held = false
        var moving = false
        val waiting get() = most == 1 && !held && !moving
    }
}

private fun centroid(ps: List<Pt>) = ps.fold(Pt(0f, 0f), Pt::plus) / ps.size.toFloat()

/** 重心からの平均の距離。2 本なら指の間隔の半分。 */
private fun spread(ps: List<Pt>, c: Pt) = ps.sumOf { it.distTo(c).toDouble() }.toFloat() / ps.size
