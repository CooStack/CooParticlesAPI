package cn.coostack.cooparticlesapi.utils

import net.minecraft.util.Identifier
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.roundToInt

object ImageUtil {

    fun loadFromIdentifier(id: Identifier): BufferedImage? {
        val namespace = id.namespace
        val path = "assets/$namespace/${id.path}"
        val stream = this::class.java.classLoader.getResourceAsStream(path) ?: return null
        return stream.use {
            ImageIO.read(it)
        }
    }

    /**
     * 使用临近硬边缘进行缩放(像素画适合)
     */
    fun scale(scalePrecent: Double, image: BufferedImage): BufferedImage {
        val new = BufferedImage(
            (image.width * scalePrecent).roundToInt(),
            (image.height * scalePrecent).roundToInt(),
            image.type
        )
        val graphics = new.createGraphics().apply {
            setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            )
            setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
            )
            setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON
            )
        }
        graphics.drawImage(image, 0, 0, new.width, new.height, null)
        graphics.dispose()
        return new
    }


    /**
     * @param image 转换为点的图片
     * @param step 每一个点的间隔
     */
    fun toPoints(image: BufferedImage, step: Double): List<RelativeLocation> {
        val res = ArrayList<RelativeLocation>()
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val alpha = getAlpha(image, x, y)
                if (alpha == 0) {
                    continue
                }
                res.add(RelativeLocation(x * step, 0.0, y * step))
            }
        }
        return res
    }

    /**
     * @param image 转换为点的图片
     * @param step 每一个点的间隔
     * 获取附带透明度的点集合 (不包括0透明度)
     */
    fun toPointsWithAlpha(image: BufferedImage, step: Double): Map<RelativeLocation, Int> {
        val res = HashMap<RelativeLocation, Int>()
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val alpha = getAlpha(image, x, y)
                if (alpha == 0) {
                    continue
                }
                res[RelativeLocation(x * step, 0.0, y * step)] = alpha
            }
        }
        return res
    }

    /**
     * @param image 转换为点的图片
     * @param step 每一个点的间隔
     * 获取附带颜色的点集合 RGB或者RGBA
     */
    fun toPointsWithRGBA(image: BufferedImage, step: Double): Map<RelativeLocation, Int> {
        val res = HashMap<RelativeLocation, Int>()
        for (x in 0 until image.width) {
            for (y in 0 until image.height) {
                val alpha = getAlpha(image, x, y)
                if (alpha == 0) {
                    continue
                }
                res[RelativeLocation(x * step, 0.0, y * step)] = image.getRGB(x, y)
            }
        }
        return res
    }


    private fun getAlpha(image: BufferedImage, x: Int, y: Int): Int {
        if (!image.colorModel.hasAlpha()) {
            return 255
        }
        val pixel = image.getRGB(x, y)
        return (pixel shr 24) and 0xFF
    }

}