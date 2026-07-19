package com.example.aitoui.inhand.count

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.BuildConfig
import com.example.aitoui.counting.CountImage
import com.example.aitoui.counting.PixelRect
import com.example.aitoui.image.ImageStore
import com.example.aitoui.ui.heading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/** Longest edge (px) the capture is downscaled to before counting — fast and threshold-stable. */
private const val ANALYSIS_MAX_DIMENSION = 1200

/** Maximum pinch-zoom on the review image, so dense clusters can be corrected accurately. */
private const val MAX_ZOOM = 5f

/** Fingertip eraser radius (screen dp); its image coverage shrinks as you zoom in. */
private val ERASER_RADIUS = 24.dp

/** Eraser cursor colour — a warm accent distinct from the blue markers. */
private val EraseColor = Color(0xFFFF8A5B)

@Composable
fun CountTabletsRoot(
    onCounted: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: CountTabletsViewModel = viewModel(factory = CountTabletsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CountTabletsScreen(
        state = state,
        onAnalyse = viewModel::analyse,
        onSensitivity = viewModel::setSensitivity,
        onConfirmDetect = viewModel::confirmDetection,
        onTapAt = viewModel::onTapAt,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onBeginCrop = viewModel::beginCrop,
        onCancelCrop = viewModel::cancelCrop,
        onApplyCrop = viewModel::applyCrop,
        onToggleErase = viewModel::toggleErase,
        onEraseStart = viewModel::beginEraseStroke,
        onEraseAt = viewModel::eraseAt,
        onRetake = viewModel::retake,
        onUseCount = { onCounted(state.count) },
        onBack = onBack,
    )
}

@Composable
fun CountTabletsScreen(
    state: CountTabletsState,
    onAnalyse: (String, CountImage) -> Unit,
    onSensitivity: (Float) -> Unit,
    onConfirmDetect: () -> Unit,
    onTapAt: (Float, Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBeginCrop: () -> Unit,
    onCancelCrop: () -> Unit,
    onApplyCrop: (PixelRect) -> Unit,
    onToggleErase: () -> Unit,
    onEraseStart: () -> Unit,
    onEraseAt: (Float, Float, Float) -> Unit,
    onRetake: () -> Unit,
    onUseCount: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    // Re-decode the captured frame from its retained path so the review survives configuration changes.
    val capturedBitmap by produceState<Bitmap?>(null, state.capturePath) {
        val path = state.capturePath
        value = if (path == null) null else {
            withContext(Dispatchers.Default) { ImageStore.decodeUpright(File(path), ANALYSIS_MAX_DIMENSION) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when {
            !hasPermission -> PermissionGate(
                onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onBack = onBack,
            )

            !state.captured -> CameraPreview(
                onCaptured = onAnalyse,
                onBack = onBack,
            )

            capturedBitmap == null -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Color.White) }

            else -> ReviewCapture(
                bitmap = capturedBitmap!!,
                state = state,
                onSensitivity = onSensitivity,
                onConfirmDetect = onConfirmDetect,
                onTapAt = onTapAt,
                onUndo = onUndo,
                onRedo = onRedo,
                onBeginCrop = onBeginCrop,
                onCancelCrop = onCancelCrop,
                onApplyCrop = onApplyCrop,
                onToggleErase = onToggleErase,
                onEraseStart = onEraseStart,
                onEraseAt = onEraseAt,
                onRetake = onRetake,
                onUseCount = onUseCount,
            )
        }
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera permission is needed to count tablets.",
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onGrant, modifier = Modifier.padding(top = 16.dp)) { Text("Grant permission") }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.padding(top = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White),
        ) { Text("Cancel") }
    }
}

