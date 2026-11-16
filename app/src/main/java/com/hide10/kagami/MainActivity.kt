package com.hide10.kagami

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent { KagamiApp() }
    }
}

private fun isCameraGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun KagamiApp(viewModel: MirrorViewModel = viewModel()) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(isCameraGranted(context)) }
    var requested by remember { mutableStateOf(false) }
    var hasFrontCamera by remember { mutableStateOf<Boolean?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        requested = true
        granted = result
    }

    // 設定アプリから戻った時などに、許可の状態を取り直す。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                granted = isCameraGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(granted) {
        if (granted && hasFrontCamera == null) {
            val provider = ProcessCameraProvider.getInstance(context).get()
            hasFrontCamera = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            !granted -> {
                val canAskAgain = !requested ||
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity, Manifest.permission.CAMERA
                    )
                PermissionGuide(
                    canAskAgain = canAskAgain,
                    onRequest = { launcher.launch(Manifest.permission.CAMERA) },
                    onOpenSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    },
                )
            }
            hasFrontCamera == false -> CenterMessage("この端末では前面カメラが使えません")
            hasFrontCamera == true -> MirrorScreen(viewModel = viewModel)
            else -> CenterMessage("準備中…")
        }
    }
}

@Composable
private fun PermissionGuide(
    canAskAgain: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "鏡",
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "自分の姿を映すためにカメラを使います。映像を保存したり、外部へ送ったりすることはありません。",
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
        if (canAskAgain) {
            Button(onClick = onRequest) { Text("カメラを許可する") }
        } else {
            Text(
                text = "カメラの許可がオフになっています。設定で許可すると使えるようになります。",
                color = Color.White,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            Button(onClick = onOpenSettings) { Text("設定で許可する") }
        }
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = Color.White)
    }
}

@Composable
fun MirrorScreen(viewModel: MirrorViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var linearZoom by remember { mutableFloatStateOf(0f) }
    var zoomSupported by remember { mutableStateOf(false) }
    var stabilizationSupported by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var edgeBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    // カメラのバインド。手ブレ補正とエッジ表示の切替では組み直す。
    LaunchedEffect(uiState.stabilizationEnabled, uiState.edgeMode) {
        val provider = ProcessCameraProvider.getInstance(context).get()
        provider.unbindAll()
        // 対応の判定をしてから有効化する（未確認で有効化すると HAL エラーになりうる）。
        val frontInfo = provider.availableCameraInfos.firstOrNull {
            it.lensFacing == CameraSelector.LENS_FACING_FRONT
        }
        val stabSupported = frontInfo?.let {
            Preview.getPreviewCapabilities(it).isStabilizationSupported
        } ?: false
        stabilizationSupported = stabSupported
        val previewBuilder = Preview.Builder()
        if (uiState.stabilizationEnabled && stabSupported) {
            previewBuilder.setPreviewStabilizationEnabled(true)
        }
        val preview = previewBuilder.build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val useCases = mutableListOf<UseCase>(preview)
        if (uiState.edgeMode) {
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(analysisExecutor, EdgeAnalyzer { bmp -> edgeBitmap = bmp })
            useCases.add(analysis)
        }
        camera = provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            *useCases.toTypedArray(),
        )
    }

    // ズーム状態とカメラエラーの観測。
    DisposableEffect(camera) {
        val info = camera?.cameraInfo
        val zoomObserver = Observer<ZoomState> { state ->
            if (state != null) {
                linearZoom = state.linearZoom
                zoomSupported = MirrorViewModel.isZoomSupported(
                    state.minZoomRatio, state.maxZoomRatio
                )
            }
        }
        val stateObserver = Observer<androidx.camera.core.CameraState> { state ->
            cameraError = state?.error?.let {
                "カメラを使えませんでした（他のアプリが使用中の可能性があります）"
            }
        }
        info?.zoomState?.observe(lifecycleOwner, zoomObserver)
        info?.cameraState?.observe(lifecycleOwner, stateObserver)
        onDispose {
            info?.zoomState?.removeObserver(zoomObserver)
            info?.cameraState?.removeObserver(stateObserver)
        }
    }

    // プレビュー上でのピンチズームとタップ（パネルの表示切替）。
    DisposableEffect(previewView, camera) {
        val cam = camera
        if (cam == null) {
            return@DisposableEffect onDispose {}
        }
        var pinchActive = false
        val scaleDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    pinchActive = true
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val zoomState = cam.cameraInfo.zoomState.value
                    val current = zoomState?.zoomRatio ?: 1f
                    val target = MirrorViewModel.clampZoomRatio(
                        current * detector.scaleFactor,
                        zoomState?.minZoomRatio ?: 1f,
                        zoomState?.maxZoomRatio ?: 1f,
                    )
                    cam.cameraControl.setZoomRatio(target)
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    pinchActive = false
                }
            },
        )
        val tapDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (!pinchActive) viewModel.togglePanel()
                    return true
                }
            },
        )
        previewView.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            tapDetector.onTouchEvent(event)
            true
        }
        onDispose { previewView.setOnTouchListener(null) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.edgeMode && edgeBitmap != null) {
            Image(
                bitmap = edgeBitmap!!.asImageBitmap(),
                contentDescription = "エッジ表示",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (cameraError != null) {
            Text(
                text = cameraError!!,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(24.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            )
        }

        if (uiState.panelVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.displayCutout),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "ズーム", color = Color.White)
                        IconButton(onClick = { viewModel.setShowInfo(true) }) {
                            Text(text = "i", color = Color.White)
                        }
                    }
                    Slider(
                        value = linearZoom,
                        onValueChange = { value ->
                            linearZoom = value
                            camera?.cameraControl?.setLinearZoom(value)
                        },
                        enabled = zoomSupported,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.edgeMode,
                            onClick = { viewModel.setEdgeMode(!uiState.edgeMode) },
                            label = { Text("エッジ") },
                        )
                        FilterChip(
                            selected = uiState.stabilizationEnabled && stabilizationSupported,
                            onClick = {
                                if (stabilizationSupported) {
                                    viewModel.setStabilizationEnabled(
                                        !uiState.stabilizationEnabled
                                    )
                                }
                            },
                            enabled = stabilizationSupported,
                            label = { Text("手ブレ補正") },
                        )
                    }
                }
            }
        }
    }

    if (uiState.showInfo) {
        val versionName = remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (e: Exception) {
                ""
            }
        }
        AlertDialog(
            onDismissRequest = { viewModel.setShowInfo(false) },
            title = { Text("鏡") },
            text = {
                Text(
                    "バージョン $versionName\n\n" +
                        "使い方：ピンチまたは下のスライダーで拡大できます。画面をタップすると操作パネルを隠せます。\n\n" +
                        "「エッジ」は輪郭を強調する表示です。細部を確かめたいときに使います。\n\n" +
                        "手ブレ補正：この端末は" +
                        (if (stabilizationSupported) "対応しています" else "対応していません") + "。\n\n" +
                        "映像は端末の中だけで処理し、保存も送信もしません。"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.setShowInfo(false) }) { Text("閉じる") }
            },
        )
    }
}
