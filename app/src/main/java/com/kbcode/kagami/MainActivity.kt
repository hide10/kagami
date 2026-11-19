package com.kbcode.kagami

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbcode.kagami.billing.LogoBillingViewModel
import com.kbcode.kagami.ui.CameraPermissionGate
import com.kbcode.kagami.ui.MirrorScreen

class MainActivity : ComponentActivity() {
    private val billing: LogoBillingViewModel by viewModels()

    // 起動したときも戻ってきたときも Play に購入を問い合わせ、ほかの端末での購入や返金を映す。
    override fun onResume() {
        super.onResume()
        billing.manager.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface {
                    val logo by billing.manager.state.collectAsStateWithLifecycle()
                    CameraPermissionGate {
                        MirrorScreen(
                            billing = logo,
                            onBuy = { billing.manager.buy(this@MainActivity) },
                            onRestore = { billing.manager.refresh(manual = true) },
                        )
                    }
                }
            }
        }
    }
}
