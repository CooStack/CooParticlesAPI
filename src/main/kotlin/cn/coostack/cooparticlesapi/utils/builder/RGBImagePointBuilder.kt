package cn.coostack.cooparticlesapi.utils.builder

import cn.coostack.cooparticlesapi.utils.ImageUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.util.Identifier

/**
 * 通过黑白的图片获取到点的集合
 * 并且附带RGBA属性 (不包括alpha = 0)
 *
 * 不支持直接加入PointsBuilder,因为无法将颜色信息传递给particle, 如同ImagePointsBuilder
 *
 */
class RGBImagePointBuilder(val image: Identifier) {
    private var scale = 1.0
    private var step = 0.01

    fun scale(scale: Double): RGBImagePointBuilder {
        this.scale = scale
        return this
    }

    fun step(step: Double): RGBImagePointBuilder {
        this.step = step
        return this
    }

    fun build(): Map<RelativeLocation, Int> {
        val picture = ImageUtil.loadFromIdentifier(image) ?: return HashMap()
        val scaled = ImageUtil.scale(scale, picture)
        val offsetX = scaled.width * step / 2.0
        val offsetZ = scaled.height * step / 2.0
        return ImageUtil.toPointsWithRGBA(scaled, step).onEach {
            it.key.x -= offsetX
            it.key.z -= offsetZ
        }
    }
}