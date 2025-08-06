package cn.coostack.cooparticlesapi.renderer.shader.data

import org.joml.Vector2f
import org.joml.Vector3f

data class VertexData(var pos: Vector3f, var color: Vector3f, var uv: Vector2f, var format: CooVertexFormat) {
    constructor(pos: Vector3f) : this(pos, Vector3f(), Vector2f(), CooVertexFormat.POINT_FORMAT)
    constructor(pos: Vector3f, uv: Vector2f) : this(pos, Vector3f(), uv, CooVertexFormat.POINT_TEXTURE_UV_FORMAT)
    constructor(pos: Vector3f, color: Vector3f) : this(pos, color, Vector2f(), CooVertexFormat.POINT_COLOR_FORMAT)
    constructor(pos: Vector3f, color: Vector3f, uv: Vector2f) : this(
        pos,
        color,
        uv,
        CooVertexFormat.POINT_COLOR_TEXTURE_UV_FORMAT
    )

}