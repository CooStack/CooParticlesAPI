package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.renderer.pipeline.CooShaderEffect
import cn.coostack.cooparticlesapi.renderer.pipeline.CooShaderEffectPlayBuilder
import cn.coostack.cooparticlesapi.renderer.pipeline.CooShaderEffectPlayback
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/**
 * 在指定玩家上下文中运行一个后处理示例。
 *
 * @property player 绑定后处理的玩家
 * @property displayName 测试项 ID；不传时使用通用后处理 ID
 * @property testingTick 最长运行时间，`-1` 表示不限时
 * @property effect 新 Pipeline API 注册的屏幕效果
 * @property configure 每次播放时写入的动态 uniform
 * @property description 人工检查提示
 */
class PostEffectDemoOption(
    private val player: Player,
    private val displayName: String = "post-effect-demo",
    private val testingTick: Int = 80,
    private val effect: CooShaderEffect,
    private val configure: CooShaderEffectPlayBuilder.(Player) -> Unit = {},
    private val description: String = "Verify the post effect binding, lifecycle, and fallback behavior visually."
) : TestOption<PostEffectDemoOption> {
    private var active: CooShaderEffectPlayback? = null
    private var remainingTicks = testingTick

    override fun paramTarget(): PostEffectDemoOption {
        return this
    }

    override fun start() {
        val serverPlayer = player as? ServerPlayer
        active = if (serverPlayer == null) {
            effect.play {
                duration(testingTick.coerceAtLeast(1))
                configure(player)
            }
        } else {
            effect.play(serverPlayer) {
                duration(testingTick.coerceAtLeast(1))
                configure(player)
            }
        }
    }

    override fun stop() {
        active?.stop()
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
