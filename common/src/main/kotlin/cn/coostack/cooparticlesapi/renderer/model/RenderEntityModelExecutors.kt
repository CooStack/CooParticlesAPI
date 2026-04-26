package cn.coostack.cooparticlesapi.renderer.model

object RenderEntityModelExecutors {
    private var active: RenderEntityModelExecutor = RenderEntityModelExecutor { _, _ -> }

    fun install(executor: RenderEntityModelExecutor) {
        active = executor
    }

    fun active(): RenderEntityModelExecutor {
        return active
    }

    fun reset() {
        active = RenderEntityModelExecutor { _, _ -> }
    }
}
