package com.kbcode.kagami.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kbcode.kagami.R
import com.kbcode.kagami.billing.LogoBillingState

/** 指の数と動きが見分けられる大きさ。 */
private val ICON = 56.dp

/** 指の操作を 1 つずつ、動く絵と、できること・操作のしかたの 2 行の文で示し、その下にロゴを消す買い切りを置く。文は絵のすぐ右に続け、読み上げは操作ごとに 1 つにまとめる。外側のタップか「閉じる」で閉じる。 */
@Composable
fun HelpDialog(billing: LogoBillingState, onBuy: () -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.help_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (icon in GestureIcon.entries) {
                    Row(Modifier.padding(vertical = 2.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
                        GestureLoop(icon)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(stringResource(icon.hint), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(icon.how),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                LogoPurchaseSection(billing, onBuy, onRestore)
            }
        },
    )
}

/** 指の操作の絵を、icon.loopMs で 1 周する動きで描き続ける。読むのは描くときだけなので、動いても組み直さない。読み上げは横の文が担う。 */
@Composable
private fun GestureLoop(icon: GestureIcon) {
    val t by rememberInfiniteTransition(label = "gesture").animateFloat(
        0f, 1f, infiniteRepeatable(tween(icon.loopMs, easing = LinearEasing)), label = "loop",
    )
    Canvas(Modifier.size(ICON)) { gestureGlyph(icon, t) }
}
