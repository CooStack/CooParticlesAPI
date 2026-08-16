package cn.coostack.cooparticlesapi.coofx.runtime.mesh.simulation

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshParticleStore
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.extend.times
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * 首版最低能力 CPU 模拟器。
 *
 * 每 tick 使用固定顺序执行 previous 滚动、经典力积分、drag、位置积分、角速度积分、
 * clip time 推进和生命周期回收。噪声只依赖完整 particle seed 与整数年龄。
 */
class CooFxMeshParticleSimulator {
    /** 推进一个逻辑 tick，并返回本 tick 回收的粒子数量。 */
    fun tick(store: CooFxMeshParticleStore): Int {
        var removed = 0
        var index = store.size - 1
        while (index >= 0) {
            simulateParticle(store, index)
            if (store.ages[index] >= store.lifetimes[index]) {
                store.removeAt(index)
                removed++
            }
            index--
        }
        return removed
    }

    private fun simulateParticle(store: CooFxMeshParticleStore, index: Int) {
        val position = store.vector3(store.positions, index)
        val velocity = store.vector3(store.velocities, index)
        val acceleration = store.vector3(store.accelerations, index)
        val gravity = store.vector3(store.gravities, index)
        val wind = store.vector3(store.winds, index)
        val noise = deterministicNoise(store, index)
        val dragMultiplier = 1F - store.drags[index]

        store.setVector3(store.previousPositions, index, position)
        val nextVelocity = (velocity + acceleration + gravity + wind + noise) * dragMultiplier
        store.setVector3(store.velocities, index, nextVelocity)
        store.setVector3(store.positions, index, position + nextVelocity)

        val rotation = store.quaternion(store.rotations, index)
        store.setQuaternion(store.previousRotations, index, rotation)
        val angularVelocity = store.vector3(store.angularVelocities, index)
        store.setQuaternion(store.rotations, index, integrateRotation(rotation, angularVelocity))

        store.previousClipTimes[index] = store.clipTimes[index]
        store.clipTimes[index] += store.playbackSpeeds[index] * 0.05F
        store.ages[index]++
    }

    private fun deterministicNoise(store: CooFxMeshParticleStore, index: Int): Vector3f {
        val frequency = store.noiseFrequencyTicks[index]
        val sampleTick = store.ages[index] / frequency
        val seed = store.particleSeeds[index] xor (sampleTick.toLong() shl 8)
        val amplitude = store.vector3(store.noiseAmplitudes, index)
        return Vector3f(
            centeredFloat(mix64(seed xor 0x58L)) * amplitude.x,
            centeredFloat(mix64(seed xor 0x59L)) * amplitude.y,
            centeredFloat(mix64(seed xor 0x5AL)) * amplitude.z,
        )
    }

    private fun integrateRotation(rotation: Quaternionf, angularVelocity: Vector3f): Quaternionf {
        val halfX = angularVelocity.x * 0.5F
        val halfY = angularVelocity.y * 0.5F
        val halfZ = angularVelocity.z * 0.5F
        val sinX = sin(halfX)
        val cosX = cos(halfX)
        val sinY = sin(halfY)
        val cosY = cos(halfY)
        val sinZ = sin(halfZ)
        val cosZ = cos(halfZ)
        val delta = Quaternionf(
            sinX * cosY * cosZ - cosX * sinY * sinZ,
            cosX * sinY * cosZ + sinX * cosY * sinZ,
            cosX * cosY * sinZ - sinX * sinY * cosZ,
            cosX * cosY * cosZ + sinX * sinY * sinZ,
        )
        return Quaternionf(rotation).mul(delta).normalize()
    }

    private fun mix64(input: Long): Long {
        var value = input + 0x9E3779B97F4A7C15uL.toLong()
        value = (value xor (value ushr 30)) * 0xBF58476D1CE4E5B9uL.toLong()
        value = (value xor (value ushr 27)) * 0x94D049BB133111EBuL.toLong()
        return value xor (value ushr 31)
    }

    private fun centeredFloat(value: Long): Float {
        val normalized = ((value ushr 40) and 0xFFFFFFL).toFloat() / 16777216F
        return normalized * 2F - 1F
    }
}
