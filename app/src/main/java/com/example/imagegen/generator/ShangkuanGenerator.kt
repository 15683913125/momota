package com.example.imagegen.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 商宽表单的一个字段定义 */
data class ShangkuanField(
    val x: Int,
    val y: Int,
    val fieldIndex: Int,        // -1 表示 datetime
    val fontSize: Int,
    val color: Int = Color.BLACK,
    val align: String = "left", // left / center / right
    val fieldType: String = "normal"  // normal / signature / datetime / address
)

/** 一张表单模板 + 字段列表 */
data class ShangkuanForm(
    val templatePath: String,
    val fields: List<ShangkuanField>
)

/**
 * 商宽处理生成器，等价 Python 版 商宽处理.process_shangkuan
 *
 * 输入：textData = "用户名称+联系人+联系电话+客户帐号+安装地址+签约带宽+代维人员"
 * 输出：两张图片 [宽带业务确认单, 感谢信]
 *
 * 注意：
 *  - 6 个字段格式会自动复制用户名称作为联系人（变为 7 字段）
 *  - normal 用主字体，signature 用签名字体
 *  - datetime 用当前日期 YYYY.MM.DD
 *  - address 截取前 20 位
 */
object ShangkuanGenerator {

    /** assets 下的字体目录 */
    private const val FONTS_DIR = "fonts"

    /** 处理入口，返回两张图片；失败或字段不合法返回 null */
    fun generate(context: Context, textData: String): List<Bitmap>? {
        if (textData.isBlank()) return null

        // 1. 解析字段
        var fields = textData.split("+").map { it.trim() }
        if (fields.size == 6) {
            // 6 字段格式：用户名称+联系电话+客户帐号+安装地址+签约带宽+代维人员
            // 复制用户名称作为联系人
            fields = listOf(
                fields[0], fields[0],
                fields[1], fields[2], fields[3], fields[4], fields[5]
            )
        }
        if (fields.size < 7) return null

        // 2. 表单配置（坐标来自原 Python 商宽处理.py process_shangkuan 函数）
        val forms = listOf(
            ShangkuanForm(
                templatePath = "images/商宽/宽带业务确认单.png",
                fields = listOf(
                    ShangkuanField(200, 28, 0, 17, fieldType = "normal"),
                    ShangkuanField(440, 28, -1, 18, fieldType = "datetime"),
                    ShangkuanField(200, 50, 1, 18, fieldType = "normal"),
                    ShangkuanField(440, 50, 2, 17, fieldType = "normal"),
                    ShangkuanField(182, 93, 3, 20, fieldType = "normal"),
                    ShangkuanField(440, 72, 4, 18, fieldType = "address"),
                    ShangkuanField(110, 145, 5, 17, fieldType = "normal"),
                    ShangkuanField(261, 145, 5, 17, fieldType = "normal"),
                    ShangkuanField(122, 555, 6, 19, fieldType = "normal"),
                    ShangkuanField(120, 580, 1, 22, fieldType = "signature")
                )
            ),
            ShangkuanForm(
                templatePath = "images/商宽/感谢信.png",
                fields = listOf(
                    ShangkuanField(181, 157, 0, 25, fieldType = "normal"),
                    ShangkuanField(900, 697, 1, 32, fieldType = "signature")
                )
            )
        )

        // 3. 加载字体（随机主字体 + 不同签名字体）
        val fontFiles = listFontFiles(context)
        val (mainPath, signaturePath) = pickFonts(fontFiles)
        val mainTypeface = loadTypeface(context, mainPath)
        val signatureTypeface = loadTypeface(context, signaturePath)

        // 4. 处理每个表单
        val results = ArrayList<Bitmap>()
        for (form in forms) {
            val bmp = drawForm(context, form, fields, mainTypeface, signatureTypeface) ?: continue
            results.add(bmp)
        }

        return if (results.isEmpty()) null else results
    }

    private fun drawForm(
        context: Context,
        form: ShangkuanForm,
        fields: List<String>,
        mainTypeface: Typeface,
        signatureTypeface: Typeface
    ): Bitmap? {
        // 加载模板
        val src = loadAssetBitmap(context, form.templatePath) ?: return null
        val output = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val currentDate = SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

        for (f in form.fields) {
            // 取文本内容
            val text: String = when {
                f.fieldType == "datetime" -> currentDate
                f.fieldIndex in fields.indices -> fields[f.fieldIndex]
                else -> ""
            }
            if (text.isEmpty()) continue

            val drawText = if (f.fieldType == "address") text.take(20) else text

            // 选字体
            val typeface = if (f.fieldType == "signature") signatureTypeface else mainTypeface
            paint.textSize = f.fontSize.toFloat()
            paint.typeface = typeface

            // 计算对齐后的 x
            val textWidth = paint.measureText(drawText)
            val adjustedX = when (f.align) {
                "center" -> f.x - textWidth / 2
                "right" -> f.x - textWidth
                else -> f.x.toFloat()
            }

            // Canvas.drawText 的 y 是基线位置，原 Python 的 y 是顶部位置
            // 基线 = y + ascent 取反（ ascent 是负值）
            val fm = paint.fontMetrics
            val baseline = f.y + (-fm.ascent)
            canvas.drawText(drawText, adjustedX, baseline, paint)
        }

        src.recycle()
        return output
    }

    /** 列出 assets/fonts 目录下所有字体文件路径 */
    private fun listFontFiles(context: Context): List<String> {
        return try {
            val files = context.assets.list(FONTS_DIR) ?: return emptyList()
            files.filter {
                it.endsWith(".ttf", true) ||
                it.endsWith(".otf", true) ||
                it.endsWith(".ttc", true)
            }.map { "$FONTS_DIR/$it" }
        } catch (e: IOException) {
            emptyList()
        }
    }

    /** 随机选主字体 + 不同的签名字体 */
    private fun pickFonts(fontFiles: List<String>): Pair<String?, String?> {
        if (fontFiles.isEmpty()) return null to null
        val mainPath = fontFiles.random()
        val signaturePath = if (fontFiles.size >= 2) {
            (fontFiles - mainPath).random()
        } else {
            mainPath
        }
        return mainPath to signaturePath
    }

    /** 加载字体，失败回退到系统默认 */
    private fun loadTypeface(context: Context, path: String?): Typeface {
        if (path == null) return Typeface.DEFAULT
        return try {
            val outFile = java.io.File(context.cacheDir, path.replace('/', '_'))
            if (!outFile.exists() || outFile.length() == 0L) {
                context.assets.open(path).use { input ->
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> input.copyTo(out) }
                }
            }
            Typeface.createFromFile(outFile)
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
    }

    private fun loadAssetBitmap(context: Context, path: String): Bitmap? {
        return try {
            context.assets.open(path).use { BitmapFactory.decodeStream(it) }
        } catch (_: IOException) {
            null
        }
    }
}
