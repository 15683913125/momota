package com.example.imagegen.generator

import android.graphics.Bitmap

/** 白色标签矩形区域 */
data class LabelRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val area: Int
)

/**
 * 白色标签区域识别，等价 Python 版 image_processor.find_white_label
 *
 * 不依赖 OpenCV，使用 BFS 连通组件分析：
 *  1. 灰度化（标准 ITU-R BT.601）
 *  2. 二值化（threshold=200，>threshold 视为白色像素）
 *  3. 4 邻域 BFS 标记连通组件，计算每个组件的 bounding rect 与面积
 *  4. 过滤（面积 / 宽高 / 宽高比 / 位置），按 y 降序取前 3
 *  5. 找不到则用宽松条件再扫一遍
 */
object WhiteLabelDetector {

    fun findWhiteLabel(bitmap: Bitmap, threshold: Int = 200): List<LabelRegion> {
        val width = bitmap.width
        val height = bitmap.height
        if (width == 0 || height == 0) return emptyList()

        // 灰度 + 二值化
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val binary = BooleanArray(width * height)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            val gray = (r * 299 + g * 587 + b * 114) / 1000
            binary[i] = gray > threshold
        }

        // 严格过滤
        val strict = scan(binary, width, height) { r ->
            r.area >= 500 &&
                r.width >= 40 && r.height >= 25 &&
                run {
                    val ar = r.width.toFloat() / r.height
                    ar in 1.5f..8f
                } &&
                r.y >= height * 0.3f &&
                (r.y + r.height) <= height * 0.995f
        }
        if (strict.isNotEmpty()) return strict

        // 宽松过滤
        return scan(binary, width, height) { r ->
            r.area >= 300 &&
                r.width >= 30 && r.height >= 20 &&
                r.y >= height * 0.2f &&
                (r.y + r.height) <= height * 0.99f
        }
    }

    /** 扫描所有连通组件，按 y 降序取前 3，过滤条件由 filter 决定 */
    private fun scan(
        binary: BooleanArray,
        width: Int,
        height: Int,
        filter: (LabelRegion) -> Boolean
    ): List<LabelRegion> {
        val visited = BooleanArray(width * height)
        val regions = ArrayList<LabelRegion>()
        val queue = ArrayDeque<Int>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                if (!binary[idx] || visited[idx]) continue

                var minX = x; var maxX = x
                var minY = y; var maxY = y
                var area = 0
                queue.clear()
                queue.add(idx)
                visited[idx] = true

                while (queue.isNotEmpty()) {
                    val cur = queue.removeFirst()
                    val cx = cur % width
                    val cy = cur / width
                    area++
                    if (cx < minX) minX = cx
                    if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy
                    if (cy > maxY) maxY = cy

                    if (cx > 0) {
                        val n = cur - 1
                        if (binary[n] && !visited[n]) { visited[n] = true; queue.add(n) }
                    }
                    if (cx < width - 1) {
                        val n = cur + 1
                        if (binary[n] && !visited[n]) { visited[n] = true; queue.add(n) }
                    }
                    if (cy > 0) {
                        val n = cur - width
                        if (binary[n] && !visited[n]) { visited[n] = true; queue.add(n) }
                    }
                    if (cy < height - 1) {
                        val n = cur + width
                        if (binary[n] && !visited[n]) { visited[n] = true; queue.add(n) }
                    }
                }

                val w = maxX - minX + 1
                val h = maxY - minY + 1
                val region = LabelRegion(minX, minY, w, h, area)
                if (filter(region)) regions.add(region)
            }
        }

        // 按 y 降序，取前 3（等价 Python: label_regions.sort(key=lambda r: r[1], reverse=True)）
        return regions.sortedByDescending { it.y }.take(3)
    }
}
