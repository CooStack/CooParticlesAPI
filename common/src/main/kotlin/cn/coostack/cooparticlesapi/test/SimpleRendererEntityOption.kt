package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import cn.coostack.cooparticlesapi.test.api.TestOption

class SimpleRendererEntityOption(
    val testEntity: RenderEntity,
    var testingTick: Int = 100
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
        return "entity: ${testEntity::class.java}"
    }

    override fun doTick() {
        if (testingTick != -1) testingTick--
    }
}