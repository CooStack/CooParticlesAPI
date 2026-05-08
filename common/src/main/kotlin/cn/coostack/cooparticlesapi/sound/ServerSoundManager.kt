package cn.coostack.cooparticlesapi.sound

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundInstanceS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.scheduler.CooScheduler
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ServerSoundManager {
    private val sounds = ConcurrentHashMap<String, ServerManagedSoundInstance>()
    private val duckingEffects = ConcurrentHashMap<String, ServerDuckingSoundEffect>()
    private val soundViewers = ConcurrentHashMap<String, MutableSet<UUID>>()
    private val duckingViewers = ConcurrentHashMap<String, MutableSet<UUID>>()

    /**
     * 创建稳定的实体级实例 key。复用同一个 key 表示控制/替换同一个逻辑声音；
     * 如果多个声音需要重叠播放，就使用不同 name 或 layer。
     */
    @JvmStatic
    fun key(entity: Entity, name: String): String {
        return SoundInstanceKeys.entity(entity, name)
    }

    /**
     * 创建带额外 layer 的实体级实例 key，适合需要多个声音重叠播放的场景。
     */
    @JvmStatic
    fun key(entity: Entity, name: String, layer: String): String {
        return SoundInstanceKeys.entity(entity, name, layer)
    }

    @JvmStatic
    @JvmOverloads
    fun builder(sound: SoundEvent, source: SoundSource = SoundSource.MASTER): SoundInstanceBuilder {
        return SoundInstanceBuilder(sound, source)
    }

    @JvmStatic
    @JvmOverloads
    fun builder(sound: ResourceLocation, source: SoundSource = SoundSource.MASTER): SoundInstanceBuilder {
        return SoundInstanceBuilder(sound, source)
    }

    @JvmStatic
    @JvmOverloads
    fun instance(sound: SoundEvent, source: SoundSource = SoundSource.MASTER): SoundInstanceBuilder {
        return builder(sound, source)
    }

    @JvmStatic
    @JvmOverloads
    fun instance(sound: ResourceLocation, source: SoundSource = SoundSource.MASTER): SoundInstanceBuilder {
        return builder(sound, source)
    }

    @JvmStatic
    fun get(key: String): ServerManagedSoundInstance? {
        return sounds[key]
    }

    @JvmStatic
    fun getDucking(key: String): ServerDuckingSoundEffect? {
        return duckingEffects[key]
    }

    @JvmStatic
    fun activeSounds(): List<ServerManagedSoundInstance> {
        return sounds.values.toList()
    }

    @JvmStatic
    fun activeDuckingEffects(): List<ServerDuckingSoundEffect> {
        return duckingEffects.values.toList()
    }

    @JvmStatic
    fun isPlaying(key: String): Boolean {
        return sounds[key]?.isStopped == false
    }

    @JvmStatic
    fun isDucking(key: String): Boolean {
        return duckingEffects[key]?.isStopped == false
    }

    /**
     * 将声音淡变到目标音量百分比/倍率。0f 为静音，1f 为正常资源音量。
     */
    @JvmStatic
    @JvmOverloads
    fun fadeTo(
        key: String,
        targetVolume: Float,
        ticks: Int,
        stopWhenFinished: Boolean = false,
        interruptWhenStopped: Boolean = true
    ): CooScheduler.TickRunnable? {
        return sounds[key]?.fadeTo(targetVolume, ticks, stopWhenFinished, interruptWhenStopped)
    }

    @JvmStatic
    @JvmOverloads
    fun fadeOut(
        key: String,
        ticks: Int,
        stopWhenFinished: Boolean = true,
        interruptWhenStopped: Boolean = true
    ): CooScheduler.TickRunnable? {
        return sounds[key]?.fadeOut(ticks, stopWhenFinished, interruptWhenStopped)
    }

    @JvmStatic
    fun fadeIn(key: String, ticks: Int): CooScheduler.TickRunnable? {
        return sounds[key]?.fadeIn(ticks)
    }

    /**
     * 从 [fromVolume] 淡入到 [targetVolume]。两个参数都是音量百分比/倍率。
     */
    @JvmStatic
    @JvmOverloads
    fun fadeIn(
        key: String,
        ticks: Int,
        targetVolume: Float,
        fromVolume: Float = 0f
    ): CooScheduler.TickRunnable? {
        return sounds[key]?.fadeIn(ticks, targetVolume, fromVolume)
    }

    @JvmStatic
    @JvmOverloads
    fun fadeDuckingTo(
        key: String,
        targetVolumeMultiplier: Float,
        ticks: Int,
        stopWhenFinished: Boolean = false
    ): CooScheduler.TickRunnable? {
        return duckingEffects[key]?.fadeTo(targetVolumeMultiplier, ticks, stopWhenFinished)
    }

    @JvmStatic
    @JvmOverloads
    fun fadeDuckingOut(key: String, ticks: Int, stopWhenFinished: Boolean = true): CooScheduler.TickRunnable? {
        return duckingEffects[key]?.fadeOut(ticks, stopWhenFinished)
    }

    @JvmStatic
    fun fadeDuckingIn(key: String, ticks: Int): CooScheduler.TickRunnable? {
        return duckingEffects[key]?.fadeIn(ticks)
    }

    @JvmStatic
    @JvmOverloads
    fun fadeDuckingIn(
        key: String,
        ticks: Int,
        targetVolumeMultiplier: Float,
        fromVolumeMultiplier: Float = 1f
    ): CooScheduler.TickRunnable? {
        return duckingEffects[key]?.fadeIn(ticks, targetVolumeMultiplier, fromVolumeMultiplier)
    }

    @JvmStatic
    fun spawn(instance: ServerManagedSoundInstance): ServerManagedSoundInstance {
        sounds[instance.key] = instance
        instance.markRestart()
        syncSound(instance)
        return instance
    }

    @JvmStatic
    fun spawn(spec: SoundInstanceSpec, world: ServerLevel): ServerManagedSoundInstance {
        return spawn(ServerManagedSoundInstance(spec, world))
    }

    @JvmStatic
    fun spawnDucking(effect: ServerDuckingSoundEffect): ServerDuckingSoundEffect {
        duckingEffects[effect.key] = effect
        effect.markDirty()
        syncDucking(effect, forceStart = true)
        return effect
    }

    @JvmStatic
    fun tick() {
        sounds.values.toList().forEach(::syncSound)
        duckingEffects.values.toList().forEach { syncDucking(it, forceStart = false) }
    }

    @JvmStatic
    fun clear() {
        sounds.values.toList().forEach {
            it.stopNow()
            syncSound(it)
        }
        duckingEffects.values.toList().forEach {
            it.stopNow()
            syncDucking(it, forceStart = false)
        }
        sounds.clear()
        duckingEffects.clear()
        soundViewers.clear()
        duckingViewers.clear()
    }

    @JvmStatic
    fun create(
        player: ServerPlayer,
        key: String,
        sound: SoundEvent,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        relative: Boolean = false
    ): ServerManagedSoundInstance {
        return create(player, key, sound.location, source, volume, pitch, looping, relative)
    }

    @JvmStatic
    fun create(
        player: ServerPlayer,
        key: String,
        sound: ResourceLocation,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        relative: Boolean = false
    ): ServerManagedSoundInstance {
        val instance = ServerManagedSoundInstance(
            key = key,
            soundId = sound,
            source = source,
            world = player.level() as ServerLevel,
            initialPos = player.position(),
            initialVolume = volume,
            initialPitch = pitch,
            looping = looping,
            relative = relative
        )
        instance.targetPlayer = player
        instance.bindToEntity(player)
        return spawn(instance)
    }

    @JvmStatic
    fun createTrackingEntity(
        entity: Entity,
        key: String,
        sound: SoundEvent,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        self: Boolean = true,
        relative: Boolean = false
    ): ServerManagedSoundInstance? {
        return createTrackingEntity(entity, key, sound.location, source, volume, pitch, looping, self, relative)
    }

    @JvmStatic
    fun createTrackingEntity(
        entity: Entity,
        key: String,
        sound: ResourceLocation,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        self: Boolean = true,
        relative: Boolean = false
    ): ServerManagedSoundInstance? {
        val level = entity.level() as? ServerLevel ?: return null
        val instance = ServerManagedSoundInstance(
            key = key,
            soundId = sound,
            source = source,
            world = level,
            initialPos = entity.position(),
            initialVolume = volume,
            initialPitch = pitch,
            looping = looping,
            relative = relative
        )
        instance.self = self
        instance.bindToEntity(entity)
        return spawn(instance)
    }

    @JvmStatic
    fun createAt(
        world: ServerLevel,
        pos: Vec3,
        key: String,
        sound: SoundEvent,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        relative: Boolean = false
    ): ServerManagedSoundInstance {
        return createAt(world, pos, key, sound.location, source, volume, pitch, looping, relative)
    }

    @JvmStatic
    fun createAt(
        world: ServerLevel,
        pos: Vec3,
        key: String,
        sound: ResourceLocation,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        relative: Boolean = false
    ): ServerManagedSoundInstance {
        return spawn(
            ServerManagedSoundInstance(
                key = key,
                soundId = sound,
                source = source,
                world = world,
                initialPos = pos,
                initialVolume = volume,
                initialPitch = pitch,
                looping = looping,
                relative = relative
            )
        )
    }

    @JvmStatic
    fun play(
        player: ServerPlayer,
        key: String,
        sound: SoundEvent,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        relative: Boolean = false
    ): ServerManagedSoundInstance {
        return create(player, key, sound, source, volume, pitch, looping, relative)
    }

    @JvmStatic
    fun play(
        player: ServerPlayer,
        key: String,
        sound: ResourceLocation,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        relative: Boolean = false
    ): ServerManagedSoundInstance {
        return create(player, key, sound, source, volume, pitch, looping, relative)
    }

    @JvmStatic
    fun playTrackingEntity(
        entity: Entity,
        key: String,
        sound: SoundEvent,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        self: Boolean = true,
        relative: Boolean = false
    ): Boolean {
        return playTrackingEntity(entity, key, sound.location, source, volume, pitch, looping, self, relative)
    }

    @JvmStatic
    fun playTrackingEntity(
        entity: Entity,
        key: String,
        sound: ResourceLocation,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        self: Boolean = true,
        relative: Boolean = false
    ): Boolean {
        return createTrackingEntity(entity, key, sound, source, volume, pitch, looping, self, relative) != null
    }

    @JvmStatic
    fun playAt(
        world: ServerLevel,
        pos: Vec3,
        key: String,
        sound: SoundEvent,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        relative: Boolean = false
    ): Boolean {
        return playAt(world, pos, key, sound.location, source, volume, pitch, looping, relative)
    }

    @JvmStatic
    fun playAt(
        world: ServerLevel,
        pos: Vec3,
        key: String,
        sound: ResourceLocation,
        source: SoundSource,
        volume: Float = 1f,
        pitch: Float = 1f,
        looping: Boolean = false,
        relative: Boolean = false
    ): Boolean {
        createAt(world, pos, key, sound, source, volume, pitch, looping, relative)
        return true
    }

    @JvmStatic
    fun update(player: ServerPlayer, key: String, volume: Float, pitch: Float = 1f) {
        val instance = sounds[key]
        if (instance != null) {
            instance.targetPlayer = player
            instance.bindToEntity(player)
            instance.volumeMultiplier = volume
            instance.pitchMultiplier = pitch
            syncSound(instance)
            return
        }
        CooParticlesServices.SERVER_NETWORK.send(
            PacketSoundInstanceS2C.update(key, player.id, player.position(), volume, pitch),
            player
        )
    }

    @JvmStatic
    fun updateTrackingEntity(
        entity: Entity,
        key: String,
        volume: Float,
        pitch: Float = 1f,
        self: Boolean = true
    ): Boolean {
        val instance = sounds[key]
        if (instance != null) {
            instance.self = self
            instance.bindToEntity(entity)
            instance.volumeMultiplier = volume
            instance.pitchMultiplier = pitch
            syncSound(instance)
            return true
        }
        val packet = PacketSoundInstanceS2C.update(key, entity.id, entity.position(), volume, pitch)
        return sendToEntityWatchers(entity, packet, self)
    }

    @JvmStatic
    fun updateAt(world: ServerLevel, pos: Vec3, key: String, volume: Float, pitch: Float = 1f) {
        val instance = sounds[key]
        if (instance != null) {
            instance.world = world
            instance.position = pos
            instance.volumeMultiplier = volume
            instance.pitchMultiplier = pitch
            syncSound(instance)
            return
        }
        sendToPlayersNear(
            world,
            pos,
            volume.toDouble() * 16.0,
            PacketSoundInstanceS2C.update(key, -1, pos, volume, pitch)
        )
    }

    @JvmStatic
    fun stop(instance: ServerManagedSoundInstance, interrupt: Boolean = true) {
        instance.stopNow(interrupt)
        if (sounds[instance.key] === instance) {
            syncSound(instance)
        }
    }

    @JvmStatic
    fun stop(player: ServerPlayer, key: String, interrupt: Boolean = true) {
        val instance = sounds[key]
        if (instance != null && instance.targetPlayer?.uuid == player.uuid) {
            instance.stopNow(interrupt)
            syncSound(instance)
            return
        }
        CooParticlesServices.SERVER_NETWORK.send(PacketSoundInstanceS2C.stop(key, interrupt), player)
    }

    @JvmStatic
    fun stopTrackingEntity(entity: Entity, key: String, self: Boolean = true, interrupt: Boolean = true): Boolean {
        val instance = sounds[key]
        if (instance != null) {
            instance.self = self
            instance.stopNow(interrupt)
            syncSound(instance)
            return true
        }
        return sendToEntityWatchers(entity, PacketSoundInstanceS2C.stop(key, interrupt), self)
    }

    @JvmStatic
    fun stopAt(world: ServerLevel, pos: Vec3, key: String, interrupt: Boolean = true) {
        val instance = sounds[key]
        if (instance != null) {
            instance.stopNow(interrupt)
            syncSound(instance)
            return
        }
        sendToPlayersNear(world, pos, 16.0, PacketSoundInstanceS2C.stop(key, interrupt))
    }

    @JvmStatic
    fun createDucking(
        player: ServerPlayer,
        key: String,
        volumeMultiplier: Float = 0f,
        range: Double = -1.0,
        origin: Vec3 = Vec3.ZERO,
        whitelistSounds: Set<ResourceLocation> = emptySet(),
        whitelistSources: Set<SoundSource> = emptySet(),
        whitelistKeys: Set<String> = emptySet()
    ): ServerDuckingSoundEffect {
        val effect = ServerDuckingSoundEffect(
            key = key,
            world = player.level() as ServerLevel,
            initialPos = origin,
            volumeMultiplier = volumeMultiplier,
            range = range,
            whitelistSounds = whitelistSounds,
            whitelistSources = whitelistSources,
            whitelistKeys = whitelistKeys
        )
        effect.targetPlayer = player
        return spawnDucking(effect)
    }

    @JvmStatic
    fun createDuckingTrackingEntity(
        entity: Entity,
        key: String,
        volumeMultiplier: Float = 0f,
        range: Double = -1.0,
        whitelistSounds: Set<ResourceLocation> = emptySet(),
        whitelistSources: Set<SoundSource> = emptySet(),
        whitelistKeys: Set<String> = emptySet(),
        self: Boolean = true
    ): ServerDuckingSoundEffect? {
        val level = entity.level() as? ServerLevel ?: return null
        val effect = ServerDuckingSoundEffect(
            key = key,
            world = level,
            initialPos = entity.position(),
            volumeMultiplier = volumeMultiplier,
            range = range,
            whitelistSounds = whitelistSounds,
            whitelistSources = whitelistSources,
            whitelistKeys = whitelistKeys
        )
        effect.self = self
        effect.bindToEntity(entity)
        return spawnDucking(effect)
    }

    @JvmStatic
    fun createDuckingAt(
        world: ServerLevel,
        pos: Vec3,
        key: String,
        volumeMultiplier: Float = 0f,
        range: Double = -1.0,
        whitelistSounds: Set<ResourceLocation> = emptySet(),
        whitelistSources: Set<SoundSource> = emptySet(),
        whitelistKeys: Set<String> = emptySet()
    ): ServerDuckingSoundEffect {
        return spawnDucking(
            ServerDuckingSoundEffect(
                key = key,
                world = world,
                initialPos = pos,
                volumeMultiplier = volumeMultiplier,
                range = range,
                whitelistSounds = whitelistSounds,
                whitelistSources = whitelistSources,
                whitelistKeys = whitelistKeys
            )
        )
    }

    @JvmStatic
    fun startDucking(
        player: ServerPlayer,
        key: String,
        volumeMultiplier: Float = 0f,
        range: Double = -1.0,
        origin: Vec3 = Vec3.ZERO,
        whitelistSounds: Set<ResourceLocation> = emptySet(),
        whitelistSources: Set<SoundSource> = emptySet(),
        whitelistKeys: Set<String> = emptySet()
    ): ServerDuckingSoundEffect {
        return createDucking(
            player,
            key,
            volumeMultiplier,
            range,
            origin,
            whitelistSounds,
            whitelistSources,
            whitelistKeys
        )
    }

    @JvmStatic
    fun startDuckingTrackingEntity(
        entity: Entity,
        key: String,
        volumeMultiplier: Float = 0f,
        range: Double = -1.0,
        whitelistSounds: Set<ResourceLocation> = emptySet(),
        whitelistSources: Set<SoundSource> = emptySet(),
        whitelistKeys: Set<String> = emptySet(),
        self: Boolean = true
    ): Boolean {
        return createDuckingTrackingEntity(
            entity,
            key,
            volumeMultiplier,
            range,
            whitelistSounds,
            whitelistSources,
            whitelistKeys,
            self
        ) != null
    }

    @JvmStatic
    fun updateDucking(
        player: ServerPlayer,
        key: String,
        volumeMultiplier: Float,
        range: Double = -1.0,
        origin: Vec3 = Vec3.ZERO,
        whitelistSounds: Set<ResourceLocation> = emptySet(),
        whitelistSources: Set<SoundSource> = emptySet(),
        whitelistKeys: Set<String> = emptySet()
    ) {
        val effect = duckingEffects[key]
        if (effect != null) {
            effect.targetPlayer = player
            effect.position = origin
            effect.volumeMultiplier = volumeMultiplier
            effect.range = range
            effect.whitelistSounds.replaceWith(whitelistSounds)
            effect.whitelistSources.replaceWith(whitelistSources)
            effect.whitelistKeys.replaceWith(whitelistKeys)
            effect.markDirty()
            syncDucking(effect, forceStart = false)
            return
        }
        CooParticlesServices.SERVER_NETWORK.send(
            PacketSoundInstanceS2C.duck(
                PacketSoundInstanceS2C.Action.DUCK_UPDATE,
                key,
                -1,
                origin,
                volumeMultiplier,
                range,
                whitelistSounds,
                whitelistSources,
                whitelistKeys
            ),
            player
        )
    }

    @JvmStatic
    fun updateDuckingTrackingEntity(
        entity: Entity,
        key: String,
        volumeMultiplier: Float,
        range: Double = -1.0,
        whitelistSounds: Set<ResourceLocation> = emptySet(),
        whitelistSources: Set<SoundSource> = emptySet(),
        whitelistKeys: Set<String> = emptySet(),
        self: Boolean = true
    ): Boolean {
        val effect = duckingEffects[key]
        if (effect != null) {
            effect.self = self
            effect.bindToEntity(entity)
            effect.volumeMultiplier = volumeMultiplier
            effect.range = range
            effect.whitelistSounds.replaceWith(whitelistSounds)
            effect.whitelistSources.replaceWith(whitelistSources)
            effect.whitelistKeys.replaceWith(whitelistKeys)
            effect.markDirty()
            syncDucking(effect, forceStart = false)
            return true
        }
        val packet = PacketSoundInstanceS2C.duck(
            PacketSoundInstanceS2C.Action.DUCK_UPDATE,
            key,
            entity.id,
            entity.position(),
            volumeMultiplier,
            range,
            whitelistSounds,
            whitelistSources,
            whitelistKeys
        )
        return sendToEntityWatchers(entity, packet, self)
    }

    @JvmStatic
    fun updateDuckingAt(
        world: ServerLevel,
        pos: Vec3,
        key: String,
        volumeMultiplier: Float,
        range: Double = -1.0,
        whitelistSounds: Set<ResourceLocation> = emptySet(),
        whitelistSources: Set<SoundSource> = emptySet(),
        whitelistKeys: Set<String> = emptySet()
    ) {
        val effect = duckingEffects[key]
        if (effect != null) {
            effect.world = world
            effect.position = pos
            effect.volumeMultiplier = volumeMultiplier
            effect.range = range
            effect.whitelistSounds.replaceWith(whitelistSounds)
            effect.whitelistSources.replaceWith(whitelistSources)
            effect.whitelistKeys.replaceWith(whitelistKeys)
            effect.markDirty()
            syncDucking(effect, forceStart = false)
            return
        }
        sendToPlayersNear(
            world,
            pos,
            range,
            PacketSoundInstanceS2C.duck(
                PacketSoundInstanceS2C.Action.DUCK_UPDATE,
                key,
                -1,
                pos,
                volumeMultiplier,
                range,
                whitelistSounds,
                whitelistSources,
                whitelistKeys
            )
        )
    }

    @JvmStatic
    fun stopDucking(effect: ServerDuckingSoundEffect) {
        effect.stopNow()
        if (duckingEffects[effect.key] === effect) {
            syncDucking(effect, forceStart = false)
        }
    }

    @JvmStatic
    fun stopDucking(player: ServerPlayer, key: String) {
        val effect = duckingEffects[key]
        if (effect != null && effect.targetPlayer?.uuid == player.uuid) {
            effect.stopNow()
            syncDucking(effect, forceStart = false)
            return
        }
        CooParticlesServices.SERVER_NETWORK.send(
            PacketSoundInstanceS2C.duck(
                PacketSoundInstanceS2C.Action.DUCK_STOP,
                key,
                -1,
                Vec3.ZERO,
                1f,
                -1.0,
                emptySet(),
                emptySet(),
                emptySet()
            ),
            player
        )
    }

    @JvmStatic
    fun stopDuckingTrackingEntity(entity: Entity, key: String, self: Boolean = true): Boolean {
        val effect = duckingEffects[key]
        if (effect != null) {
            effect.self = self
            effect.stopNow()
            syncDucking(effect, forceStart = false)
            return true
        }
        val packet = PacketSoundInstanceS2C.duck(
            PacketSoundInstanceS2C.Action.DUCK_STOP,
            key,
            entity.id,
            entity.position(),
            1f,
            -1.0,
            emptySet(),
            emptySet(),
            emptySet()
        )
        return sendToEntityWatchers(entity, packet, self)
    }

    @JvmStatic
    fun stopDuckingAt(world: ServerLevel, pos: Vec3, key: String) {
        val effect = duckingEffects[key]
        if (effect != null) {
            effect.stopNow()
            syncDucking(effect, forceStart = false)
            return
        }
        sendToPlayersNear(
            world,
            pos,
            16.0,
            PacketSoundInstanceS2C.duck(
                PacketSoundInstanceS2C.Action.DUCK_STOP,
                key,
                -1,
                pos,
                1f,
                -1.0,
                emptySet(),
                emptySet(),
                emptySet()
            )
        )
    }

    private fun syncSound(instance: ServerManagedSoundInstance) {
        instance.tick()
        val viewers = soundViewerSet(instance.key)
        pruneOfflineViewers(viewers)
        val needsPlay = instance.needsPlayPacket()
        val needsUpdate = instance.needsUpdatePacket()
        val stopPacket = if (instance.isStopped) instance.toStopPacket() else null

        CooParticlesAPI.server.playerList.players.forEach { player ->
            val wasVisible = player.uuid in viewers
            val shouldBeVisible = !instance.isStopped && instance.shouldSyncTo(player)
            when {
                shouldBeVisible && needsPlay -> {
                    CooParticlesServices.SERVER_NETWORK.send(instance.toPlayPacket(instance.volumeFor(player)), player)
                    viewers.add(player.uuid)
                }

                shouldBeVisible && wasVisible && needsUpdate -> {
                    CooParticlesServices.SERVER_NETWORK.send(instance.toUpdatePacket(instance.volumeFor(player)), player)
                }

                !shouldBeVisible && wasVisible -> {
                    CooParticlesServices.SERVER_NETWORK.send(stopPacket ?: instance.toStopPacket(), player)
                    viewers.remove(player.uuid)
                }
            }
        }

        if (instance.isStopped) {
            sounds.remove(instance.key, instance)
            soundViewers.remove(instance.key)
        }
        instance.clearSyncFlags()
    }

    private fun syncDucking(effect: ServerDuckingSoundEffect, forceStart: Boolean) {
        effect.tick()
        val viewers = duckingViewerSet(effect.key)
        pruneOfflineViewers(viewers)
        val needsUpdate = effect.needsUpdatePacket()
        val startPacket = if (forceStart) effect.toStartPacket() else null
        val updatePacket = if (needsUpdate) effect.toUpdatePacket() else null
        val stopPacket = if (effect.isStopped) effect.toStopPacket() else null

        CooParticlesAPI.server.playerList.players.forEach { player ->
            val wasVisible = player.uuid in viewers
            val shouldBeVisible = !effect.isStopped && effect.shouldSyncTo(player)
            when {
                shouldBeVisible && !wasVisible -> {
                    CooParticlesServices.SERVER_NETWORK.send(startPacket ?: effect.toStartPacket(), player)
                    viewers.add(player.uuid)
                }

                shouldBeVisible && needsUpdate -> {
                    CooParticlesServices.SERVER_NETWORK.send(updatePacket ?: effect.toUpdatePacket(), player)
                }

                !shouldBeVisible && wasVisible -> {
                    CooParticlesServices.SERVER_NETWORK.send(stopPacket ?: effect.toStopPacket(), player)
                    viewers.remove(player.uuid)
                }
            }
        }

        if (effect.isStopped) {
            duckingEffects.remove(effect.key, effect)
            duckingViewers.remove(effect.key)
        }
        effect.clearSyncFlags()
    }

    private fun sendToEntityWatchers(entity: Entity, packet: PacketSoundInstanceS2C, self: Boolean): Boolean {
        val level = entity.level() as? ServerLevel ?: return false
        CooParticlesAPI.server.playerList.players.forEach { player ->
            if (player.level() != level) {
                return@forEach
            }
            if (!self && entity is ServerPlayer && player.uuid == entity.uuid) {
                return@forEach
            }
            if (player.position().distanceTo(entity.position()) <= 16.0) {
                CooParticlesServices.SERVER_NETWORK.send(packet, player)
            }
        }
        return true
    }

    private fun sendToPlayersNear(world: ServerLevel, pos: Vec3, range: Double, packet: PacketSoundInstanceS2C) {
        CooParticlesAPI.server.playerList.players.forEach { player ->
            if (player.level() != world) {
                return@forEach
            }
            if (range >= 0.0 && player.position().distanceTo(pos) > range) {
                return@forEach
            }
            CooParticlesServices.SERVER_NETWORK.send(packet, player)
        }
    }

    private fun soundViewerSet(key: String): MutableSet<UUID> {
        return soundViewers.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }
    }

    private fun duckingViewerSet(key: String): MutableSet<UUID> {
        return duckingViewers.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }
    }

    private fun pruneOfflineViewers(viewers: MutableSet<UUID>) {
        val online = CooParticlesAPI.server.playerList.players.mapTo(HashSet()) { it.uuid }
        viewers.removeIf { it !in online }
    }

    private fun <T> MutableSet<T>.replaceWith(values: Set<T>) {
        clear()
        addAll(values)
    }
}
