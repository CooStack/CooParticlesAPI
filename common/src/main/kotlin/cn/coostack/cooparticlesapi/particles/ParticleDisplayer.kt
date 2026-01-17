package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.network.particle.style.ParticleGroupStyle
import cn.coostack.cooparticlesapi.particles.control.ControlParticleManager
import cn.coostack.cooparticlesapi.particles.control.group.ControlableParticleGroup
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.Vec3

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

        @JvmStatic
        fun withDisplayEntity(entity: DisplayEntity): ParticleDisplayer {
            return DisplayEntityDisplayer(entity)
        }

        @JvmStatic
        fun withComposition(composition: ParticleComposition): ParticleDisplayer {
            return ParticleCompositionDisplayer(composition)
        }
    }

    fun display(loc: Vec3, world: ClientLevel): Controlable<*>?
    class ParticleStyleDisplayer(val style: ParticleGroupStyle) : ParticleDisplayer {
        override fun display(
            loc: Vec3,
            world: ClientLevel
        ): Controlable<*> {
            style.display(loc, world)
            return style
        }
    }

    class ParticleCompositionDisplayer(val composition: ParticleComposition) : ParticleDisplayer {
        override fun display(
            loc: Vec3,
            world: ClientLevel
        ): Controlable<*> {
            composition.display()
            return composition
        }
    }

    class SingleParticleDisplayer(val effect: ControlableParticleEffect) : ParticleDisplayer {
        /**
         * 始终返回null 因为此时粒子一定还未设置成功
         */
        override fun display(loc: Vec3, world: ClientLevel): Controlable<ControlableParticle>? {
            world.addParticle(effect, true, loc.x, loc.y, loc.z, 0.0, 0.0, 0.0)
            return ControlParticleManager.getControl(effect.controlUUID)
        }
    }

    class ParticleGroupDisplayer(val group: ControlableParticleGroup) : ParticleDisplayer {
        override fun display(loc: Vec3, world: ClientLevel): Controlable<ControlableParticleGroup> {
            group.display(loc, world)
            return group
        }
    }

    class DisplayEntityDisplayer(val entity: DisplayEntity) : ParticleDisplayer {
        override fun display(loc: Vec3, world: ClientLevel): Controlable<DisplayEntity> {
            DisplayEntityManager.addClient(entity.apply {
                this.pos = loc
                this.world = world
            })
            return entity
        }
    }
}