package cn.coostack.cooparticlesapi.renderer.shader.buffer

object ShaderBufferRegistry {
    private val layouts = LinkedHashMap<String, ShaderBufferLayout<*>>()
    private val nextBinding = mutableMapOf(
        ShaderBufferBinding.UNIFORM_BUFFER to 0,
        ShaderBufferBinding.SHADER_STORAGE_BUFFER to 0
    )

    @Suppress("UNCHECKED_CAST")
    /**
     * 把输入对象加入 `ShaderBufferRegistry` 的 `register` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`register(layout = layout)`。
     *
     * @param layout 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun <T> register(layout: ShaderBufferLayout<T>): ShaderBufferLayout<T> {
        val existing = layouts[layout.name]
        if (existing != null) {
            return existing as ShaderBufferLayout<T>
        }
        val binding = nextBinding.getValue(layout.requestedBinding)
        nextBinding[layout.requestedBinding] = binding + 1
        val assigned = layout.assignBinding(binding)
        layouts[assigned.name] = assigned
        return assigned
    }

    /**
     * 从 `ShaderBufferRegistry` 当前维护的状态中读取 `get` 结果，不创建新的渲染资源。
     *
     * 示例：`get(name = name)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun get(name: String): ShaderBufferLayout<*>? = layouts[name]

    /**
     * 执行 `ShaderBufferRegistry` 定义的 `all` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`all()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun all(): Collection<ShaderBufferLayout<*>> = layouts.values

    /**
     * 从 `ShaderBufferRegistry` 当前维护的状态中读取 `bindingCount` 结果，不创建新的渲染资源。
     *
     * 示例：`bindingCount(binding = binding)`。
     *
     * @param binding 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前集合、资源或实例的数量
     */
    fun bindingCount(binding: ShaderBufferBinding): Int = nextBinding.getValue(binding)

    /**
     * 清理 `ShaderBufferRegistry` 的 `clear` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`clear()`。
     */
    fun clear() {
        layouts.clear()
        nextBinding[ShaderBufferBinding.UNIFORM_BUFFER] = 0
        nextBinding[ShaderBufferBinding.SHADER_STORAGE_BUFFER] = 0
    }
}
