package com.example.aitoui.inhand.blister

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.view.Surface
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.BuildConfig
import com.example.aitoui.counting.CellRef
import com.example.aitoui.counting.CountImage
import com.example.aitoui.counting.FrameHit
import com.example.aitoui.counting.PACK_GRID_MARGIN_LONG
import com.example.aitoui.counting.PACK_GRID_MARGIN_SHORT
import com.example.aitoui.counting.PackRegion
import com.example.aitoui.counting.adjustedCellCenter
import com.example.aitoui.counting.adjustedCellHit
import com.example.aitoui.counting.contains
import com.example.aitoui.counting.corners
import com.example.aitoui.counting.hitTest
import com.example.aitoui.counting.rotationHandle
import com.example.aitoui.counting.rowMajorOrder
import com.example.aitoui.counting.topEdgeMidpoint
import com.example.aitoui.image.ImageStore
import com.example.aitoui.ui.heading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Longest edge (px) the capture is downscaled to before segmenting — fast and threshold-stable. */
private const val ANALYSIS_MAX_DIMENSION = 1200

/** Empty/popped blister marker colour (amber), shared by the pop grid. */
private val PoppedColor = Color(0xFFFFC24B)

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
        onSelectFrame = viewModel::selectFrame,
        onMoveFrame = viewModel::moveSelectedFrame,
        onResizeFrame = viewModel::resizeSelectedFrame,
        onRotateFrame = viewModel::rotateSelectedFrame,
        onAddFrame = viewModel::addFrame,
        onDeleteFrame = viewModel::deleteSelectedFrame,
        onConfirmFrames = viewModel::confirmFrames,
        onSetColumns = viewModel::setColumns,
        onSetRows = viewModel::setRows,
        onConfirmFormat = viewModel::confirmFormat,
        onToggleCell = viewModel::toggleCell,
        onPanGrid = viewModel::panCurrentGrid,
        onScaleGrid = viewModel::scaleCurrentGridSpacing,
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
    onSelectFrame: (Int?) -> Unit,
    onMoveFrame: (Float, Float) -> Unit,
    onResizeFrame: (Int, Float, Float) -> Unit,
    onRotateFrame: (Float) -> Unit,
    onAddFrame: () -> Unit,
    onDeleteFrame: () -> Unit,
    onConfirmFrames: () -> Unit,
    onSetColumns: (Int) -> Unit,
    onSetRows: (Int) -> Unit,
    onConfirmFormat: () -> Unit,
    onToggleCell: (CellRef) -> PopResult,
    onPanGrid: (Float, Float) -> Unit,
    onScaleGrid: (Float) -> Unit,
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

            state.phase == BlisterPhase.FRAME -> FrameEditorView(
                bitmap = bitmap!!, state = state,
                onSelectFrame = onSelectFrame, onMoveFrame = onMoveFrame, onResizeFrame = onResizeFrame,
                onRotateFrame = onRotateFrame, onAddFrame = onAddFrame, onDeleteFrame = onDeleteFrame,
                onContinue = onConfirmFrames,
            )

            state.phase == BlisterPhase.FORMAT -> FormatView(
                bitmap = bitmap!!, state = state,
                onSetColumns = onSetColumns, onSetRows = onSetRows, onContinue = onConfirmFormat,
            )

            state.phase == BlisterPhase.POP -> {
                val pack = state.currentPack
                if (pack == null) LoadingOverlay("…")
                else PopView(
                    bitmap = bitmap!!, state = state, pack = pack,
                    onToggleCell = onToggleCell, onPanGrid = onPanGrid, onScaleGrid = onScaleGrid,
                    onReset = onResetPops, onNext = onNextPack,
                )
            }

            else -> LoadingOverlay("…")
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
        Text(
            label, color = Color.White,
            modifier = Modifier.padding(top = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
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
                imageCapture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
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
            text = "Lay the packs down and take a photo. They can touch or overlap — you'll frame each one next.",
            color = Color.White, textAlign = TextAlign.Center,
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
                modifier = Modifier.semantics { contentDescription = "Take photo" }.padding(bottom = 32.dp).size(80.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
            ) {
                Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White))
            }
        }
    }
}

