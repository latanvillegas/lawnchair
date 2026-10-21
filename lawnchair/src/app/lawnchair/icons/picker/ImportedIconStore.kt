package app.lawnchair.icons.picker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Persists user-selected icon images independently of the source gallery or document URI.
 *
 * Originals are copied byte-for-byte: importing never JPEG-recompresses, downsizes,
 * or strips an existing alpha channel. Rendering can request a target size separately.
 */
object ImportedIconStore {
    const val PACKAGE_NAME = "app.lawnchair.imported.icons"
    private const val DIRECTORY = "custom_icons"
    private const val MAX_BYTES = 32L * 1024L * 1024L

    fun import(context: Context, source: Uri): String {
        val resolver = context.contentResolver
        val mime = resolver.getType(source)?.lowercase()
        require(mime == null || mime in setOf("image/png", "image/jpeg", "image/webp")) {
            "Unsupported icon image type"
        }
        val folder = File(context.filesDir, DIRECTORY).apply { check(isDirectory || mkdirs()) }
        val temporary = File.createTempFile("icon-", ".tmp", folder)
        try {
            var copied = 0L
            resolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Unable to read selected icon" }
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_BYTES) { "Icon image exceeds 32 MiB" }
                        output.write(buffer, 0, count)
                    }
                }
            }
            require(copied > 0) { "Empty icon image" }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.absolutePath, options)
            require(options.outWidth > 0 && options.outHeight > 0) { "Invalid image" }
            require(options.outWidth.toLong() * options.outHeight <= 100_000_000L) {
                "Icon dimensions exceed safe decode limits"
            }
            val extension = when (mime ?: options.outMimeType) {
                "image/png" -> "png"
                "image/jpeg" -> "jpg"
                "image/webp" -> "webp"
                else -> error("Unsupported icon image format")
            }
            val name = UUID.randomUUID().toString() + "." + extension
            val destination = File(folder, name)
            check(temporary.renameTo(destination)) { "Unable to save icon image" }
            return name
        } finally {
            temporary.delete()
        }
    }

    fun resolve(context: Context, name: String): File {
        require(Regex("[0-9a-fA-F-]{36}\\.(png|jpg|webp)").matches(name)) {
            "Invalid imported icon identifier"
        }
        return File(File(context.filesDir, DIRECTORY), name)
    }

    /**
     * Decodes at the requested display size without rewriting the saved original.
     * Hardware scaling remains the responsibility of the view rendering the bitmap.
     */
    fun decode(context: Context, name: String, targetPixels: Int): Bitmap {
        require(targetPixels > 0)
        val file = resolve(context, name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(file)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val width = info.size.width
                val height = info.size.height
                val factor = minOf(1.0, targetPixels.toDouble() / maxOf(width, height))
                decoder.setTargetSize(
                    maxOf(1, (width * factor).toInt()),
                    maxOf(1, (height * factor).toInt()),
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPixels) {
            sample *= 2
        }
        return requireNotNull(BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ))
    }
}
