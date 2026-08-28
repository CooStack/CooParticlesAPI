package cn.coostack.cooparticlesapi.compat

/**
 * CooFX 交给 Iris entity G-buffer 的基础材质程序选择。
 *
 * 该枚举仅用于客户端当前帧的 shader 选择，不参与资源序列化。默认值是
 * [TRANSLUCENT]，以保持既有 RenderEntity 调用的兼容行为；CooFX glTF 材质会根据
 * OPAQUE/MASK 选择 [SOLID] 或 [CUTOUT]。其中 [CUTOUT] 遵循 Iris entity program
 * 固定的 alpha 阈值，不能表达任意 glTF `alphaCutoff`。
 */
internal enum class IrisEntityShaderKind {
    /** 使用 Iris 的实体半透明程序，兼容旧的 RenderEntity 默认路径。 */
    TRANSLUCENT,

    /** 使用 Iris 的实体不透明程序，不执行 cutout alpha 丢弃。 */
    SOLID,

    /** 使用 Iris 的无剔除 cutout 程序，阈值由 Iris 固定为 0.1。 */
    CUTOUT,
}
