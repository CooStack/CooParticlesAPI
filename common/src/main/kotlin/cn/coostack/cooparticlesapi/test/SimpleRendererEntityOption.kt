package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode

class SimpleRendererEntityOption(
    val testEntity: RenderEntity,
    var testingTick: Int = 100,
    val displayName: String = "entity: ${testEntity::class.java.simpleName}"
) : TestOption {
    override fun start() {
        ServerRenderEntityManager.spawn(testEntity)
    }

    override fun stop() {
        testEntity.remove()
    }

    override fun isValid(): Boolean {
        return !testEntity.canceled && (testingTick > 0 || testingTick == -1)
    }

    override fun onFailed() {
    }

    override fun onSuccess() {
    }

    override fun optionID(): String {
        return displayName
    }

    override fun doTick() {
        if (testingTick != -1) testingTick--
    }

    override fun reviewMode(): TestReviewMode {
        return TestReviewMode.MANUAL_VISUAL
    }

    override fun reviewDescription(): String {
        return "请人工确认视觉效果、遮挡关系、屏幕后处理与动画是否符合预期"
    }
}
