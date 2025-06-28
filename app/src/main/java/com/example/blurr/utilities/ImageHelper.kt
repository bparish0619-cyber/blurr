package com.example.blurr.utilities

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

class ImageHelper {
    internal fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        // Compress the bitmap to a PNG format in memory
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        // Get the byte array and encode it to a Base64 string
        val imageBytes = outputStream.toByteArray()
        return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
    }
}