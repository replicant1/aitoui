package com.example.aitoui.inhand.blister

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.BuildConfig
import com.example.aitoui.counting.CountImage
import com.example.aitoui.counting.PACK_GRID_MARGIN_LONG
import com.example.aitoui.counting.PACK_GRID_MARGIN_SHORT
import com.example.aitoui.counting.cellCenter
import com.example.aitoui.image.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Longest edge (px) the capture is downscaled to before segmenting — fast and threshold-stable. */
private const val ANALYSIS_MAX_DIMENSION = 1200

/** Maximum pinch-zoom on the review image, so dense packs can be popped accurately. */
private const val MAX_ZOOM = 5f

@Composable
fun BlisterCountRoot(
    onCounted: (Int) -> Unit,
    onBack: () -> Unit,
    viewModel: BlisterCountViewModel = viewModel(factory = BlisterCountViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BlisterCountScreen(
        state = state,
        onAnalyse = viewModel::analyse,
        onSetColumns = viewModel::setColumns,
        onSetRows = viewModel::setRows,
        onConfirmLayout = viewModel::confirmLayout,
        onPopAt = viewModel::popAt,
        onResetPops = viewModel::resetCurrentPops,
        onNextPack = viewModel::nextPack,
        onRetake = viewModel::retake,
        onUseTotal = { onCounted(state.total) },
        onBack = onBack,
    )
}

@Composable
fun BlisterCountScreen(
    state: BlisterCountState,
    onAnalyse: (String, CountImage) -> Unit,
    onSetColumns: (Int) -> Unit,
    onSetRows: (Int) -> Unit,
    onConfirmLayout: () -> Unit,
    onPopAt: (Float, Float) -> PopResult,
    onResetPops: () -> Unit,
    onNextPack: () -> Unit,
    onRetake: () -> Unit,
    onUseTotal: () -> Unit,
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
    val bitmap by produceState<Bitmap?>(null, state.capturePath) {
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

            state.capturePath == null -> CameraCapture(onCaptured = onAnalyse, onBack = onBack)

            state.phase == BlisterPhase.SUMMARY -> SummaryView(state = state, onUseTotal = onUseTotal, onRetake = onRetake)

            bitmap == null -> LoadingOverlay("Loading…")

            state.analysing -> LoadingOverlay("Finding packs…")

            state.packs.isEmpty() -> NoPacksFound(onRetake = onRetake)

            else -> {
                val pack = state.currentPack!!
                when (state.phase) {
                    BlisterPhase.CONFIRM_LAYOUT -> ConfirmLayoutView(
                        bitmap = bitmap!!, state = state, pack = pack,
                        onSetColumns = onSetColumns, onSetRows = onSetRows, onConfirm = onConfirmLayout,
                    )
                    BlisterPhase.POP -> PopView(
                        bitmap = bitmap!!, state = state, pack = pack,
                        onPopAt = onPopAt, onReset = onResetPops, onNext = onNextPack,
                    )
                    else -> LoadingOverlay("…")
                }
            }
        }
    }
}

@Composable
private fun LoadingOverlay(label: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Color.White)
        Text(label, color = Color.White, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun NoPacksFound(onRetake: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "No packs found. Lay them on a plain surface so they don't touch or overlap, then try again.",
            color = Color.White, textAlign = TextAlign.Center,
        )
        Button(onClick = onRetake, modifier = Modifier.padding(top = 20.dp)) { Text("Retake") }
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
            text = "Camera permission is needed to count packs.",
            color = Color.White, textAlign = TextAlign.Center,
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
private fun CameraCapture(onCaptured: (String, CountImage) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    var capturing by remember { mutableStateOf(false) }

    // Debug-only: feed a pushed test image (filesDir/test-image.jpg) through the same flow as a capture.
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
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                imageCapture.targetRotation = previewView.display?.rotation ?: 0
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            }, ContextCompat.getMainExecutor(context))
            previewView
        },
    )

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
            text = "Lay the packs on a plain surface so they don't touch or overlap — foil-up, dome-up, or mixed. Then take a photo.",
            color = Color.White, textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
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
                modifier = Modifier.padding(bottom = 32.dp).size(80.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

@Composable
private fun ConfirmLayoutView(
    bitmap: Bitmap,
    state: BlisterCountState,
    pack: PackState,
    onSetColumns: (Int) -> Unit,
    onSetRows: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Header(
            title = "Confirm layout",
            trailing = if (state.packs.size > 1) "Pack ${state.currentPackIndex + 1} / ${state.packs.size}" else null,
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            PackImageView(bitmap, state.imageWidth, state.imageHeight, pack, interactive = false, onPopAt = { _, _ -> PopResult.NONE })
        }
        Text(
            text = "Set the rows and columns to match the pack.",
            color = Color.White, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stepper("Columns", pack.cols) { onSetColumns(it) }
            Stepper("Rows", pack.rows) { onSetRows(it) }
        }
        Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text("Continue ›")
        }
    }
}

