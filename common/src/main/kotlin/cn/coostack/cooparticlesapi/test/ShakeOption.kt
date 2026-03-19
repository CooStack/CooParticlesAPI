package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import cn.coostack.cooparticlesapi.utils.ServerCameraUtil
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

class ShakeOption(val maxTick: Int = 20, val player: Player) : TestOption {
    var tick = 0
    override fun start() {
        tick = 0
    }

    override fun stop() {
    }

    override fun isValid(): Boolean {
        return tick <= maxTick
    }

    override fun onFailed() {
    }

    override fun onSuccess() {
    }

    override fun optionID(): String {
        return "shake-option"
    }

    override fun doTick() {
        tick++
        if (player is ServerPlayer) {
            ServerCameraUtil.sendShake(player.serverLevel(),
                player.eyePosition, 256.0, 3.0, 10, 300.0, false)
        }
    }

    override fun reviewMode(): TestReviewMode {
        return TestReviewMode.MANUAL_VISUAL
    }

    override fun reviewDescription(): String {
        return "请人工确认镜头抖动幅度、持续时间和体感是否符合预期"
    }
}
