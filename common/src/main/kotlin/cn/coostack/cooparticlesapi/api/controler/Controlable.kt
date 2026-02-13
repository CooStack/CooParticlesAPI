package cn.coostack.cooparticlesapi.api.controler

import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * T 控制器对象
 */
interface Controlable<T> {
    fun controlUUID(): UUID
    fun rotateToPoint(to: RelativeLocation)
    fun rotateToWithAngle(to: RelativeLocation, radian: Double)

    /**
     * 将图形绕着他的轴旋转 radian弧度
     *
     * roll += radian
     *
     * @param radian 弧度
     */
    fun rotateAsAxis(radian: Double)
    fun teleportTo(to: Vec3)
    fun teleportTo(x: Double, y: Double, z: Double)
    fun remove(reason: RemoveReason)
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