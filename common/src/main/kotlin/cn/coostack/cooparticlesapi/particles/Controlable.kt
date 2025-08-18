package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * T 控制器对象
 */
interface Controlable<T> {
    fun controlUUID(): UUID
    fun rotateParticlesToPoint(to: RelativeLocation)
    fun rotateToWithAngle(to: RelativeLocation, angle: Double)
    fun rotateParticlesAsAxis(angle: Double)
    fun teleportTo(pos: Vec3)
    fun teleportTo(x: Double, y: Double, z: Double)
    fun remove()
    fun getControlObject(): T
    fun <S> getControlCasted(): S {
        val obj = getControlObject()
        return obj as S
    }

    fun <S> getControlCastedOrNull(): S? {
        val obj = getControlObject()
        return runCatching { obj as S }.getOrNull()
    }
}
