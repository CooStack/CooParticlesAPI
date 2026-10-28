package cn.coostack.cooparticlesapi.compat

/**
 * Iris 直接几何绘制在绑定 program 之前确定的提交路线。
 *
 * [NATIVE] 使用当前 Iris program，要求其拓扑与普通几何一致；
 * [OWN_SHADER] 仅在 Iris 已绑定正确 framebuffer 后用本库 shader 保留原拓扑；
 * [UNAVAILABLE] 表示能力或回退缺失，不能改变 GL 状态或宣称已提交。
 * 该枚举不保存 GL 资源，也不跨帧缓存光影包能力。
 */
internal enum class IrisProgramDrawRoute {
    NATIVE,
    OWN_SHADER,
    UNAVAILABLE,
}
