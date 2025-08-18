package cn.coostack.cooparticlesapi.renderer.shader.data


import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

data class VertexData(var pos: Vector3f, var color: Vector4f, var uv: Vector2f) {
    constructor(pos: Vector3f) : this(pos, Vector4f(), Vector2f())

    constructor(pos: Vector3f, uv: Vector2f) : this(pos, Vector4f(), uv)

    constructor(pos: Vector3f, color: Vector4f) : this(pos, color, Vector2f())


}