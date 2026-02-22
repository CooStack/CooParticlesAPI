package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.network.packet.server.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

object ServerCameraUtil {
    fun sendShake(world: ServerLevel, amplitude: Double, tick: Int) {
        require(tick > 0)
        require(amplitude > 0.0)
        val packet = PacketCameraShakeS2C.shake(-1.0, Vec3.ZERO, amplitude, tick)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    fun sendShake(world: ServerLevel, origin: Vec3, range: Double, amplitude: Double, tick: Int) {
        require(range > 0)
        require(amplitude > 0.0)
        require(tick > 0)
        val packet = PacketCameraShakeS2C.shake(range, origin, amplitude, tick)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    fun sendShake(target: ServerPlayer, amplitude: Double, tick: Int) {
        require(tick > 0)
        require(amplitude > 0.0)
        CooParticlesServices.SERVER_NETWORK.send(
            PacketCameraShakeS2C.shake(-1.0, Vec3.ZERO, amplitude, tick),
            target
        )
    }

    fun sendShake(target: ServerPlayer, origin: Vec3, range: Double, amplitude: Double, tick: Int) {
        require(range > 0)
        require(amplitude > 0.0)
        require(tick > 0)
        CooParticlesServices.SERVER_NETWORK.send(
            PacketCameraShakeS2C.shake(range, origin, amplitude, tick),
            target
        )
    }

    fun setCameraOffset(
        target: ServerPlayer,
        positionOffset: Vec3,
        yawOffset: Float = 0f,
        pitchOffset: Float = 0f,
        instant: Boolean = false
    ) {
        CooParticlesServices.SERVER_NETWORK.send(
            PacketCameraShakeS2C.setOffset(positionOffset, yawOffset, pitchOffset, instant),
            target
        )
    }

    fun resetCameraOffset(target: ServerPlayer, instant: Boolean = false) {
        CooParticlesServices.SERVER_NETWORK.send(PacketCameraShakeS2C.resetOffset(instant), target)
    }

    fun forceCameraPosition(target: ServerPlayer, position: Vec3, instant: Boolean = false) {
        CooParticlesServices.SERVER_NETWORK.send(PacketCameraShakeS2C.forcePosition(position, instant), target)
    }

    fun forceCameraPosition(world: ServerLevel, position: Vec3, instant: Boolean = false) {
        val packet = PacketCameraShakeS2C.forcePosition(position, instant)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    fun forceCameraPosition(world: ServerLevel, origin: Vec3, range: Double, position: Vec3, instant: Boolean = false) {
        require(range > 0)
        val packet = PacketCameraShakeS2C.forcePosition(position, instant)
        world.players().forEach {
            if (it.position().distanceTo(origin) > range) {
                return@forEach
            }
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    fun resetForcedCameraPosition(target: ServerPlayer, instant: Boolean = false) {
        CooParticlesServices.SERVER_NETWORK.send(PacketCameraShakeS2C.resetForcePosition(instant), target)
    }

    fun resetForcedCameraPosition(world: ServerLevel, instant: Boolean = false) {
        val packet = PacketCameraShakeS2C.resetForcePosition(instant)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    fun resetCamera(target: ServerPlayer, instant: Boolean = false) {
        CooParticlesServices.SERVER_NETWORK.send(PacketCameraShakeS2C.resetAll(instant), target)
    }
}