/**
 * Manual framing: the whole captured photo with an oriented rectangle per pack. Corner handles resize the
 * selected box along its own axes, the handle above it rotates it, and its body drags it; a tap selects a
 * box or clears the selection. Frames may overlap freely. Produces the [PackRegion]s the counter consumes.
 */
@Composable
private fun FrameEditorView(
    bitmap: Bitmap,
    state: BlisterCountState,
    onSelectFrame: (Int?) -> Unit,
    onMoveFrame: (Float, Float) -> Unit,
    onResizeFrame: (Int, Float, Float) -> Unit,
    onRotateFrame: (Float) -> Unit,
    onAddFrame: () -> Unit,
    onDeleteFrame: () -> Unit,
    onContinue: () -> Unit,
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val frameColor = MaterialTheme.colorScheme.primary
    val imgW = state.imageWidth
    val imgH = state.imageHeight
    val latest by rememberUpdatedState(state)
    val textMeasurer = rememberTextMeasurer()
    // The pop-order label per frame: its 1-based rank in row-major order (what the pack will be popped as).
    val labels = remember(state.frames) {
        IntArray(state.frames.size).also { arr ->
            rowMajorOrder(state.frames).forEachIndexed { rank, frameIndex -> arr[frameIndex] = rank + 1 }
        }
    }
    var dragMode by remember { mutableStateOf<FrameHit?>(null) }
    // Reference captured when a rotation drag starts, so the box turns by the drag delta about its centre.
    var rotCenter by remember { mutableStateOf(Offset.Zero) }
    var rotStartPointer by remember { mutableFloatStateOf(0f) }
    var rotStartAngle by remember { mutableFloatStateOf(0f) }

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        // No pack count here — each pack now carries its own number, which makes the total obvious.
        Header(title = "Frame the packs", trailing = null)
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (imgW == 0 || imgH == 0) return@Box
            BoxWithConstraints {
                val ratio = imgW.toFloat() / imgH
                val fitByWidth = maxWidth / ratio <= maxHeight
                val boxW = if (fitByWidth) maxWidth else maxHeight * ratio
                val boxH = if (fitByWidth) maxWidth / ratio else maxHeight

                Box(
                    modifier = Modifier
                        .size(boxW, boxH)
                        .clipToBounds()
                        .pointerInput(imgW, imgH) {
                            val handleRadiusPx = 26.dp.toPx()
                            val rotationGapPx = 30.dp.toPx()
                            detectDragGestures(
                                onDragStart = { start ->
                                    val s = size.width.toFloat() / imgW
                                    val px = start.x / s
                                    val py = start.y / s
                                    val handleR = handleRadiusPx / s
                                    val gap = rotationGapPx / s
                                    val sel = latest.selectedFrame?.let { latest.frames.getOrNull(it) }
                                    val onHandle = sel?.hitTest(px, py, handleR, gap)
                                    dragMode = when (onHandle) {
                                        is FrameHit.Rotate -> {
                                            rotCenter = Offset(sel.cx, sel.cy)
                                            rotStartPointer = atan2(py - sel.cy, px - sel.cx)
                                            rotStartAngle = sel.angleRad
                                            FrameHit.Rotate
                                        }
                                        is FrameHit.Corner -> onHandle
                                        else -> {
                                            val idx = latest.frames.indexOfLast { it.contains(px, py) }
                                            if (idx >= 0) { onSelectFrame(idx); FrameHit.Body } else FrameHit.None
                                        }
                                    }
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    val s = size.width.toFloat() / imgW
                                    val px = change.position.x / s
                                    val py = change.position.y / s
                                    when (val m = dragMode) {
                                        is FrameHit.Rotate -> {
                                            val cur = atan2(py - rotCenter.y, px - rotCenter.x)
                                            onRotateFrame(rotStartAngle + (cur - rotStartPointer))
                                        }
                                        is FrameHit.Corner -> onResizeFrame(m.index, px, py)
                                        is FrameHit.Body -> onMoveFrame(amount.x / s, amount.y / s)
                                        else -> Unit
                                    }
                                },
                                onDragEnd = { dragMode = null },
                                onDragCancel = { dragMode = null },
                            )
                        }
                        .pointerInput(imgW, imgH) {
                            detectTapGestures { pos ->
                                val s = size.width.toFloat() / imgW
                                val px = pos.x / s
                                val py = pos.y / s
                                val idx = latest.frames.indexOfLast { it.contains(px, py) }
                                onSelectFrame(if (idx >= 0) idx else null)
                            }
                        },
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val s = size.width / imgW
                        drawImage(
                            image = imageBitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(imgW, imgH),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                        )
                        val rotationGap = 30.dp.toPx() / s
                        state.frames.forEachIndexed { i, frame ->
                            val selected = i == state.selectedFrame
                            val c = frame.corners().map { Offset(it.x * s, it.y * s) }
                            val path = Path().apply {
                                moveTo(c[0].x, c[0].y); lineTo(c[1].x, c[1].y)
                                lineTo(c[2].x, c[2].y); lineTo(c[3].x, c[3].y); close()
                            }
                            if (selected) drawPath(path, frameColor.copy(alpha = 0.14f))
                            drawPath(
                                path,
                                color = if (selected) frameColor else frameColor.copy(alpha = 0.5f),
                                style = Stroke(width = (if (selected) 2.5f else 2f).dp.toPx()),
                            )
                            if (selected) {
                                // Rotation handle above the box's top edge (whichever edge is highest on screen).
                                val anchor = frame.topEdgeMidpoint()
                                val rh = frame.rotationHandle(rotationGap)
                                drawLine(frameColor, Offset(anchor.x * s, anchor.y * s), Offset(rh.x * s, rh.y * s), strokeWidth = 2.dp.toPx())
                                drawCircle(Color.White, radius = 8.dp.toPx(), center = Offset(rh.x * s, rh.y * s))
                                drawCircle(frameColor, radius = 8.dp.toPx(), center = Offset(rh.x * s, rh.y * s), style = Stroke(width = 2.dp.toPx()))
                            }
                            // Corner handles: solid for the selected pack, de-emphasised for the rest so an
                            // unselected frame still reads as a draggable rectangle.
                            val handleFill = if (selected) Color.White else Color.White.copy(alpha = 0.4f)
                            val handleRing = if (selected) frameColor else frameColor.copy(alpha = 0.5f)
                            val handleR = (if (selected) 7f else 5.5f).dp.toPx()
                            c.forEach { corner ->
                                drawCircle(handleFill, radius = handleR, center = corner)
                                drawCircle(handleRing, radius = handleR, center = corner, style = Stroke(width = 2.dp.toPx()))
                            }
                            // The pop-order number, large and centred on the pack. A drop shadow keeps it
                            // legible whether it sits over a white pack or the darker background.
                            val fontPx = (min(frame.halfW, frame.halfH) * s * 0.9f).coerceAtLeast(16.dp.toPx())
                            val measured = textMeasurer.measure(
                                text = "${labels.getOrElse(i) { i + 1 }}",
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = fontPx.toSp(),
                                    fontWeight = FontWeight.Bold,
                                    shadow = Shadow(Color.Black.copy(alpha = 0.85f), Offset(0f, 2.dp.toPx()), blurRadius = 6f),
                                ),
                            )
                            drawText(
                                measured,
                                topLeft = Offset(
                                    frame.cx * s - measured.size.width / 2f,
                                    frame.cy * s - measured.size.height / 2f,
                                ),
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = "Tap a pack to select it, then drag its corners to fit and spin the top handle to align. " +
                "Overlapping is fine.",
            color = Color.White, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onAddFrame, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White),
            ) { Text("+ Add pack") }
            OutlinedButton(
                onClick = onDeleteFrame, modifier = Modifier.weight(1f), enabled = state.selectedFrame != null,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(1.dp, Color.White.copy(alpha = if (state.selectedFrame != null) 1f else 0.3f)),
            ) { Text("Delete") }
        }
        Button(
            onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            enabled = state.frames.isNotEmpty(),
        ) { Text("Continue ›") }
    }
}

