package cn.coostack.cooparticlesapi.network.particle

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI

interface ServerControler<T> {
    fun teleportTo(to: Vec3)

    fun teleportTo(x: Double, y: Double, z: Double)

    fun rotateParticlesToPoint(to: RelativeLocation)

    fun rotateToWithAngle(to: RelativeLocation, angle: Double)

    fun rotateParticlesAsAxis(angle: Double)

    fun remove()

    /**
     * 在服务器进行渲染
     */
    fun spawn(world: Level, pos: Vec3)

    fun getValue(): T
}