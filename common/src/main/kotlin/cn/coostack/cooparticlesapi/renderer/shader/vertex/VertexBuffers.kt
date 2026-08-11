package cn.coostack.cooparticlesapi.renderer.shader.vertex

import cn.coostack.cooparticlesapi.renderer.shader.data.CooVertexFormat
import cn.coostack.cooparticlesapi.renderer.utils.ShaderUtil
import org.joml.Vector3f

object VertexBuffers {


    /**
     * 从 `VertexBuffers` 当前维护的状态中读取 `getScreenBuffer` 结果，不创建新的渲染资源。
     *
     * 示例：`getScreenBuffer()`。
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
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
