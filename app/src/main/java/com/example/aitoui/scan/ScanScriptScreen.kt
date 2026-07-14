package com.example.aitoui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aitoui.image.ImageStore
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ScanScriptRoot(
    onScanned: (ScanResult) -> Unit,
    onEnterManually: () -> Unit,
    onBack: () -> Unit,
    viewModel: ScanScriptViewModel = viewModel(factory = ScanScriptViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    LaunchedEffect(result) {
        result?.let {
            viewModel.consumeResult()
            onScanned(it)
        }
    }

    ScanScriptScreen(
        state = state,
        onCapture = viewModel::scan,
        onEnterManually = onEnterManually,
        onDismissError = viewModel::dismissError,
        onBack = onBack,
    )
}

@Composable
fun ScanScriptScreen(
    state: ScanState,
    onCapture: (java.io.File) -> Unit,
    onEnterManually: () -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
        if (!granted) onBack()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            val previewView = remember {
                PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
            }
            val imageCapture = remember {
                ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            }
            var camera by remember { mutableStateOf<Camera?>(null) }

            val flashModes = listOf(
                ImageCapture.FLASH_MODE_OFF, ImageCapture.FLASH_MODE_AUTO, ImageCapture.FLASH_MODE_ON,
            )
            var flashIndex by remember { mutableIntStateOf(0) }

            var focusPoint by remember { mutableStateOf<Offset?>(null) }
            LaunchedEffect(focusPoint) { if (focusPoint != null) { delay(700); focusPoint = null } }

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        imageCapture.targetRotation = previewView.display?.rotation ?: 0
                        imageCapture.flashMode = flashModes[flashIndex]
                        provider.unbindAll()
                        camera = provider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture,
                        )
                    }, ContextCompat.getMainExecutor(context))
                    previewView
                },
            )

            // Tap-to-focus over the whole preview. The controls below are drawn later, so they take
            // their own taps; taps on the open preview area focus the camera.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { pos ->
                            camera?.let { cam ->
                                val point = previewView.meteringPointFactory.createPoint(pos.x, pos.y)
                                cam.cameraControl.startFocusAndMetering(
                                    FocusMeteringAction.Builder(point).build(),
                                )
                                focusPoint = pos
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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Fit the whole PB038 form in the frame, tap to focus, then tap to scan.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
                // Shutter.
                IconButton(
                    onClick = {
                        if (state.busy) return@IconButton
                        val file = ImageStore.newCaptureFile(context)
                        imageCapture.flashMode = flashModes[flashIndex]
                        imageCapture.takePicture(
                            ImageCapture.OutputFileOptions.Builder(file).build(),
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(result: ImageCapture.OutputFileResults) = onCapture(file)
                                override fun onError(exc: ImageCaptureException) { file.delete() }
                            },
                        )
                    },
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White))
                }
                TextButton(
                    onClick = onEnterManually,
                    modifier = Modifier.padding(top = 12.dp),
                ) { Text("Enter manually", color = Color.White) }
            }

            // Flash toggle, top-end.
            IconButton(
                onClick = {
                    flashIndex = (flashIndex + 1) % flashModes.size
                    imageCapture.flashMode = flashModes[flashIndex]
                },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            ) {
                Icon(
                    imageVector = when (flashModes[flashIndex]) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                        else -> Icons.Filled.FlashOff
                    },
                    contentDescription = "Flash",
                    tint = Color.White,
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Camera permission is needed to scan a prescription form.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Grant permission") }
                TextButton(
                    onClick = onEnterManually,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Enter manually instead", color = Color.White) }
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
        ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
        }

        if (state.busy) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = Color.White) }
        }

        state.error?.let { error ->
            AlertDialog(
                onDismissRequest = onDismissError,
                title = { Text("Scan failed") },
                text = { Text("$error You can try again or enter the script manually.") },
                confirmButton = { TextButton(onClick = onDismissError) { Text("Try again") } },
                dismissButton = { TextButton(onClick = onEnterManually) { Text("Enter manually") } },
            )
        }
    }
}
