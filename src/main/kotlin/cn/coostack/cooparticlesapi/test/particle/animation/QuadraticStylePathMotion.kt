package cn.coostack.cooparticlesapi.test.particle.animation

import cn.coostack.cooparticlesapi.network.animation.StylePathMotion
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import net.minecraft.util.math.Vec3d
import kotlin.math.pow

class QuadraticStylePathMotion(targetStyle: ParticleGroupStyle) : StylePathMotion(targetStyle.pos, targetStyle) {
    override fun pathFunction(): Vec3d {
        val x = (currentTick - 20) / 10.0

        return Vec3d(x, -0.25 * x.pow(2) + 20, 0.0)
    }
}