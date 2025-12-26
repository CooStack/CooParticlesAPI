package cn.coostack.cooparticlesapi.test.api

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
}