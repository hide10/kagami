package com.kbcode.kagami.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.kbcode.kagami.R

private enum class Access { ASKING, GRANTED, DENIED }

@Composable
fun CameraPermissionGate(content: @Composable () -> Unit) {
    val activity = LocalContext.current as Activity
    fun granted() = ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var access by remember { mutableStateOf(if (granted()) Access.GRANTED else Access.ASKING) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        access = if (it) Access.GRANTED else Access.DENIED
    }
    LaunchedEffect(Unit) { if (access == Access.ASKING) request.launch(Manifest.permission.CAMERA) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (granted()) access = Access.GRANTED }

    when (access) {
        Access.GRANTED -> content()
        Access.ASKING -> Unit
        Access.DENIED -> Column(
            Modifier.fillMaxSize().padding(32.dp),
            Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.camera_permission_reason))
            Button({
                // 2回断ると許可ダイアログはもう出ないので、アプリの設定画面から許可してもらう。
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
                    request.launch(Manifest.permission.CAMERA)
                } else {
                    activity.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)),
                    )
                }
            }) { Text(stringResource(R.string.camera_permission_allow)) }
        }
    }
}
