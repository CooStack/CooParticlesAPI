package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.utils.builder.FourierSeriesBuilder
import org.joml.Vector2d
import org.joml.Vector2i
import java.awt.image.BufferedImage
import kotlin.math.*

/**
 *
 * 欸 爱 神 力 Made By ChatGPT 5.2
 *
 * 多轮廓版：图片(透明通道) -> 所有连通块轮廓 -> 每个轮廓生成一个 FourierSeriesBuilder
 *
 * 输出点在 XZ 平面：
 * - x = (pixelX - centerX) * step
 * - z = (centerY - pixelY) * step
 *
 * 复数用 Vector2d 表示：x=Re(实部), y=Im(虚部)
 */
object FourierPhotoUtil {

    // =======================
    // 对外 API
    // =======================

    /**
     * 单轮廓：只取“最大连通块”生成 builder（适合你想要一个主体的时候）
     */
    fun toFourierBuilder(
        image: BufferedImage,
        alphaThreshold: Int = 1,
        sampleCount: Int = 1024,
        harmonics: Int = 150,
        step: Double = 1.0,
        centerMode: Boolean = true,
        sortByAmplitude: Boolean = true
    ): FourierSeriesBuilder {
        val builders = toFourierBuilders(
            image, alphaThreshold, sampleCount, harmonics, step, centerMode, sortByAmplitude
        )
        require(builders.isNotEmpty()) { "未找到任何轮廓：请检查图片透明通道/alphaThreshold=$alphaThreshold" }
        // 取最大连通块（我们在 toFourierBuilders 里会按面积从大到小排序）
        return builders.first()
    }

    /**
     * 多轮廓：返回每个连通块一个 FourierSeriesBuilder
     *
     * @param maxComponents 限制最多处理多少个连通块（防止噪点太多），默认 16
     * @param minComponentPixels 过滤太小的连通块（比如抗锯齿噪点），默认 20
     */
    fun toFourierBuilders(
        image: BufferedImage,
        alphaThreshold: Int = 1,
        sampleCount: Int = 1024,
        harmonics: Int = 150,
        step: Double = 1.0,
        centerMode: Boolean = true,
        sortByAmplitude: Boolean = true,
        maxComponents: Int = 16,
        minComponentPixels: Int = 20
    ): List<FourierSeriesBuilder> {
        val mask = buildSolidMask(image, alphaThreshold)
        val components = findConnectedComponents(mask, image.width, image.height, minComponentPixels)
            .sortedByDescending { it.size } // 最大连通块优先
            .take(maxComponents)

        val builders = ArrayList<FourierSeriesBuilder>(components.size)

        for (comp in components) {
            val contour = extractOrderedContourFromComponent(
                width = image.width,
                height = image.height,
                componentPixels = comp
            )

            // 轮廓点太少就跳过
            if (contour.size < 8) continue

            val resampled = resampleClosedPath(contour, sampleCount)
            val samples = mapToXZ(resampled, image.width, image.height, step, centerMode)

            val coeffs = dft(samples)
            val series = coeffsToFouriers(coeffs, harmonics, sortByAmplitude)

            val builder = FourierSeriesBuilder()
                .count(sampleCount)
                .scale(1.0)

            series.forEach { f ->
                builder.addFourier(r = f.r, w = f.w, startAngle = f.startAngle)
            }
            builders.add(builder)
        }

        return builders
    }

