package cn.coostack.cooparticlesapi.network.packet.client.listener

import cn.coostack.cooparticlesapi.network.packet.server.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.platform.network.ClientContext
import cn.coostack.cooparticlesapi.utils.ClientCameraUtil
import net.minecraft.world.phys.Vec3

object ClientCameraShakeHandler {
    fun receive(
        payload: PacketCameraShakeS2C,
        context: ClientContext
    ) {
        when (payload.operation) {
            PacketCameraShakeS2C.CameraOperation.SHAKE -> handleShake(payload, context)
            PacketCameraShakeS2C.CameraOperation.SET_OFFSET -> {
                if (payload.instant) {
                    ClientCameraUtil.setOffsetNow(payload.position, payload.yawOffset, payload.pitchOffset)
                } else {
                    ClientCameraUtil.setOffset(payload.position, payload.yawOffset, payload.pitchOffset)
                }
            }

            PacketCameraShakeS2C.CameraOperation.RESET_OFFSET -> {
                if (payload.instant) {
                    ClientCameraUtil.resetOffsetNow()
                } else {
                    ClientCameraUtil.setOffset(Vec3.ZERO, 0f, 0f)
                }
            }

            PacketCameraShakeS2C.CameraOperation.FORCE_POSITION -> {
                if (payload.instant) {
                    ClientCameraUtil.setForcedCameraPositionNow(payload.position)
                } else {
                    ClientCameraUtil.setForcedCameraPosition(payload.position)
                }
            }

            PacketCameraShakeS2C.CameraOperation.RESET_FORCE_POSITION -> {
                if (payload.instant) {
                    ClientCameraUtil.resetForcedCameraPositionNow()
                } else {
                    ClientCameraUtil.resetForcedCameraPosition()
                }
            }

            PacketCameraShakeS2C.CameraOperation.RESET_ALL -> {
                if (payload.instant) {
                    ClientCameraUtil.resetAllNow()
                } else {
                    ClientCameraUtil.stopShakeCamera()
                    ClientCameraUtil.setOffset(Vec3.ZERO, 0f, 0f)
                    ClientCameraUtil.resetForcedCameraPosition()
                }
            }
        }
    }

    private fun handleShake(payload: PacketCameraShakeS2C, context: ClientContext) {
        val range = payload.range
        val player = context.player()
        if (range > 0) {
            val distance = player.position().distanceTo(payload.origin)
            if (distance > range) {
                return
            }
        }
        ClientCameraUtil.startShakeCamera(payload.tick, payload.amplitude)
    }
}
