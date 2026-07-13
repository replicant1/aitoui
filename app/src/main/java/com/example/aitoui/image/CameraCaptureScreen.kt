package com.example.aitoui.image

import android.Manifest
import android.content.pm.PackageManager
import android.util.Rational
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

/**
 * A full-screen in-app camera. A square viewfinder is bound to a 1:1 [ViewPort], so what is framed in
 * the square is exactly what is captured and what the thumbnail will clip. On capture, a square JPEG is
 * written to a temp file and returned via [onCaptured]; [onCancel] backs out.
 */
@Composable
fun CameraCaptureScreen(
    onCaptured: (File) -> Unit,
    onCancel: () -> Unit,
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
        if (!granted) onCancel()
    }

    BackHandler(onBack = onCancel)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Consume taps so nothing behind this full-screen overlay can be touched.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        if (hasPermission) {
            val imageCapture = remember {
                ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
            }
            var capturing by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Square viewfinder — the exact area that becomes the hi-res image and thumbnail.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                val rotation = previewView.display?.rotation ?: 0
                                imageCapture.targetRotation = rotation
                                val viewPort = ViewPort.Builder(Rational(1, 1), rotation)
                                    .setScaleType(ViewPort.FILL_CENTER)
                                    .build()
                                val group = UseCaseGroup.Builder()
                                    .addUseCase(preview)
                                    .addUseCase(imageCapture)
                                    .setViewPort(viewPort)
                                    .build()
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group,
                                )
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                    )
                    // Template border showing the crop boundary.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp)),
                    )
                }

                Text(
                    text = "Frame the tablet inside the square",
                    color = Color.White,
                    modifier = Modifier.padding(top = 24.dp),
                )

                // Shutter button.
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
                                    onCaptured(file)
                                }

                                override fun onError(exc: ImageCaptureException) {
                                    file.delete()
                                    capturing = false
                                }
                            },
                        )
                    },
                    modifier = Modifier
                        .padding(top = 24.dp)
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Camera permission is needed to photograph a tablet.",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Grant permission") }
            }
        }

        // Close (cancel) button.
        IconButton(
            onClick = onCancel,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
        ) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Cancel", tint = Color.White)
        }
    }
}
