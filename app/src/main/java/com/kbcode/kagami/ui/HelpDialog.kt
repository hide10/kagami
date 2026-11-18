package com.kbcode.kagami.ui

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kbcode.kagami.R

/** 指の操作ひとつ。操作の絵と、それでできることの一言。 */
private class Hint(val icons: List<GestureIcon>, @StringRes val text: Int)

private val HINTS = listOf(
    Hint(listOf(GestureIcon.TAP), R.string.hint_focus),
    Hint(listOf(GestureIcon.DOUBLE_TAP), R.string.hint_light),
    Hint(listOf(GestureIcon.LONG_PRESS), R.string.hint_pause),
    Hint(listOf(GestureIcon.EDGE_DRAG, GestureIcon.PINCH, GestureIcon.TWO_FINGER_VERTICAL), R.string.hint_zoom),
    Hint(listOf(GestureIcon.TWO_FINGER_HORIZONTAL), R.string.hint_exposure),
    Hint(listOf(GestureIcon.TWO_FINGER_TAP), R.string.hint_full_screen),
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
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.help_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (hint in HINTS) {
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.width(ICONS_WIDTH), Arrangement.spacedBy(ICON_GAP, Alignment.End)) {
                            for (icon in hint.icons) {
                                val name = stringResource(icon.label)
                                Canvas(Modifier.size(ICON).semantics { contentDescription = name }) { gestureGlyph(icon) }
                            }
                        }
                        Text(stringResource(hint.text), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
    )
}
