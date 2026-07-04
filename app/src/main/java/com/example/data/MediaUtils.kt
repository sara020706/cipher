package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object MediaUtils {
    // Firestore documents cap at 1MB and the pipeline Base64-encodes twice
    // (raw → media string → encrypted ciphertext), inflating size ~78%.
    // 300KB raw stays safely under the limit after all overhead.
    private const val MAX_BYTES = 300_000
    private const val MAX_DIMENSION = 1280

    // Load an image Uri, downscale, and compress until it fits the byte budget.
    // Returns Base64-encoded JPEG, or null if the image can't be read.
    suspend fun uriToCompressedBase64(
        context: Context,
        uri: Uri,
        maxDimension: Int = MAX_DIMENSION,
        maxBytes: Int = MAX_BYTES
    ): String? = withContext(Dispatchers.IO) {
        try {
            // First pass: read bounds only to compute a sample size.
            // Note: decodeStream intentionally returns null in this mode —
            // success is indicated by outWidth/outHeight being populated.
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val boundsStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            boundsStream.use { BitmapFactory.decodeStream(it, null, boundsOptions) }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return@withContext null

            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDimension * 2 ||
                boundsOptions.outHeight / sampleSize > maxDimension * 2
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return@withContext null

            // Scale down to the max dimension if still larger
            val largestSide = maxOf(bitmap.width, bitmap.height)
            if (largestSide > maxDimension) {
                val scale = maxDimension.toFloat() / largestSide
                bitmap = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            }

            // Compress, stepping quality down until it fits
            var quality = 80
            var bytes: ByteArray
            do {
                val stream = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                bytes = stream.toByteArray()
                quality -= 10
            } while (bytes.size > maxBytes && quality >= 20)

            if (bytes.size > maxBytes) return@withContext null
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    fun base64ToBytes(base64: String): ByteArray? = try {
        Base64.decode(base64, Base64.NO_WRAP)
    } catch (e: Exception) {
        null
    }

    fun getMimeType(context: Context, uri: Uri): String? =
        context.contentResolver.getType(uri)

    // Read a file as-is (no recompression) — used for GIFs, which must keep
    // their original bytes to stay animated. Null if unreadable or over budget.
    suspend fun uriToRawBase64(context: Context, uri: Uri, maxBytes: Int = MAX_BYTES): String? =
        withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext null
                if (bytes.size > maxBytes) null
                else Base64.encodeToString(bytes, Base64.NO_WRAP)
            } catch (e: Exception) {
                null
            }
        }
}
