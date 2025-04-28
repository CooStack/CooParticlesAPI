package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import net.minecraft.client.world.ClientWorld
import net.minecraft.util.math.Vec3d

/**
 * 为了更好的让ControlableParticleGroup 进行操作
 */
interface ParticleDisplayer {
    companion object {
        @JvmStatic
        fun withSingle(effect: ControlableParticleEffect): ParticleDisplayer {
            return SingleParticleDisplayer(effect)
        }

        @JvmStatic
        fun withGroup(group: ControlableParticleGroup): ParticleDisplayer {
            return ParticleGroupDisplayer(group)
        }

        @JvmStatic
        fun withStyle(style: ParticleGroupStyle): ParticleDisplayer {
            return ParticleStyleDisplayer(style)
        }
    }

    fun display(loc: Vec3d, world: ClientWorld): Controlable<*>?
    class ParticleStyleDisplayer(val style: ParticleGroupStyle) : ParticleDisplayer {
        override fun display(
            loc: Vec3d,
            world: ClientWorld
        ): Controlable<*> {
            style.display(loc, world)
            return style
        }
    }

    class SingleParticleDisplayer(val effect: ControlableParticleEffect) : ParticleDisplayer {
        /**
         * 始终返回null 因为此时粒子一定还未设置成功
         */
        override fun display(loc: Vec3d, world: ClientWorld): Controlable<ControlableParticle>? {
            world.addParticle(effect, true, loc.x, loc.y, loc.z, 0.0, 0.0, 0.0)
            return ControlParticleManager.getControl(effect.controlUUID)
        }
    }

    class ParticleGroupDisplayer(val group: ControlableParticleGroup) : ParticleDisplayer {
        override fun display(loc: Vec3d, world: ClientWorld): Controlable<ControlableParticleGroup> {
            group.display(loc, world)
            return group
        }
    }
}