package com.example.imagegen.generator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.IOException
import kotlin.math.min

/**
 * 标签端口图片生成器，等价 Python 版 image_processor.generate_label_image
 *
 * 流程：
 *  1. 从端口提取下行数 N；N∈{4,8} 用模板 8 文件夹，否则用模板 16 文件夹
 *  2. 在 assets/images/8 或 assets/images/16 随机挑一张模板
 *  3. 检测白色标签区域，下方放二维码、上方放文字
 *  4. 生成 35 位随机码 -> 透明背景二维码 -> 缩放到 40% 可用空间
 *  5. 文字：帐号:脱敏后的tag / 端口:port 或 null
 *  6. 自适应字体大小、智能 -POS 换行，绘制黑色文字
 */
object LabelImageGenerator {

    private const val FIXED_FONT_SIZE = 32

    /** 入口：生成图片 Bitmap，失败返回 null */
    fun generate(context: Context, tag: String?, port: String?): Bitmap? {
        if (tag.isNullOrEmpty()) return null

        val portValue = port?.trim() ?: ""
        val portNonNull = portValue.isNotEmpty() &&
            !portValue.equals("null", ignoreCase = true) &&
            !portValue.equals("none", ignoreCase = true)

        // 1. 选模板文件夹
        val downlink = MessageParser.extractDownlinkNumber(portValue)
        val folder = if (downlink == 4 || downlink == 8) "8" else "16"

        val templatePath = pickRandomTemplate(context, folder) ?: return null
        val srcBitmap = loadAssetBitmap(context, templatePath) ?: return null

        // 2. 检测白色标签区域
        val regions = WhiteLabelDetector.findWhiteLabel(srcBitmap)
        if (regions.isEmpty()) return null

        // 3. 准备可变输出 Bitmap
        val output = srcBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val qrRegion: LabelRegion
        val textRegion: LabelRegion
        if (regions.size >= 2) {
            qrRegion = regions[0]
            textRegion = regions[1]
        } else {
            qrRegion = regions[0]
            textRegion = regions[0]
        }

        // 4. 生成并绘制二维码
        drawQrCode(canvas, qrRegion)

        // 5. 文字内容
        val maskedTag = MessageParser.maskPhoneOrAccount(tag)
        val textLines = ArrayList<String>()
        textLines.add("帐号:$maskedTag")
        if (portNonNull) {
            val cleanPort = portValue.replace("：", ":")
            textLines.add("端口:$cleanPort")
        } else {
            textLines.add("端口:null")
        }

        // 6. 绘制文字
        drawTextOnLabel(canvas, textRegion, textLines, portNonNull, portValue,
            loadCustomTypeface(context, "fonts2/msyh.ttc"))

        srcBitmap.recycle()
        return output
    }

    /** 从 assets/images/$folder 随机挑一张图片路径 */
    private fun pickRandomTemplate(context: Context, folder: String): String? {
        val dir = "images/$folder"
        return try {
            val files = context.assets.list(dir) ?: return null
            val imgs = files.filter {
                it.endsWith(".jpg", true) ||
                it.endsWith(".jpeg", true) ||
                it.endsWith(".png", true) ||
                it.endsWith(".bmp", true)
            }
            if (imgs.isEmpty()) null else "$dir/${imgs.random()}"
        } catch (e: IOException) {
            null
        }
    }