    /**
     * 多轮廓：返回每个连通块一个 FourierSeriesBuilder，并返回该连通块相对于“全图中心”的 offset（XZ平面）
     *
     * key   = offset(RelativeLocation)  -> (offX, 0, offZ)
     * value = FourierSeriesBuilder      -> build() 得到的是“以连通块自身中心为原点”的局部点
     *
     * 渲染时用：pos = origin + offset + point
     */
    fun toFourierBuildersWithOffset(
        image: BufferedImage,
        alphaThreshold: Int = 1,
        sampleCount: Int = 1024,
        harmonics: Int = 150,
        step: Double = 1.0,
        sortByAmplitude: Boolean = true,
        maxComponents: Int = 16,
        minComponentPixels: Int = 20
    ): Map<RelativeLocation, FourierSeriesBuilder> {

        val mask = buildSolidMask(image, alphaThreshold)
        val components = findConnectedComponents(mask, image.width, image.height, minComponentPixels)
            .sortedByDescending { it.size }
            .take(maxComponents)

        // LinkedHashMap 保持“从大到小”的顺序
        val result = LinkedHashMap<RelativeLocation, FourierSeriesBuilder>(components.size)

        // 全图中心（像素坐标）
        val globalCx = image.width / 2.0
        val globalCy = image.height / 2.0

        for (comp in components) {
            val contour = extractOrderedContourFromComponent(
                width = image.width,
                height = image.height,
                componentPixels = comp
            )
            if (contour.size < 8) continue

            // 1) 计算该连通块在“全图坐标系”中的中心（像素）
            val (compCx, compCy) = computeComponentCenterPx(comp, image.width)

            // 2) 计算 offset：相对于全图中心，并映射到 XZ 平面
            val offX = (compCx - globalCx) * step
            val offZ = (globalCy - compCy) * step // 翻转y -> z
            val offset = RelativeLocation(offX, 0.0, offZ)

            // 3) 连通块内部做“局部居中”：
            //    这里强制以该连通块自身中心(compCx, compCy)为中心，把轮廓点转换成局部坐标再做DFT
            val resampled = resampleClosedPath(contour, sampleCount)
            val localSamples = resampled.map { p ->
                val x = (p.x - compCx) * step
                val z = (compCy - p.y) * step
                Vector2d(x, z) // re=x, im=z
            }

            val coeffs = dft(localSamples)
            val series = coeffsToFouriers(coeffs, harmonics, sortByAmplitude)

            val builder = FourierSeriesBuilder()
                .count(sampleCount)
                .scale(1.0)

            series.forEach { f ->
                builder.addFourier(r = f.r, w = f.w, startAngle = f.startAngle)
            }

            result[offset] = builder
        }

        return result
    }

    /**
     * 计算连通块中心（像素坐标）
     * compPixels: IntArray 存的是 y*w + x
     */
    private fun computeComponentCenterPx(compPixels: IntArray, w: Int): Pair<Double, Double> {
        var sx = 0.0
        var sy = 0.0
        for (p in compPixels) {
            sx += (p % w).toDouble()
            sy += (p / w).toDouble()
        }
        val n = compPixels.size.coerceAtLeast(1)
        return Pair(sx / n, sy / n)
    }

    /**
     * 多轮廓：直接得到点（按轮廓分组）
     */
    fun toFourierPointsMulti(
        image: BufferedImage,
        alphaThreshold: Int = 1,
        sampleCount: Int = 1024,
        harmonics: Int = 150,
        step: Double = 1.0,
        centerMode: Boolean = true,
        sortByAmplitude: Boolean = true,
        maxComponents: Int = 16,
        minComponentPixels: Int = 20
    ): List<List<RelativeLocation>> {
        return toFourierBuilders(
            image = image,
            alphaThreshold = alphaThreshold,
            sampleCount = sampleCount,
            harmonics = harmonics,
            step = step,
            centerMode = centerMode,
            sortByAmplitude = sortByAmplitude,
            maxComponents = maxComponents,
            minComponentPixels = minComponentPixels
        ).map { it.build() }
    }

    // =======================
    // 1) 构建 solid mask
    // =======================

