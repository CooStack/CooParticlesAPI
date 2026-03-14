package cn.coostack.cooparticlesapi.renderer.runtime

import java.util.concurrent.atomic.AtomicInteger

data class LocalRenderTargetHandle(
    val id: String
)

class LocalRenderTargetPool {
    private val poolId = POOL_COUNTER.incrementAndGet()
    private var nextHandleId = 0

    fun allocate(label: String = "target"): LocalRenderTargetHandle {
        val handleId = nextHandleId++
        return LocalRenderTargetHandle("local-$poolId-$label-$handleId")
    }

    companion object {
        private val POOL_COUNTER = AtomicInteger()
    }
}
