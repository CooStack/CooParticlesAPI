package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.supports.sound.ServerDuckingSoundEffect
import cn.coostack.cooparticlesapi.supports.sound.ServerManagedSoundInstance
import cn.coostack.cooparticlesapi.supports.sound.ServerSoundManager
import cn.coostack.cooparticlesapi.test.api.TestOption
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player

/**
 * 根据模式选择一个服务端声音测试。
 *
 * @property player 运行声音测试的玩家
 * @param mode 要执行的声音测试模式
 * @property id 测试项 ID；不传时使用所选模式原来的 ID
 */
class ServerSoundTestOption @JvmOverloads constructor(
    private val player: Player,
    mode: Mode,
    private val id: String = defaultTestId(mode)
) : TestOption<ServerSoundTestOption> {
    enum class Mode {
        START_LOOP,
        START_DUCK_LOOP,
        VOLUME_FADE
    }

    private val delegate: TestOption<*> = when (mode) {
        Mode.START_LOOP -> ServerSoundStartLoopTestOption(player, id)
        Mode.START_DUCK_LOOP -> ServerSoundStartDuckLoopTestOption(player, id)
        Mode.VOLUME_FADE -> ServerSoundFadeTestOption(player, id)
    }

    override fun paramTarget(): ServerSoundTestOption {
        return this
    }

    override fun start() {
        delegate.start()
    }

    override fun stop() {
        delegate.stop()
    }

    override fun isValid(): Boolean {
        return delegate.isValid()
    }

    override fun onFailed() {
        delegate.onFailed()
    }

    override fun onSuccess() {
        delegate.onSuccess()
    }

    override fun optionID(): String {
        return id
    }

    override fun doTick() {
        delegate.doTick()
    }
}

/**
 * 播放开始音和循环音的服务端声音测试。
 *
 * @param player 运行测试的玩家
 * @param id 测试项 ID；不传时保持原来的 ID
 */
class ServerSoundStartLoopTestOption @JvmOverloads constructor(
    player: Player,
    id: String = "server-sound-start-loop"
) : BaseServerSoundTestOption<ServerSoundStartLoopTestOption>(
    player = player,
    testId = id,
    finishTick = LOOP_TICKS + CLEANUP_TICKS
) {
    override fun paramTarget(): ServerSoundStartLoopTestOption = this

    override fun startServer(player: ServerPlayer) {
        playStart(player, layer("start"))
        loop = createLoop(player, layer("loop"), volume = 0.8f)
    }

    override fun tickServer(player: ServerPlayer, tick: Int) {
        when (tick) {
            LOOP_TICKS - OBLITERATION_BEFORE_END_TICKS -> playObliteration(player, layer("obliteration"))
            LOOP_TICKS -> stopLoopAfterCurrent()
        }
    }
}

/**
 * 播放带压低效果循环音的服务端声音测试。
 *
 * @param player 运行测试的玩家
 * @param id 测试项 ID；不传时保持原来的 ID
 */
class ServerSoundStartDuckLoopTestOption @JvmOverloads constructor(
    player: Player,
    id: String = "server-sound-start-duck-loop"
) : BaseServerSoundTestOption<ServerSoundStartDuckLoopTestOption>(
    player = player,
    testId = id,
    finishTick = LOOP_TICKS + CLEANUP_TICKS
) {
    override fun paramTarget(): ServerSoundStartDuckLoopTestOption = this

    override fun startServer(player: ServerPlayer) {
        playStart(player, layer("start"))
        ducking = createDucking(player, layer("duck"))
        loop = createLoop(player, layer("loop"), volume = 0.8f)
    }

    override fun tickServer(player: ServerPlayer, tick: Int) {
        when (tick) {
            LOOP_TICKS - OBLITERATION_BEFORE_END_TICKS -> playObliteration(player, layer("obliteration"))
            LOOP_TICKS -> {
                stopLoopAfterCurrent()
                stopDuckingNow()
            }
        }
    }
}

/**
 * 测试循环音淡入和淡出。
 *
 * @param player 运行测试的玩家
 * @param id 测试项 ID；不传时保持原来的 ID
 */
class ServerSoundFadeTestOption @JvmOverloads constructor(
    player: Player,
    id: String = "server-sound-fade"
) : BaseServerSoundTestOption<ServerSoundFadeTestOption>(
    player = player,
    testId = id,
    finishTick = FADE_TEST_TICKS + CLEANUP_TICKS
) {
    override fun paramTarget(): ServerSoundFadeTestOption = this

    override fun startServer(player: ServerPlayer) {
        playStart(player, layer("start"))
        loop = createLoop(player, layer("loop"), volume = 0f)
            .apply {
                fadeIn(FADE_TICKS, targetVolume = 0.8f, fromVolume = 0f)
            }
    }

    override fun tickServer(player: ServerPlayer, tick: Int) {
        when (tick) {
            FADE_TEST_TICKS - FADE_TICKS -> {
                loop?.fadeOut(FADE_TICKS, stopWhenFinished = true, interruptWhenStopped = false)
            }
        }
    }
}

