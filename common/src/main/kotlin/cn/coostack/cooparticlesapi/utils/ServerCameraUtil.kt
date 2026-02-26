package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.network.packet.server.PacketCameraShakeS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3

/**
 * 服务端相机控制工具。
 *
 * 用于向客户端发送相机抖动、相机偏移、强制相机位置及重置相关数据包。
 */
object ServerCameraUtil {
    /**
     * 向世界内所有玩家发送基础相机抖动（无距离衰减，使用默认频率）。
     *
     * @param world 服务端世界，会向该世界内全部在线玩家广播。
     * @param amplitude 抖动幅度，必须大于 0，值越大抖动越明显。
     * @param tick 抖动持续时长（单位：tick），必须大于 0。
     */
    fun sendShake(world: ServerLevel, amplitude: Double, tick: Int) {
        require(tick > 0)
        require(amplitude > 0.0)
        val packet = PacketCameraShakeS2C.shake(-1.0, Vec3.ZERO, amplitude, tick)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    /**
     * 向世界内所有玩家发送相机抖动（无距离衰减，可指定频率）。
     *
     * @param world 服务端世界，会向该世界内全部在线玩家广播。
     * @param amplitude 抖动幅度，必须大于 0，值越大抖动越明显。
     * @param tick 抖动持续时长（单位：tick），必须大于 0。
     * @param frequency 抖动频率系数，必须大于 0，值越大抖动目标更新越快。
     */
    fun sendShake(world: ServerLevel, amplitude: Double, tick: Int, frequency: Double) {
        require(tick > 0)
        require(amplitude > 0.0)
        require(frequency > 0.0)
        val packet = PacketCameraShakeS2C.shake(-1.0, Vec3.ZERO, amplitude, tick, frequency, false)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    /**
     * 向世界内所有玩家发送范围抖动（范围内生效，不做距离衰减）。
     *
     * @param world 服务端世界，会向该世界内全部在线玩家广播。
     * @param origin 抖动中心点，用于客户端进行距离判定。
     * @param range 生效半径，必须大于 0，超过该半径的玩家不会触发抖动。
     * @param amplitude 抖动幅度，必须大于 0，值越大抖动越明显。
     * @param tick 抖动持续时长（单位：tick），必须大于 0。
     */
    fun sendShake(world: ServerLevel, origin: Vec3, range: Double, amplitude: Double, tick: Int) {
        require(range > 0)
        require(amplitude > 0.0)
        require(tick > 0)
        val packet = PacketCameraShakeS2C.shake(range, origin, amplitude, tick)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    /**
     * 向世界内所有玩家发送范围抖动（可指定频率，可选距离衰减）。
     *
     * @param world 服务端世界，会向该世界内全部在线玩家广播。
     * @param origin 抖动中心点，用于客户端计算与中心点的距离。
     * @param range 生效半径，必须大于 0，超过该半径的玩家不会触发抖动。
     * @param amplitude 基础抖动幅度，必须大于 0。
     * @param tick 抖动持续时长（单位：tick），必须大于 0。
     * @param frequency 基础抖动频率系数，必须大于 0。
     * @param attenuateByDistance 是否按距离线性衰减振幅和频率。
     * 为 true 时，客户端按 (1 - distance / range) 衰减。
     */
    fun sendShake(
        world: ServerLevel,
        origin: Vec3,
        range: Double,
        amplitude: Double,
        tick: Int,
        frequency: Double,
        attenuateByDistance: Boolean = false
    ) {
        require(range > 0)
        require(amplitude > 0.0)
        require(tick > 0)
        require(frequency > 0.0)
        val packet = PacketCameraShakeS2C.shake(range, origin, amplitude, tick, frequency, attenuateByDistance)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    /**
     * 向单个玩家发送基础相机抖动（无距离衰减，使用默认频率）。
     *
     * @param target 目标玩家。
     * @param amplitude 抖动幅度，必须大于 0，值越大抖动越明显。
     * @param tick 抖动持续时长（单位：tick），必须大于 0。
     */
    fun sendShake(target: ServerPlayer, amplitude: Double, tick: Int) {
        require(tick > 0)
        require(amplitude > 0.0)
        CooParticlesServices.SERVER_NETWORK.send(
            PacketCameraShakeS2C.shake(-1.0, Vec3.ZERO, amplitude, tick),
            target
        )
    }

    /**
     * 向单个玩家发送相机抖动（无距离衰减，可指定频率）。
     *
     * @param target 目标玩家。
     * @param amplitude 抖动幅度，必须大于 0，值越大抖动越明显。
     * @param tick 抖动持续时长（单位：tick），必须大于 0。
     * @param frequency 抖动频率系数，必须大于 0，值越大抖动目标更新越快。
     */
    fun sendShake(target: ServerPlayer, amplitude: Double, tick: Int, frequency: Double) {
        require(tick > 0)
        require(amplitude > 0.0)
        require(frequency > 0.0)
        CooParticlesServices.SERVER_NETWORK.send(
            PacketCameraShakeS2C.shake(-1.0, Vec3.ZERO, amplitude, tick, frequency, false),
            target
        )
    }

    /**
     * 向单个玩家发送范围抖动（范围内生效，不做距离衰减）。
     *
     * @param target 目标玩家。
     * @param origin 抖动中心点，用于客户端进行距离判定。
     * @param range 生效半径，必须大于 0，超过该半径不会触发抖动。
     * @param amplitude 抖动幅度，必须大于 0，值越大抖动越明显。
     * @param tick 抖动持续时长（单位：tick），必须大于 0。
     */
    fun sendShake(target: ServerPlayer, origin: Vec3, range: Double, amplitude: Double, tick: Int) {
        require(range > 0)
        require(amplitude > 0.0)
        require(tick > 0)
        CooParticlesServices.SERVER_NETWORK.send(
            PacketCameraShakeS2C.shake(range, origin, amplitude, tick),
            target
        )
    }

    /**
     * 向单个玩家发送范围抖动（可指定频率，可选距离衰减）。
     *
     * @param target 目标玩家。
     * @param origin 抖动中心点，用于客户端计算与中心点的距离。
     * @param range 生效半径，必须大于 0，超过该半径不会触发抖动。
     * @param amplitude 基础抖动幅度，必须大于 0。
     * @param tick 抖动持续时长（单位：tick），必须大于 0。
     * @param frequency 基础抖动频率系数，必须大于 0。
     * @param attenuateByDistance 是否按距离线性衰减振幅和频率。
     * 为 true 时，客户端按 (1 - distance / range) 衰减。
     */
    fun sendShake(
        target: ServerPlayer,
        origin: Vec3,
        range: Double,
        amplitude: Double,
        tick: Int,
        frequency: Double,
        attenuateByDistance: Boolean = false
    ) {
        require(range > 0)
        require(amplitude > 0.0)
        require(tick > 0)
        require(frequency > 0.0)
        CooParticlesServices.SERVER_NETWORK.send(
            PacketCameraShakeS2C.shake(range, origin, amplitude, tick, frequency, attenuateByDistance),
            target
        )
    }

    /**
     * 设置单个玩家的相机偏移。
     *
     * @param target 目标玩家。
     * @param positionOffset 相机位置偏移向量（x/y/z）。
     * @param yawOffset 相机偏航角偏移（单位：度）。
     * @param pitchOffset 相机俯仰角偏移（单位：度）。
     * @param instant 是否立即生效。true 为立即应用，false 为客户端平滑过渡。
     */
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

    /**
     * 重置单个玩家的相机偏移。
     *
     * @param target 目标玩家。
     * @param instant 是否立即重置。true 为立即重置，false 为客户端平滑过渡。
     */
    fun resetCameraOffset(target: ServerPlayer, instant: Boolean = false) {
        CooParticlesServices.SERVER_NETWORK.send(PacketCameraShakeS2C.resetOffset(instant), target)
    }

    /**
     * 强制设置单个玩家的相机位置。
     *
     * @param target 目标玩家。
     * @param position 目标相机位置。
     * @param instant 是否立即生效。true 为立即应用，false 为客户端平滑过渡。
     */
    fun forceCameraPosition(target: ServerPlayer, position: Vec3, instant: Boolean = false) {
        CooParticlesServices.SERVER_NETWORK.send(PacketCameraShakeS2C.forcePosition(position, instant), target)
    }

    /**
     * 强制设置世界内所有玩家的相机位置。
     *
     * @param world 服务端世界，会向该世界内全部在线玩家广播。
     * @param position 目标相机位置。
     * @param instant 是否立即生效。true 为立即应用，false 为客户端平滑过渡。
     */
    fun forceCameraPosition(world: ServerLevel, position: Vec3, instant: Boolean = false) {
        val packet = PacketCameraShakeS2C.forcePosition(position, instant)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    /**
     * 强制设置世界内范围玩家的相机位置。
     *
     * @param world 服务端世界，会向该世界内在线玩家进行距离筛选。
     * @param origin 距离判定中心点。
     * @param range 生效半径，必须大于 0，超过该半径的玩家不会收到该设置。
     * @param position 目标相机位置。
     * @param instant 是否立即生效。true 为立即应用，false 为客户端平滑过渡。
     */
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

    /**
     * 重置单个玩家的强制相机位置状态。
     *
     * @param target 目标玩家。
     * @param instant 是否立即重置。true 为立即重置，false 为客户端平滑过渡。
     */
    fun resetForcedCameraPosition(target: ServerPlayer, instant: Boolean = false) {
        CooParticlesServices.SERVER_NETWORK.send(PacketCameraShakeS2C.resetForcePosition(instant), target)
    }

    /**
     * 重置世界内所有玩家的强制相机位置状态。
     *
     * @param world 服务端世界，会向该世界内全部在线玩家广播。
     * @param instant 是否立即重置。true 为立即重置，false 为客户端平滑过渡。
     */
    fun resetForcedCameraPosition(world: ServerLevel, instant: Boolean = false) {
        val packet = PacketCameraShakeS2C.resetForcePosition(instant)
        world.players().forEach {
            CooParticlesServices.SERVER_NETWORK.send(packet, it)
        }
    }

    /**
     * 重置单个玩家的全部相机效果（抖动、偏移、强制位置）。
     *
     * @param target 目标玩家。
     * @param instant 是否立即重置。true 为立即重置，false 为客户端平滑过渡。
     */
    fun resetCamera(target: ServerPlayer, instant: Boolean = false) {
        CooParticlesServices.SERVER_NETWORK.send(PacketCameraShakeS2C.resetAll(instant), target)
    }
}
