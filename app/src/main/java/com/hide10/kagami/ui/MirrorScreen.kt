package com.hide10.kagami.ui

import android.app.Activity
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import android.view.WindowManager
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
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
import com.hide10.kagami.camera.runFrontCamera
import com.hide10.kagami.core.ColorAdjust
import com.hide10.kagami.core.Crop
import com.hide10.kagami.core.CropPlanner
import com.hide10.kagami.core.CropSmoother
import com.hide10.kagami.core.FaceHold
import com.hide10.kagami.core.FaceLandmarks
import com.hide10.kagami.core.FacePart
import com.hide10.kagami.core.ImageMapping
import com.hide10.kagami.core.MirrorState
import com.hide10.kagami.core.Panel
import com.hide10.kagami.core.Pt
import com.hide10.kagami.core.ScreenLight
import com.hide10.kagami.core.Size
import com.hide10.kagami.core.ViewIntent
import kotlin.math.roundToInt

@Composable
fun MirrorScreen(vm: MirrorViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val hold = remember { FaceHold() }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val size = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        var crop by remember(size) { mutableStateOf(Crop.full(size)) }
        LaunchedEffect(size) {
            var last = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                val target = CropPlanner.target(vm.state.value.intent, hold.get(now), size)
                crop = CropSmoother.step(crop, target, (now - last) / 1e9f)
                last = now
            }
        }

        CameraPreview(
            color = state.color,
            onFace = { face, image -> hold.seen(ImageMapping(image, size, mirror = true).toView(face), System.nanoTime()) },
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
                .pointerInput(size) { detectTapGestures { vm.show(CropPlanner.tap(Pt(it.x, it.y), crop, size)) } }
                .pointerInput(size) {
                    detectTransformGestures { _, _, zoom, _ ->
                        if (zoom != 1f) vm.pinch(zoom, hold.get(System.nanoTime()), crop, size)
                    }
                },
        )
        if (state.light.on) LightFrame(state.light)
        Controls(state, vm, Modifier.align(Alignment.BottomCenter))
    }
}

/** 画面のふちを光らせて顔を照らす。出ているあいだは画面の明るさを最大にし、消えたら端末の設定に戻す。 */
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
    Box(
        Modifier.fillMaxSize().drawBehind {
            val width = 72.dp.toPx()
            inset(width / 2) { drawRect(Color(c.r, c.g, c.b), style = Stroke(width)) }
        },
    )
}

@Composable
private fun CameraPreview(color: ColorAdjust, onFace: (FaceLandmarks, Size) -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val latestOnFace by rememberUpdatedState(onFace)
    var noCamera by remember { mutableStateOf(false) }
    LaunchedEffect(view) {
        runFrontCamera(context, owner, view) { face, image -> latestOnFace(face, image) }
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
private fun Controls(state: MirrorState, vm: MirrorViewModel, modifier: Modifier) {
    // Surface は自分の上のタップを下のプレビューへ通さない。外側のタップはそのまま通る。
    Surface(modifier.fillMaxWidth(), color = Color.Black.copy(alpha = 0.35f), contentColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(vertical = 4.dp)) {
            when (state.panel) {
                Panel.COLOR -> ColorPanel(state.color, vm)
                Panel.LIGHT -> LightPanel(state.light, vm)
                null -> Unit
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                BarButton("顔", state.intent is ViewIntent.FaceLock) { vm.show(ViewIntent.FaceLock()) }
                for ((part, label) in PARTS) {
                    BarButton(label, (state.intent as? ViewIntent.Part)?.part == part) { vm.show(ViewIntent.Part(part)) }
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
            BarButton("元に戻す", selected = false) { vm.adjustColor { ColorAdjust() } }
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
        val percent = (light.brightness * 100).roundToInt()
        SliderRow("明るさ", "$percent%", light.brightness, ScreenLight.BRIGHTNESS, enabled = light.on) { v ->
            vm.adjustLight { it.copy(brightness = v) }
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
private fun BarButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        Alignment.Center,
    ) { Text(label, color = Color.White, fontSize = 16.sp) }
}