@Composable
private fun FormatView(
    bitmap: Bitmap,
    state: BlisterCountState,
    onSetColumns: (Int) -> Unit,
    onSetRows: (Int) -> Unit,
    onContinue: () -> Unit,
) {
    val representative = state.packs.firstOrNull() ?: return
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Header(title = "Pack format", trailing = null)
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            PackImageView(
                bitmap, state.imageWidth, state.imageHeight, representative,
                state.alongLong, state.alongShort, interactive = false,
                onPopCell = { PopResult.NONE }, onPanGrid = { _, _ -> }, onScaleGrid = { },
            )
        }
        Text(
            text = "Set the rows and columns for the packs.",
            color = Color.White, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        if (state.packs.size > 1) {
            Text(
                text = "Applies to all ${state.packs.size} packs",
                color = PoppedColor, style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stepper("Columns", state.cols) { onSetColumns(it) }
            Stepper("Rows", state.rows) { onSetRows(it) }
        }
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
            Text("Continue ›")
        }
    }
}

@Composable
private fun PopView(
    bitmap: Bitmap,
    state: BlisterCountState,
    pack: PackState,
    onToggleCell: (CellRef) -> PopResult,
    onPanGrid: (Float, Float) -> Unit,
    onScaleGrid: (Float) -> Unit,
    onReset: () -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Header(title = "Pop blisters") {
            // The thumbnail identifies the current pack and its place among the rest, so no "Pack x / y" text.
            if (state.packs.size > 1) {
                PackThumbnail(state.packs, state.currentPackIndex, state.imageWidth, state.imageHeight)
            }
        }
        val feedback = rememberPopFeedback()
        fun playFor(result: PopResult) {
            when (result) {
                PopResult.POPPED -> feedback.pop()
                PopResult.UNPOPPED -> feedback.unpop()
                PopResult.NONE -> Unit
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            PackImageView(
                bitmap, state.imageWidth, state.imageHeight, pack, state.alongLong, state.alongShort,
                interactive = true,
                onPopCell = { cell -> playFor(onToggleCell(cell)) },
                onPanGrid = onPanGrid, onScaleGrid = onScaleGrid,
            )
            // Touch-free semantics overlay so TalkBack can navigate and pop each blister (sighted taps
            // fall through to the image beneath).
            AccessibleBlisterGrid(state.alongLong, state.alongShort, pack.popped) { cell -> playFor(onToggleCell(cell)) }
        }
        Text(
            text = "Drag the grid to line the circles up with the blisters, and pinch to match their spacing. " +
                "Then tap the empty blisters to pop them.",
            color = Color.White, style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Full ${state.fullCountOf(pack)}   ·   Empty ${pack.popped.size}",
                color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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

/**
 * A transparent grid of one semantics node per blister, laid over the image. Each node exposes the
 * blister's position and full/empty state, and a "Pop"/"Restore" action — so TalkBack can operate the pop
 * task. Semantics-only (no pointer input), so sighted taps fall through to the image beneath.
 */
@Composable
private fun AccessibleBlisterGrid(alongLong: Int, alongShort: Int, popped: Set<CellRef>, onToggle: (CellRef) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        for (along in 0 until alongLong) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (across in 0 until alongShort) {
                    val cell = CellRef(along, across)
                    val isPopped = cell in popped
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics(mergeDescendants = true) {
                                contentDescription = "Blister, row ${along + 1}, column ${across + 1}"
                                stateDescription = if (isPopped) "empty" else "full"
                                onClick(label = if (isPopped) "Restore" else "Pop") {
                                    onToggle(cell)
                                    true
                                }
                            },
                    )
                }
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
                    Text("Pack ${i + 1}  ·  ${state.cols}×${state.rows}", color = Color.White)
                    Text("${state.fullCountOf(pack)} / ${state.blisterCount}", color = Color.White, fontWeight = FontWeight.SemiBold)
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
    Header(title) {
        if (trailing != null) Text(trailing, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)
    }
}

/** [Header] with an arbitrary trailing slot (e.g. the pop screen's pack thumbnail + "Pack x / y"). */
@Composable
private fun Header(title: String, trailing: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.heading())
        trailing()
    }
}

