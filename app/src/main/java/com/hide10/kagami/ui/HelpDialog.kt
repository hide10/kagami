package com.hide10.kagami.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hide10.kagami.core.Crop
import com.hide10.kagami.core.CropPlanner
import com.hide10.kagami.core.FacePart
import kotlin.math.roundToInt

private class Help(val name: String, val text: String, val glyph: (DrawScope.() -> Unit)? = null)

private val TOP = listOf(
    Help(
        "ライト",
        "タップで画面を光らせて顔を照らし、もう一度タップで消します。長押しでパネルを開き、光り方・光の量・色温度を選びます。" +
            "光り方の「縁」は画面のふちを光らせ、「縮小」は映像を角の丸い小さな画面にしてその周りを光らせます。",
    ) { bulbGlyph(false) },
    Help("静止", "いまの映像を止めます。もう一度押すとライブに戻ります。止めているあいだも指で寄ったり動かしたりできますが、部位のアイコンは使えません。") {
        pauseGlyph(false)
    },
    Help("色・明るさ", "映像の明るさと色温度を変えるパネルを開け閉めします。") { slidersGlyph(false) },
    Help("情報", "この説明を開きます。") { infoGlyph(false) },
)

private val BOTTOM = listOf(
    Help("全体", "寄るのをやめて全体を映します。") { faceGlyph(null) },
    Help("左目", "画面の左に映っている目に寄ります。") { faceGlyph(FacePart.LEFT_EYE) },
    Help("右目", "画面の右に映っている目に寄ります。") { faceGlyph(FacePart.RIGHT_EYE) },
    Help("鼻", "鼻に寄ります。") { faceGlyph(FacePart.NOSE) },
    Help("口", "口が画面の幅いっぱい近くに映るまで寄ります。") { faceGlyph(FacePart.MOUTH) },
)

private val GESTURES = listOf(
    Help("タップ", "その点を真ん中にして ${"%.1f".format(CropPlanner.POINT_ZOOM)} 倍まで寄ります。すでにそれより寄っていれば倍率はそのままです。"),
    Help("1 本指でドラッグ", "見えている範囲を動かします。"),
    Help("2 本指でつまむ・広げる", "引いたり寄ったりします。"),
    Help(
        "右端を上下になぞる",
        "上へなぞると寄り、下へなぞると引きます。片手で使えます。右端の細い線のつまみがいまの倍率で、" +
            "線の下端が全体、上端が ${Crop.MAX_ZOOM.roundToInt()} 倍です。",
    ),
    Help("映像を長押し", "指を動かさずに押し続けると、静止を切り替えます。"),
    Help("ライトのアイコンを長押し", "ライトのパネルを開きます。"),
)

/** アイコンの意味と指の操作の説明。外側のタップか「閉じる」で閉じる。 */
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("閉じる") } },
        title = { Text("使い方") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Section("上のアイコン", TOP)
                Section("下のアイコン", BOTTOM)
                Text(
                    "部位のアイコンは押したときに一度だけ顔を探して寄り、そのあと顔が動いても追いかけません。顔が見つからなければ何もしません。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Section("指の操作", GESTURES)
            }
        },
    )
}

@Composable
private fun Section(title: String, items: List<Help>) {
    Text(title, Modifier.padding(top = 16.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
    for (item in items) {
        Row(Modifier.padding(vertical = 4.dp)) {
            item.glyph?.let {
                Canvas(Modifier.size(28.dp), onDraw = it)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(item.name, fontWeight = FontWeight.Bold)
                Text(item.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}
