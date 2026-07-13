package com.example.aitoui.image

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

/**
 * A full-screen viewer for a unit's tablet photo by [fileName]. Shows the hi-res image, falling back
 * to the thumbnail for photos taken before hi-res was stored. Tap anywhere to dismiss.
 */
@Composable
fun FullImageDialog(fileName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = ImageStore.fullFileFor(context, fileName).takeIf { it.exists() }
        ?: ImageStore.fileFor(context, fileName)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = file,
                contentDescription = "Full-size tablet photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}
