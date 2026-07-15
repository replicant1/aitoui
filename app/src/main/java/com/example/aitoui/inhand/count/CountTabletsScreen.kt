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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.BuildConfig
import com.example.aitoui.counting.CountImage
import com.example.aitoui.image.ImageStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Longest edge (px) the capture is downscaled to before counting — fast and threshold-stable. */
private const val ANALYSIS_MAX_DIMENSION = 1200

/** Maximum pinch-zoom on the review image, so dense clusters can be corrected accurately. */
private const val MAX_ZOOM = 5f

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
        onTapAt = viewModel::onTapAt,
        onRetake = viewModel::retake,
        onUseCount = { onCounted(state.count) },
        onBack = onBack,
    )
}

@Composable
fun CountTabletsScreen(
    state: CountTabletsState,
    onAnalyse: (String, CountImage) -> Unit,
    onTapAt: (Float, Float) -> Unit,
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
                onTapAt = onTapAt,
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
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) { Text("Cancel") }
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
    onTapAt: (Float, Float) -> Unit,
    onRetake: () -> Unit,
    onUseCount: () -> Unit,
) {
    val markerColor = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text(
            text = if (state.analysing) "Counting…" else "${state.count} tablets",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

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

                Box(
                    modifier = Modifier
                        .size(boxW, boxH)
                        .clipToBounds()
                        .pointerInput(state.imageWidth, state.imageHeight) {
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
                        .pointerInput(state.imageWidth, state.imageHeight) {
                            detectTapGestures { pos ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val content = (pos - center - offset) / scale + center
                                onTapAt(
                                    content.x / size.width * state.imageWidth,
                                    content.y / size.height * state.imageHeight,
                                )
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
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Markers are drawn in unscaled box space; the graphicsLayer zooms them with the image.
                            val r = size.minDimension * 0.018f / scale
                            state.markers.forEach { m ->
                                val cx = m.x / state.imageWidth * size.width
                                val cy = m.y / state.imageHeight * size.height
                                drawCircle(
                                    color = markerColor,
                                    radius = r,
                                    center = Offset(cx, cy),
                                    style = Stroke(width = r * 0.5f),
                                )
                                drawCircle(
                                    color = markerColor,
                                    radius = r * 0.35f,
                                    center = Offset(cx, cy),
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = "Pinch to zoom. Tap a missed tablet to add it, or tap a marker to remove it.",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Retake") }
            Button(
                onClick = onUseCount,
                enabled = !state.analysing,
                modifier = Modifier.weight(1f),
            ) { Text("Use ${state.count}") }
        }
    }
}

/** Read the bitmap's pixels into a platform-independent [CountImage] for [com.example.aitoui.counting]. */
private fun Bitmap.toCountImage(): CountImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return CountImage(width, height, pixels)
}