@Composable
private fun PopView(
    bitmap: Bitmap,
    state: BlisterCountState,
    pack: PackState,
    onPopAt: (Float, Float) -> PopResult,
    onReset: () -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Header(
            title = "Pop blisters",
            trailing = if (state.packs.size > 1) "Pack ${state.currentPackIndex + 1} / ${state.packs.size}" else null,
        )
        val feedback = rememberPopFeedback()
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            PackImageView(
                bitmap, state.imageWidth, state.imageHeight, pack, interactive = true,
                onPopAt = { x, y ->
                    val result = onPopAt(x, y)
                    when (result) {
                        PopResult.POPPED -> feedback.pop()
                        PopResult.UNPOPPED -> feedback.unpop()
                        PopResult.NONE -> Unit
                    }
                    result
                },
            )
        }
        Text(
            text = "Every blister starts full — tap the gone ones to pop them. Pinch to zoom.",
            color = Color.White, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Full ${pack.fullCount}   ·   Empty ${pack.popped.size}",
                color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onReset, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White),
            ) { Text("Undo all") }
            Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                Text(if (state.isLastPack) "Done" else "Next pack ›")
            }
        }
    }
}

@Composable
private fun SummaryView(state: BlisterCountState, onUseTotal: () -> Unit, onRetake: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Header(title = "Total on hand", trailing = null)
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp)) {
            state.packs.forEachIndexed { i, pack ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Pack ${i + 1}  ·  ${pack.cols}×${pack.rows}", color = Color.White)
                    Text("${pack.fullCount} / ${pack.blisterCount}", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Tablets remaining", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text("${state.total}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
        Button(onClick = onUseTotal, modifier = Modifier.fillMaxWidth()) { Text("Use ${state.total}") }
        OutlinedButton(
            onClick = onRetake, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White),
        ) { Text("Retake") }
    }
}

@Composable
private fun Header(title: String, trailing: String?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        if (trailing != null) Text(trailing, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(value - 1) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Fewer $label", tint = Color.White)
            }
            Text("$value", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
            IconButton(onClick = { onChange(value + 1) }) {
                Icon(Icons.Filled.Add, contentDescription = "More $label", tint = Color.White)
            }
        }
    }
}

/**
 * The current pack, cropped from the captured frame to fill the view (so confirm/pop happen over one pack,
 * not the whole photo), with its blister grid drawn on top: pinch-to-zoom + pan, and (when [interactive])
 * taps mapped back to image pixels and forwarded to [onPopAt]. Full blisters show a hollow ring; popped ones
 * a filled hole.
 */
