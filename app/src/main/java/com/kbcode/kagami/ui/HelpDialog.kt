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
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kbcode.kagami.R
import com.kbcode.kagami.billing.LogoBillingState

/** 指の数と動きが見分けられる大きさ。 */
private val ICON = 56.dp

/** 指の操作を 1 行に 1 つ、動く絵と一言で示し、その下にロゴを消す買い切りを置く。一言は絵のすぐ右に続ける。外側のタップか「閉じる」で閉じる。 */
@Composable
fun HelpDialog(billing: LogoBillingState, onBuy: () -> Unit, onRestore: () -> Unit, onDismiss: () -> Unit) {
    val gap = AlertDialogDefaults.containerColor
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onDismiss) { Text(stringResource(R.string.close)) } },
        title = { Text(stringResource(R.string.help_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (icon in GestureIcon.entries) {
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        GestureLoop(icon, gap)
                        Text(stringResource(icon.hint), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                LogoPurchaseSection(billing, onBuy, onRestore)
            }
        },
    )
}

/** 指の操作の絵を、icon.loopMs で 1 周する動きで描き続ける。読むのは描くときだけなので、動いても組み直さない。 */
@Composable
private fun GestureLoop(icon: GestureIcon, gap: Color) {
    val t by rememberInfiniteTransition(label = "gesture").animateFloat(
        0f, 1f, infiniteRepeatable(tween(icon.loopMs, easing = LinearEasing)), label = "loop",
    )
    val name = stringResource(icon.label)
    Canvas(Modifier.size(ICON).semantics { contentDescription = name }) { gestureGlyph(icon, gap, t) }
}
