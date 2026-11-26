package com.example.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.ByteArrayOutputStream

data class PdfPageThumbnail(
    val pageIndex: Int,
    val bitmap: Bitmap,
)

object PdfPageProcessor {
    fun getPdfPageCount(context: Context, uri: Uri): Int {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("تعذر فتح ملف الـ PDF.")
        return pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                renderer.pageCount
            }
        }
    }

    fun renderPdfThumbnails(
        context: Context,
        uri: Uri,
        maxThumbWidth: Int = 340,
        maxPagesToPreview: Int = 24,
    ): List<PdfPageThumbnail> {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("تعذر فتح ملف الـ PDF.")
        return pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                val count = minOf(renderer.pageCount, maxPagesToPreview)
                (0 until count).map { idx ->
                    renderer.openPage(idx).use { page ->
                        val scale = maxThumbWidth.toFloat() / maxOf(1, page.width)
                        val w = maxOf(1, (page.width * scale).toInt())
                        val h = maxOf(1, (page.height * scale).toInt())
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        PdfPageThumbnail(idx, bmp)
                    }
                }
            }
        }
    }

    fun renderPdfPagesForAi(
        context: Context,
        uri: Uri,
        pageIndices: List<Int>,
        maxEdgePx: Int = 1500,
    ): List<Bitmap> {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw IllegalStateException("تعذر فتح ملف الـ PDF.")
        return pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                pageIndices.mapNotNull { idx ->
                    if (idx !in 0 until renderer.pageCount) return@mapNotNull null
                    renderer.openPage(idx).use { page ->
                        val longest = maxOf(page.width, page.height).coerceAtLeast(1)
                        val scale = minOf(2.5f, maxEdgePx.toFloat() / longest)
                        val w = maxOf(1, (page.width * scale).toInt())
                        val h = maxOf(1, (page.height * scale).toInt())
                        val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(raw).drawColor(Color.WHITE)
                        page.render(raw, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        compressBitmapForAi(raw)
                    }
                }
            }
        }
    }

    fun decodeImageUriForAi(
        context: Context,
        uri: Uri,
        maxEdgePx: Int = 1500,
    ): Bitmap {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        val longest = maxOf(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longest / sample > maxEdgePx * 2) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: throw IllegalStateException("تعذر قراءة الصورة المختارة.")

        val scaled = scaleDownIfNeeded(decoded, maxEdgePx)
        return compressBitmapForAi(scaled)
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxEdgePx: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxEdgePx) return bitmap
        val ratio = maxEdgePx.toFloat() / longest
        val w = maxOf(1, (bitmap.width * ratio).toInt())
        val h = maxOf(1, (bitmap.height * ratio).toInt())
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun compressBitmapForAi(bitmap: Bitmap): Bitmap {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        val bytes = baos.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: bitmap
    }
}
