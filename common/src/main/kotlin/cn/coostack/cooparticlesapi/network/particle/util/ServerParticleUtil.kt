package cn.coostack.cooparticlesapi.network.particle.util

import cn.coostack.cooparticlesapi.network.packet.PacketParticleS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

object ServerParticleUtil {
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