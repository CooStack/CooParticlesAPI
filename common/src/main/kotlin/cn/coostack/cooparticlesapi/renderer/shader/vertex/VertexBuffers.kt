package cn.coostack.cooparticlesapi.renderer.shader.vertex

import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.shader.utils.ShaderUtil
import org.joml.Vector3f

object VertexBuffers {


    fun getScreenBuffer(): SimpleVertexBuffer {
        return SimpleVertexBuffer().apply {
            setVertexes(
                ShaderUtil.genSquareUVScreen(
                    Vector3f(-1f, 1f, 0f),
                    Vector3f(1f, 1f, 0f),
                    Vector3f(1f, -1f, 0f),
                    Vector3f(-1f, -1f, 0f),
                ), CooVertexFormat.POINT_TEXTURE_UV_FORMAT
            )
        }
    }

}