/**
 * A small map of every pack as it sits in the original photo — each pack's oriented outline drawn to scale,
 * numbered by pop order, with [currentIndex] filled in. Lets the user see which pack they're popping and
 * where it is among the rest.
 */
@Composable
private fun PackThumbnail(packs: List<PackState>, currentIndex: Int, imageWidth: Int, imageHeight: Int) {
    if (imageWidth == 0 || imageHeight == 0 || packs.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()
    val maxDim = 48.dp
    val ratio = imageWidth.toFloat() / imageHeight
    val w = if (ratio >= 1f) maxDim else maxDim * ratio
    val h = if (ratio >= 1f) maxDim / ratio else maxDim

    // A thin border stands in for the edge of the original photo the packs sit in.
    Canvas(
        modifier = Modifier
            .size(w, h)
            .border(1.dp, Color.White.copy(alpha = 0.5f))
            .padding(2.dp),
    ) {
        val sx = size.width / imageWidth
        val sy = size.height / imageHeight
        packs.forEachIndexed { i, pack ->
            val r = pack.region
            fun corner(lc: Float, sc: Float) = Offset(
                (r.cx + lc * r.longX + sc * r.shortX) * sx,
                (r.cy + lc * r.longY + sc * r.shortY) * sy,
            )
            val c = listOf(
                corner(r.longMin, r.shortMin), corner(r.longMax, r.shortMin),
                corner(r.longMax, r.shortMax), corner(r.longMin, r.shortMax),
            )
            val path = Path().apply {
                moveTo(c[0].x, c[0].y); lineTo(c[1].x, c[1].y)
                lineTo(c[2].x, c[2].y); lineTo(c[3].x, c[3].y); close()
            }
            val current = i == currentIndex
            if (current) drawPath(path, primary.copy(alpha = 0.9f))
            drawPath(
                path,
                color = if (current) primary else primary.copy(alpha = 0.55f),
                style = Stroke(width = 1.5.dp.toPx()),
            )
            val fontPx = (min(abs(r.longMax - r.longMin), abs(r.shortMax - r.shortMin)) * min(sx, sy) * 0.7f)
                .coerceIn(7.dp.toPx(), 12.dp.toPx())
            val measured = textMeasurer.measure(
                text = "${i + 1}",
                style = TextStyle(
                    color = if (current) Color.White else primary,
                    fontSize = fontPx.toSp(),
                    fontWeight = FontWeight.Bold,
                ),
            )
            drawText(
                measured,
                topLeft = Offset(r.cx * sx - measured.size.width / 2f, r.cy * sy - measured.size.height / 2f),
            )
        }
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
 * The given pack, cropped from the captured frame to fill the view, with its blister grid drawn on top.
 *
 * When [interactive], the grid can be hand-aligned to the real blisters: a one-finger drag that *starts on
 * empty space* pans the whole grid ([onPanGrid]); a two-finger pinch scales the spacing between blister
 * centres ([onScaleGrid]) — equally along both axes, without resizing the circles; and a touch that *starts
 * on a circle* pops that blister ([onPopCell]). Full blisters show a hollow ring; popped ones a filled hole.
 * The grid is [alongLong] × [alongShort].
 */
@Composable
private fun PackImageView(
    bitmap: Bitmap,
    imageWidth: Int,
    imageHeight: Int,
    pack: PackState,
    alongLong: Int,
    alongShort: Int,
    interactive: Boolean,
    onPopCell: (CellRef) -> Unit,
    onPanGrid: (Float, Float) -> Unit,
    onScaleGrid: (Float) -> Unit,
) {
    val ringColor = MaterialTheme.colorScheme.primary
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }

    // Axis-aligned crop around just this pack (padded), so it fills the view.
    val crop = remember(pack.region, imageWidth, imageHeight) {
        packCropRect(pack.region, imageWidth, imageHeight, pad = 0.08f)
    }
    val cropX = crop[0]; val cropY = crop[1]; val cropW = crop[2]; val cropH = crop[3]

    // The gesture coroutine outlives individual recompositions, so it reads the live adjust through this
    // handle rather than the stale value captured when the pointer pipeline last (re)started.
    val latestAdjust by rememberUpdatedState(pack.adjust)

    BoxWithConstraints {
        val ratio = cropW.toFloat() / cropH
        val fitByWidth = maxWidth / ratio <= maxHeight
        val boxW = if (fitByWidth) maxWidth else maxHeight * ratio
        val boxH = if (fitByWidth) maxWidth / ratio else maxHeight

        // Blister marker radius in image pixels: a fraction of the smaller blister pitch. Fixed — spacing
        // changes move the centres apart, never the circles' size.
        val longPitch = abs(pack.region.longMax - pack.region.longMin) * (1 - 2 * PACK_GRID_MARGIN_LONG) / alongLong
        val shortPitch = abs(pack.region.shortMax - pack.region.shortMin) * (1 - 2 * PACK_GRID_MARGIN_SHORT) / alongShort
        val radiusImg = 0.30f * min(longPitch, shortPitch)

        Box(
            modifier = Modifier
                .size(boxW, boxH)
                .clipToBounds()
                .pointerInput(interactive, pack.region, alongLong, alongShort) {
                    if (!interactive) return@pointerInput
                    awaitEachGesture {
                        // Image pixels per view pixel (equal on both axes — the box matches the crop's ratio).
                        val toImg = cropW.toFloat() / size.width
                        val down = awaitFirstDown()
                        val startX = cropX + down.position.x * toImg
                        val startY = cropY + down.position.y * toImg
                        val hit = adjustedCellHit(
                            pack.region, alongLong, alongShort, startX, startY, latestAdjust, radiusImg * 1.15f,
                        )
                        if (hit != null) {
                            // Landed on a circle → pop it, then swallow the rest of this gesture.
                            onPopCell(hit)
                            down.consume()
                            do {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            return@awaitEachGesture
                        }
                        // Started on empty space → pan (one finger) or scale spacing (two fingers). Once a
                        // second finger appears the gesture stays a pinch, so lifting back to one finger
                        // doesn't lurch into a pan.
                        var pinching = false
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } >= 2) pinching = true
                            if (pinching) {
                                val zoom = event.calculateZoom()
                                if (zoom != 1f) onScaleGrid(zoom)
                            } else {
                                val pan = event.calculatePan()
                                if (pan != Offset.Zero) onPanGrid(pan.x * toImg, pan.y * toImg)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
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
                for (along in 0 until alongLong) for (across in 0 until alongShort) {
                    val c = adjustedCellCenter(pack.region, alongLong, alongShort, along, across, pack.adjust)
                    val center = Offset((c.x - cropX) * sx, (c.y - cropY) * sy)
                    val popped = CellRef(along, across) in pack.popped
                    if (popped) {
                        drawCircle(color = Color.Black.copy(alpha = 0.55f), radius = r, center = center)
                        drawCircle(color = PoppedColor, radius = r, center = center, style = Stroke(width = r * 0.35f))
                    } else {
                        drawCircle(color = ringColor, radius = r, center = center, style = Stroke(width = r * 0.3f))
                    }
                }
            }
        }
    }
}

/** Axis-aligned pixel crop rect [x, y, w, h] around a pack's oriented box, padded and clamped to the image. */
private fun packCropRect(region: PackRegion, imageWidth: Int, imageHeight: Int, pad: Float): IntArray {
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
