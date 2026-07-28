package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode

/**
 * 测试一个服务端同步的渲染实体。
 *
 * @param T 渲染实体的具体类型
 * @property testEntity 本次测试使用的渲染实体
 * @property testingTick 最长运行时间，`-1` 表示不限时
 * @property displayName 测试项 ID；不传时沿用原来的简单类名格式
 */
class SimpleRendererEntityOption<T : RenderEntity>(
    val testEntity: T,
    var testingTick: Int = 100,
    val displayName: String = "entity: ${testEntity::class.java.simpleName}"
) : TestOption<T> {
    override fun paramTarget(): T {
        return testEntity
    }

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
