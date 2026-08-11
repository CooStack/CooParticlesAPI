package cn.coostack.cooparticlesapi.renderer.model

/**
 * 模型图元提交到 OpenGL 时使用的绘制模式。
 *
 * 该值决定 GPU 如何把顶点组装为几何图元，因此会直接影响模型的表面形状、线框表现
 * 以及每个 primitive 需要的顶点数量。选择的模式必须与 builder 写入的顶点顺序一致。
 */
enum class RenderEntityModelPrimitiveMode {
    /** 每两个顶点组成一条独立线段，对应 `GL_LINES`，适合线框和轨迹。 */
    LINES,

    /** 每三个顶点组成一个独立三角形，对应 `GL_TRIANGLES`，适合通用实体表面。 */
    TRIANGLES,

    /** 每四个顶点组成一个四边形，对应 `GL_QUADS`，适合按四角描述的平面面片。 */
    QUADS
}
