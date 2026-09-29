package cn.coostack.cooparticlesapi.gpudata

/**
 * # 共享图层的着色器存储绑定点表
 *
 * 绑定点是有限的 GL 资源：OpenGL 4.3 只保证 `GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS >= 8`。
 * 因此共享图层**不按资源数量动态申请绑定点**，而是使用下表登记的固定绑定点。
 * 新增共享图层必须在下面登记，而不是就地写死数字或无限追加绑定。
 *
 * | 绑定点 | 用途 | 生命周期 |
 * | --- | --- | --- |
 * | 0 | CParticle 粒子实例缓冲 | 仅在一次 dispatch 内绑定 |
 * | 1 | 方块占用碰撞网格 | 仅在一次 dispatch 内绑定 |
 * | 2 | 粒子 metadata（selector 用） | 仅在一次 dispatch 内绑定 |
 * | 3 | Force Command 打包 | 仅在一次 dispatch 内绑定 |
 * | 4 | 路径图层（全部路径共用一张） | 在模拟与渲染之间保持绑定 |
 * | 5 | 路径提前结束通道（每个 system 各自使用） | 仅在一次 dispatch 内绑定 |
 */
object CooGpuDataBindingPoints {
    /** `cparticle_sim.comp` 的粒子实例 SSBO。 */
    const val PARTICLE_INSTANCE = 0

    /** `cparticle_sim.comp` 的方块占用位图 SSBO。 */
    const val COLLISION_GRID = 1

    /** Force Command selector 使用的 metadata SSBO。 */
    const val PARTICLE_METADATA = 2

    /** Force Command 打包 SSBO。 */
    const val COMMAND_PACKED = 3

    /**
     * 路径图层绑定点。
     *
     * 全部路径打包在同一张图层里，因此只需要一个绑定点。该绑定点在 CParticle 模拟刷新后
     * 保持绑定，让 RenderEntity 的 shader 也能在同一个渲染帧里读取同一份资源。
     */
    const val PATH_LAYER = 4

    /**
     * 路径提前结束通道绑定点。
     *
     * 每个 system 有自己的结束缓冲，但都使用本绑定点：绑定与恢复发生在同一次 dispatch 的
     * `try/finally` 内，不需要常驻占用。
     */
    const val PATH_END_CHANNEL = 5

    /** 当前已登记的固定绑定点数量。 */
    const val REGISTERED_COUNT = 6

    /**
     * 校验一个绑定点是否在 OpenGL 4.3 保证的范围内。
     *
     * @param binding 待校验的绑定点
     * @throws IllegalArgumentException 超出保证范围时抛出
     */
    @JvmStatic
    fun requireSupported(binding: Int) {
        require(binding in 0 until SUPPORTED_BINDING_LIMIT) {
            "Shader storage binding $binding exceeds the OpenGL 4.3 guaranteed limit " +
                "of $SUPPORTED_BINDING_LIMIT bindings"
        }
    }

    /** OpenGL 4.3 保证的 `GL_MAX_SHADER_STORAGE_BUFFER_BINDINGS` 下界。 */
    const val SUPPORTED_BINDING_LIMIT = 8
}
