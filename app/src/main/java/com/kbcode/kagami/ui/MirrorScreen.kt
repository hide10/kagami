package com.kbcode.kagami.ui

import android.app.Activity
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kbcode.kagami.R
import com.kbcode.kagami.camera.CameraCrop
import com.kbcode.kagami.camera.CameraFocus
import com.kbcode.kagami.camera.FaceAnalyzer
import com.kbcode.kagami.camera.runFrontCamera
import com.kbcode.kagami.core.ColorAdjust
import com.kbcode.kagami.core.Crop
import com.kbcode.kagami.core.CropPlanner
import com.kbcode.kagami.core.CropSmoother
import com.kbcode.kagami.core.FacePart
import com.kbcode.kagami.core.Finger
import com.kbcode.kagami.core.Gesture
import com.kbcode.kagami.core.ImageMapping
import com.kbcode.kagami.core.LightMode
import com.kbcode.kagami.core.MirrorState
import com.kbcode.kagami.core.Panel
import com.kbcode.kagami.core.Pt
import com.kbcode.kagami.core.ScreenLight
import com.kbcode.kagami.core.SensorMapping
import com.kbcode.kagami.core.SensorRect
import com.kbcode.kagami.core.SensorSpec
import com.kbcode.kagami.core.Size
import com.kbcode.kagami.core.TouchReader
import com.kbcode.kagami.core.TouchRules
import com.kbcode.kagami.core.ZoomStrip
import kotlin.math.roundToInt

