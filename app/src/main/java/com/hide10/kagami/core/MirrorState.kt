package com.hide10.kagami.core

enum class Panel { COLOR, LIGHT }

/**
 * target は見せたい切り出しで、null は全体。動かすのはボタンと指だけで、顔を追いかけはしない。
 * pendingPart は押された部位ボタンで、顔が見つかったときに一度だけ寄る。
 */
data class MirrorState(
    val target: Crop? = null,
    val pendingPart: FacePart? = null,
    val color: ColorAdjust = ColorAdjust(),
    val light: ScreenLight = ScreenLight(),
    val panel: Panel? = null,
) {
    fun toggle(p: Panel) = copy(panel = p.takeUnless { it == panel })

    fun showAll() = copy(target = null, pendingPart = null)

    /** タップや指で決めた切り出し。押したばかりの部位ボタンは取り消す。 */
    fun moveTo(crop: Crop) = copy(target = crop, pendingPart = null)

    /** 顔が見つかるまで見え方は変えない。続けて押せば最後の部位が残る。 */
    fun request(part: FacePart) = copy(pendingPart = part)

    /** face はビュー座標。待っている部位がなければ何も変えない。 */
    fun faceFound(face: FaceLandmarks, size: Size) =
        pendingPart?.let { copy(target = CropPlanner.zoomOnPart(it, face, size), pendingPart = null) } ?: this
}
