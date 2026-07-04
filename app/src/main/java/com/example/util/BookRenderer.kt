package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object BookRenderer {
    private const val TAG = "BookRenderer"

    // In-memory cache for rendered pages to ensure butter-smooth scrolling
    private val pageCache = object : LruCache<String, Bitmap>(30) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024 // cache size in KB
        }
    }

    // Helper to generate a unique cache key
    private fun getCacheKey(bookId: String, pageIndex: Int, width: Int, height: Int): String {
        return "${bookId}_${pageIndex}_${width}_${height}"
    }

    /**
     * Retrieves a page bitmap from cache or renders it.
     */
    suspend fun getPageBitmap(
        context: Context,
        bookId: String,
        filePath: String,
        format: String,
        pageIndex: Int,
        maxWidth: Int = 1080,
        maxHeight: Int = 1920
    ): Bitmap? = withContext(Dispatchers.IO) {
        val cacheKey = getCacheKey(bookId, pageIndex, maxWidth, maxHeight)
        pageCache.get(cacheKey)?.let { return@withContext it }

        val bitmap = try {
            if (format.lowercase() == "pdf") {
                renderPdfPage(context, filePath, pageIndex, maxWidth, maxHeight)
            } else {
                renderCbzPage(context, filePath, pageIndex, maxWidth, maxHeight)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error rendering page $pageIndex for $filePath", e)
            null
        }

        if (bitmap != null) {
            pageCache.put(cacheKey, bitmap)
        }
        return@withContext bitmap
    }

    /**
     * Clears all cached pages from memory
     */
    fun clearCache() {
        pageCache.evictAll()
    }

    /**
     * Renders a specific page of a PDF using standard Android PdfRenderer.
     */
    private fun renderPdfPage(
        context: Context,
        filePath: String,
        pageIndex: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? {
        val uri = Uri.parse(filePath)
        val pfd = openFileDescriptor(context, uri) ?: return null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        try {
            renderer = PdfRenderer(pfd)
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null

            page = renderer.openPage(pageIndex)

            // Determine appropriate scale to fit maxWidth/maxHeight while preserving aspect ratio
            val originalWidth = page.width
            val originalHeight = page.height
            val scale = minOf(
                maxWidth.toFloat() / originalWidth,
                maxHeight.toFloat() / originalHeight,
                2.0f // cap zoom/scale at 2x to avoid massive memory usage
            )

            val destWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
            val destHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render PDF page $pageIndex", e)
            return null
        } finally {
            try { page?.close() } catch (ignored: Exception) {}
            try { renderer?.close() } catch (ignored: Exception) {}
            try { pfd.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Extracts and decodes a specific page of a CBZ (ZIP) file.
     */
    private fun renderCbzPage(
        context: Context,
        filePath: String,
        pageIndex: Int,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap? {
        val uri = Uri.parse(filePath)
        val imageEntries = getCbzImageEntries(context, uri)
        if (pageIndex < 0 || pageIndex >= imageEntries.size) return null
        val targetEntryName = imageEntries[pageIndex]

        var inputStream: InputStream? = null
        var zipInputStream: ZipInputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return null
            zipInputStream = ZipInputStream(inputStream)
            var entry: ZipEntry? = zipInputStream.nextEntry
            while (entry != null) {
                if (entry.name == targetEntryName) {
                    // Found the target image, decode it with sample size to conserve memory
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    val byteBytes = zipInputStream.readBytes()
                    BitmapFactory.decodeByteArray(byteBytes, 0, byteBytes.size, options)

                    // Calculate inSampleSize to avoid OOM
                    options.inSampleSize = calculateInSampleSize(options, maxWidth, maxHeight)
                    options.inJustDecodeBounds = false

                    return BitmapFactory.decodeByteArray(byteBytes, 0, byteBytes.size, options)
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to render CBZ page $pageIndex", e)
            return null
        } finally {
            try { zipInputStream?.close() } catch (ignored: Exception) {}
            try { inputStream?.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Gets a sorted list of image entry names inside a CBZ file.
     */
    fun getCbzImageEntries(context: Context, uri: Uri): List<String> {
        val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
        val entries = mutableListOf<String>()
        var inputStream: InputStream? = null
        var zipInputStream: ZipInputStream? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
            zipInputStream = ZipInputStream(inputStream)
            var entry: ZipEntry? = zipInputStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val ext = entry.name.substringAfterLast('.', "").lowercase()
                    if (ext in imageExtensions && !entry.name.contains("__MACOSX") && !entry.name.startsWith(".")) {
                        entries.add(entry.name)
                    }
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing CBZ entries", e)
        } finally {
            try { zipInputStream?.close() } catch (ignored: Exception) {}
            try { inputStream?.close() } catch (ignored: Exception) {}
        }
        // Natural sorting of image entries (so page2 < page10, unlike plain string sort)
        return entries.sortedWith(naturalOrderComparator)
    }

    /**
     * Natural order comparator: splits names into digit/non-digit chunks so that
     * numeric parts are compared by value (page2 < page10) instead of lexicographically
     * (which would otherwise put page10 before page2).
     */
    private val naturalOrderComparator = Comparator<String> { a, b ->
        val chunkRegex = Regex("\\d+|\\D+")
        val partsA = chunkRegex.findAll(a).map { it.value }.toList()
        val partsB = chunkRegex.findAll(b).map { it.value }.toList()
        val len = minOf(partsA.size, partsB.size)
        var result = 0
        for (i in 0 until len) {
            val pa = partsA[i]
            val pb = partsB[i]
            val bothNumeric = pa.isNotEmpty() && pb.isNotEmpty() && pa[0].isDigit() && pb[0].isDigit()
            val cmp = if (bothNumeric) {
                (pa.toLongOrNull() ?: 0L).compareTo(pb.toLongOrNull() ?: 0L)
            } else {
                pa.compareTo(pb, ignoreCase = true)
            }
            if (cmp != 0) {
                result = cmp
                break
            }
        }
        if (result != 0) result else partsA.size - partsB.size
    }

    /**
     * Helper to open file descriptor for any Uri (SAF content:// or file://)
     */
    private fun openFileDescriptor(context: Context, uri: Uri): ParcelFileDescriptor? {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")
        } catch (e: Exception) {
            Log.e(TAG, "Could not open ParcelFileDescriptor for Uri: $uri", e)
            null
        }
    }

    /**
     * Standard utility to calculate sub-sampling of bitmaps for memory protection.
     */
    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    /**
     * Generates a permanent cover thumbnail and saves it in app cache.
     * Returns the local file path of the thumbnail, or null if failed.
     */
    suspend fun generateCoverThumbnail(
        context: Context,
        bookId: String,
        filePath: String,
        format: String
    ): String? = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDirs(), "book_covers").apply { mkdirs() }
        val coverFile = File(cacheDir, "${bookId.hashCode()}.png")

        if (coverFile.exists() && coverFile.length() > 0) {
            return@withContext coverFile.absolutePath
        }

        // Generate the first page bitmap
        val bitmap = getPageBitmap(context, bookId, filePath, format, 0, maxWidth = 300, maxHeight = 450)
        if (bitmap == null) {
            return@withContext null
        }

        try {
            FileOutputStream(coverFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
            }
            return@withContext coverFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving cover thumbnail", e)
            null
        }
    }

    private fun Context.cacheDirs(): File {
        return this.cacheDir
    }
}
