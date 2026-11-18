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
import com.hide10.kagami.core.FacePart

/** 名前と短い一行だけの説明。見ればわかるアイコン(情報)は載せない。 */
private class Help(val name: String, val text: String, val glyph: (DrawScope.() -> Unit)? = null)

private val TOP = listOf(
    Help("ライト", "タップで点灯・消灯。長押しで設定を開く") { bulbGlyph(false) },
    Help("静止", "映像を止める。もう一度押すと戻る") { pauseGlyph(false) },
    Help("色・明るさ", "明るさと色温度を変える") { slidersGlyph(false) },
)

private val BOTTOM = listOf(
    Help("全体", "全体を映す") { faceGlyph(null) },
    Help("左目", "左の目に寄る") { faceGlyph(FacePart.LEFT_EYE) },
    Help("右目", "右の目に寄る") { faceGlyph(FacePart.RIGHT_EYE) },
    Help("鼻", "鼻に寄る") { faceGlyph(FacePart.NOSE) },
    Help("口", "口に寄る") { faceGlyph(FacePart.MOUTH) },
)

private val GESTURES = listOf(
    Help("タップ", "タップした場所に寄って、ピントを合わせる"),
    Help("1本指ドラッグ", "表示を動かす"),
    Help("ピンチ", "寄る・引く"),
    Help("右端スライダー", "上下でズーム"),
    Help("長押し", "静止する"),
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
                Text("部位のボタンは、押したときだけ顔を探して寄ります", style = MaterialTheme.typography.bodyMedium)
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
