package com.example.aitoui.image

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.aitoui.R

/**
 * A popup viewer for a unit's tablet photo by [fileName], shown centred over the standard dialog scrim.
 * It draws no scrim of its own — the [Dialog] window's own dim provides it, exactly as for the app's
 * confirmation dialogs. Uses the hi-res image, falling back to the thumbnail for photos taken before
 * hi-res was stored. Tapping anywhere dismisses it.
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
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = file,
                contentDescription = stringResource(R.string.full_image_viewer_photo_cd),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}
