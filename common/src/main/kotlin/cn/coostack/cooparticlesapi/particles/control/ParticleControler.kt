package cn.coostack.cooparticlesapi.particles.control

import cn.coostack.cooparticlesapi.particles.Controlable
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 代理粒子
 * 此Controler 由 ControlerGroup代理创建 (Builder) 并使用
 */
class ParticleControler(private val uuid: UUID) : Controlable<ControlableParticle> {
    lateinit var particle: ControlableParticle
        private set

    private var init = false
    private val invokeQueue = mutableListOf<ControlableParticle.() -> Unit>()

    /**
     * 参数缓存 (tick等)
     */
    val bufferedData = ConcurrentHashMap<String, Any>()
    lateinit var initInvoker: ControlableParticle.() -> Unit

    fun addPreTickAction(action: ControlableParticle.() -> Unit): ParticleControler {
        invokeQueue.add(action)
        return this
    }

    fun controlAction(action: (ControlableParticle.() -> Unit)): ParticleControler {
        action(particle)
        return this
    }

    internal fun loadParticle(particle: ControlableParticle) {
        if (::particle.isInitialized) {
            return
        }
        if (particle.controlUUID != uuid) {
            throw IllegalArgumentException("Particle uuid invalid")
        }
        this.particle = particle
    }

    internal fun particleInit() {
        if (init) {
            return
        }
        if (!::initInvoker.isInitialized) {
            initInvoker = {}
        }
        initInvoker(particle)
        init = true
    }

    /**
     * @see ControlableParticle.tick 执行该函数
     */
    internal fun doTick() {
        invokeQueue.forEach {
            it(particle)
        }
        // 防呆用的
        if (particle.death) {
            ControlParticleManager.removeControl(uuid)
        }
    }

    fun rotateParticleTo(target: RelativeLocation) {
        rotateParticleTo(Vector3f(target.x.toFloat(), target.y.toFloat(), target.z.toFloat()))
    }

    fun rotateParticleTo(target: Vec3) {
        rotateParticleTo(target.toVector3f())
    }

    fun rotateParticleTo(target: Vector3f) {
        particle.rotateParticleTo(target)
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
        particle.teleportTo(pos)
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        particle.teleportTo(x, y, z)
    }

    /**
     * @see ControlableParticle.markDead()
     */
    override fun remove() {
        particle.remove()
    }

    override fun getControlObject(): ControlableParticle {
        return particle
    }


}