@Composable
fun MirrorScreen(vm: MirrorViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val camera = remember { CameraCrop() }
    val faces = remember { FaceAnalyzer(camera) }
    DisposableEffect(faces) { onDispose { faces.close() } }
    val focus = remember { CameraFocus() }
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

    var help by remember { mutableStateOf(false) }
    if (help) HelpDialog { help = false }

    val haptics = LocalHapticFeedback.current
    val light = state.light
    if (light.on) FullBrightness()
    if (state.fullScreen) HideSystemBars()
    val cardScale by animateFloatAsState(state.previewScale, label = "card")
    val glow = light.color().let { Color(it.r, it.g, it.b) }
    // 周りが見えるのは縮小のときだけだが、全面の出入りでカードが伸び縮みするあいだも光って見えるよう、点いていれば光の色を敷く。
    BoxWithConstraints(Modifier.fillMaxSize().background(if (light.on) glow else Color.Black)) {
        val size = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        // crop は見せている切り出しで、唯一の正。カメラはその上限までを切り出し、layer は実際に切り出された一枚に残りの拡大をかける。
        var crop by remember(size) { mutableStateOf(Crop.full(size)) }
        var layer by remember(size) { mutableStateOf(Crop.full(size)) }
        val frame = remember(size) { CameraFrame(size) }
        var focused by remember(size) { mutableStateOf<FocusMark?>(null) }
        // 2 本指で横へ動かし始めたときの露出。動かした量はいつもここから測るので、1/3 段に丸めても小さな動きが消えない。
        var exposureFrom by remember(size) { mutableFloatStateOf(0f) }
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
        fun toggleFreeze() {
            if (still != null) {
                goLive()
            } else {
                preview.bitmap?.let {
                    still = it.asImageBitmap()
                    faces.cancel()
                    vm.freeze(frame.applied, size)
                }
            }
        }
        fun onGesture(g: Gesture) {
            val s = vm.state.value
            if (!s.allows(g)) return
            when (g) {
                is Gesture.Tap -> {
                    // 寄るのと同じ点にピントと露出を合わせる。止めた一枚はカメラの今の映像ではないので合わせない。
                    if (!s.frozen) {
                        val seen = crop.toSource(g.at, size)
                        if (focus.at(seen, frame.mapping, crop.zoom)) focused = FocusMark(seen)
                    }
                    vm.moveTo(CropPlanner.tap(g.at, crop, size, s.extent(size)))
                }
                // 1 回目のタップはもう寄ってピントも合わせたので、2 回目はライトだけを切り替え、もう一度は寄らない。
                Gesture.DoubleTap -> vm.toggleLight()
                Gesture.LongPress -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    toggleFreeze()
                }
                Gesture.TwoFingerTap -> vm.toggleFullScreen()
                is Gesture.Move -> {
                    crop = CropPlanner.transform(crop, g.centroid, g.pan, g.zoom, size, s.extent(size))
                    vm.moveTo(crop)
                }
                // 右端のスライダーと同じく、上へ動かすと寄り、画面の高さいっぱいで 1 倍から最大まで動く。
                is Gesture.ZoomDrag -> {
                    crop = ZoomStrip.drag(crop, g.dy, size.h, size, s.extent(size))
                    vm.moveTo(crop)
                }
                is Gesture.ExposureDrag -> {
                    if (g.starts) exposureFrom = s.color.exposureEv
                    vm.adjustColor { it.withExposureDragged(exposureFrom, g.dx, size.w) }
                }
            }
        }
        // 縮小のライトでは、この入れ物ごと角の丸いカードに縮めて周りを光らせる。graphicsLayer はレイアウトの大きさを変えず、
        // 中の指の座標も縮める前のこの入れ物の座標で届くので、切り出しとジェスチャーの計算はそのまま使える。
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer {
                    scaleX = cardScale
                    scaleY = cardScale
                    shape = if (cardScale < 1f) CARD else RectangleShape
                    clip = true
                }
                .background(Color.Black),
        ) {
            CameraPreview(preview, state.color, faces, camera, focus, zoomed)
            // getBitmap() はミラーと切り抜きを済ませた、プレビューと同じ大きさの一枚で、カメラが切り出した範囲だけが写っている。
            // その範囲を still として覚え、残りの拡大と色をかければ止めた瞬間と同じに見える。
            still?.let { Image(it, null, zoomed, contentScale = ContentScale.FillBounds, colorFilter = state.color.filter()) }
            FocusRing(focused, { crop }, size)
            Box(Modifier.fillMaxSize().pointerInput(size) { readTouches(::onGesture) })
        }
        if (state.glowing == LightMode.BAND) LightBand(light, glow)
        if (!state.fullScreen) Column(Modifier.fillMaxSize()) {
            FunctionRow(state, vm, helping = help, onFreeze = ::toggleFreeze, onHelp = { help = true })
            // 帯は縮めるカードの外に置くので、ライトの光らせ方によらず画面の右端にある。戻るジェスチャーの幅だけ内側へ寄せる。
            val edge = WindowInsets.safeDrawing.union(WindowInsets.systemGestures).only(WindowInsetsSides.Horizontal)
            Box(Modifier.weight(1f).fillMaxWidth().windowInsetsPadding(edge)) {
                ZoomTrack(
                    zoom = { crop.zoom },
                    onDrag = { dy, length ->
                        crop = ZoomStrip.drag(crop, dy, length, size, vm.state.value.extent(size))
                        vm.moveTo(crop)
                    },
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(TRACK_WIDTH),
                )
            }
            PartBar(state, vm) { part ->
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
            }
        }
    }
}

private val CARD = RoundedCornerShape(24.dp)

/** 帯の幅で、指で押せる範囲。48dp より広くとる。 */
private val TRACK_WIDTH = 56.dp

private val THUMB_RADIUS = 18.dp

/** 帯の両端の余白。つまみが端で欠けないだけ空ける。 */
private val TRACK_INSET = THUMB_RADIUS + 2.dp

/** つまみの「+」と縁取り。光っている周りの上でも白いつまみの形が見える濃さ。 */
private val THUMB_INK = Color(0xFF424242)

/**
 * 右端の寄り引きのスライダー。細い線の上の「+」のつまみが今の倍率を対数で示し、寄っているあいだは横に倍率を出す。
 * つまみを掴んでも帯のどこをなぞっても、指が動いた分だけ寄り引きするので、つまみは指についてくる。
 * 帯で始まった指はここで受け止めるので、プレビューを動かしもタップで寄りもしない。
 */
