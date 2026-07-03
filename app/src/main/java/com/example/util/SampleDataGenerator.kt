package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object SampleDataGenerator {
    private const val TAG = "SampleDataGenerator"

    /**
     * Generates a multi-page demo PDF and a demo CBZ comic book inside the user's selected SAF directory.
     */
    suspend fun generateDemoFilesIfEmpty(context: Context, folderUriStr: String) = withContext(Dispatchers.IO) {
        try {
            val rootUri = Uri.parse(folderUriStr)
            val rootDir = DocumentFile.fromTreeUri(context, rootUri)
            if (rootDir == null || !rootDir.exists() || !rootDir.isDirectory) {
                return@withContext
            }

            // Check if the directory already contains any PDFs or CBZs
            val files = rootDir.listFiles()
            val hasReaderFiles = files.any { file ->
                val name = file.name ?: ""
                name.lowercase().endsWith(".pdf") || name.lowercase().endsWith(".cbz")
            }

            if (!hasReaderFiles) {
                Log.d(TAG, "Selected folder is empty of PDF/CBZ files. Injecting high-quality samples...")
                createSamplePdf(context, rootDir)
                createSampleCbz(context, rootDir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating sample files", e)
        }
    }

    /**
     * Programmatically constructs a multi-page PDF document using Android's native PdfDocument.
     */
    private fun createSamplePdf(context: Context, directory: DocumentFile) {
        val pdfFile = directory.createFile("application/pdf", "Offline Reader User Guide.pdf") ?: return
        val pdfDocument = PdfDocument()

        val paint = Paint()
        val titlePaint = Paint().apply {
            color = Color.rgb(33, 150, 243)
            textSize = 28f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            color = Color.BLACK
            textSize = 14f
        }
        val accentPaint = Paint().apply {
            color = Color.rgb(76, 175, 80)
            textSize = 16f
            isFakeBoldText = true
        }

        // --- PAGE 1: Welcome Screen ---
        var pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 standard size
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        // Header Background
        paint.color = Color.rgb(240, 244, 248)
        canvas.drawRect(0f, 0f, 595f, 120f, paint)

        canvas.drawText("OFFLINE READER USER GUIDE", 40f, 70f, titlePaint)
        canvas.drawText("Your premium local PDF & Comic Reader companion.", 40f, 100f, textPaint)

        var y = 180f
        canvas.drawText("Thank you for choosing Offline Reader!", 40f, y, accentPaint)
        y += 40f
        canvas.drawText("This app runs completely sandbox-isolated, meaning absolutely zero tracking,", 40f, y, textPaint)
        y += 20f
        canvas.drawText("zero cellular or network overhead, and blistering-fast rendering speeds.", 40f, y, textPaint)
        y += 40f
        canvas.drawText("Core Features Installed:", 40f, y, accentPaint)
        y += 30f
        canvas.drawText("• Fast Canvas-based Double Tap & Pinch Dynamic Zoom levels.", 60f, y, textPaint)
        y += 20f
        canvas.drawText("• Standard Jetpack Compose Lists for ultra-smooth vertical continuous scrolling.", 60f, y, textPaint)
        y += 20f
        canvas.drawText("• Quick Switch Drawer to toggle books instantly from the sidebar menu.", 60f, y, textPaint)
        y += 20f
        canvas.drawText("• Standard Room persistence storing Favorites, Bookmarks, and Reading States.", 60f, y, textPaint)
        y += 20f
        canvas.drawText("• Fully-fledged AMOLED Black, Dynamic Material You, and Brightness adjustments.", 60f, y, textPaint)

        // Footer
        paint.color = Color.rgb(120, 120, 120)
        paint.textSize = 10f
        canvas.drawText("Page 1 of 3", 500f, 800f, paint)

        pdfDocument.finishPage(page)

        // --- PAGE 2: Gestures guide ---
        pageInfo = PdfDocument.PageInfo.Builder(595, 842, 2).create()
        page = pdfDocument.startPage(pageInfo)
        canvas = page.canvas

        // Header
        paint.color = Color.rgb(240, 244, 248)
        canvas.drawRect(0f, 0f, 595f, 100f, paint)
        canvas.drawText("GESTURES & CONTROLS CHEATSHEET", 40f, 60f, titlePaint)

        y = 150f
        canvas.drawText("Touch Interaction Map", 40f, y, accentPaint)
        y += 40f
        canvas.drawText("1. Edge Tap Zone Navigation:", 40f, y, textPaint)
        y += 20f
        canvas.drawText("   Tapping the leftmost 20% or rightmost 20% of your viewport triggers", 40f, y, textPaint)
        y += 20f
        canvas.drawText("   instant Page-Backward and Page-Forward transitions.", 40f, y, textPaint)

        y += 40f
        canvas.drawText("2. Center Tap Menu:", 40f, y, textPaint)
        y += 20f
        canvas.drawText("   Tapping the center 60% of your reader screen displays/hides", 40f, y, textPaint)
        y += 20f
        canvas.drawText("   the high-contrast Material 3 header bars and sliders.", 40f, y, textPaint)

        y += 40f
        canvas.drawText("3. Pinch-to-Zoom & Double Tap:", 40f, y, textPaint)
        y += 20f
        canvas.drawText("   Double tapping automatically centers and magnifies the active book image,", 40f, y, textPaint)
        y += 20f
        canvas.drawText("   allowing seamless detail inspections on tablets or dense mangas.", 40f, y, textPaint)

        canvas.drawText("Page 2 of 3", 500f, 800f, paint)
        pdfDocument.finishPage(page)

        // --- PAGE 3: Support / Future formats ---
        pageInfo = PdfDocument.PageInfo.Builder(595, 842, 3).create()
        page = pdfDocument.startPage(pageInfo)
        canvas = page.canvas

        paint.color = Color.rgb(240, 244, 248)
        canvas.drawRect(0f, 0f, 595f, 100f, paint)
        canvas.drawText("FUTURE EXPANSIONS & COMPLIANCE", 40f, 60f, titlePaint)

        y = 150f
        canvas.drawText("Architecture Compliance", 40f, y, accentPaint)
        y += 35f
        canvas.drawText("Offline Reader uses decoupled, clean architectural repositories.", 40f, y, textPaint)
        y += 20f
        canvas.drawText("We support lazy loading and strict low-memory caching so your app", 40f, y, textPaint)
        y += 20f
        canvas.drawText("stays completely stable even when indexing 10,000+ files.", 40f, y, textPaint)

        y += 40f
        canvas.drawText("Extending File Formats:", 40f, y, accentPaint)
        y += 25f
        canvas.drawText("To add support for other formats (like EPUB, CBR, or MOBI), simply subclass", 40f, y, textPaint)
        y += 20f
        canvas.drawText("our BookRenderer class or configure the SQLite Room entities.", 40f, y, textPaint)

        canvas.drawText("Page 3 of 3", 500f, 800f, paint)
        pdfDocument.finishPage(page)

        // Save PDF to SAF URI stream
        try {
            context.contentResolver.openOutputStream(pdfFile.uri)?.use { outputStream ->
                pdfDocument.writeTo(outputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing PDF bytes to SAF document file", e)
        } finally {
            pdfDocument.close()
        }
    }

    /**
     * Programmatically constructs a beautiful CBZ (ZIP) file containing drawn graphic pages.
     */
    private fun createSampleCbz(context: Context, directory: DocumentFile) {
        val cbzFile = directory.createFile("application/zip", "Amazing Superhero Comic.cbz") ?: return

        try {
            context.contentResolver.openOutputStream(cbzFile.uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zos ->
                    // Create 4 distinct colorful comic book pages
                    val pageColors = listOf(
                        Color.rgb(255, 235, 59),  // Yellow background
                        Color.rgb(178, 235, 242), // Cyan background
                        Color.rgb(248, 187, 208), // Pink background
                        Color.rgb(200, 230, 201)  // Green background
                    )

                    for (i in 1..4) {
                        val bitmap = Bitmap.createBitmap(800, 1200, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bitmap)

                        // 1. Draw page background
                        canvas.drawColor(pageColors[i - 1])

                        // 2. Draw border frame
                        val paint = Paint().apply {
                            color = Color.BLACK
                            style = Paint.Style.STROKE
                            strokeWidth = 15f
                        }
                        canvas.drawRect(20f, 20f, 780f, 1180f, paint)

                        // 3. Draw Title Box
                        paint.apply {
                            style = Paint.Style.FILL
                            color = Color.BLACK
                        }
                        canvas.drawRect(60f, 60f, 740f, 180f, paint)

                        val textPaint = Paint().apply {
                            color = Color.WHITE
                            textSize = 36f
                            isFakeBoldText = true
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText("THE OFFLINE COMIC RUN!", 400f, 130f, textPaint)

                        // 4. Draw Comic Content panel drawings
                        paint.apply {
                            style = Paint.Style.STROKE
                            strokeWidth = 8f
                            color = Color.rgb(211, 47, 47) // Red
                        }
                        canvas.drawRect(80f, 220f, 720f, 850f, paint)

                        // Draw a simple graphic "Stick Figure Hero" action drawing
                        paint.apply {
                            style = Paint.Style.FILL
                            color = Color.BLACK
                            strokeWidth = 6f
                        }
                        // head
                        canvas.drawCircle(400f, 400f, 60f, paint)
                        // body
                        canvas.drawLine(400f, 460f, 400f, 680f, paint)
                        // arms
                        canvas.drawLine(400f, 520f, 250f, 450f, paint) // dynamic punching pose!
                        canvas.drawLine(400f, 520f, 550f, 450f, paint)
                        // legs
                        canvas.drawLine(400f, 680f, 280f, 800f, paint)
                        canvas.drawLine(400f, 680f, 520f, 800f, paint)

                        // Superhero Cape
                        paint.color = Color.rgb(211, 47, 47)
                        val capePath = android.graphics.Path().apply {
                            moveTo(400f, 460f)
                            lineTo(200f, 580f)
                            lineTo(280f, 680f)
                            close()
                        }
                        canvas.drawPath(capePath, paint)

                        // Speech Bubble
                        paint.color = Color.WHITE
                        canvas.drawRoundRect(450f, 270f, 700f, 370f, 20f, 20f, paint)
                        // bubble tail
                        val bubbleTail = android.graphics.Path().apply {
                            moveTo(480f, 370f)
                            lineTo(440f, 400f)
                            lineTo(510f, 370f)
                            close()
                        }
                        canvas.drawPath(bubbleTail, paint)

                        val bubbleTextPaint = Paint().apply {
                            color = Color.BLACK
                            textSize = 20f
                            isFakeBoldText = true
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText("I RUN COMPLETELY", 575f, 315f, bubbleTextPaint)
                        canvas.drawText("OFFLINE IN COMIC ${i}!", 575f, 345f, bubbleTextPaint)

                        // 5. Draw Page Number Label
                        val pageLabelPaint = Paint().apply {
                            color = Color.BLACK
                            textSize = 28f
                            isFakeBoldText = true
                            textAlign = Paint.Align.CENTER
                        }
                        canvas.drawText("CHAPTER ${i} • PAGE ${i}", 400f, 1050f, pageLabelPaint)

                        // Compress and write to ZIP
                        val byteStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteStream)
                        val bytes = byteStream.toByteArray()

                        val zipEntry = ZipEntry("page_${String.format("%03d", i)}.jpg")
                        zos.putNextEntry(zipEntry)
                        zos.write(bytes)
                        zos.closeEntry()

                        bitmap.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error writing CBZ pages to SAF document file", e)
        }
    }
}
