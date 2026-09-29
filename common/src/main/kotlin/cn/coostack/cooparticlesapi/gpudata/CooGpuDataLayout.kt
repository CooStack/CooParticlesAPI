package cn.coostack.cooparticlesapi.gpudata

/**
 * # GPU 数据图层的布局描述
 *
 * 图层只负责存储 float 数据，本类型负责说明“这些 float 应该如何被解释”。它与具体使用端解耦：
 * CParticle 的路径命令、RenderEntity 的 shader 都可以读取同一个图层，但各自按自己的语义解释字段。
 *
 * 布局只描述**元素级的固定成分**：每个元素固定占用 [componentCount] 个 float，按 [componentNames]
 * 给出的顺序排列。若使用端需要 header 或额外的辅助表，应在自己的图层协议里声明，而不是塞进本类型。
 *
 * @property id 布局的稳定标识，例如 `cooparticlesapi:path`；同一图层在生命周期内不变
 * @property componentCount 每个元素的 float 成分数；有效长度会始终是它的整数倍
 * @property componentNames 每个成分的语义名，顺序必须与 [componentCount] 一致；仅用于文档与诊断
 */
class CooGpuDataLayout(
    val id: String,
    val componentCount: Int,
    val componentNames: List<String>,
) {
    init {
        require(id.isNotBlank()) { "Gpu data layout id must not be blank" }
        require(componentCount > 0) { "Gpu data layout component count must be positive: $componentCount" }
        require(componentNames.size == componentCount) {
            "Gpu data layout $id declares $componentCount components but names ${componentNames.size}"
        }
        require(componentNames.all { it.isNotBlank() }) {
            "Gpu data layout $id contains a blank component name"
        }
    }

    /** 一个元素占用的字节数；std430 下 float 成分等宽，可直接由成分数推导。 */
    val byteStride: Int
        get() = componentCount * Float.SIZE_BYTES

    /**
     * 计算容纳 [elements] 个元素所需的 float 数量。
     *
     * @param elements 元素个数，必须非负
     * @return 该元素个数对应的 float 总量
     */
    fun floatsFor(elements: Int): Int {
        require(elements >= 0) { "Gpu data layer element count must be non-negative: $elements" }
        return elements * componentCount
    }
}
