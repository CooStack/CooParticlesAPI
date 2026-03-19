package cn.coostack.cooparticlesapi.api.controler.server

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 面向服务端控制语义的基础接口。
 *
 * 它定义了一类对象最常见的“控制动作”，例如生成、移动、旋转和移除。
 */
interface ServerControler<T> {
    /**
     * 直接把对象移动到指定位置。
     */
    fun teleportTo(to: Vec3)

    /**
     * `teleportTo(Vec3)` 的坐标拆分重载。
     */
    fun teleportTo(x: Double, y: Double, z: Double)

    /**
     * 让对象朝向某个相对位置。
     */
    fun rotateToPoint(to: RelativeLocation)

    /**
     * 以给定弧度朝向某个相对位置。
     */
    fun rotateToWithAngle(to: RelativeLocation, radian: Double)

    /**
     * 围绕自身轴旋转给定弧度。
     */
    fun rotateAsAxis(radian: Double)

    /**
     * 标记对象已移除。
     */
    fun remove()

    /**
     * 在服务端世界中生成对象。
     */
    fun spawn(world: Level, pos: Vec3)

    /**
     * 返回当前控制器实际承载的对象值。
     */
    fun getValue(): T

    /**
     * 判断当前对象是否仍处于有效状态。
     */
    fun isValid(): Boolean

}
