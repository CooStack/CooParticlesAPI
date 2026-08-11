package cn.coostack.cooparticlesapi.renderer.shader.buffer

object ShaderBufferCache {
    private val buffers = LinkedHashMap<String, ShaderBufferObject<*>>()
    var shaderStorageSupported: Boolean = true

    @Suppress("UNCHECKED_CAST")
    /**
     * 从 `ShaderBufferCache` 当前维护的状态中读取 `getOrCreate` 结果，不创建新的渲染资源。
     *
     * 示例：`getOrCreate(layout = layout)`。
     *
     * @param layout 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun <T> getOrCreate(layout: ShaderBufferLayout<T>): ShaderBufferObject<T> {
        val registered = ShaderBufferRegistry.register(layout)
        return buffers.getOrPut(registered.name) {
            ShaderBufferObject(registered, shaderStorageSupported)
        } as ShaderBufferObject<T>
    }

    /**
     * 把输入对象加入 `ShaderBufferCache` 的 `bind` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`bind(layout = layout)`。
     *
     * @param layout 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun bind(layout: ShaderBufferLayout<*>) {
        getOrCreate(layout).bindBase()
    }

    /**
     * 把输入对象加入 `ShaderBufferCache` 的 `bindAll` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`bindAll(layouts = layouts)`。
     *
     * @param layouts 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
     */
    fun bindAll(layouts: Collection<ShaderBufferLayout<*>>) {
        layouts.forEach(::bind)
    }

    /**
     * 从 `ShaderBufferCache` 的 `unbind` 管理范围移除目标，后续调用不再使用对应绑定或资源。
     *
     * 示例：`unbind(layout = layout)`。
     *
     * @param layout 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun unbind(layout: ShaderBufferLayout<*>) {
        getOrCreate(layout).unbind()
    }

    /**
     * 释放 `ShaderBufferCache` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release(name = name)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun release(name: String): Boolean {
        val buffer = buffers.remove(name) ?: return false
        buffer.release()
        return true
    }

    /**
     * 释放 `ShaderBufferCache` 在 `release` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`release(layout = layout)`。
     *
     * @param layout 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun release(layout: ShaderBufferLayout<*>): Boolean {
        return release(layout.name)
    }

    /**
     * 释放 `ShaderBufferCache` 在 `releaseLayouts` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`releaseLayouts(layouts = layouts)`。
     *
     * @param layouts 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun releaseLayouts(layouts: Collection<ShaderBufferLayout<*>>): Int {
        return layouts
            .asSequence()
            .map { it.name }
            .distinct()
            .count { release(it) }
    }

    /**
     * 释放 `ShaderBufferCache` 在 `releaseAll` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`releaseAll()`。
     */
    fun releaseAll() {
        buffers.values.forEach { it.release() }
        buffers.clear()
    }
}
