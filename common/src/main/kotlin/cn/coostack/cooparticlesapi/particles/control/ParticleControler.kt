package cn.coostack.cooparticlesapi.particles.control

import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.Tickable
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap


/**
 * 代理粒子
 */
class ParticleControler(private val uuid: UUID) : Controlable<ControlableParticle>, Tickable<ControlableParticle> {
    lateinit var particle: ControlableParticle
        private set

    private var init = false
    private val invokeQueue = mutableListOf<ControlableParticle.() -> Unit>()

    @Volatile
    private var state: ParticleControlerState = ParticleControlerState.UNBOUND

    /**
     * 参数缓存 (tick 级)
     */
    val bufferedData = ConcurrentHashMap<String, Any>()
    private var initInvoker: ControlableParticle.() -> Unit = {}
    private var destroyInvoker: ControlableParticle.(RemoveReason) -> Unit = {}

    val currentState: ParticleControlerState
        get() = state

    val isBound: Boolean
        get() = state == ParticleControlerState.BOUND || state == ParticleControlerState.ACTIVE

    val isInitialized: Boolean
        get() = state == ParticleControlerState.ACTIVE

    val isRemoved: Boolean
        get() = state == ParticleControlerState.REMOVED

    override fun addPreTickAction(action: ControlableParticle.() -> Unit): ParticleControler {
        if (isRemoved) {
            return this
        }
        invokeQueue.add(action)
        return this
    }


    fun controlAction(action: (ControlableParticle.() -> Unit)): ParticleControler {
        action(requireParticleBound())
        return this
    }

    fun applyDestroyAction(action: (ControlableParticle.(RemoveReason) -> Unit)): ParticleControler {
        destroyInvoker = action
        return this
    }

    fun applyInitializedAction(action: (ControlableParticle.() -> Unit)): ParticleControler {
        initInvoker = action
        return this
    }

    internal fun loadParticle(particle: ControlableParticle) {
        if (isRemoved) {
            return
        }
        if (::particle.isInitialized) {
            return
        }
        if (particle.controlUUID != uuid) {
            throw IllegalArgumentException("Particle uuid invalid")
        }
        this.particle = particle
        state = ParticleControlerState.BOUND
    }

    internal fun particleInit() {
        if (init || state == ParticleControlerState.ACTIVE || isRemoved) {
            return
        }
        if (!::particle.isInitialized) {
            throw IllegalStateException("ParticleControler[$uuid] is not bound to particle yet")
        }
        initInvoker(particle)
        init = true
        state = ParticleControlerState.ACTIVE
    }

    /**
     * @see ControlableParticle.tick
     */
    override fun tick() {
        if (state != ParticleControlerState.ACTIVE || !::particle.isInitialized) {
            return
        }
        val stableSize = invokeQueue.size
        var index = 0
        while (index < stableSize) {
            if (state != ParticleControlerState.ACTIVE) {
                return
            }
            if (index >= invokeQueue.size) {
                break
            }
            val action = invokeQueue[index]
            action(particle)
            index++
        }
        if (state != ParticleControlerState.ACTIVE) {
            return
        }
        // 防呆: 粒子外部死亡后确保 controller 资源释放
        if (particle.death) {
            if (particle.currentAge >= particle.lifetime) {
                remove(RemoveReason.LIFECYCLE)
                return
            }
            cleanupAfterRemoved()
        }
    }

    fun rotateParticleTo(target: RelativeLocation) {
        rotateParticleTo(Vector3f(target.x.toFloat(), target.y.toFloat(), target.z.toFloat()))
    }

    fun rotateParticleTo(target: Vec3) {
        rotateParticleTo(target.toVector3f())
    }

    fun rotateParticleTo(target: Vector3f) {
        requireParticleBound().rotateParticleTo(target)
    }

    override fun controlUUID(): UUID {
        return uuid
    }

    override fun rotateToPoint(to: RelativeLocation) {
    }

    override fun rotateToWithAngle(to: RelativeLocation, angle: Double) {
    }

    override fun rotateAsAxis(angle: Double) {
    }

    override fun teleportTo(pos: Vec3) {
        requireParticleBound().teleportTo(pos)
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        requireParticleBound().teleportTo(x, y, z)
    }

    /**
     * @see ControlableParticle.markDead
     */
    override fun remove() {
        remove(RemoveReason.QUEUE)
    }

    override fun remove(reason: RemoveReason) {
        if (isRemoved) {
            return
        }
        try {
            if (::particle.isInitialized) {
                destroyInvoker(particle, reason)
                if (particle.isAlive) {
                    particle.remove()
                }
            }
        } finally {
            cleanupAfterRemoved()
        }
    }

    override fun getControlObject(): ControlableParticle {
        return requireParticleBound()
    }

    private fun cleanupAfterRemoved() {
        state = ParticleControlerState.REMOVED
        invokeQueue.clear()
        bufferedData.clear()
        initInvoker = {}
        destroyInvoker = {}
        ControlParticleManager.removeControl(uuid)
    }

    private fun requireParticleBound(): ControlableParticle {
        if (!::particle.isInitialized) {
            throw IllegalStateException("ParticleControler[$uuid] has no bound particle, state=$state")
        }
        return particle
    }
}
