package com.hide10.kagami.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** 指の操作ひとつ。操作の絵と、それでできることの一言。訳すときのために英語も持つ。 */
private class Hint(val icons: List<GestureIcon>, val ja: String, val en: String)

private val HINTS = listOf(
    Hint(listOf(GestureIcon.TAP), "ピント調整", "Focus"),
    Hint(listOf(GestureIcon.DOUBLE_TAP), "ライト オン・オフ", "Light on/off"),
    Hint(listOf(GestureIcon.LONG_PRESS), "一時停止", "Pause"),
    Hint(listOf(GestureIcon.EDGE_DRAG, GestureIcon.PINCH, GestureIcon.TWO_FINGER_VERTICAL), "ズーム", "Zoom"),
    Hint(listOf(GestureIcon.TWO_FINGER_HORIZONTAL), "露出", "Exposure"),
    Hint(listOf(GestureIcon.TWO_FINGER_TAP), "全面モード", "Full screen"),
)

private val ICON = 36.dp
private val ICON_GAP = 4.dp

/** 絵の列の幅。いちばん多く並ぶ行に合わせ、絵は右に寄せて一言のすぐ隣に置く。 */
private val ICONS_WIDTH = HINTS.maxOf { it.icons.size }.let { ICON * it + ICON_GAP * (it - 1) }

/** 指の操作を絵と一言で示す。外側のタップか「閉じる」で閉じる。 */
@Composable
fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text("閉じる") } },
        title = { Text("使い方") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (hint in HINTS) {
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.width(ICONS_WIDTH), Arrangement.spacedBy(ICON_GAP, Alignment.End)) {
                            for (icon in hint.icons) {
                                Canvas(Modifier.size(ICON).semantics { contentDescription = icon.ja }) { gestureGlyph(icon) }
                            }
                        }
                        Text(hint.ja, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
    )
}
