package cn.coostack.cooparticlesapi.compat

/**
 * Iris 阴影渲染阶段的反射探测结果。
 *
 * 该枚举只描述当前客户端是否处于 Iris shadow pass，不参与序列化，也不应跨线程缓存。
 * [ACTIVE] 表示 Iris 明确报告正在绘制阴影，[INACTIVE] 表示明确不在阴影阶段；[UNKNOWN]
 * 表示 Iris 未安装、版本没有对应字段或反射失败，调用方必须按保守路径处理。
 */
internal enum class IrisShadowPassState {
    /** Iris 明确处于阴影 framebuffer 绘制阶段。 */
    ACTIVE,

    /** Iris 明确处于普通世界或实体绘制阶段。 */
    INACTIVE,

    /** 无法从当前 Iris 版本可靠判断阶段。 */
    UNKNOWN,
}
