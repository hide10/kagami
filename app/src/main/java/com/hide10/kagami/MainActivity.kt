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
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
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
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException

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
    // 許可を求めた履歴はプロセスが終わっても残す（永久拒否の案内を初回から正しく出すため）。
    val prefs = remember {
        context.getSharedPreferences("kagami_prefs", Context.MODE_PRIVATE)
    }
    var requested by remember { mutableStateOf(prefs.getBoolean("camera_requested", false)) }
    var hasFrontCamera by remember { mutableStateOf<Boolean?>(null) }
    var providerError by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { result ->
        requested = true
        prefs.edit().putBoolean("camera_requested", true).apply()
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
        if (granted && hasFrontCamera == null && !providerError) {
            try {
                val provider = ProcessCameraProvider.awaitInstance(context)
                hasFrontCamera = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                providerError = true
            }
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
                    requested = requested,
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
            providerError -> CenterMessage("カメラを準備できませんでした。アプリを再起動してみてください")
            hasFrontCamera == false -> CenterMessage("この端末では前面カメラが使えません")
            hasFrontCamera == true -> MirrorScreen(viewModel = viewModel)
            else -> CenterMessage("準備中…")
        }
    }
}

@Composable
private fun PermissionGuide(
    canAskAgain: Boolean,
    requested: Boolean,
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
            if (requested) {
                TextButton(onClick = onOpenSettings) { Text("設定で許可する") }
            }
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
        Text(text = text, color = Color.White, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun EdgeImage(bitmap: Bitmap) {
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "エッジ表示",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
fun MirrorScreen(viewModel: MirrorViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var providerRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundUseCases by remember { mutableStateOf<List<UseCase>>(emptyList()) }
    var previewUseCase by remember { mutableStateOf<Preview?>(null) }
    var analysisUseCase by remember { mutableStateOf<ImageAnalysis?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var linearZoom by remember { mutableFloatStateOf(0f) }
    var zoomSupported by remember { mutableStateOf(false) }
    var stabilizationSupported by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var bindFailed by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var edgeBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var sliderDragging by remember { mutableStateOf(false) }
    val edgeGeneration = remember { AtomicInteger(0) }
    var displayRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            providerRef?.unbind(*boundUseCases.toTypedArray())
        }
    }

    // エッジ表示を切り替えたら、前のフレームは捨てる。
    LaunchedEffect(uiState.edgeMode) { edgeBitmap = null }

    fun currentRotation(displayManager: DisplayManager): Int =
        previewView.display?.rotation
            ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
            ?: Surface.ROTATION_0

    // 表示の回転（180度の反転を含む）を DisplayListener で拾う。
    DisposableEffect(Unit) {
        val displayManager =
            context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) {}
            override fun onDisplayRemoved(displayId: Int) {}
            override fun onDisplayChanged(displayId: Int) {
                displayRotation = currentRotation(displayManager)
            }
        }
        displayManager.registerDisplayListener(listener, null)
        displayRotation = currentRotation(displayManager)
        onDispose { displayManager.unregisterDisplayListener(listener) }
    }

    // カメラのバインド。手ブレ補正・エッジ表示の切替と、失敗時の再試行で組み直す。
    LaunchedEffect(uiState.stabilizationEnabled, uiState.edgeMode, retryKey) {
        bindFailed = false
        val myGeneration = edgeGeneration.incrementAndGet()
        val provider = try {
            ProcessCameraProvider.awaitInstance(context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            bindFailed = true
            return@LaunchedEffect
        }
        providerRef = provider
        // 組み直し前のズーム倍率を覚えておき、後で戻す。
        val savedRatio = camera?.cameraInfo?.zoomState?.value?.zoomRatio
        provider.unbind(*boundUseCases.toTypedArray())

        val stabSupported = try {
            val info = provider.getCameraInfo(CameraSelector.DEFAULT_FRONT_CAMERA)
            Preview.getPreviewCapabilities(info).isStabilizationSupported
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        stabilizationSupported = stabSupported

        fun buildAndBind(useStabilization: Boolean): Camera {
            val previewBuilder = Preview.Builder()
            if (useStabilization) previewBuilder.setPreviewStabilizationEnabled(true)
            val preview = previewBuilder.build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val cases = mutableListOf<UseCase>(preview)
            var analysis: ImageAnalysis? = null
            if (uiState.edgeMode) {
                analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(
                    analysisExecutor,
                    EdgeAnalyzer { bmp ->
                        // 組み直し前の世代のフレームは表示しない。
                        if (edgeGeneration.get() == myGeneration) edgeBitmap = bmp
                    },
                )
                cases.add(analysis)
            }
            val bound = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                *cases.toTypedArray(),
            )
            previewUseCase = preview
            analysisUseCase = analysis
            boundUseCases = cases
            return bound
        }

        camera = try {
            buildAndBind(uiState.stabilizationEnabled && stabSupported)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 手ブレ補正つきの構成が通らない端末では、補正なしで1回だけ組み直す。
            if (uiState.stabilizationEnabled && stabSupported) {
                try {
                    buildAndBind(false)
                } catch (e2: CancellationException) {
                    throw e2
                } catch (e2: Exception) {
                    bindFailed = true
                    null
                }
            } else {
                bindFailed = true
                null
            }
        }
        val boundCamera = camera
        if (boundCamera != null && savedRatio != null) {
            val state = boundCamera.cameraInfo.zoomState.value
            if (state != null) {
                boundCamera.cameraControl.setZoomRatio(
                    MirrorViewModel.clampZoomRatio(
                        savedRatio, state.minZoomRatio, state.maxZoomRatio
                    )
                )
            }
        }
    }

    // 回転に合わせて、プレビューと解析の目標回転を更新する。
    LaunchedEffect(displayRotation, previewUseCase, analysisUseCase) {
        previewUseCase?.targetRotation = displayRotation
        analysisUseCase?.targetRotation = displayRotation
    }

    // ズーム状態とカメラエラーの観測。
    DisposableEffect(camera) {
        val info = camera?.cameraInfo
        val zoomObserver = Observer<ZoomState> { state ->
            if (state != null) {
                if (!sliderDragging) linearZoom = state.linearZoom
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

    Box(modifier = Modifier.fillMaxSize()) {
        // プレビューは常に置いたままにし、エッジ画像を上に重ねる
        // （表示先の Surface を消さないため）。
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        if (uiState.edgeMode && edgeBitmap != null) {
            EdgeImage(bitmap = edgeBitmap!!)
        }

        // ジェスチャーは両モード共通の透明な層で受ける。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { viewModel.togglePanel() }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomFactor, _ ->
                        val cam = camera ?: return@detectTransformGestures
                        val state = cam.cameraInfo.zoomState.value
                            ?: return@detectTransformGestures
                        cam.cameraControl.setZoomRatio(
                            MirrorViewModel.clampZoomRatio(
                                state.zoomRatio * zoomFactor,
                                state.minZoomRatio,
                                state.maxZoomRatio,
                            )
                        )
                    }
                },
        )

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

        if (bindFailed) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "カメラを起動できませんでした",
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Button(onClick = {
                    bindFailed = false
                    retryKey++
                }) { Text("もう一度試す") }
            }
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
                            sliderDragging = true
                            linearZoom = value
                            camera?.cameraControl?.setLinearZoom(value)
                        },
                        onValueChangeFinished = { sliderDragging = false },
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
