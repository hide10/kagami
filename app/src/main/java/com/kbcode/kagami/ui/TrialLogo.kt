package com.kbcode.kagami.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.kbcode.kagami.R

/**
 * 未購入のあいだ左下に出す「試用版」の文字。課金テンプレートの体裁(20sp・太字・濃さ 0.55)で、
 * 背景色の縁取りを重ねて映像の上でも読めるようにする。押せず、読み上げもしない。
 */
@Composable
fun TrialLogo(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.logo_trial_label)
    val style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
    Box(modifier.alpha(0.55f).clearAndSetSemantics {}) {
        Text(label, color = MaterialTheme.colorScheme.background, style = style.copy(drawStyle = Stroke(width = 8f, join = StrokeJoin.Round)))
        Text(label, color = MaterialTheme.colorScheme.onBackground, style = style)
    }
}
