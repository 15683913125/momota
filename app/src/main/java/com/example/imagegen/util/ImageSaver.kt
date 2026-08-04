package com.example.imagegen.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 图片保存到相册工具类
 *  - Android 10+ 用 MediaStore API（无需写权限）
 *  - Android 9 及以下直接写文件到 Pictures/ImageGenerator（需 WRITE_EXTERNAL_STORAGE）
 */
object ImageSaver {

    private const val RELATIVE_DIR = "Pictures/ImageGenerator"

    /** 保存 Bitmap 到相册，返回文件 Uri；失败返回 null */
    fun saveToGallery(context: Context, bitmap: Bitmap, prefix: String = "img"): Uri? {
        val displayName = makeFileName(prefix)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(context, bitmap, displayName)
        } else {
            saveViaFile(context, bitmap, displayName)
        }
    }

    /** Android 10+：MediaStore 插入 + 写入 + IS_PENDING 流程 */
    private fun saveViaMediaStore(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null

        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    throw IOException("compress failed")
                }
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: IOException) {
            resolver.delete(uri, null, null)
            return null
        }
    }

    /** Android 9 及以下：直接写文件，需要 WRITE_EXTERNAL_STORAGE 权限 */
    private fun saveViaFile(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): Uri? {
        if (!hasWritePermission(context)) return null

        val pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val dir = File(pictures, "ImageGenerator").apply { if (!exists()) mkdirs() }
        val outFile = File(dir, displayName)

        return try {
            FileOutputStream(outFile).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                    throw IOException("compress failed")
                }
            }
            // 通知相册扫描
            val mediaUri = Uri.fromFile(outFile)
            context.sendBroadcast(android.content.Intent(Intent_MEDIA_SCANNER_SCAN_FILE, mediaUri))
            mediaUri
        } catch (e: IOException) {
            null
        }
    }

    private fun hasWritePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun makeFileName(prefix: String): String {
        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "${prefix}_$time.jpg"
    }

    // 兼容旧常量
    private const val Intent_MEDIA_SCANNER_SCAN_FILE = "android.intent.action.MEDIA_SCANNER_SCAN_FILE"
}
