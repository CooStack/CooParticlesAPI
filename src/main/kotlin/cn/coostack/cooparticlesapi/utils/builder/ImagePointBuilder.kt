package cn.coostack.cooparticlesapi.utils.builder

import cn.coostack.cooparticlesapi.utils.ImageUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.util.Identifier

/**
 * 通过黑白的图片获取到点的集合
 * 会自动便宜图形中心点
 */
class ImagePointBuilder(val image: Identifier) {
    private var scale = 1.0
    private var step = 0.01

    fun scale(scale: Double): ImagePointBuilder {
        this.scale = scale
        return this
    }

    fun step(step: Double): ImagePointBuilder {
        this.step = step
        return this
    }

    fun build(): List<RelativeLocation> {
        val picture = ImageUtil.loadFromIdentifier(image) ?: return ArrayList<RelativeLocation>()
        val scaled = ImageUtil.scale(scale, picture)
        val offsetX = scaled.width * step / 2.0
        val offsetZ = scaled.height * step / 2.0
        return ImageUtil.toPoints(scaled, step).onEach {
            it.x -= offsetX
            it.z -= offsetZ
        }
    }
}