@Composable
private fun CameraPreview(
    onCaptured: (String, CountImage) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    var capturing by remember { mutableStateOf(false) }

    // Debug-only: feed a pushed test image (filesDir/test-image.jpg) through the same review flow as a
    // capture, for previewing tap-to-correct on a known dense spread without the live camera.
    fun loadTestImage() {
        if (capturing) return
        capturing = true
        scope.launch {
            val loaded = withContext(Dispatchers.Default) {
                val src = File(context.filesDir, "test-image.jpg")
                if (!src.exists()) return@withContext null
                val copy = ImageStore.newCaptureFile(context)
                src.copyTo(copy, overwrite = true)
                copy.absolutePath to ImageStore.decodeUpright(copy, ANALYSIS_MAX_DIMENSION).toCountImage()
            }
            capturing = false
            loaded?.let { onCaptured(it.first, it.second) }
        }
    }

    androidx.compose.ui.viewinterop.AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageCapture.targetRotation = previewView.display?.rotation ?: 0
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture,
                )
            }, ContextCompat.getMainExecutor(context))
            previewView
        },
    )

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
            }
            if (BuildConfig.DEBUG) {
                TextButton(onClick = { loadTestImage() }, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Use test image", color = Color.White)
                }
            }
        }
        Text(
            text = "Spread the tablets in a single layer on a plain, contrasting surface, then take a photo.",
            color = Color.White,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            // A scrim keeps the white instruction legible over the live preview behind it.
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            IconButton(
                onClick = {
                    if (capturing) return@IconButton
                    capturing = true
                    val file = ImageStore.newCaptureFile(context)
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                scope.launch {
                                    // Keep the file: the review re-decodes it from its path across config changes.
                                    val image = withContext(Dispatchers.Default) {
                                        ImageStore.decodeUpright(file, ANALYSIS_MAX_DIMENSION).toCountImage()
                                    }
                                    capturing = false
                                    onCaptured(file.absolutePath, image)
                                }
                            }

                            override fun onError(exc: ImageCaptureException) {
                                file.delete()
                                capturing = false
                            }
                        },
                    )
                },
                modifier = Modifier
                    .semantics { contentDescription = "Take photo" }
                    .padding(bottom = 32.dp)
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun ReviewCapture(
    bitmap: Bitmap,
    state: CountTabletsState,
    onSensitivity: (Float) -> Unit,
    onConfirmDetect: () -> Unit,
    onTapAt: (Float, Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBeginCrop: () -> Unit,
    onCancelCrop: () -> Unit,
    onApplyCrop: (PixelRect) -> Unit,
    onToggleErase: () -> Unit,
    onEraseStart: () -> Unit,
    onEraseAt: (Float, Float, Float) -> Unit,
    onRetake: () -> Unit,
    onUseCount: () -> Unit,
) {
    if (state.phase == CountPhase.CROP) {
        CropView(
            bitmap = bitmap, imageWidth = state.imageWidth, imageHeight = state.imageHeight,
            initial = state.cropRect, onApply = onApplyCrop, onCancel = onCancelCrop,
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text(
            text = if (state.analysing) "Counting…" else "${state.count} tablets",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .heading(),
            textAlign = TextAlign.Center,
        )

        // The image + markers are shared by both phases; tap/erase edit only in the EDIT phase.
        MarkerImage(
            bitmap = bitmap, state = state, interactive = state.phase == CountPhase.EDIT,
            onTapAt = onTapAt, onEraseStart = onEraseStart, onEraseAt = onEraseAt,
        )

        when (state.phase) {
            CountPhase.DETECT -> {
                Text(
                    text = "Slide to drop stray marks from glare or clutter, then move on.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Sensitivity", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = state.sensitivity,
                        onValueChange = onSensitivity,
                        enabled = !state.analysing,
                        modifier = Modifier.weight(1f).padding(start = 12.dp)
                            .semantics { contentDescription = "Detection sensitivity" },
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White),
                    ) { Text("Retake") }
                    OutlinedButton(
                        onClick = onBeginCrop,
                        enabled = !state.analysing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White),
                    ) { Text(if (state.cropRect == null) "Crop" else "Re-crop") }
                    Button(
                        onClick = onConfirmDetect,
                        enabled = !state.analysing,
                        modifier = Modifier.weight(1f),
                    ) { Text("Next ›") }
                }
            }

            CountPhase.EDIT -> {
                EditToolRow(
                    erasing = state.erasing, onToggleErase = onToggleErase,
                    canUndo = state.canUndo, canRedo = state.canRedo, onUndo = onUndo, onRedo = onRedo,
                )
                Text(
                    text = if (state.erasing) {
                        "Press and drag over stray markers to wipe them. Tap “Erasing” to stop."
                    } else {
                        "Pinch to zoom. Tap a missed tablet to add it, or tap a marker to remove it."
                    },
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onRetake,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White),
                    ) { Text("Retake") }
                    Button(
                        onClick = onUseCount,
                        enabled = !state.analysing,
                        modifier = Modifier.weight(1f),
                    ) { Text("Use ${state.count}") }
                }
            }
        }
    }
}

