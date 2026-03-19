package cn.coostack.cooparticlesapi.renderer.shader.api.glsl

import net.minecraft.resources.ResourceLocation

/**
 * 单个 OpenGL shader 对象抽象。
 */
interface GlShader {
    /**
     * 当前 shader 的阶段类型。
     */
    val type: GlShaderType

    /**
     * 返回底层 shader id。
     */
    fun shaderID(): Int

    /**
     * 编译当前 shader。
     */
    fun compile()

    /**
     * 断言当前 shader 已成功编译。
     */
    fun assertCompiled()

    /**
     * 删除底层 shader 对象。
     */
    fun deleteShader()

    /**
     * 返回该 shader 对应的资源路径。
     *
     * 返回 `null` 表示它不是从资源系统直接托管加载的。
     */
    fun sourceLocation(): ResourceLocation? = null
}
