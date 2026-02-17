package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.network.packet.server.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3

object ServerCameraUtil {
    fun sendShake(world: ServerLevel, amplitude: Double, tick: Int) {
        require(tick > 0)
        require(amplitude > 0.0)
        val packet = PacketCameraShakeS2C(-1.0, Vec3.ZERO, amplitude, tick)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    fun sendShake(world: ServerLevel, origin: Vec3, range: Double, amplitude: Double, tick: Int) {
        require(range > 0)
        require(amplitude > 0.0)
        require(tick > 0)
        val packet = PacketCameraShakeS2C(range, origin, amplitude, tick)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

}