/**
 * 管理服务端声音测试的生命周期和资源清理。
 *
 * @param T 具体声音测试项类型
 * @property player 运行测试的玩家
 * @property testId 测试项 ID
 * @property finishTick 测试完成时刻
 */
abstract class BaseServerSoundTestOption<T : BaseServerSoundTestOption<T>>(
    private val player: Player,
    private val testId: String,
    private val finishTick: Int
) : TestOption<T> {
    protected var loop: ServerManagedSoundInstance? = null
    protected var ducking: ServerDuckingSoundEffect? = null

    private val sounds = LinkedHashSet<ServerManagedSoundInstance>()
    private val duckings = LinkedHashSet<ServerDuckingSoundEffect>()
    private var serverPlayer: ServerPlayer? = null
    private var tick = 0
    private var valid = true

    override fun start() {
        if (player !is ServerPlayer || player.level() !is ServerLevel) {
            valid = false
            player.sendSystemMessage(Component.literal("$testId must run from a server player"))
            return
        }
        serverPlayer = player
        tick = 0
        valid = true
        startServer(player)
    }

    override fun stop() {
        sounds.forEach { ServerSoundManager.stop(it, interrupt = true) }
        duckings.forEach { ServerSoundManager.stopDucking(it) }
        loop = null
        ducking = null
        sounds.clear()
        duckings.clear()
        valid = false
    }

    override fun isValid(): Boolean {
        return valid && tick < finishTick
    }

    override fun onFailed() {
        stop()
    }

    override fun onSuccess() {
    }

    override fun optionID(): String {
        return testId
    }

    override fun doTick() {
        val player = serverPlayer ?: return
        tickServer(player, tick)
        tick++
        if (tick >= finishTick) {
            valid = false
        }
    }

    protected abstract fun startServer(player: ServerPlayer)

    protected abstract fun tickServer(player: ServerPlayer, tick: Int)

    protected fun layer(name: String): String {
        return name
    }

    protected fun playStart(player: ServerPlayer, layer: String): ServerManagedSoundInstance {
        return playOnce(player, layer, START_SOUND)
    }

    protected fun playObliteration(player: ServerPlayer, layer: String): ServerManagedSoundInstance {
        return playOnce(player, layer, OBLITERATION_SOUND)
    }

    protected fun createLoop(player: ServerPlayer, layer: String, volume: Float): ServerManagedSoundInstance {
        return ServerSoundManager.instance(LOOP_SOUND, SoundSource.PLAYERS)
            .volume(volume)
            .pitch(1f)
            .entity(player)
            .name(testId)
            .layer(layer)
            .looping()
            .spawn()
            .track()
    }

    protected fun createDucking(
        player: ServerPlayer,
        layer: String
    ): ServerDuckingSoundEffect {
        val effect = ServerSoundManager.spawnDucking(
            ServerDuckingSoundEffect(
                key = "${player.uuid}:$testId:$layer",
                world = player.level() as ServerLevel,
                initialPos = player.position(),
                volumeMultiplier = 0.25f,
                range = 32.0,
                whitelistSounds = setOf(START_SOUND, LOOP_SOUND, OBLITERATION_SOUND)
            ).apply {
                targetPlayer = player
                bindToEntity(player)
                visibleRange = 32.0
            }
        )
        return effect.track()
    }

    protected fun stopLoopAfterCurrent() {
        loop?.stopAfterCurrentLoop()
        loop = null
    }

    protected fun stopDuckingNow() {
        ducking?.stopNow()
        ducking = null
    }

    private fun playOnce(player: ServerPlayer, layer: String, sound: ResourceLocation): ServerManagedSoundInstance {
        return ServerSoundManager.instance(sound, SoundSource.PLAYERS)
            .volume(1f)
            .pitch(1f)
            .entity(player)
            .name(testId)
            .layer(layer)
            .spawn()
            .track()
    }

    private fun ServerManagedSoundInstance.track(): ServerManagedSoundInstance {
        sounds.add(this)
        return this
    }

    private fun ServerDuckingSoundEffect.track(): ServerDuckingSoundEffect {
        duckings.add(this)
        return this
    }
}

private const val LOOP_TICKS = 100
private const val OBLITERATION_BEFORE_END_TICKS = 20
private const val FADE_TICKS = 20
private const val FADE_TEST_TICKS = 120
private const val CLEANUP_TICKS = 20

private fun defaultTestId(mode: ServerSoundTestOption.Mode): String {
    return when (mode) {
        ServerSoundTestOption.Mode.START_LOOP -> "server-sound-start-loop"
        ServerSoundTestOption.Mode.START_DUCK_LOOP -> "server-sound-start-duck-loop"
        ServerSoundTestOption.Mode.VOLUME_FADE -> "server-sound-fade"
    }
}

private val START_SOUND = sound("test.laser_start")
private val LOOP_SOUND = sound("test.laser_loop")
private val OBLITERATION_SOUND = sound("test.laser_obliteration")

private fun sound(path: String): ResourceLocation {
    return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
}
