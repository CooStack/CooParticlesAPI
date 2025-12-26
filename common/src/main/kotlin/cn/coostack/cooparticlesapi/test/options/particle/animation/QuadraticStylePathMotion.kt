package cn.coostack.cooparticlesapi.test.options.particle.animation

import cn.coostack.cooparticlesapi.network.animation.StylePathMotion
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import net.minecraft.world.phys.Vec3
import kotlin.math.pow

class QuadraticStylePathMotion(targetStyle: ParticleGroupStyle) : StylePathMotion(targetStyle.pos, targetStyle) {
    override fun pathFunction(): Vec3 {
        val x = (currentTick - 20) / 10.0

        return Vec3(x, -0.25 * x.pow(2) + 20, 0.0)
    }
}