@Composable
private fun ZoomTrack(zoom: () -> Float, onDrag: (dy: Float, length: Float) -> Unit, modifier: Modifier) {
    val drag by rememberUpdatedState(onDrag)
    val text = rememberTextMeasurer()
    val description = stringResource(R.string.zoom_track)
    Box(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dy ->
                    change.consume()
                    drag(dy, size.height - 2 * TRACK_INSET.toPx())
                }
            }
            .drawBehind {
                val x = size.width / 2
                val top = TRACK_INSET.toPx()
                val bottom = size.height - top
                // 暗い縁取りの上に明るい線を引くので、プレビューの上でも光った周りの上でも見える。
                drawLine(Color.Black.copy(alpha = 0.3f), Offset(x, top), Offset(x, bottom), 4.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.5f), Offset(x, top), Offset(x, bottom), 2.dp.toPx(), StrokeCap.Round)
                val z = zoom()
                val thumb = Offset(x, bottom - ZoomStrip.thumb(z) * (bottom - top))
                val r = THUMB_RADIUS.toPx()
                drawCircle(THUMB_INK.copy(alpha = 0.5f), r + 1.5.dp.toPx(), thumb)
                drawCircle(Color.White.copy(alpha = 0.92f), r, thumb)
                val arm = Offset(r * 0.45f, 0f)
                val stroke = 2.5.dp.toPx()
                drawLine(THUMB_INK, thumb - arm, thumb + arm, stroke, StrokeCap.Round)
                drawLine(THUMB_INK, thumb - Offset(0f, arm.x), thumb + Offset(0f, arm.x), stroke, StrokeCap.Round)
                ZoomStrip.label(z)?.let {
                    val label = text.measure(it, ZOOM_LABEL)
                    val at = Offset(x - r - 6.dp.toPx() - label.size.width, thumb.y - label.size.height / 2f)
                    drawText(label, topLeft = at)
                }
            },
    )
}

/** 倍率の文字。プレビューの明るいところでも読めるよう、薄い影をつける。 */
private val ZOOM_LABEL = TextStyle(
    color = Color.White.copy(alpha = 0.85f),
    fontSize = 12.sp,
    shadow = Shadow(Color.Black.copy(alpha = 0.6f), blurRadius = 4f),
)

