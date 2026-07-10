package com.example.aitoui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Stores one downscaled tablet photo per dispensable unit in internal storage. The camera app writes
 * a full-res capture into a temp file (via [newCaptureTarget]); [saveTabletPhoto] then downscales and
 * re-encodes it under `unit_images/`, returning the filename to persist on the dispensable unit.
 */
object ImageStore {

    private const val CAPTURES_DIR = "captures"
    private const val IMAGES_DIR = "unit_images"
    private const val MAX_DIMENSION = 1024
    private const val JPEG_QUALITY = 85

    private fun capturesDir(context: Context) = File(context.filesDir, CAPTURES_DIR).apply { mkdirs() }
    private fun imagesDir(context: Context) = File(context.filesDir, IMAGES_DIR).apply { mkdirs() }

    /** The saved photo file for [fileName], for display (e.g. with Coil). */
    fun fileFor(context: Context, fileName: String): File = File(imagesDir(context), fileName)

    /** A temp file for the camera to write a full-res capture into, plus a shareable FileProvider Uri. */
    fun newCaptureTarget(context: Context): Pair<Uri, File> {
        val file = File(capturesDir(context), "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return uri to file
    }

    /**
     * Downscales and re-encodes [source] into a stored JPEG under `unit_images/`, applying EXIF
     * rotation, and deletes the temp [source]. Returns the stored filename. Do off the main thread.
     */
    fun saveTabletPhoto(context: Context, source: File): String {
        val decoded = decodeDownscaled(source)
        val upright = applyExifRotation(source, decoded)
        val name = "unit_${UUID.randomUUID()}.jpg"
        fileFor(context, name).outputStream().use { out ->
            upright.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        }
        upright.recycle()
        source.delete()
        return name
    }

    /** Deletes the stored photo [fileName], if any. */
    fun delete(context: Context, fileName: String?) {
        if (fileName != null) fileFor(context, fileName).delete()
    }

    private fun decodeDownscaled(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: throw IOException("Could not decode image ${file.name}")
    }

    private fun applyExifRotation(file: File, bitmap: Bitmap): Bitmap {
        val degrees = when (
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        ) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(degrees) }, true,
        )
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }
}
