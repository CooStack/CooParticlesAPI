package cn.coostack.cooparticlesapi.network.particle.util

import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleBatchS2C
import cn.coostack.cooparticlesapi.network.packet.server.PacketParticleS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

object ServerParticleUtil {

    @JvmStatic
    fun spawnBatch(
        type: ParticleOptions,
        world: ServerLevel,
        positions: Iterable<Vec3>,
        velocity: Vec3,
        range: Double,
    ) {
        val allPositions = positions.toList()
        if (allPositions.isEmpty()) return
        world.players().forEach { player ->
            val visiblePositions = allPositions.filter { player.position().distanceTo(it) <= range }
            visiblePositions.chunked(PacketParticleBatchS2C.MAX_PARTICLES).forEach { batch ->
                CooParticlesServices.SERVER_NETWORK.send(PacketParticleBatchS2C(type, batch, velocity), player)
            }
        }
    }

    @JvmStatic
    fun spawnBatch(
        type: ParticleOptions,
        world: ServerLevel,
        positions: Iterable<Vec3>,
        velocity: Vec3,
    ) {
        positions.toList()
            .chunked(PacketParticleBatchS2C.MAX_PARTICLES)
            .filter { it.isNotEmpty() }
            .forEach { batch ->
                val packet = PacketParticleBatchS2C(type, batch, velocity)
                world.players().forEach { CooParticlesServices.SERVER_NETWORK.send(packet, it) }
            }
    }
    /**
     * 使用minecraft的 spawnParticle方法
     * 可能无法设置粒子移动方向
     */
    fun spawnSingle(
        type: ParticleOptions,
        world: ServerLevel, pos: Vec3, delta: Vec3, force: Boolean, speed: Double, count: Int
    ) {
        world.players().forEach {
            world.sendParticles(
                it, type, force, pos.x, pos.y, pos.z, count, delta.x, delta.y, delta.z, speed
            )
        }
    }

    /**
     * 使用minecraft的 spawnParticle方法
     * 可能无法设置粒子移动方向
     */
    fun spawnSingle(
        type: ParticleOptions,
        world: ServerLevel, pos: Vec3, delta: Vec3, force: Boolean, speed: Double, count: Int, range: Double
    ) {
        world.players().forEach {
            if (it.position().distanceTo(pos) > range) {
                return@forEach
            }
            world.sendParticles(
                it, type, force, pos.x, pos.y, pos.z, count, delta.x, delta.y, delta.z, speed
            )
        }
    }

    /**
     * 使用CooParticleAPI的 spawnParticle方法
     */
    fun spawnSingle(
        type: ParticleOptions,
        world: ServerLevel,
        pos: RelativeLocation,
        velocity: RelativeLocation,
        range: Double
    ) {
        spawnSingle(type, world, pos.toVector(), velocity.toVector(), range)
    }

    fun spawnSingle(type: ParticleOptions, world: ServerLevel, pos: RelativeLocation, velocity: RelativeLocation) {
        spawnSingle(type, world, pos.toVector(), velocity.toVector())
    }

    fun spawnSingle(type: ParticleOptions, world: ServerLevel, pos: Vec3, velocity: Vec3, range: Double) {
        world.players().forEach {
            if (it.position().distanceTo(pos) > range) {
                return@forEach
            }
            CooParticlesServices.SERVER_NETWORK.send(PacketParticleS2C(type, pos, velocity), it)
        }
    }

    fun spawnSingle(type: ParticleOptions, world: ServerLevel, pos: Vec3, velocity: Vec3) {
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(PacketParticleS2C(type, pos, velocity), it)
        }
    }

}
