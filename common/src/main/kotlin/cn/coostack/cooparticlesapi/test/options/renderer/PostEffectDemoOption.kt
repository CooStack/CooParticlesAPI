package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.renderer.post.CooPostEffects
import cn.coostack.cooparticlesapi.renderer.post.PostEffectInstance
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

class PostEffectDemoOption(
    private val player: Player,
    private val displayName: String,
    private val testingTick: Int = 80,
    private val instanceFactory: (Player) -> PostEffectInstance,
    private val useTrackingChunkSpawn: Boolean = false,
    private val description: String = "Verify the post effect binding, lifecycle, and fallback behavior visually."
) : TestOption {
    private var active: PostEffectInstance? = null
    private var remainingTicks = testingTick

    override fun start() {
        val instance = instanceFactory(player)
            .duration(testingTick.coerceAtLeast(1))
        active = instance
        val serverPlayer = player as? ServerPlayer
        if (serverPlayer == null) {
            CooPostEffects.client.add(instance)
            return
        }
        if (useTrackingChunkSpawn) {
            CooPostEffects.server.spawn(serverPlayer.serverLevel(), instance)
        } else {
            CooPostEffects.server.send(serverPlayer, instance)
        }
    }

    override fun stop() {
        val instanceId = active?.instanceId ?: return
        val serverPlayer = player as? ServerPlayer
        if (serverPlayer == null) {
            CooPostEffects.client.remove(instanceId)
        } else {
            CooPostEffects.server.remove(serverPlayer, instanceId)
        }
        active = null
    }

    override fun isValid(): Boolean {
        return remainingTicks > 0 || remainingTicks == -1
    }

    override fun onFailed() = Unit

    override fun onSuccess() = Unit

    override fun optionID(): String = displayName

    override fun doTick() {
        if (remainingTicks != -1) {
            remainingTicks--
        }
    }

    override fun reviewMode(): TestReviewMode = TestReviewMode.MANUAL_VISUAL

    override fun reviewDescription(): String = description
}
