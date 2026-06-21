package cn.coostack.cooparticlesapi.supports.sound

import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

/**
 * 可同步音频实例的统一参数快照。
 *
 * 这个类型不引用客户端专属类，也不保存服务端世界对象，所以可以同时被服务端构建逻辑和
 * 客户端播放镜像使用。真正播放时：服务端会把它构造成 [ServerManagedSoundInstance]，
 * 客户端会把它构造成 [ManagedSoundInstance]。
 *
 * [key] 是实例身份。同一个 key 表示同一个可控声音；不同 key 才能同时播放多个实例。
 * [volume] 是客户端播放音量百分比/倍率，0f 表示静音，1f 表示资源正常音量。
 * [relative] 是原版“相对监听者”声音，不是实体跟随。实体跟随由 [entityId] 或服务端绑定实体控制。
 * [volumeFalloff] 决定服务端是否按玩家到声音位置的距离，为不同玩家计算不同音量。
 */
data class SoundInstanceSpec @JvmOverloads constructor(
    val key: String,
    val soundId: ResourceLocation,
    val source: SoundSource,
    val entityId: Int = -1,
    val position: Vec3 = Vec3.ZERO,
    val volume: Float = 1f,
    val pitch: Float = 1f,
    val looping: Boolean = false,
    val relative: Boolean = false,
    val self: Boolean = true,
    val visibleRange: Double = -1.0,
    val volumeFalloff: SoundVolumeFalloff = SoundVolumeFalloff.NONE,
    val syncEveryTick: Boolean = true,
    val stopWhenBoundEntityMissing: Boolean = true
)
