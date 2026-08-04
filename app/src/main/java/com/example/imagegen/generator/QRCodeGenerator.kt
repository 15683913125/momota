package com.example.imagegen.generator

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.random.Random

/**
 * 二维码生成器，等价于 Python 版 image_processor.generate_qrcode_image
 *  - L 纠错，1 边框
 *  - 黑色像素保留，白色像素变透明（RGBA）
 */
object QRCodeGenerator {

    /** 生成透明背景二维码 Bitmap（每个 module = boxSize 像素） */
    fun generate(text: String, boxSize: Int = 3): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val writer = QRCodeWriter()
            val matrix = writer.encode(text, BarcodeFormat.QR_CODE, 0, 0, hints)

            val matrixWidth = matrix.width
            val matrixHeight = matrix.height
            if (matrixWidth <= 0 || matrixHeight <= 0) return null

            val bmpWidth = matrixWidth * boxSize
            val bmpHeight = matrixHeight * boxSize
            val bmp = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)

            val pixels = IntArray(bmpWidth * bmpHeight)
            for (y in 0 until bmpHeight) {
                val my = y / boxSize
                for (x in 0 until bmpWidth) {
                    val mx = x / boxSize
                    pixels[y * bmpWidth + x] =
                        if (matrix[mx, my]) Color.BLACK else Color.TRANSPARENT
                }
            }
            bmp.setPixels(pixels, 0, bmpWidth, 0, 0, bmpWidth, bmpHeight)
            bmp
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 等价于 Python 版 generate_random_code：固定生成 35 位字符串
     * 格式：11 组（每组的格式根据 bz/wz 随机控制）+ 末尾 2 个数字，再截断到 35 位
     */
    fun generateRandomCode(): String {
        val digits = "0123456789"
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val sb = StringBuilder()

        val bz = Random.nextInt(0, 11)    // 0..10
        val wz = Random.nextInt(4, 7)     // 4..6

        for (i in 0 until 11) {
            sb.append(digits.random())
            if (i != bz) sb.append(digits.random())
            sb.append(letters.random())
            if (i == wz) sb.append(digits.random())
        }
        sb.append(digits.random())
        sb.append(digits.random())

        return sb.toString().take(35)
    }
}
