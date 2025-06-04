package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.network.packet.PacketCameraShakeS2C
import net.fabricmc.api.EnvType
import net.fabricmc.api.Environment
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World

object ServerCameraUtil {
    fun sendShake(world: ServerWorld, amplitude: Double, tick: Int) {
        require(tick > 0)
        require(amplitude > 0.0)
        val packet = PacketCameraShakeS2C(-1.0, Vec3d.ZERO, amplitude, tick)
        world.players.forEach {
            ServerPlayNetworking.send(it, packet)
        }
    }

    fun sendShake(world: ServerWorld, origin: Vec3d, range: Double, amplitude: Double, tick: Int) {
        require(range > 0)
        require(amplitude > 0.0)
        require(tick > 0)
        val packet = PacketCameraShakeS2C(range, origin, amplitude, tick)
        world.players.forEach {
            ServerPlayNetworking.send(it, packet)
        }
    }

}