package cn.coostack.cooparticlesapi.network.particle

import cn.coostack.cooparticlesapi.network.buffer.ParticleControlerDataBuffers
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.util.math.Vec3d
import kotlin.math.PI

interface ServerControler<T> {

    fun teleportTo(to: Vec3d)

    fun teleportTo(x: Double, y: Double, z: Double)

    fun rotateParticlesToPoint(to: RelativeLocation)

    fun rotateToWithAngle(to: RelativeLocation, angle: Double)

    fun rotateParticlesAsAxis(angle: Double)

    fun remove()

    fun getValue(): T
}