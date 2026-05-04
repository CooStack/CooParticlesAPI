package cn.coostack.cooparticlesapi.sound

import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3

/**
 * 服务端音频实例在客户端的镜像。
 *
 * [key] 是逻辑实例 id。同 key 的 PLAY 包会替换当前客户端声音。
 * 音量使用 SoundInstance 的百分比/倍率语义：0f 表示静音，1f 表示资源正常音量。
 * [relative] 是原版“相对监听者”模式，不是实体跟随。实体跟随由 [entityId] 控制；
 * [relative] 只决定声音是否按监听者相对声音处理，而不是普通世界坐标声音。
 */
class ManagedSoundInstance(
    val key: String,
    soundId: ResourceLocation,
    source: SoundSource,
    entityId: Int,
    initialPos: Vec3,
    initialVolume: Float,
    initialPitch: Float,
    looping: Boolean,
    relative: Boolean
) : AbstractTickableSoundInstance(
    SoundEvent.createVariableRangeEvent(soundId),
    source,
    SoundInstance.createUnseededRandom()
) {
    constructor(spec: SoundInstanceSpec) : this(
        key = spec.key,
        soundId = spec.soundId,
        source = spec.source,
        entityId = spec.entityId,
        initialPos = spec.position,
        initialVolume = spec.volume,
        initialPitch = spec.pitch,
        looping = spec.looping,
        relative = spec.relative
    ) {
        stopWhenBoundEntityMissing = spec.stopWhenBoundEntityMissing
    }

    var entityId: Int = entityId

    /**
     * 当前音量百分比/倍率，最终由 SoundEngine 按声源分类音量和系统设置计算实际输出。
     */
    var volumeMultiplier: Float = initialVolume
        set(value) {
            field = value.coerceAtLeast(0f)
        }

    var pitchMultiplier: Float = initialPitch
        set(value) {
            field = value
        }

    var position: Vec3
        get() = Vec3(x, y, z)
        set(value) {
            entityId = -1
            setRawPosition(value)
        }

    var posX: Double
        get() = x
        set(value) {
            entityId = -1
            x = value
        }

    var posY: Double
        get() = y
        set(value) {
            entityId = -1
            y = value
        }

    var posZ: Double
        get() = z
        set(value) {
            entityId = -1
            z = value
        }

    var loopingSound: Boolean
        get() = looping
        set(value) {
            looping = value
        }

    var loopDelay: Int
        get() = delay
        set(value) {
            delay = value.coerceAtLeast(0)
        }

    var relativeSound: Boolean
        get() = relative
        set(value) {
            relative = value
        }

    var stopWhenBoundEntityMissing: Boolean = true
    var isStoppingAfterCurrentLoop = false
        private set

    init {
        this.looping = looping
        this.delay = 0
        this.volumeMultiplier = initialVolume
        this.pitchMultiplier = initialPitch
        setRawPosition(initialPos)
        this.relative = relative
    }

    override fun tick() {
        if (entityId < 0) {
            return
        }
        val level = Minecraft.getInstance().level ?: run {
            stopIfEntityTrackingFailed()
            return
        }
        val entity = level.getEntity(entityId) ?: run {
            stopIfEntityTrackingFailed()
            return
        }
        if (!entity.isAlive) {
            stopIfEntityTrackingFailed()
            return
        }
        setRawPosition(entity.position())
    }

    override fun canStartSilent(): Boolean {
        return true
    }

    override fun getVolume(): Float {
        return volumeMultiplier
    }

    override fun getPitch(): Float {
        return pitchMultiplier
    }

    fun update(
        entityId: Int,
        pos: Vec3,
        volume: Float,
        pitch: Float,
        looping: Boolean = loopingSound,
        relative: Boolean = relativeSound
    ) {
        this.entityId = entityId
        this.volumeMultiplier = volume
        this.pitchMultiplier = pitch
        this.loopingSound = looping
        this.relativeSound = relative
        setRawPosition(pos)
    }

    fun bindToEntity(entityId: Int) {
        this.entityId = entityId
    }

    fun unbindEntity() {
        this.entityId = -1
    }

    fun stopNow() {
        stop()
    }

    fun stopAfterCurrentLoop() {
        isStoppingAfterCurrentLoop = true
        looping = false
    }

    fun toSpec(): SoundInstanceSpec {
        return SoundInstanceSpec(
            key = key,
            soundId = location,
            source = source,
            entityId = entityId,
            position = position,
            volume = volumeMultiplier,
            pitch = pitchMultiplier,
            looping = loopingSound,
            relative = relativeSound,
            stopWhenBoundEntityMissing = stopWhenBoundEntityMissing
        )
    }

    private fun setRawPosition(pos: Vec3) {
        x = pos.x
        y = pos.y
        z = pos.z
    }

    private fun stopIfEntityTrackingFailed() {
        if (stopWhenBoundEntityMissing) {
            stopNow()
        }
    }
}
