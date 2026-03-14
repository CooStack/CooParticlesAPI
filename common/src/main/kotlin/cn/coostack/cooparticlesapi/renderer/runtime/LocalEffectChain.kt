package cn.coostack.cooparticlesapi.renderer.runtime

import java.util.concurrent.atomic.AtomicInteger

class LocalEffectChain(
    private val renderTargetPool: LocalRenderTargetPool
) {
    val chainId: String = "local-effect-chain-${CHAIN_COUNTER.incrementAndGet()}"
    val primaryTarget: LocalRenderTargetHandle = renderTargetPool.allocate("primary")

    private val localSteps = mutableListOf<LocalEffectStep>()

    fun addStep(step: LocalEffectStep) {
        localSteps += step
    }

    fun replaceSteps(steps: List<LocalEffectStep>) {
        localSteps.clear()
        localSteps += steps
    }

    fun steps(): List<LocalEffectStep> {
        return localSteps.toList()
    }

    fun execute() {
        localSteps.toList().forEach(LocalEffectStep::execute)
    }

    companion object {
        private val CHAIN_COUNTER = AtomicInteger()
    }
}
