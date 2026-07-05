package cn.coostack.cooparticlesapi.test.api

enum class TestReviewMode {
    AUTO,
    MANUAL_VISUAL
}

/**
 * 测试单项
 *
 */
interface TestOption {
    fun start()

    fun stop()

    /**
     * 该测试是否已经完成 （不管报错还是正常完成）
     *
     * @return false 代表该测试项已经无效 则已经完成
     */
    fun isValid(): Boolean

    fun onFailed()

    fun onSuccess()

    fun optionID(): String

    fun doTick()

    fun reviewMode(): TestReviewMode {
        return TestReviewMode.AUTO
    }

    fun reviewDescription(): String? {
        return null
    }

    fun <T : Any> applyParam(type: TestOptionParamType<T>, defaultValue: T): TestOption {
        return TestOptionParamSupport.applyParam(this, type, defaultValue)
    }

    fun applyTo(action: TestOption.(Any) -> Unit): TestOption {
        return TestOptionParamSupport.applyTo(this, action)
    }

    fun applyOptionParams(values: Map<String, String>) {
        TestOptionParamSupport.applyOptionParams(this, values)
    }

    fun optionParamSpecs(): List<TestOptionParamSpec<*>> {
        return TestOptionParamSupport.optionParamSpecs(this)
    }

    fun optionParamValues(): Map<String, String> {
        return TestOptionParamSupport.optionParamValues(this)
    }

    fun <T : Any> getParam(id: String): T? {
        return TestOptionParamSupport.getParam(this, id)
    }

    fun paramTarget(): Any {
        return this
    }
}