@Composable
private fun PackImageView(
    bitmap: Bitmap,
    imageWidth: Int,
    imageHeight: Int,
    pack: PackState,
    interactive: Boolean,
    onPopAt: (Float, Float) -> PopResult,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    val poppedColor = Color(0xFFFFC24B)
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    // Axis-aligned crop around just this pack (padded), so it fills the view.
    val crop = remember(pack.region, imageWidth, imageHeight) {
        packCropRect(pack.region, imageWidth, imageHeight, pad = 0.08f)
    }
    val cropX = crop[0]; val cropY = crop[1]; val cropW = crop[2]; val cropH = crop[3]

    BoxWithConstraints {
        val ratio = cropW.toFloat() / cropH
        val fitByWidth = maxWidth / ratio <= maxHeight
        val boxW = if (fitByWidth) maxWidth else maxHeight * ratio
        val boxH = if (fitByWidth) maxWidth / ratio else maxHeight

        var scale by remember(bitmap, pack.region) { mutableFloatStateOf(1f) }
        var offset by remember(bitmap, pack.region) { mutableStateOf(Offset.Zero) }

        // Blister marker radius in image pixels: a fraction of the smaller blister pitch.
        val longPitch = abs(pack.region.longMax - pack.region.longMin) * (1 - 2 * PACK_GRID_MARGIN_LONG) / pack.alongLong
        val shortPitch = abs(pack.region.shortMax - pack.region.shortMin) * (1 - 2 * PACK_GRID_MARGIN_SHORT) / pack.alongShort
        val radiusImg = 0.30f * min(longPitch, shortPitch)

        Box(
            modifier = Modifier
                .size(boxW, boxH)
                .clipToBounds()
                .pointerInput(pack.region) {
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
                .pointerInput(interactive, pack.region) {
                    if (!interactive) return@pointerInput
                    detectTapGestures { pos ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val content = (pos - center - offset) / scale + center
                        onPopAt(cropX + content.x / size.width * cropW, cropY + content.y / size.height * cropH)
                    }
                },
        ) {
            Box(
                modifier = Modifier.matchParentSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(
                        image = imageBitmap,
                        srcOffset = IntOffset(cropX, cropY),
                        srcSize = IntSize(cropW, cropH),
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    )
                    val sx = size.width / cropW
                    val sy = size.height / cropH
                    val r = radiusImg * sx
                    for (along in 0 until pack.alongLong) for (across in 0 until pack.alongShort) {
                        val c = cellCenter(pack.region, pack.alongLong, pack.alongShort, along, across)
                        val center = Offset((c.x - cropX) * sx, (c.y - cropY) * sy)
                        val popped = com.example.aitoui.counting.CellRef(along, across) in pack.popped
                        if (popped) {
                            drawCircle(color = Color.Black.copy(alpha = 0.55f), radius = r, center = center)
                            drawCircle(color = poppedColor, radius = r, center = center, style = Stroke(width = r * 0.35f))
                        } else {
                            drawCircle(color = ringColor, radius = r, center = center, style = Stroke(width = r * 0.3f))
                        }
                    }
                }
            }
        }
    }
}

/** Axis-aligned pixel crop rect [x, y, w, h] around a pack's oriented box, padded and clamped to the image. */
private fun packCropRect(region: com.example.aitoui.counting.PackRegion, imageWidth: Int, imageHeight: Int, pad: Float): IntArray {
    var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
    for (lc in floatArrayOf(region.longMin, region.longMax)) {
        for (sc in floatArrayOf(region.shortMin, region.shortMax)) {
            val x = region.cx + lc * region.longX + sc * region.shortX
            val y = region.cy + lc * region.longY + sc * region.shortY
            minX = min(minX, x); maxX = max(maxX, x); minY = min(minY, y); maxY = max(maxY, y)
        }
    }
    val padX = (maxX - minX) * pad
    val padY = (maxY - minY) * pad
    val x0 = (minX - padX).coerceIn(0f, (imageWidth - 1).toFloat()).toInt()
    val y0 = (minY - padY).coerceIn(0f, (imageHeight - 1).toFloat()).toInt()
    val w = (maxX + padX - x0).roundToInt().coerceIn(1, imageWidth - x0)
    val h = (maxY + padY - y0).roundToInt().coerceIn(1, imageHeight - y0)
    return intArrayOf(x0, y0, w, h)
}

@Composable
private fun rememberPopFeedback(): PopFeedback {
    val context = LocalContext.current
    val feedback = remember { PopFeedback(context) }
    DisposableEffect(Unit) { onDispose { feedback.release() } }
    return feedback
}

/** Read the bitmap's pixels into a platform-independent [CountImage] for [com.example.aitoui.counting]. */
private fun Bitmap.toCountImage(): CountImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return CountImage(width, height, pixels)
}
