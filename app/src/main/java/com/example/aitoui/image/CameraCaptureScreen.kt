package com.example.aitoui.image

import android.Manifest
import android.content.pm.PackageManager
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.aitoui.R
import com.example.aitoui.ui.heading
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.roundToInt

/**
 * A full-screen in-app camera with two phases. First, a square viewfinder (bound to a 1:1 [ViewPort])
 * with a flash toggle and tap-to-focus; the shutter freezes a square photo. Then a crop overlay — a
 * draggable, corner-resizable square over the frozen photo — lets the user pick the thumbnail region.
 * On OK, the temp file and the chosen [SquareCrop] are returned via [onCaptured]. The hi-res image is
 * the whole square; the thumbnail is the crop.
 */
@Composable
fun CameraCaptureScreen(
    onCaptured: (File, SquareCrop) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) onCancel()
    }

    var capturedFile by remember { mutableStateOf<File?>(null) }

    fun cancelAll() {
        capturedFile?.delete()
        onCancel()
    }
    BackHandler { if (capturedFile != null) { capturedFile?.delete(); capturedFile = null } else onCancel() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        if (hasPermission) {
            val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
            val imageCapture = remember {
                ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
            }
            var camera by remember { mutableStateOf<Camera?>(null) }
            var capturing by remember { mutableStateOf(false) }

            val flashModes = listOf(ImageCapture.FLASH_MODE_OFF, ImageCapture.FLASH_MODE_AUTO, ImageCapture.FLASH_MODE_ON)
            var flashIndex by remember { mutableIntStateOf(0) }

            var focusPoint by remember { mutableStateOf<Offset?>(null) }
            LaunchedEffect(focusPoint) { if (focusPoint != null) { delay(700); focusPoint = null } }

            // Square viewfinder / cropping area.
            var boxSizePx by remember { mutableFloatStateOf(0f) }
            var cropCenter by remember { mutableStateOf(Offset.Zero) }
            var cropRadius by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(capturedFile) {
                if (capturedFile != null && boxSizePx > 0f) {
                    cropRadius = boxSizePx * 0.32f
                    cropCenter = Offset(boxSizePx / 2f, boxSizePx / 2f)
                }
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .onSizeChanged { boxSizePx = it.width.toFloat() },
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            val providerFuture = ProcessCameraProvider.getInstance(context)
                            providerFuture.addListener({
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val rotation = previewView.display?.rotation ?: 0
                                imageCapture.targetRotation = rotation
                                imageCapture.flashMode = flashModes[flashIndex]
                                val viewPort = ViewPort.Builder(Rational(1, 1), rotation)
                                    .setScaleType(ViewPort.FILL_CENTER).build()
                                val group = UseCaseGroup.Builder()
                                    .addUseCase(preview).addUseCase(imageCapture).setViewPort(viewPort).build()
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group,
                                )
                            }, ContextCompat.getMainExecutor(context))
                            previewView
                        },
                    )

                    if (capturedFile == null) {
                        // Preview phase: tap-to-focus + template border.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                .pointerInput(Unit) {
                                    detectTapGestures { pos ->
                                        camera?.let { cam ->
                                            val pt = previewView.meteringPointFactory.createPoint(pos.x, pos.y)
                                            cam.cameraControl.startFocusAndMetering(
                                                FocusMeteringAction.Builder(pt).build(),
                                            )
                                            focusPoint = pos
                                        }
                                    }
                                }
                                // Pinch to drive the camera's optical/digital zoom.
                                .pointerInput(Unit) {
                                    detectTransformGestures { _, _, zoom, _ ->
                                        if (zoom == 1f) return@detectTransformGestures
                                        camera?.let { cam ->
                                            val zs = cam.cameraInfo.zoomState.value
                                            val current = zs?.zoomRatio ?: 1f
                                            val min = zs?.minZoomRatio ?: 1f
                                            val max = zs?.maxZoomRatio ?: 1f
                                            cam.cameraControl.setZoomRatio((current * zoom).coerceIn(min, max))
                                        }
                                    }
                                },
                        )
                        focusPoint?.let { p ->
                            val ring = 64.dp
                            Box(
                                modifier = Modifier
                                    .offset {
                                        val r = ring.toPx()
                                        IntOffset((p.x - r / 2f).roundToInt(), (p.y - r / 2f).roundToInt())
                                    }
                                    .size(ring)
                                    .border(2.dp, Color.Yellow, CircleShape),
                            )
                        }
                    } else {
                        // Crop phase: frozen photo + adjustable crop square.
                        AsyncImage(
                            model = capturedFile,
                            contentDescription = stringResource(R.string.camera_capture_captured_photo_cd),
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                        val minR = boxSizePx * 0.1f
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val l = cropCenter.x - cropRadius
                            val t = cropCenter.y - cropRadius
                            val s = cropRadius * 2f
                            val w = size.width
                            val h = size.height
                            val dim = Color.Black.copy(alpha = 0.5f)
                            drawRect(dim, Offset(0f, 0f), Size(w, t))
                            drawRect(dim, Offset(0f, t + s), Size(w, h - (t + s)))
                            drawRect(dim, Offset(0f, t), Size(l, s))
                            drawRect(dim, Offset(l + s, t), Size(w - (l + s), s))
                            drawRect(Color.White, Offset(l, t), Size(s, s), style = Stroke(width = 2.dp.toPx()))
                        }
                        // The crop rectangle is Canvas-drawn; expose its current position and size so a
                        // screen reader can at least perceive the bounds (adjusting stays a visual gesture).
                        val cropDesc = if (boxSizePx > 0f) {
                            stringResource(
                                R.string.camera_capture_crop_square_cd,
                                (cropRadius * 2f / boxSizePx * 100).roundToInt(),
                                (cropCenter.x / boxSizePx * 100).roundToInt(),
                                (cropCenter.y / boxSizePx * 100).roundToInt(),
                            )
                        } else {
                            stringResource(R.string.camera_capture_crop_square_fallback_cd)
                        }
                        // Move the whole square.
                        Box(
                            modifier = Modifier
                                .offset { IntOffset((cropCenter.x - cropRadius).roundToInt(), (cropCenter.y - cropRadius).roundToInt()) }
                                .size(with(density) { (cropRadius * 2f).toDp() })
                                .semantics { contentDescription = cropDesc }
                                .pointerInput(Unit) {
                                    detectDragGestures { _, drag ->
                                        cropCenter = Offset(
                                            (cropCenter.x + drag.x).coerceIn(cropRadius, boxSizePx - cropRadius),
                                            (cropCenter.y + drag.y).coerceIn(cropRadius, boxSizePx - cropRadius),
                                        )
                                    }
                                },
                        )
                        // Four corner resize handles. A 48dp transparent touch target centres a 30dp
                        // visible circle, so the hit area meets the minimum without changing the look.
                        val handleVisual = 30.dp
                        val handleTouch = 48.dp
                        listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1).forEach { (sx, sy) ->
                            Box(
                                modifier = Modifier
                                    .offset {
                                        val hp = handleTouch.toPx()
                                        IntOffset(
                                            (cropCenter.x + sx * cropRadius - hp / 2f).roundToInt(),
                                            (cropCenter.y + sy * cropRadius - hp / 2f).roundToInt(),
                                        )
                                    }
                                    .size(handleTouch)
                                    .pointerInput(Unit) {
                                        detectDragGestures { _, drag ->
                                            val dr = (sx * drag.x + sy * drag.y) / 2f
                                            val nr = (cropRadius + dr).coerceIn(minR, boxSizePx / 2f)
                                            cropRadius = nr
                                            cropCenter = Offset(
                                                cropCenter.x.coerceIn(nr, boxSizePx - nr),
                                                cropCenter.y.coerceIn(nr, boxSizePx - nr),
                                            )
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(handleVisual)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(1.dp, Color.Gray, CircleShape),
                                )
                            }
                        }
                    }
                }

                if (capturedFile == null) {
                    Text(
                        text = stringResource(R.string.camera_capture_viewfinder_instruction_text),
                        color = Color.White,
                        // Heading so a screen reader can jump to it; scrim keeps the white text legible
                        // where the letterbox is small enough that it sits over the preview.
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .heading(),
                    )
                    // Shutter.
                    val takePhotoCd = stringResource(R.string.camera_capture_take_photo_cd)
                    IconButton(
                        onClick = {
                            if (capturing) return@IconButton
                            capturing = true
                            val file = ImageStore.newCaptureFile(context)
                            imageCapture.flashMode = flashModes[flashIndex]
                            imageCapture.takePicture(
                                ImageCapture.OutputFileOptions.Builder(file).build(),
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                        capturing = false
                                        capturedFile = file
                                    }

                                    override fun onError(exc: ImageCaptureException) {
                                        file.delete()
                                        capturing = false
                                    }
                                },
                            )
                        },
                        modifier = Modifier
                            .semantics { contentDescription = takePhotoCd }
                            .padding(top = 16.dp)
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.camera_capture_crop_instruction_text),
                        color = Color.White,
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .heading(),
                    )
                    Row(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { capturedFile?.delete(); capturedFile = null }) {
                            Text(stringResource(R.string.camera_capture_retake_button_label), color = Color.White)
                        }
                        Button(onClick = {
                            val file = capturedFile ?: return@Button
                            val l = ((cropCenter.x - cropRadius) / boxSizePx).coerceIn(0f, 1f)
                            val t = ((cropCenter.y - cropRadius) / boxSizePx).coerceIn(0f, 1f)
                            val s = (cropRadius * 2f / boxSizePx).coerceIn(0f, 1f)
                            capturedFile = null
                            onCaptured(file, SquareCrop(l, t, s))
                        }) {
                            Icon(imageVector = Icons.Filled.Check, contentDescription = null)
                            Text(stringResource(R.string.camera_capture_ok_button_label), modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }

            // Flash toggle (preview phase only), top-end.
            if (capturedFile == null) {
                IconButton(
                    onClick = {
                        flashIndex = (flashIndex + 1) % flashModes.size
                        imageCapture.flashMode = flashModes[flashIndex]
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    val flashMode = flashModes[flashIndex]
                    Icon(
                        imageVector = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                            else -> Icons.Filled.FlashOff
                        },
                        // Announce the current mode so tapping (off → auto → on) is intelligible.
                        contentDescription = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> stringResource(R.string.camera_capture_flash_on_cd)
                            ImageCapture.FLASH_MODE_AUTO -> stringResource(R.string.camera_capture_flash_auto_cd)
                            else -> stringResource(R.string.camera_capture_flash_off_cd)
                        },
                        tint = Color.White,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.camera_capture_permission_message),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text(stringResource(R.string.camera_capture_grant_permission_button_label)) }
            }
        }

        // Close (cancel) button.
        IconButton(
            onClick = { cancelAll() },
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.camera_capture_cancel_cd), tint = Color.White)
        }
    }
}
