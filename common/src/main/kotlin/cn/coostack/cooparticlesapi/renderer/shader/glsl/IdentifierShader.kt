package cn.coostack.cooparticlesapi.renderer.shader.glsl

import cn.coostack.cooparticlesapi.renderer.shader.CooShaderSourceLoader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.utils.GlslUtil
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL33.*

class IdentifierShader(val id: ResourceLocation, override val type: GlShaderType) : GlShader {
    private var shaderID = 0
    /**
     * 执行 `IdentifierShader` 定义的 `shaderID` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`shaderID()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun shaderID(): Int {
        return shaderID
    }

    /**
     * 初始化或准备 `IdentifierShader` 的 `compile` 阶段，使后续渲染调用可以使用相关资源。
     *
     * 示例：`compile()`。
     */
    override fun compile() {
        shaderID = glCreateShader(type.gl)
        glShaderSource(shaderID, readFromJar())
        glCompileShader(shaderID)
        assertCompiled()
    }

    /**
     * 执行 `IdentifierShader` 定义的 `assertCompiled` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`assertCompiled()`。
     */
    override fun assertCompiled() {
        require(glGetShaderi(shaderID, GL_COMPILE_STATUS) != GL_FALSE) {
            "compile code failed info:${glGetShaderInfoLog(shaderID)} shader: $id"
        }
    }

    /**
     * 释放 `IdentifierShader` 在 `deleteShader` 中管理的资源；再次使用前必须重新初始化。
     *
     * 示例：`deleteShader()`。
     */
    override fun deleteShader() {
        glDeleteShader(shaderID)
    }

    /**
     * 执行 `IdentifierShader` 定义的 `sourceLocation` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`sourceLocation()`。
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    override fun sourceLocation(): ResourceLocation = id

    private fun readFromJar(): String = readWithCooImports()

    /**
     * 通过 Coo 源码加载器读取本 shader，并展开 `#coo_import` 指令。
     *
     * Coo 框架要求所有自有 shader 的 `#` 预处理指令都由本框架的解析器处理，不能用原版加载规则。
     * 资源管理器可用时读取资源包内容，否则回退到 classpath；**两条路径都会展开 include**，
     * 只做 jar 直读会让驱动收到未知的 `#coo_import` 指令并直接编译失败。
     *
     * @return 展开后的源码
     * @throws IllegalArgumentException 程序自身或任一 include 都不存在时抛出
     */
    private fun readWithCooImports(): String {
        val resources = runCatching { Minecraft.getInstance().resourceManager }.getOrNull()
        if (resources != null) {
            // 资源管理器用的是真实资源路径，必须带上 shaders/ 前缀。
            val located = ResourceLocation.fromNamespaceAndPath(id.namespace, "shaders/${id.path}")
            if (resources.getResource(located).isPresent) {
                return CooShaderSourceLoader.load(resources, located)
            }
        }
        return CooShaderSourceLoader.loadFromClasspath(id)
    }
}