/** The hand-correction tool row: the Erase toggle on the left, Undo / Redo on the right (each dims off). */
@Composable
private fun EditToolRow(
    erasing: Boolean,
    onToggleErase: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    val colors = ButtonDefaults.textButtonColors(
        contentColor = Color.White,
        disabledContentColor = Color.White.copy(alpha = 0.3f),
    )
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (erasing) {
            Button(
                onClick = onToggleErase,
                colors = ButtonDefaults.buttonColors(containerColor = EraseColor, contentColor = Color(0xFF201206)),
            ) { Text("Erasing") }
        } else {
            OutlinedButton(
                onClick = onToggleErase,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White),
            ) { Text("Erase") }
        }
        Spacer(modifier = Modifier.weight(1f))
        TextButton(onClick = onUndo, enabled = canUndo, colors = colors) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Undo", modifier = Modifier.padding(start = 6.dp))
        }
        TextButton(onClick = onRedo, enabled = canRedo, colors = colors) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("Redo", modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/**
 * Frame a rectangle over the full photo to restrict detection to it — dim outside, four corner handles to
 * resize, and a body drag to move. Applying re-runs detection inside the box (which recomputes the threshold
 * locally — the key to counting tablets on a busy or textured surface).
 */
@Composable
private fun CropView(
    bitmap: Bitmap,
    imageWidth: Int,
    imageHeight: Int,
    initial: PixelRect?,
    onApply: (PixelRect) -> Unit,
    onCancel: () -> Unit,
) {
    val density = LocalDensity.current
    var l by remember { mutableFloatStateOf(initial?.let { it.left.toFloat() / imageWidth } ?: 0.12f) }
    var t by remember { mutableFloatStateOf(initial?.let { it.top.toFloat() / imageHeight } ?: 0.12f) }
    var r by remember { mutableFloatStateOf(initial?.let { it.right.toFloat() / imageWidth } ?: 0.88f) }
    var b by remember { mutableFloatStateOf(initial?.let { it.bottom.toFloat() / imageHeight } ?: 0.88f) }
    val minFrac = 0.06f

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text(
            text = "Crop to the tablets",
            color = Color.White, style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth().heading(), textAlign = TextAlign.Center,
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            BoxWithConstraints {
                val ratio = imageWidth.toFloat() / imageHeight.coerceAtLeast(1)
                val fitByWidth = maxWidth / ratio <= maxHeight
                val vw = if (fitByWidth) maxWidth else maxHeight * ratio
                val vh = if (fitByWidth) maxWidth / ratio else maxHeight
                var boxPx by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier.size(vw, vh).clipToBounds()
                        .onSizeChanged { boxPx = Offset(it.width.toFloat(), it.height.toFloat()) },
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(), contentDescription = "Captured tablets",
                        contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize(),
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cl = l * size.width; val ct = t * size.height; val cr = r * size.width; val cb = b * size.height
                        val dim = Color.Black.copy(alpha = 0.5f)
                        drawRect(dim, Offset(0f, 0f), Size(size.width, ct))
                        drawRect(dim, Offset(0f, cb), Size(size.width, size.height - cb))
                        drawRect(dim, Offset(0f, ct), Size(cl, cb - ct))
                        drawRect(dim, Offset(cr, ct), Size(size.width - cr, cb - ct))
                        drawRect(Color.White, Offset(cl, ct), Size(cr - cl, cb - ct), style = Stroke(width = 2.dp.toPx()))
                    }
                    // Move the whole box.
                    Box(
                        modifier = Modifier
                            .offset { IntOffset((l * boxPx.x).roundToInt(), (t * boxPx.y).roundToInt()) }
                            .size(
                                width = with(density) { ((r - l) * boxPx.x).toDp() },
                                height = with(density) { ((b - t) * boxPx.y).toDp() },
                            )
                            .pointerInput(boxPx) {
                                detectDragGestures { _, d ->
                                    if (boxPx.x <= 0f || boxPx.y <= 0f) return@detectDragGestures
                                    val w = r - l; val h = b - t
                                    val nl = (l + d.x / boxPx.x).coerceIn(0f, 1f - w)
                                    val nt = (t + d.y / boxPx.y).coerceIn(0f, 1f - h)
                                    l = nl; t = nt; r = nl + w; b = nt + h
                                }
                            },
                    )
                    CropHandle(l, t, boxPx) { dx, dy -> l = (l + dx).coerceIn(0f, r - minFrac); t = (t + dy).coerceIn(0f, b - minFrac) }
                    CropHandle(r, t, boxPx) { dx, dy -> r = (r + dx).coerceIn(l + minFrac, 1f); t = (t + dy).coerceIn(0f, b - minFrac) }
                    CropHandle(l, b, boxPx) { dx, dy -> l = (l + dx).coerceIn(0f, r - minFrac); b = (b + dy).coerceIn(t + minFrac, 1f) }
                    CropHandle(r, b, boxPx) { dx, dy -> r = (r + dx).coerceIn(l + minFrac, 1f); b = (b + dy).coerceIn(t + minFrac, 1f) }
                }
            }
        }
        Text(
            text = "Drag the box over just the tablets, then Apply — detection re-runs inside it.",
            color = Color.White, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White),
            ) { Text("Cancel") }
            Button(onClick = { onApply(fracToRect(l, t, r, b, imageWidth, imageHeight)) }, modifier = Modifier.weight(1f)) {
                Text("Apply crop")
            }
        }
    }
}