/** ライトが点いているあいだは画面の明るさを最大にし、消えたら端末の設定に戻す。光らせ方を変えても続く。 */
@Composable
private fun FullBrightness() {
    val window = (LocalContext.current as Activity).window
    DisposableEffect(window) {
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        onDispose {
            window.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
}

/** 全面のあいだはステータスバーとナビゲーションバーを隠し、端からなぞったときだけ一時的に出す。抜ければ戻す。 */
@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    val bars = remember(view) { WindowCompat.getInsetsController((view.context as Activity).window, view) }
    fun hide() {
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(bars) {
        hide()
        onDispose { bars.show(WindowInsetsCompat.Type.systemBars()) }
    }
    // 古い端末はほかのアプリへ移るとバーを出し直すので、戻ってきたら隠し直す。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { hide() }
}

/** ダブルタップの 2 回目が 1 回目からずれてよい距離。Android の ViewConfiguration の doubleTapSlop と同じ。 */
private val DOUBLE_TAP_SLOP = 100.dp

/** 届いた指を TouchReader で読み分けて onGesture へ渡す。長押しは次の動きを待たず、決まる時刻に渡す。 */
private suspend fun PointerInputScope.readTouches(onGesture: (Gesture) -> Unit) {
    val reader = TouchReader(
        TouchRules(
            slop = viewConfiguration.touchSlop,
            longPressMs = viewConfiguration.longPressTimeoutMillis,
            doubleTapMs = viewConfiguration.doubleTapTimeoutMillis,
            doubleTapSlop = DOUBLE_TAP_SLOP.toPx(),
        ),
    )
    awaitPointerEventScope {
        while (true) {
            val wait = reader.deadline?.minus(SystemClock.uptimeMillis())
            val event = when {
                wait == null -> awaitPointerEvent()
                wait > 0 -> withTimeoutOrNull(wait) { awaitPointerEvent() }
                else -> null
            }
            val g = if (event == null) {
                reader.tick(SystemClock.uptimeMillis())
            } else {
                val time = event.changes.firstOrNull()?.uptimeMillis ?: continue
                reader.read(time, event.changes.map { Finger(it.id.value, Pt(it.position.x, it.position.y), it.pressed) })
            }
            g?.let(onGesture)
        }
    }
}

/** 画面のふちを光らせて顔を照らす。帯は外側が塗りつぶしで、内側へ向かって帯の太さちょうどで消える。 */
@Composable
private fun LightBand(light: ScreenLight, glow: Color) {
    val fade = listOf(glow, glow.copy(alpha = 0f))
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

/** ピントを合わせた点(画像全体を倍率 1 で見たビュー座標)。タップのたびに作り直すので、同じ点をもう一度タップしても印が出直す。 */
private class FocusMark(val at: Pt)

/** 合わせた点に二重の輪を出し、FOCUS_FADE_MS で消す。輪は映像と一緒に動くので、その点へ寄っていくあいだも合わせた所に重なる。 */
@Composable
private fun FocusRing(mark: FocusMark?, crop: () -> Crop, view: Size) {
    val fade = remember { Animatable(0f) }
    LaunchedEffect(mark) {
        if (mark == null) return@LaunchedEffect
        fade.snapTo(1f)
        fade.animateTo(0f, tween(FOCUS_FADE_MS, easing = LinearEasing))
    }
    Canvas(Modifier.fillMaxSize()) {
        val a = fade.value
        if (mark == null || a == 0f) return@Canvas
        val c = crop().toScreen(mark.at, view).let { Offset(it.x, it.y) }
        // 外の輪は消えながら少し縮む。暗い縁取りの上に白で描くので、明るい肌の上でも見える。
        for (r in listOf(36.dp.toPx() * (1f + 0.2f * a), 12.dp.toPx())) {
            drawCircle(Color.Black.copy(alpha = 0.3f * a), r, c, style = Stroke(3.dp.toPx()))
            drawCircle(Color.White.copy(alpha = a), r, c, style = Stroke(1.5.dp.toPx()))
        }
    }
}

private const val FOCUS_FADE_MS = 800

@Composable
private fun CameraPreview(view: PreviewView, color: ColorAdjust, analyzer: ImageAnalysis.Analyzer, crop: CameraCrop, focus: CameraFocus, modifier: Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var noCamera by remember { mutableStateOf(false) }
    LaunchedEffect(view, analyzer, crop, focus) {
        runFrontCamera(context, owner, view, analyzer, crop, focus)
        noCamera = true
    }
    if (noCamera) {
        Box(modifier, Alignment.Center) { Text(stringResource(R.string.no_front_camera), color = Color.White) }
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
    FacePart.LEFT_EYE to R.string.part_left_eye,
    FacePart.RIGHT_EYE to R.string.part_right_eye,
    FacePart.NOSE to R.string.part_nose,
    FacePart.MOUTH to R.string.part_mouth,
)

/** アイコンの下に敷く暗い下地。光っている画面の上でも白いアイコンが読める濃さ。 */
private val SCRIM = Color.Black.copy(alpha = 0.55f)

/** 機能のアイコン。ステータスバーと切り欠きの下に、右へ寄せて並べる。 */
@Composable
private fun FunctionRow(state: MirrorState, vm: MirrorViewModel, helping: Boolean, onFreeze: () -> Unit, onHelp: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(8.dp),
        Arrangement.End,
    ) {
        // Surface は自分の上のタップを下のプレビューへ通さない。外側のタップはそのまま通る。
        Surface(shape = CircleShape, color = SCRIM, contentColor = Color.White) {
            Row {
                GlyphButton(R.string.button_light, selected = state.light.on, onLongClick = { vm.toggle(Panel.LIGHT) }, onClick = vm::toggleLight) {
                    bulbGlyph(state.light.on)
                }
                GlyphButton(R.string.button_pause, selected = state.frozen, onClick = onFreeze) { pauseGlyph(state.frozen) }
                val coloring = state.panel == Panel.COLOR
                GlyphButton(R.string.button_color, selected = coloring, onClick = { vm.toggle(Panel.COLOR) }) { slidersGlyph(coloring) }
                GlyphButton(R.string.button_info, selected = helping, onClick = onHelp) { infoGlyph(helping) }
            }
        }
    }
}

/** 部位のアイコンと、その上に開くパネル。 */
@Composable
private fun PartBar(state: MirrorState, vm: MirrorViewModel, onPart: (FacePart) -> Unit) {
    Surface(Modifier.fillMaxWidth(), color = SCRIM, contentColor = Color.White) {
        Column(Modifier.navigationBarsPadding().padding(vertical = 4.dp)) {
            when (state.panel) {
                Panel.COLOR -> ColorPanel(state.color, vm)
                Panel.LIGHT -> LightPanel(state.light, vm)
                null -> Unit
            }
            Row(Modifier.fillMaxWidth()) {
                val cell = Modifier.weight(1f)
                val whole = state.target == null && state.pendingPart == null
                GlyphButton(R.string.part_all, cell, selected = whole, onClick = vm::showAll) { partGlyph(null) }
                for ((part, name) in PARTS) {
                    GlyphButton(name, cell, selected = state.pendingPart == part, enabled = !state.frozen, onClick = { onPart(part) }) {
                        partGlyph(part)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorPanel(color: ColorAdjust, vm: MirrorViewModel) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SliderRow(R.string.exposure, stringResource(R.string.exposure_value, color.exposureEv), color.exposureEv, ColorAdjust.EV) { v ->
            vm.adjustColor { it.withExposure(v) }
        }
        SliderRow(R.string.color_temperature, kelvinLabel(color.kelvin), color.kelvin, ColorAdjust.KELVIN) { v ->
            vm.adjustColor { it.withKelvin(v) }
        }
        Box(Modifier.fillMaxWidth(), Alignment.CenterEnd) {
            TextButton({ vm.adjustColor { ColorAdjust() } }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                Text(stringResource(R.string.reset), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun LightPanel(light: ScreenLight, vm: MirrorViewModel) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.light_mode), Modifier.width(LABEL_WIDTH))
            SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                LIGHT_MODES.forEachIndexed { i, (mode, name) ->
                    SegmentedButton(
                        selected = light.mode == mode,
                        onClick = { vm.adjustLight { it.copy(mode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(i, LIGHT_MODES.size),
                    ) { Text(stringResource(name)) }
                }
            }
        }
        val percent = (light.thicknessDp / ScreenLight.THICKNESS_DP.endInclusive * 100).roundToInt()
        SliderRow(R.string.light_amount, stringResource(R.string.percent_value, percent), light.thicknessDp, ScreenLight.THICKNESS_DP) { v ->
            vm.adjustLight { it.withThickness(v) }
        }
        SliderRow(R.string.color_temperature, kelvinLabel(light.kelvin), light.kelvin, ScreenLight.KELVIN) { v ->
            vm.adjustLight { it.copy(kelvin = v) }
        }
    }
}

private val LIGHT_MODES = listOf(LightMode.BAND to R.string.light_mode_band, LightMode.SHRINK to R.string.light_mode_shrink)

@Composable
private fun kelvinLabel(kelvin: Float) = stringResource(R.string.kelvin_value, (kelvin / 100f).roundToInt() * 100)

/** パネルの項目名の幅。英語のいちばん長い「Exposure」が 1 行に収まる。 */
private val LABEL_WIDTH = 72.dp

@Composable
private fun SliderRow(
    @StringRes label: Int,
    shown: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(label), Modifier.width(LABEL_WIDTH))
        Slider(value, onChange, Modifier.weight(1f), valueRange = range)
        Text(shown, Modifier.width(72.dp), textAlign = TextAlign.End)
    }
}

/** 絵だけのボタン。押せる範囲は 48dp 以上で、選んでいるあいだは下地を明るくする。長押しには手応えを返す。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlyphButton(
    @StringRes description: Int,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    glyph: DrawScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val name = stringResource(description)
    Box(
        modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(CircleShape)
            .background(if (selected) Color.White.copy(alpha = 0.25f) else Color.Transparent)
            .semantics {
                contentDescription = name
                this.selected = selected
            }
            .combinedClickable(
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick?.let { long ->
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        long()
                    }
                },
                onClick = onClick,
            ),
        Alignment.Center,
    ) { Canvas(Modifier.size(28.dp).alpha(if (enabled) 1f else 0.38f), onDraw = glyph) }
}
