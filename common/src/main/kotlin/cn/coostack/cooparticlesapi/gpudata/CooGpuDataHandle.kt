package cn.coostack.cooparticlesapi.gpudata

/**
 * # GPU 数据图层的稳定逻辑引用
 *
 * 句柄是调用方唯一应该长期保存的东西。它由 [id] 和一个单调递增的 [generation] 组成：
 * 资源被释放后 id 可能被复用，但 generation 一定不同，因此旧句柄不会静默指向新资源。
 *
 * 句柄**不包含**任何 OpenGL 句柄。GL buffer、SSBO 绑定点和上传缓冲都属于客户端渲染线程，
 * 服务端声明、网络同步和资源包重载都不能携带它们。
 *
 * 使用端必须在解析时通过 [CooGpuDataRegistry.layerOf] 校验 generation，不要自行用 id 反查。
 *
 * @property id 资源分配时取得的紧凑逻辑 ID，总是正数
 * @property generation 资源重用计数；同一个 id 每被重新分配一次就加一
 */
data class CooGpuDataHandle(
    val id: Int,
    val generation: Int,
) {
    init {
        require(id > 0) { "Gpu data handle id must be positive: $id" }
        require(generation >= 0) { "Gpu data handle generation must be non-negative: $generation" }
    }

    /** 供日志与诊断使用的稳定文本形式。 */
    override fun toString(): String = "#$id:$generation"
}
