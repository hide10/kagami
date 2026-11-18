package com.hide10.kagami.ui

import android.app.Activity
import android.graphics.Paint
import android.view.View
import android.view.WindowManager
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hide10.kagami.camera.CameraCrop
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
import com.hide10.kagami.core.SensorMapping
import com.hide10.kagami.core.SensorRect
import com.hide10.kagami.core.SensorSpec
import com.hide10.kagami.core.Size
import kotlin.math.roundToInt

@Composable
fun MirrorScreen(vm: MirrorViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val camera = remember { CameraCrop() }
    val faces = remember { FaceAnalyzer(camera) }
    DisposableEffect(faces) { onDispose { faces.close() } }
    val context = LocalContext.current
    val preview = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }

    // 押した部位ボタンの通し番号。最後に押したものの結果だけを使う。
    var presses by remember { mutableIntStateOf(0) }

    // 止めた一枚。メモリにだけ置き、保存も共有もしない。カメラはつないだままなので、ライブへはすぐ戻れる。
    var still by remember { mutableStateOf<ImageBitmap?>(null) }
    fun goLive() {
        still = null
        vm.unfreeze()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { goLive() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val size = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        // crop は見せている切り出しで、唯一の正。カメラはその上限までを切り出し、layer は実際に切り出された一枚に残りの拡大をかける。
        var crop by remember(size) { mutableStateOf(Crop.full(size)) }
        var layer by remember(size) { mutableStateOf(Crop.full(size)) }
        val frame = remember(size) { CameraFrame(size) }
        LaunchedEffect(size) {
            // 切り出しはこの大きさのビュー座標なので、大きさが変わったら全体から始める。
            goLive()
            vm.showAll()
            var asked: Crop? = null
            var askedWith: SensorMapping? = null
            var last = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                val s = vm.state.value
                crop = CropSmoother.step(crop, s.target ?: s.extent(size), (now - last) / 1e9f)
                last = now
                frame.update(camera)
                // 頼んだ範囲が届くのは数フレーム後で、端末が丸めたり無視したりもするので、残りは実際に切り出された範囲から決める。
                // 寄りすぎた一枚(引くときの遅れ)では画像の外を見せないよう、その一枚の全体で止める。
                layer = crop.within(s.still ?: frame.applied, size).clamp(size)
                val mapping = frame.mapping
                if (mapping != null && !s.frozen) {
                    // 部位の顔を待つあいだは全体を頼む。見え方は layer がそのまま保つ。
                    val ask = if (s.pendingPart != null) Crop.full(size) else crop
                    if (ask != asked || mapping !== askedWith) {
                        asked = ask
                        askedWith = mapping
                        camera.requested.value = mapping.toSensor(mapping.cameraPart(ask))
                    }
                }
            }
        }

        val zoomed = Modifier.fillMaxSize().graphicsLayer {
            val t = layer.layerTransform(size)
            scaleX = t.scale
            scaleY = t.scale
            translationX = t.translationX
            translationY = t.translationY
        }
        CameraPreview(preview, state.color, faces, camera, zoomed)
        // getBitmap() はミラーと切り抜きを済ませた、プレビューと同じ大きさの一枚で、カメラが切り出した範囲だけが写っている。
        // その範囲を still として覚え、残りの拡大と色をかければ止めた瞬間と同じに見える。
        still?.let { Image(it, null, zoomed, contentScale = ContentScale.FillBounds, colorFilter = state.color.filter()) }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(size) { detectTapGestures { vm.moveTo(CropPlanner.tap(Pt(it.x, it.y), crop, size, vm.state.value.extent(size))) } }
                .pointerInput(size) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        crop = CropPlanner.transform(crop, Pt(centroid.x, centroid.y), Pt(pan.x, pan.y), zoom, size, vm.state.value.extent(size))
                        vm.moveTo(crop)
                    }
                },
        )
        if (state.light.on) LightFrame(state.light)
        Controls(
            state,
            vm,
            onPart = { part ->
                if (vm.request(part)) {
                    val press = ++presses
                    val mapping = frame.mapping
                    faces.requestFace { found ->
                        if (press != presses) return@requestFace
                        if (found == null) {
                            vm.partMissed()
                        } else {
                            val shot = found.crop?.let { mapping?.toView(it) } ?: Crop.full(size)
                            vm.faceFound(ImageMapping(found.image, size, mirror = true, shot).toView(found.face), size)
                        }
                    }
                }
            },
            onFreeze = {
                if (still != null) {
                    goLive()
                } else {
                    preview.bitmap?.let {
                        still = it.asImageBitmap()
                        faces.cancel()
                        vm.freeze(frame.applied, size)
                    }
                }
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
private fun CameraPreview(view: PreviewView, color: ColorAdjust, analyzer: ImageAnalysis.Analyzer, crop: CameraCrop, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var noCamera by remember { mutableStateOf(false) }
    LaunchedEffect(view, analyzer, crop) {
        runFrontCamera(context, owner, view, analyzer, crop)
        noCamera = true
    }
    if (noCamera) {
        Box(modifier, Alignment.Center) { Text("前面カメラが見つかりません", color = Color.White) }
    } else {
        AndroidView({ view }, modifier) { it.tint(color) }
    }
}

/** この画面の大きさで見たカメラ側の切り出し。毎フレーム update し、ボタンからも読む。 */
private class CameraFrame(private val size: Size) {
    private var spec: SensorSpec? = null
    private var appliedRect: SensorRect? = null

    /** カメラが切り出せないあいだは null。 */
    var mapping: SensorMapping? = null
        private set

    /** いちばん新しい一枚にカメラが切り出していた範囲(ビュー座標)。カメラが切り出さないなら全体。 */
    var applied = Crop.full(size)
        private set

    fun update(camera: CameraCrop) {
        val s = camera.spec
        if (s != spec) {
            spec = s
            mapping = s?.let { SensorMapping(it, size) }
            appliedRect = null
        }
        val r = camera.latest
        if (r !== appliedRect) {
            appliedRect = r
            applied = r?.let { mapping?.toView(it) } ?: Crop.full(size)
        }
    }
}

/** graphicsLayer には色フィルタがなく、RenderEffect の色フィルタは API 31 からなので、View のレイヤーで色をかける。 */
private fun PreviewView.tint(color: ColorAdjust) {
    val filter = color.filter()
    if (filter == null) {
        setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        setLayerType(View.LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = filter.asAndroidColorFilter() })
    }
}

/** プレビューにも止めた一枚にも同じものをかける。無加工なら null。 */
private fun ColorAdjust.filter() = if (isNeutral) null else ColorFilter.colorMatrix(ColorMatrix(matrix()))

private val PARTS = listOf(
    FacePart.LEFT_EYE to "左目",
    FacePart.RIGHT_EYE to "右目",
    FacePart.NOSE to "鼻",
    FacePart.MOUTH to "口",
)

@Composable
private fun Controls(
    state: MirrorState,
    vm: MirrorViewModel,
    onPart: (FacePart) -> Unit,
    onFreeze: () -> Unit,
    modifier: Modifier,
) {
    // Surface は自分の上のタップを下のプレビューへ通さない。外側のタップはそのまま通る。
    Surface(modifier.fillMaxWidth(), color = Color.Black.copy(alpha = 0.35f), contentColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(vertical = 4.dp)) {
            when (state.panel) {
                Panel.COLOR -> ColorPanel(state.color, vm)
                Panel.LIGHT -> LightPanel(state.light, vm)
                null -> Unit
            }
            // 幅を等分する。360dp で 8 個なら 1 個 45dp で、いちばん長い「ライト」(14sp で 42dp)も 1 行に収まる。
            Row(Modifier.fillMaxWidth()) {
                val cell = Modifier.weight(1f)
                BarButton("全体", cell) { vm.showAll() }
                for ((part, label) in PARTS) {
                    BarButton(label, cell, enabled = !state.frozen) { onPart(part) }
                }
                BarButton("静止", cell, state.frozen, onClick = onFreeze)
                BarButton("色", cell, state.panel == Panel.COLOR) { vm.toggle(Panel.COLOR) }
                BarButton("ライト", cell, state.light.on, onLongClick = { vm.toggle(Panel.LIGHT) }) { vm.toggleLight() }
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
        val percent = (light.thicknessDp / ScreenLight.THICKNESS_DP.endInclusive * 100).roundToInt()
        SliderRow("光の量", "$percent%", light.thicknessDp, ScreenLight.THICKNESS_DP) { v ->
            vm.adjustLight { it.withThickness(v) }
        }
        SliderRow("色温度", kelvinLabel(light.kelvin), light.kelvin, ScreenLight.KELVIN) { v ->
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
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp))
        Slider(value, onChange, Modifier.weight(1f), valueRange = range)
        Text(shown, Modifier.width(80.dp), textAlign = TextAlign.End)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarButton(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .combinedClickable(enabled = enabled, onLongClick = onLongClick, onClick = onClick),
        Alignment.Center,
    ) { Text(label, color = Color.White.copy(alpha = if (enabled) 1f else 0.38f), fontSize = 14.sp) }
}
