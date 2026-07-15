package com.example.aitoui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

/**
 * A square crop within a square image, as fractions in 0..1: [left] and [top] give the crop's
 * top-left corner and [size] its side length. The default selects the whole image.
 */
data class SquareCrop(val left: Float = 0f, val top: Float = 0f, val size: Float = 1f)

/**
 * Stores two images per dispensable unit in internal storage, sharing one filename:
 * a downscaled square **thumbnail** under `unit_images/` and the higher-resolution **full** image
 * (also square, as framed in the in-app camera) under `unit_images_full/`. The camera captures a
 * square JPEG into a temp file (see [newCaptureFile]); [saveTabletPhoto] centre-squares it (defensively),
 * writes the hi-res copy, derives the thumbnail from it, and returns the shared filename to persist on
 * the unit's `imagePath`.
 */
object ImageStore {

    private const val CAPTURES_DIR = "captures"
    private const val THUMBS_DIR = "unit_images"
    private const val FULL_DIR = "unit_images_full"
    private const val MAX_THUMB = 1024
    private const val MAX_FULL = 2048
    private const val THUMB_QUALITY = 85
    private const val FULL_QUALITY = 90

    private fun capturesDir(context: Context) = File(context.filesDir, CAPTURES_DIR).apply { mkdirs() }
    private fun thumbsDir(context: Context) = File(context.filesDir, THUMBS_DIR).apply { mkdirs() }
    private fun fullDir(context: Context) = File(context.filesDir, FULL_DIR).apply { mkdirs() }

    /** The thumbnail file for [fileName], for list display (e.g. with Coil). */
    fun fileFor(context: Context, fileName: String): File = File(thumbsDir(context), fileName)

    /** The hi-res file for [fileName], for the full-image viewer. */
    fun fullFileFor(context: Context, fileName: String): File = File(fullDir(context), fileName)

    /** A fresh temp file for the in-app camera to write a full-res capture into. */
    fun newCaptureFile(context: Context): File =
        File(capturesDir(context), "capture_${UUID.randomUUID()}.jpg")

    /**
     * Decodes [file] downscaled so its longest side is at most [maxDimension] and rotated upright per EXIF —
     * for analysing a camera capture (e.g. tablet counting), not for storage. Do off the main thread.
     */
    fun decodeUpright(file: File, maxDimension: Int): Bitmap =
        applyExifRotation(file, decodeDownscaled(file, maxDimension))

    /**
     * From a camera [source] JPEG, writes the hi-res square capture and a downscaled thumbnail of the
     * [crop] region within it (both under the same returned filename), then deletes [source]. Returns the
     * shared filename. Applies EXIF rotation and centre-squares defensively. Do off the main thread.
     */
    fun saveTabletPhoto(context: Context, source: File, crop: SquareCrop = SquareCrop()): String {
        val square = centreSquare(applyExifRotation(source, decodeDownscaled(source, MAX_FULL)))
        val name = "unit_${UUID.randomUUID()}.jpg"

        // Hi-res = the whole captured square.
        fullFileFor(context, name).outputStream().use { out ->
            square.compress(Bitmap.CompressFormat.JPEG, FULL_QUALITY, out)
        }

        // Thumbnail = the crop region, downscaled.
        val w = square.width
        val side = (crop.size * w).roundToInt().coerceIn(1, w)
        val x = (crop.left * w).roundToInt().coerceIn(0, w - side)
        val y = (crop.top * w).roundToInt().coerceIn(0, w - side)
        val cropped = Bitmap.createBitmap(square, x, y, side, side)
        val thumb = scaledDown(cropped, MAX_THUMB)
        fileFor(context, name).outputStream().use { out ->
            thumb.compress(Bitmap.CompressFormat.JPEG, THUMB_QUALITY, out)
        }

        if (thumb != cropped) thumb.recycle()
        if (cropped != square) cropped.recycle()
        square.recycle()
        source.delete()
        return name
    }

    /** Deletes both the thumbnail and hi-res files for [fileName], if any. */
    fun delete(context: Context, fileName: String?) {
        if (fileName != null) {
            fileFor(context, fileName).delete()
            fullFileFor(context, fileName).delete()
        }
    }

    private fun decodeDownscaled(file: File, maxDimension: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(file.absolutePath, opts)
            ?: throw IOException("Could not decode image ${file.name}")
    }

    /** Crops [bitmap] to its largest centred square (a no-op if already square). */
    private fun centreSquare(bitmap: Bitmap): Bitmap {
        val side = minOf(bitmap.width, bitmap.height)
        if (bitmap.width == side && bitmap.height == side) return bitmap
        val cropped = Bitmap.createBitmap(
            bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side,
        )
        if (cropped != bitmap) bitmap.recycle()
        return cropped
    }

    /** Scales [bitmap] down so its longest side is at most [maxDimension] (a no-op if already smaller). */
    private fun scaledDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true,
        )
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
