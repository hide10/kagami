package com.hide10.kagami.ui

import android.app.Activity
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import android.view.WindowManager
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hide10.kagami.camera.FaceAnalyzer
import com.hide10.kagami.camera.runFrontCamera
import com.hide10.kagami.core.ColorAdjust
import com.hide10.kagami.core.Crop
import com.hide10.kagami.core.CropPlanner
import com.hide10.kagami.core.CropSmoother
import com.hide10.kagami.core.FacePart
import com.hide10.kagami.core.ImageMapping
import com.hide10.kagami.core.MirrorState
import com.hide10.kagami.core.Panel
import com.hide10.kagami.core.Pt
import com.hide10.kagami.core.ScreenLight
import com.hide10.kagami.core.Size
import kotlin.math.roundToInt

@Composable
fun MirrorScreen(vm: MirrorViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val faces = remember { FaceAnalyzer() }
    DisposableEffect(faces) { onDispose { faces.close() } }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val size = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        var crop by remember(size) { mutableStateOf(Crop.full(size)) }
        LaunchedEffect(size) {
            // 切り出しはこの大きさのビュー座標なので、大きさが変わったら全体から始める。
            vm.showAll()
            var last = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                crop = CropSmoother.step(crop, vm.state.value.target ?: Crop.full(size), (now - last) / 1e9f)
                last = now
            }
        }

        CameraPreview(
            color = state.color,
            analyzer = faces,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val t = crop.layerTransform(size)
                scaleX = t.scale
                scaleY = t.scale
                translationX = t.translationX
                translationY = t.translationY
            },
        )
        Box(
            Modifier.fillMaxSize()
                .pointerInput(size) { detectTapGestures { vm.moveTo(CropPlanner.tap(Pt(it.x, it.y), crop, size)) } }
                .pointerInput(size) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        crop = CropPlanner.transform(crop, Pt(centroid.x, centroid.y), Pt(pan.x, pan.y), zoom, size)
                        vm.moveTo(crop)
                    }
                },
        )
        if (state.light.on) LightFrame(state.light)
        Controls(
            state,
            vm,
            onPart = { part ->
                vm.request(part)
                faces.requestFace { face, image -> vm.faceFound(ImageMapping(image, size, mirror = true).toView(face), size) }
            },
            Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 画面のふちを光らせて顔を照らす。帯は外側が塗りつぶしで、内側へ向かって帯の太さちょうどで消える。出ているあいだは画面の明るさを最大にし、消えたら端末の設定に戻す。 */
@Composable
private fun LightFrame(light: ScreenLight) {
    val window = (LocalContext.current as Activity).window
    DisposableEffect(window) {
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
    val c = light.color()
    val fade = listOf(Color(c.r, c.g, c.b), Color(c.r, c.g, c.b, alpha = 0f))
    Box(
        Modifier.fillMaxSize().drawBehind {
            val t = light.thicknessDp.dp.toPx()
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(fade, 0f, t), size = size.copy(height = t))
            drawRect(Brush.verticalGradient(fade, h, h - t), Offset(0f, h - t), size.copy(height = t))
            drawRect(Brush.horizontalGradient(fade, 0f, t), size = size.copy(width = t))
            drawRect(Brush.horizontalGradient(fade, w, w - t), Offset(w - t, 0f), size.copy(width = t))
        },
    )
}

@Composable
private fun CameraPreview(color: ColorAdjust, analyzer: ImageAnalysis.Analyzer, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    var noCamera by remember { mutableStateOf(false) }
    LaunchedEffect(view, analyzer) {
        runFrontCamera(context, owner, view, analyzer)
        noCamera = true
    }
    if (noCamera) {
        Box(modifier, Alignment.Center) { Text("前面カメラが見つかりません", color = Color.White) }
    } else {
        AndroidView({ view }, modifier) { it.tint(color) }
    }
}

/** graphicsLayer には色フィルタがなく、RenderEffect の色フィルタは API 31 からなので、View のレイヤーで色をかける。 */
private fun PreviewView.tint(color: ColorAdjust) {
    if (color.isNeutral) {
        setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(color.matrix()) })
    }
}

private val PARTS = listOf(
    FacePart.LEFT_EYE to "左目",
    FacePart.RIGHT_EYE to "右目",
    FacePart.NOSE to "鼻",
    FacePart.MOUTH to "口",
)

@Composable
private fun Controls(state: MirrorState, vm: MirrorViewModel, onPart: (FacePart) -> Unit, modifier: Modifier) {
    // Surface は自分の上のタップを下のプレビューへ通さない。外側のタップはそのまま通る。
    Surface(modifier.fillMaxWidth(), color = Color.Black.copy(alpha = 0.35f), contentColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(vertical = 4.dp)) {
            when (state.panel) {
                Panel.COLOR -> ColorPanel(state.color, vm)
                Panel.LIGHT -> LightPanel(state.light, vm)
                null -> Unit
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BarButton("全体") { vm.showAll() }
                for ((part, label) in PARTS) {
                    BarButton(label) { onPart(part) }
                }
                BarButton("色", state.panel == Panel.COLOR) { vm.toggle(Panel.COLOR) }
                BarButton("ライト", state.panel == Panel.LIGHT) { vm.toggle(Panel.LIGHT) }
            }
        }
    }
}

@Composable
private fun ColorPanel(color: ColorAdjust, vm: MirrorViewModel) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SliderRow("明るさ", "%+.1f EV".format(color.exposureEv), color.exposureEv, ColorAdjust.EV) { v ->
            vm.adjustColor { it.withExposure(v) }
        }
        SliderRow("色温度", kelvinLabel(color.kelvin), color.kelvin, ColorAdjust.KELVIN) { v ->
            vm.adjustColor { it.withKelvin(v) }
        }
        Box(Modifier.fillMaxWidth(), Alignment.CenterEnd) {
            BarButton("元に戻す") { vm.adjustColor { ColorAdjust() } }
        }
    }
}

@Composable
private fun LightPanel(light: ScreenLight, vm: MirrorViewModel) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("点灯", Modifier.weight(1f))
            Switch(light.on, { on -> vm.adjustLight { it.copy(on = on) } })
        }
        val percent = (light.thicknessDp / ScreenLight.THICKNESS_DP.endInclusive * 100).roundToInt()
        SliderRow("光の量", "$percent%", light.thicknessDp, ScreenLight.THICKNESS_DP, enabled = light.on) { v ->
            vm.adjustLight { it.withThickness(v) }
        }
        SliderRow("色温度", kelvinLabel(light.kelvin), light.kelvin, ScreenLight.KELVIN, enabled = light.on) { v ->
            vm.adjustLight { it.copy(kelvin = v) }
        }
    }
}

private fun kelvinLabel(kelvin: Float) = "${(kelvin / 100f).roundToInt() * 100}K"

@Composable
private fun SliderRow(
    label: String,
    shown: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean = true,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp))
        Slider(value, onChange, Modifier.weight(1f), enabled = enabled, valueRange = range)
        Text(shown, Modifier.width(80.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun BarButton(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 16.sp) }
}
