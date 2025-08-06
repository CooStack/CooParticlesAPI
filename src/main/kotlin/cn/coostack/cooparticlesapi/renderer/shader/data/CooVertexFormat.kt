package cn.coostack.cooparticlesapi.renderer.shader.data

enum class CooVertexFormat {
    /**
     * float 参数为 3
     * layer 0 vec3 pos
     */
    POINT_FORMAT,

    /**
     * float 参数为 6
     * layer 0 vec3 pos
     * layer 1 vec3 color
     */
    POINT_COLOR_FORMAT,

    /**
     * float 参数为 5
     * layer 0 vec3 pos
     * layer 1 vec2 uv
     */
    POINT_TEXTURE_UV_FORMAT,

    /**
     * float 参数为 8
     * layer 0 vec3 pos
     * layer 1 vec3 color
     * layer 2 vec2 uv
     */
    POINT_COLOR_TEXTURE_UV_FORMAT,
}