/** A draggable corner handle at image-fraction ([fx], [fy]); reports the drag as a fraction of the box. */
@Composable
private fun BoxScope.CropHandle(fx: Float, fy: Float, boxPx: Offset, onDrag: (Float, Float) -> Unit) {
    val density = LocalDensity.current
    val touch = 44.dp
    Box(
        modifier = Modifier
            .offset {
                val tp = with(density) { touch.toPx() }
                IntOffset((fx * boxPx.x - tp / 2f).roundToInt(), (fy * boxPx.y - tp / 2f).roundToInt())
            }
            .size(touch)
            .pointerInput(boxPx) {
                detectDragGestures { _, d ->
                    if (boxPx.x > 0f && boxPx.y > 0f) onDrag(d.x / boxPx.x, d.y / boxPx.y)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(22.dp).clip(CircleShape).background(Color.White).border(2.dp, Color(0xFF2F6FED), CircleShape))
    }
}

private fun fracToRect(l: Float, t: Float, r: Float, b: Float, imageWidth: Int, imageHeight: Int): PixelRect {
    val left = (l * imageWidth).roundToInt()
    val top = (t * imageHeight).roundToInt()
    return PixelRect(left, top, (r * imageWidth).roundToInt() - left, (b * imageHeight).roundToInt() - top)
}

/**
 * The captured frame with a correctable marker on each detected tablet, filling the available height.
 * Pinch-to-zoom + pan always; taps map back to image pixels and call [onTapAt] only when [interactive].
 */
@Composable
private fun ColumnScope.MarkerImage(
    bitmap: Bitmap,
    state: CountTabletsState,
    interactive: Boolean,
    onTapAt: (Float, Float) -> Unit,
    onEraseStart: () -> Unit,
    onEraseAt: (Float, Float, Float) -> Unit,
) {
    val markerColor = MaterialTheme.colorScheme.primary
    Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        BoxWithConstraints {
            val ratio = state.imageWidth.toFloat() / state.imageHeight.coerceAtLeast(1)
            val fitByWidth = maxWidth / ratio <= maxHeight
            val boxW = if (fitByWidth) maxWidth else maxHeight * ratio
            val boxH = if (fitByWidth) maxWidth / ratio else maxHeight

            // Transient view-only zoom/pan; resets when a new frame is decoded. Content point p maps to
            // box point: box = center + scale*(p - center) + offset; taps invert this to image pixels.
            var scale by remember(bitmap) { mutableFloatStateOf(1f) }
            var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
            // Live screen position of the eraser while a wipe is in progress (null when not erasing).
            var erasePointer by remember(bitmap) { mutableStateOf<Offset?>(null) }

            Box(
                modifier = Modifier
                    .size(boxW, boxH)
                    .clipToBounds()
                    // Zoom/pan, except while erasing (a one-finger drag then wipes instead of pans).
                    .pointerInput(state.imageWidth, state.imageHeight, state.erasing) {
                        if (state.erasing) return@pointerInput
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val newScale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                            val z = newScale / scale
                            val panned = offset * z + (centroid - center) * (1f - z) + pan
                            val maxX = (newScale - 1f).coerceAtLeast(0f) * size.width / 2f
                            val maxY = (newScale - 1f).coerceAtLeast(0f) * size.height / 2f
                            scale = newScale
                            offset = Offset(panned.x.coerceIn(-maxX, maxX), panned.y.coerceIn(-maxY, maxY))
                        }
                    }
                    .pointerInput(state.imageWidth, state.imageHeight, interactive, state.erasing) {
                        val eraseRadiusPx = ERASER_RADIUS.toPx()
                        fun toImage(pos: Offset): Pair<Float, Float> {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val content = (pos - center - offset) / scale + center
                            return content.x / size.width * state.imageWidth to content.y / size.height * state.imageHeight
                        }
                        fun radiusImg() = eraseRadiusPx / scale * state.imageWidth / size.width
                        when {
                            interactive && state.erasing -> detectDragGestures(
                                onDragStart = { pos ->
                                    erasePointer = pos
                                    onEraseStart()
                                    val (ix, iy) = toImage(pos)
                                    onEraseAt(ix, iy, radiusImg())
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    erasePointer = change.position
                                    val (ix, iy) = toImage(change.position)
                                    onEraseAt(ix, iy, radiusImg())
                                },
                                onDragEnd = { erasePointer = null },
                                onDragCancel = { erasePointer = null },
                            )
                            interactive -> detectTapGestures { pos ->
                                val (ix, iy) = toImage(pos)
                                onTapAt(ix, iy)
                            }
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                ) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured tablets",
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Canvas(
                        // The markers are Canvas-only; surface how many there are so a screen reader
                        // can perceive the counted total (individual positions stay a visual aid).
                        modifier = Modifier
                            .fillMaxSize()
                            .semantics { contentDescription = "${state.markers.size} tablet markers" },
                    ) {
                        // If a crop is active, dim the excluded surround so it's clear where detection ran.
                        state.cropRect?.let { cr ->
                            val cl = cr.left.toFloat() / state.imageWidth * size.width
                            val ct = cr.top.toFloat() / state.imageHeight * size.height
                            val cRight = cr.right.toFloat() / state.imageWidth * size.width
                            val cBottom = cr.bottom.toFloat() / state.imageHeight * size.height
                            val dim = Color.Black.copy(alpha = 0.4f)
                            drawRect(dim, Offset(0f, 0f), Size(size.width, ct))
                            drawRect(dim, Offset(0f, cBottom), Size(size.width, size.height - cBottom))
                            drawRect(dim, Offset(0f, ct), Size(cl, cBottom - ct))
                            drawRect(dim, Offset(cRight, ct), Size(size.width - cRight, cBottom - ct))
                        }
                        // Markers are drawn in unscaled box space; the graphicsLayer zooms them with the image.
                        val r = size.minDimension * 0.018f / scale
                        state.markers.forEach { m ->
                            val cx = m.x / state.imageWidth * size.width
                            val cy = m.y / state.imageHeight * size.height
                            drawCircle(color = markerColor, radius = r, center = Offset(cx, cy), style = Stroke(width = r * 0.5f))
                            drawCircle(color = markerColor, radius = r * 0.35f, center = Offset(cx, cy))
                        }
                    }
                }
                // Eraser cursor (screen space, not zoomed): a fingertip circle with cross-hairs poking past
                // it, so the finger doesn't hide where the eraser is.
                if (state.erasing) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        erasePointer?.let { p ->
                            val rad = ERASER_RADIUS.toPx()
                            drawCircle(EraseColor.copy(alpha = 0.15f), rad, p)
                            drawCircle(EraseColor, rad, p, style = Stroke(width = 2.dp.toPx()))
                            val gap = rad + 8.dp.toPx(); val len = 16.dp.toPx(); val sw = 2.5f.dp.toPx()
                            drawLine(EraseColor, Offset(p.x, p.y - gap - len), Offset(p.x, p.y - gap), strokeWidth = sw)
                            drawLine(EraseColor, Offset(p.x, p.y + gap), Offset(p.x, p.y + gap + len), strokeWidth = sw)
                            drawLine(EraseColor, Offset(p.x - gap - len, p.y), Offset(p.x - gap, p.y), strokeWidth = sw)
                            drawLine(EraseColor, Offset(p.x + gap, p.y), Offset(p.x + gap + len, p.y), strokeWidth = sw)
                        }
                    }
                }
            }
        }
    }
}

/** Read the bitmap's pixels into a platform-independent [CountImage] for [com.example.aitoui.counting]. */
private fun Bitmap.toCountImage(): CountImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return CountImage(width, height, pixels)
}
