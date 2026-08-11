package cn.coostack.cooparticlesapi.renderer.model

object RenderEntityModelExecutors {
    private var active: RenderEntityModelExecutor = RenderEntityModelExecutor { _, _ -> }

    /**
     * 把输入对象加入 `RenderEntityModelExecutors` 的 `install` 管理范围，后续查询、构建或绘制会使用该绑定。
     *
     * 示例：`install(executor = executor)`。
     *
     * @param executor 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     */
    fun install(executor: RenderEntityModelExecutor) {
        active = executor
    }

    /**
     * 从 `RenderEntityModelExecutors` 当前维护的状态中读取 `active` 结果，不创建新的渲染资源。
     *
     * 示例：`active()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    fun active(): RenderEntityModelExecutor {
        return active
    }

    /**
     * 清理 `RenderEntityModelExecutors` 的 `reset` 状态，使缓存、绑定或 OpenGL 状态可以重新初始化。
     *
     * 示例：`reset()`。
     */
    fun reset() {
        active = RenderEntityModelExecutor { _, _ -> }
    }
}
