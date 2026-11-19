package com.kbcode.kagami.core

enum class Panel { COLOR, LIGHT }

/**
 * target は見せたい切り出しで、null は全体。動かすのはボタンと指だけで、顔を追いかけはしない。
 * pendingPart は押された部位ボタンで、顔が見つかったときに一度だけ寄る。
 * shownPart は最後に寄った部位で、target はその部位の切り出し。指やタップで動かすか全体に戻すと null になる。
 * still は止めた一枚を映しているあいだ、その一枚にカメラが切り出していた範囲(ビュー座標)。止めているあいだはその範囲が全体になる。
 * 部位はカメラの今の顔で決めるので、止めているあいだは寄れない。
 * fullScreen は全面鏡モードで、ボタンと帯とパネルを隠して映像を画面いっぱいに映す。指の操作はふだんと同じに使える。
 */
data class MirrorState(
    val target: Crop? = null,
    val pendingPart: FacePart? = null,
    val shownPart: FacePart? = null,
    val still: Crop? = null,
    val color: ColorAdjust = ColorAdjust(),
    val light: ScreenLight = ScreenLight(),
    val panel: Panel? = null,
    val fullScreen: Boolean = false,
) {
    val frozen get() = still != null

    /** 入るのも抜けるのも 2 本指タップ。ほかの状態は変えないので、抜ければ元の画面に戻る。 */
    fun toggleFullScreen() = copy(fullScreen = !fullScreen)

    /** 画面を光らせている光らせ方。消えていれば null。全面では縮小でも映像を画面いっぱいに保ち、同じ光の量の縁で光らせる。 */
    val glowing: LightMode?
        get() = when {
            !light.on -> null
            fullScreen -> LightMode.BAND
            else -> light.mode
        }

    /** プレビューを映す大きさ(画面に対する比)。縮小で光らせているときだけ 1 より小さい。 */
    val previewScale get() = if (glowing == LightMode.SHRINK) light.previewScale else 1f

    /** 見せられるいちばん広い範囲。止めているあいだは止めた一枚の範囲、ほかはカメラの全体。 */
    fun extent(size: Size) = still ?: Crop.full(size)

    /** ライトのパネルは点いているあいだだけ開くので、消えているときに開けばライトも点ける。 */
    fun toggle(p: Panel) = when {
        p == panel -> copy(panel = null)
        p == Panel.LIGHT -> copy(panel = p, light = light.copy(on = true))
        else -> copy(panel = p)
    }

    /** ライトの点灯と消灯を切り替える。光の量と色温度はそのまま。消したらライトのパネルも閉じ、点け直しても開かない。 */
    fun toggleLight(): MirrorState {
        val on = !light.on
        return copy(light = light.copy(on = on), panel = panel.takeUnless { !on && it == Panel.LIGHT })
    }

    /**
     * 部位の帯でボタン part(null は全体)を選んでいるか。部位は最後に寄った部位で、顔を待つあいだも見つからなかったときも前のまま。
     * 全体は見せられる範囲をまるごと見せているあいだで、指で 1 倍まで引いたときも含む。止めているあいだも選んだままにする。
     */
    fun selects(part: FacePart?): Boolean = when {
        part != null -> part == shownPart
        shownPart != null -> false
        else -> target.let { it == null || it.zoom <= (still?.zoom ?: 1f) }
    }

    fun showAll() = copy(target = null, pendingPart = null, shownPart = null)

    /** タップや指で決めた切り出し。押したばかりの部位ボタンも、寄っていた部位も外す。 */
    fun moveTo(crop: Crop) = copy(target = crop, pendingPart = null, shownPart = null)

    /** 顔が見つかるまで見え方は変えない。続けて押せば最後の部位が残る。止めているあいだは受け付けない。 */
    fun request(part: FacePart) = if (frozen) this else copy(pendingPart = part)

    /** 見え方はそのままで止める。見せたい切り出しは止めた一枚の範囲に収め、押したばかりの部位ボタンは取り消す。 */
    fun freeze(extent: Crop, size: Size): MirrorState {
        if (frozen) return this
        val e = extent.clamp(size)
        return copy(still = e, target = target?.clamp(size, e), pendingPart = null)
    }

    fun unfreeze() = copy(still = null)

    /** 押した部位の顔が見つからなかった。見え方は変えない。 */
    fun partMissed() = copy(pendingPart = null)

    /** face はビュー座標。待っている部位がなければ何も変えない。 */
    fun faceFound(face: FaceLandmarks, size: Size) =
        pendingPart?.let { copy(target = CropPlanner.zoomOnPart(it, face, size), pendingPart = null, shownPart = it) } ?: this
}
