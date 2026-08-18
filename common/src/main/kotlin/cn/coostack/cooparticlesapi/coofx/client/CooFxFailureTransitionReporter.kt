package cn.coostack.cooparticlesapi.coofx.client

/**
 * 将连续的同类失败压缩为一次失败诊断，并在恢复时记录一次恢复诊断。
 *
 * 重试调用方仍应继续执行实际操作；本类型只控制诊断频率，不缓存失败结果。
 */
internal class CooFxFailureTransitionReporter(
    private val emitFailure: (Throwable) -> Unit,
    private val emitRecovery: () -> Unit,
) {
    private var failed = false

    fun onFailure(failure: Throwable) {
        if (failed) return
        failed = true
        emitFailure(failure)
    }

    fun onSuccess() {
        if (!failed) return
        failed = false
        emitRecovery()
    }
}