    private fun buildSolidMask(image: BufferedImage, alphaThreshold: Int): BooleanArray {
        val w = image.width
        val h = image.height
        val out = BooleanArray(w * h)

        val hasAlpha = image.colorModel.hasAlpha()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val solid = if (!hasAlpha) {
                    true
                } else {
                    val pixel = image.getRGB(x, y)
                    val a = (pixel ushr 24) and 0xFF
                    a > alphaThreshold
                }
                out[y * w + x] = solid
            }
        }
        return out
    }

    // =======================
    // 2) 连通块提取（8邻域）
    // =======================

    private fun findConnectedComponents(
        mask: BooleanArray,
        w: Int,
        h: Int,
        minPixels: Int
    ): List<IntArray> {
        val visited = BooleanArray(mask.size)
        val comps = ArrayList<IntArray>()

        val neighbors = intArrayOf(
            -1, 0, 1, 0, 0, -1, 0, 1,   // 4邻域
            -1, -1, 1, -1, -1, 1, 1, 1  // 斜对角（凑成8邻域）
        )

        fun inBounds(x: Int, y: Int) = x in 0 until w && y in 0 until h
        fun idx(x: Int, y: Int) = y * w + x

        val queue = IntArray(w * h) // 复用队列，避免频繁分配
        for (y in 0 until h) for (x in 0 until w) {
            val start = idx(x, y)
            if (!mask[start] || visited[start]) continue

            var qs = 0
            var qe = 0
            queue[qe++] = start
            visited[start] = true

            val pixels = ArrayList<Int>(256)

            while (qs < qe) {
                val cur = queue[qs++]
                pixels.add(cur)
                val cx = cur % w
                val cy = cur / w

                var n = 0
                while (n < neighbors.size) {
                    val nx = cx + neighbors[n]
                    val ny = cy + neighbors[n + 1]
                    n += 2
                    if (!inBounds(nx, ny)) continue
                    val ni = idx(nx, ny)
                    if (visited[ni] || !mask[ni]) continue
                    visited[ni] = true
                    queue[qe++] = ni
                }
            }

            if (pixels.size >= minPixels) {
                val arr = IntArray(pixels.size)
                for (i in pixels.indices) arr[i] = pixels[i]
                comps.add(arr)
            }
        }

        return comps
    }

    // =======================
    // 3) 从连通块提取有序轮廓
    // =======================

    private fun extractOrderedContourFromComponent(
        width: Int,
        height: Int,
        componentPixels: IntArray
    ): List<Vector2i> {
        // 用哈希集合判断某像素是否属于该连通块
        //（这里用 BooleanArray 子mask 更快：只标记该连通块）
        val compMask = BooleanArray(width * height)
        for (p in componentPixels) compMask[p] = true

        fun isSolid(x: Int, y: Int): Boolean {
            if (x !in 0 until width || y !in 0 until height) return false
            return compMask[y * width + x]
        }

        // 找一个边缘点作为起点：solid 且 8邻域存在空
        var start: Vector2i? = null
        for (p in componentPixels) {
            val x = p % width
            val y = p / width
            if (isEdgePixel(x, y, ::isSolid)) {
                start = Vector2i(x, y)
                break
            }
        }
        if (start == null) return emptyList()

        return traceMoore(start!!, ::isSolid, width, height)
    }

    private fun isEdgePixel(x: Int, y: Int, isSolid: (Int, Int) -> Boolean): Boolean {
        for (dy in -1..1) for (dx in -1..1) {
            if (dx == 0 && dy == 0) continue
            if (!isSolid(x + dx, y + dy)) return true
        }
        return false
    }

    /**
     * Moore-Neighbor tracing（简化版）：从 start 开始沿边缘走一圈
     */
    private fun traceMoore(
        start: Vector2i,
        isSolid: (Int, Int) -> Boolean,
        w: Int,
        h: Int
    ): List<Vector2i> {
        val dir = arrayOf(
            Vector2i(1, 0), Vector2i(1, 1), Vector2i(0, 1), Vector2i(-1, 1),
            Vector2i(-1, 0), Vector2i(-1, -1), Vector2i(0, -1), Vector2i(1, -1)
        )

        fun inside(p: Vector2i) = p.x in 0 until w && p.y in 0 until h

        var current = Vector2i(start)
        var backDirIndex = 4 // W
        val contour = ArrayList<Vector2i>(4096)
        contour.add(Vector2i(current))

        val maxSteps = w * h * 4
        var steps = 0

        while (steps++ < maxSteps) {
            var foundNext: Vector2i? = null
            var foundDirIndex = -1

            for (i in 1..8) {
                val idx = (backDirIndex + i) and 7
                val nxt = Vector2i(current.x + dir[idx].x, current.y + dir[idx].y)
                if (!inside(nxt)) continue
                if (!isSolid(nxt.x, nxt.y)) continue
                foundNext = nxt
                foundDirIndex = idx
                break
            }

            if (foundNext == null) break

            val next = foundNext
            if (next.x == start.x && next.y == start.y && contour.size > 10) break

            contour.add(Vector2i(next))
            backDirIndex = (foundDirIndex + 4) and 7
            current.set(next)
        }

        return contour
    }

    // =======================
    // 4) 重采样（闭合曲线等距采样）
    // =======================

    private fun resampleClosedPath(path: List<Vector2i>, targetCount: Int): List<Vector2d> {
        val pts = path.map { Vector2d(it.x.toDouble(), it.y.toDouble()) }
        if (pts.size <= targetCount) return pts

        val n = pts.size
        val dist = DoubleArray(n + 1)
        dist[0] = 0.0
        for (i in 1..n) {
            val a = pts[i - 1]
            val b = pts[i % n]
            dist[i] = dist[i - 1] + hypot(b.x - a.x, b.y - a.y)
        }

        val total = dist[n]
        val step = total / targetCount

        val out = ArrayList<Vector2d>(targetCount)
        var seg = 0
        for (k in 0 until targetCount) {
            val d = k * step
            while (seg + 1 <= n && dist[seg + 1] < d) seg++

            val a = pts[seg % n]
            val b = pts[(seg + 1) % n]
            val da = dist[seg]
            val db = dist[seg + 1]
            val t = if (db - da <= 1e-9) 0.0 else (d - da) / (db - da)

            out.add(
                Vector2d(
                    a.x + (b.x - a.x) * t,
                    a.y + (b.y - a.y) * t
                )
            )
        }
        return out
    }

    // =======================
    // 5) 映射到 XZ + 居中
    // 复数序列：Vector2d(re=x, im=z)
    // =======================

    private fun mapToXZ(
        pts: List<Vector2d>,
        width: Int,
        height: Int,
        step: Double,
        centerMode: Boolean
    ): List<Vector2d> {
        var cx = width / 2.0
        var cy = height / 2.0

        if (centerMode) {
            var sx = 0.0
            var sy = 0.0
            pts.forEach { p -> sx += p.x; sy += p.y }
            cx = sx / pts.size
            cy = sy / pts.size
        }

        return pts.map { p ->
            val x = (p.x - cx) * step
            val z = (cy - p.y) * step
            Vector2d(x, z)
        }
    }

    // =======================
    // 6) DFT
    // X[k] = (1/N) Σ x[n] * e^{-i 2π k n / N}
    // 复数：Vector2d(x=Re, y=Im)
    // =======================

    private fun dft(samples: List<Vector2d>): Array<Vector2d> {
        val n = samples.size
        val out = Array(n) { Vector2d(0.0, 0.0) }

        for (k in 0 until n) {
            var re = 0.0
            var im = 0.0
            val coef = -2.0 * Math.PI * k / n

            for (i in 0 until n) {
                val angle = coef * i
                val c = cos(angle)
                val s = sin(angle)
                val x = samples[i]

                re += x.x * c - x.y * s
                im += x.x * s + x.y * c
            }

            out[k].set(re / n, im / n)
        }
        return out
    }

    // =======================
    // 7) 系数 -> Fourier 列表
    // k=0..N-1 -> w in [-N/2, N/2)
    // w = if (k <= N/2) k else k - N
    // startAngle = arg(Ck) (deg)
    // r = |Ck|
    // =======================

    private fun coeffsToFouriers(
        coeffs: Array<Vector2d>,
        harmonics: Int,
        sortByAmplitude: Boolean
    ): List<FourierSeriesBuilder.Fourier> {
        val n = coeffs.size

        fun kToW(k: Int): Int = if (k <= n / 2) k else k - n

        val all = ArrayList<FourierSeriesBuilder.Fourier>(n)
        for (k in 0 until n) {
            val w = kToW(k).toDouble()
            val c = coeffs[k]
            val r = hypot(c.x, c.y)
            val angRad = atan2(c.y, c.x)
            val startAngleDeg = Math.toDegrees(angRad)
            all.add(FourierSeriesBuilder.Fourier(w = w, r = r, startAngle = startAngleDeg))
        }

        val zero = all.firstOrNull { it.w == 0.0 } ?: FourierSeriesBuilder.Fourier(0.0, 0.0, 0.0)
        val others = all.filter { it.w != 0.0 }

        val selected = if (sortByAmplitude) {
            others.sortedByDescending { it.r }.take(harmonics)
        } else {
            val h = harmonics.coerceAtMost(n / 2 - 1).coerceAtLeast(1)
            val freqList = ArrayList<FourierSeriesBuilder.Fourier>(2 * h)
            for (w in -h..-1) freqList.add(all.first { it.w == w.toDouble() })
            for (w in 1..h) freqList.add(all.first { it.w == w.toDouble() })
            freqList
        }

        return buildList {
            add(zero)
            addAll(selected)
        }
    }
}
