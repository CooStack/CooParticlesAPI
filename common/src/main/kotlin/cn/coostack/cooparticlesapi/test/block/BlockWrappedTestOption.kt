package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode

class BlockWrappedTestOption(
    private val delegate: TestOption
) : BlockTestOption {
    override fun start() = delegate.start()

    override fun stop() = delegate.stop()

    override fun isValid(): Boolean = delegate.isValid()

    override fun onFailed() = delegate.onFailed()

    override fun onSuccess() = delegate.onSuccess()

    override fun optionID(): String = delegate.optionID()

    override fun doTick() = delegate.doTick()

    override fun reviewMode(): TestReviewMode = delegate.reviewMode()

    override fun reviewDescription(): String? = delegate.reviewDescription()
}