    private fun loadAssetBitmap(context: Context, path: String): Bitmap? {
        return try {
            context.assets.open(path).use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun drawQrCode(canvas: Canvas, region: LabelRegion) {
        val content = QRCodeGenerator.generateRandomCode()
        val qrBmp = QRCodeGenerator.generate(content, boxSize = 3) ?: return

        val padding = 8
        val availW = region.width - padding * 2
        val availH = region.height - padding * 2
        if (availW <= 0 || availH <= 0) {
            qrBmp.recycle()
            return
        }
        val maxSize = (min(availW, availH) * 0.4).toInt().coerceAtLeast(20)
        val scaled = Bitmap.createScaledBitmap(qrBmp, maxSize, maxSize, true)

        val posX = region.x + (region.width - maxSize) / 2
        val posY = region.y + region.height - maxSize - 20

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(scaled, posX.toFloat(), posY.toFloat(), paint)

        if (scaled !== qrBmp) scaled.recycle()
        qrBmp.recycle()
    }

    private fun drawTextOnLabel(
        canvas: Canvas,
        region: LabelRegion,
        textLines: List<String>,
        portNonNull: Boolean,
        portText: String,
        typeface: Typeface
    ) {
        val padding = 8
        val availWidth = region.width - padding * 2
        val availHeight = region.height - padding * 2
        if (availWidth <= 0 || availHeight <= 0) return

        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            this.typeface = typeface
            isFakeBoldText = true
        }

        // 计算字体大小
        val fontSize = if (portNonNull) {
            var maxFs = 12 + (region.width - 100) / 25
            maxFs = maxFs.coerceIn(12, 28)
            // 从大到小尝试，找到第一个能放下的
            calculateBestFontSize(maxFs, availWidth - 15, availHeight, textLines, basePaint, portText)
        } else {
            FIXED_FONT_SIZE
        }

        basePaint.textSize = fontSize.toFloat()

        // 换行
        val maxWidth = (availWidth - 15).toFloat()
        val wrappedLines = ArrayList<String>()
        for (line in textLines) {
            if (portNonNull && line.contains("端口:") && line.contains("-POS")) {
                wrappedLines.addAll(smartWrapWithPos(basePaint, line, maxWidth))
            } else {
                wrappedLines.addAll(wrapText(basePaint, line, maxWidth))
            }
            if (wrappedLines.size > 4) break
        }
        val finalLines = wrappedLines.take(4)

        // 行高与间距
        val fm = basePaint.fontMetrics
        val lineHeight = (fm.descent - fm.ascent)
        val lineSpacing = fontSize * 0.02f
        val totalLineHeight = (lineHeight + lineSpacing)

        var x = (region.x + padding).toFloat()
        var y = (region.y + padding).toFloat() + (-fm.ascent)

        for (line in finalLines) {
            canvas.drawText(line, x, y, basePaint)
            y += totalLineHeight
        }
    }

    /**
     * 自适应字体大小：从 maxFs 到 8 倒序尝试，第一个满足"总高度 <= 可用高度*0.7"的即返回
     * 等价 Python FontSizeCalculator.calculate_best_font_size
     */
    private fun calculateBestFontSize(
        maxFs: Int,
        availWidth: Int,
        availHeight: Int,
        textLines: List<String>,
        paint: Paint,
        portText: String
    ): Int {
        val minFs = 8
        val lineSpacingRatio = 0.35f

        for (fs in maxFs downTo minFs) {
            paint.textSize = fs.toFloat()
            val fm = paint.fontMetrics
            val lineHeight = fm.descent - fm.ascent
            val lineSpacing = fs * lineSpacingRatio
            val totalLineHeight = lineHeight + lineSpacing

            // 换行
            val lines = ArrayList<String>()
            for (line in textLines) {
                if (line.contains("端口:") && line.contains("-POS")) {
                    lines.addAll(smartWrapWithPos(paint, line, availWidth.toFloat()))
                } else {
                    lines.addAll(wrapText(paint, line, availWidth.toFloat()))
                }
                if (lines.size > 4) break
            }
            val numLines = min(lines.size, 4)
            val totalHeight = numLines * totalLineHeight - lineSpacing
            if (totalHeight <= availHeight * 0.7f) {
                return fs
            }
        }
        return minFs
    }

    /**
     * 智能换行：检查 -POS 是否会写在第一行
     *  - 若 "端口:" + -POS 前部分宽度 <= maxWidth，则强制换行
     *  - 否则整段做自适应换行
     * 等价 Python image_processor.auto_wrap_text
     */
    private fun smartWrapWithPos(paint: Paint, text: String, maxWidth: Float): List<String> {
        if (!text.contains("端口:") || !text.contains("-POS")) {
            return wrapText(paint, text, maxWidth)
        }
        val prefix = "端口:"
        val portAddress = text.substring(prefix.length)
        val posIndex = portAddress.indexOf("-POS")
        if (posIndex == -1) return wrapText(paint, text, maxWidth)

        val beforePos = portAddress.substring(0, posIndex)
        val firstLine = prefix + beforePos
        val firstWidth = paint.measureText(firstLine)
        val safeMaxWidth = maxWidth * 0.9f

        return if (firstWidth <= safeMaxWidth) {
            val result = ArrayList<String>()
            result.add(firstLine)
            result.addAll(wrapText(paint, portAddress.substring(posIndex), maxWidth))
            result
        } else {
            val result = ArrayList<String>()
            result.addAll(wrapText(paint, prefix + beforePos, maxWidth))
            result.addAll(wrapText(paint, portAddress.substring(posIndex), maxWidth))
            result
        }
    }

    /** 自适应换行：逐字累加，超过宽度就换行，最多 4 行 */
    private fun wrapText(paint: Paint, text: String, maxWidth: Float): List<String> {
        val lines = ArrayList<String>()
        if (text.isEmpty()) return lines
        val current = StringBuilder()

        for (ch in text) {
            val testLine = current.toString() + ch
            if (paint.measureText(testLine) <= maxWidth) {
                current.append(ch)
            } else {
                if (current.isNotEmpty()) {
                    lines.add(current.toString())
                    current.setLength(0)
                }
                // 单字符仍超宽，单独塞一行避免死循环
                if (paint.measureText(ch.toString()) > maxWidth && current.isEmpty()) {
                    lines.add(ch.toString())
                } else {
                    current.append(ch)
                }
            }
            if (lines.size >= 4) return lines.take(4)
        }
        if (current.isNotEmpty() && lines.size < 4) {
            lines.add(current.toString())
        }
        return lines.take(4)
    }

    /** 加载自定义字体（assets/fonts/$path），失败回退到系统粗体 */
    private fun loadCustomTypeface(context: Context, path: String): Typeface {
        return try {
            context.assets.open(path).use { input ->
                val outFile = java.io.File(context.cacheDir, path.replace('/', '_'))
                outFile.parentFile?.mkdirs()
                if (!outFile.exists() || outFile.length() == 0L) {
                    outFile.outputStream().use { out -> input.copyTo(out) }
                }
                Typeface.createFromFile(outFile)
            }
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }
}
