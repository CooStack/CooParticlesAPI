package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.utils.builder.PointsBuilder
import kotlin.math.roundToInt

/**
 * 各种图形的预设
 * 此类会提供以y轴为对称轴的xy平面上的图形预设
 */
object MathPresets {
    /**
     * 获取罗马字母I的预设
     * @param scale 缩放大小
     */
    fun romaI(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01) { "缩放最小值为0.01" }
        val builder = PointsBuilder()
        val preLineCount = (5 * scale).roundToInt().coerceAtLeast(1)

        /** I
         * ---
         *  |
         *  |
         *  |
         * ---
         */
        val height = 0.25 * scale
        val weight = 0.125 * scale
        builder.addLine(
            RelativeLocation(-weight, height, 0.0),
            RelativeLocation(weight, height, 0.0),
            preLineCount
        ).addLine(
            RelativeLocation(-weight, -height, 0.0),
            RelativeLocation(weight, -height, 0.0),
            preLineCount
        ).addLine(
            RelativeLocation(0.0, height, 0.0),
            RelativeLocation(0.0, -height, 0.0),
            preLineCount
        )
        return builder.create()
    }

    fun romaII(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01) { "缩放最小值为0.01" }
        val res = ArrayList<RelativeLocation>()
        val offset = getRomaOffsetX(scale) / 2
        res.addAll(
            romaI(scale).onEach { it.x -= offset }
        )
        res.addAll(
            romaI(scale).onEach { it.x += offset }
        )
        return res
    }

    fun romaIII(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01) { "缩放最小值为0.01" }
        val res = ArrayList<RelativeLocation>()
        val offset = getRomaOffsetX(scale)
        res.addAll(romaI(scale))
        res.addAll(romaI(scale).onEach { it.x -= offset })
        res.addAll(romaI(scale).onEach { it.x += offset })
        return res
    }

    fun romaIV(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01) { "缩放最小值为0.01" }
        val res = ArrayList<RelativeLocation>()
        val offset = getRomaOffsetX(scale) / 2
        res.addAll(romaV(scale).onEach { it.x += offset })
        res.addAll(romaI(scale).onEach { it.x -= offset })
        return res
    }

    fun romaV(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01) { "缩放最小值为0.01" }
        val builder = PointsBuilder()
        val preLineCount = (5 * scale).roundToInt().coerceAtLeast(1)

        /** V
         * \   /
         *  \ /
         *   .
         */
        val height = 0.25 * scale
        val weight = 0.125 * scale
        builder.addLine(
            RelativeLocation(-weight, height, 0.0),
            RelativeLocation(0.0, -height, 0.0),
            preLineCount
        ).addLine(
            RelativeLocation(weight, height, 0.0),
            RelativeLocation(0.0, -height, 0.0),
            preLineCount
        )
        return builder.create()
    }

    fun romaVI(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01) { "缩放最小值为0.01" }
        val res = ArrayList<RelativeLocation>()
        val offset = getRomaOffsetX(scale) / 2
        res.addAll(romaV(scale).onEach { it.x -= offset })
        res.addAll(romaI(scale).onEach { it.x += offset })
        return res
    }

    fun romaVII(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01) { "缩放最小值为0.01" }
        val res = ArrayList<RelativeLocation>()
        val offset = getRomaOffsetX(scale)
        res.addAll(romaV(scale).onEach { it.x -= offset })
        res.addAll(romaI(scale))
        res.addAll(romaI(scale).onEach { it.x += offset })
        return res
    }

    fun romaVIII(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01) { "缩放最小值为0.01" }
        val res = ArrayList<RelativeLocation>()
        val offset = getRomaOffsetX(scale) / 2
        res.addAll(romaVII(scale).onEach { it.x -= offset })
        res.addAll(romaI(scale).onEach { it.x += offset * 3 })
        return res
    }

    fun romaIX(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01) { "缩放最小值为0.01" }
        val res = ArrayList<RelativeLocation>()
        val offset = getRomaOffsetX(scale) / 2
        res.addAll(romaX(scale).onEach { it.x += offset })
        res.addAll(romaI(scale).onEach { it.x -= offset })
        return res
    }

    fun romaX(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01) { "缩放最小值为0.01" }
        val builder = PointsBuilder()
        val preLineCount = (5 * scale).roundToInt().coerceAtLeast(1)

        /** X
         * \   /
         *  \ /
         *   .
         *  / \
         * /   \
         */
        val height = 0.25 * scale
        val weight = 0.125 * scale
        builder.addLine(
            RelativeLocation(-weight, height, 0.0),
            RelativeLocation(weight, -height, 0.0),
            preLineCount
        ).addLine(
            RelativeLocation(-weight, -height, 0.0),
            RelativeLocation(weight, height, 0.0),
            preLineCount
        )
        return builder.create()
    }

    fun romaXI(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01)
        val res = ArrayList<RelativeLocation>()
        val offset = getRomaOffsetX(scale) / 2
        res.addAll(romaX(scale).onEach { it.x -= offset })
        res.addAll(romaI(scale).onEach { it.x += offset })
        return res
    }

    fun romaXII(scale: Double): List<RelativeLocation> {
        require(scale >= 0.01)
        val res = ArrayList<RelativeLocation>()
        val offset = getRomaOffsetX(scale) / 2
        res.addAll(romaXI(scale).onEach { it.x -= offset * 2 })
        res.addAll(romaI(scale).onEach { it.x += offset })
        return res
    }

    fun getRomaOffsetX(scale: Double): Double = 0.125 * scale * 2
    fun getRomaOffsetY(scale: Double): Double = 0.25 * scale * 2
    fun withRomaNumber(i: Int, scale: Double): List<RelativeLocation> {
        require(i in 1..12) { "只支持1-10的罗马数字" }
        return when (i) {
            1 -> romaI(scale)
            2 -> romaII(scale)
            3 -> romaIII(scale)
            4 -> romaIV(scale)
            5 -> romaV(scale)
            6 -> romaVI(scale)
            7 -> romaVII(scale)
            8 -> romaVIII(scale)
            9 -> romaIX(scale)
            10 -> romaX(scale)
            11 -> romaXI(scale)
            12 -> romaXII(scale)
            else -> {
                ArrayList<RelativeLocation>()
            }
        }
    }
}