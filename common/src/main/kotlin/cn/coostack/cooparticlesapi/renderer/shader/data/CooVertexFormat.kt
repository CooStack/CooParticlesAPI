package cn.coostack.cooparticlesapi.renderer.shader.data

/**
 * 顶点缓冲中每个顶点的布局格式枚举。
 */
enum class CooVertexFormat {
    /**
     * 仅包含位置的顶点格式。
     *
     * - 总 float 数：3
     * - 第 0 层：`vec3 position`
     */
    POINT_FORMAT,

    /**
     * 包含位置和颜色的顶点格式。
     *
     * - 总 float 数：6
     * - 第 0 层：`vec3 position`
     * - 第 1 层：`vec3 color`
     */
    POINT_COLOR_FORMAT,

    /**
     * 包含位置和 UV 的顶点格式。
     *
     * - 总 float 数：5
     * - 第 0 层：`vec3 position`
     * - 第 1 层：`vec2 uv`
     */
    POINT_TEXTURE_UV_FORMAT,

    /**
     * 包含位置、颜色和 UV 的顶点格式。
     *
     * - 总 float 数：8
     * - 第 0 层：`vec3 position`
     * - 第 1 层：`vec3 color`
     * - 第 2 层：`vec2 uv`
     */
    POINT_COLOR_TEXTURE_UV_FORMAT,
}
