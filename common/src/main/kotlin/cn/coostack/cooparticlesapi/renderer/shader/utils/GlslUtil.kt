package cn.coostack.cooparticlesapi.renderer.shader.utils

import cn.coostack.cooparticlesapi.renderer.shader.data.TextureData
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import net.minecraft.resources.ResourceLocation
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBImage
import java.nio.ByteBuffer

object GlslUtil {
    /**
     * 从指定来源读取并解析 `readGlslCodeFromJar` 数据；输入必须符合 `GlslUtil` 使用的资源或网络格式。
     *
     * 示例：`readGlslCodeFromJar(name = name)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun readGlslCodeFromJar(name: String): String {
        val path = "assets/shaders/$name"
        val stream = this::class.java.classLoader.getResourceAsStream(path) ?: return ""
        return stream.use {
            it.readAllBytes().decodeToString()
        }
    }

    /**
     * 从指定来源读取并解析 `readGlslCodeFromJar` 数据；输入必须符合 `GlslUtil` 使用的资源或网络格式。
     *
     * 示例：`readGlslCodeFromJar(id = id)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun readGlslCodeFromJar(id: ResourceLocation): String {
        val path = "assets/${id.namespace}/shaders/${id.path}"
        val stream = this::class.java.classLoader.getResourceAsStream(path) ?: return ""
        return stream.use {
            it.readAllBytes().decodeToString()
        }
    }

    /**
     * 从指定来源读取并解析 `readTextureFromJar` 数据；输入必须符合 `GlslUtil` 使用的资源或网络格式。
     *
     * 示例：`readTextureFromJar(name = name)`。
     *
     * @param name 用于查找、绑定或记录目标的名称
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun readTextureFromJar(name: String): TextureData {
        val path = "textures/$name"
        val bytes = this::class.java.classLoader.getResourceAsStream(path)!!.readAllBytes()
        val read = ByteBuffer.allocateDirect(bytes.size)
        read.put(bytes)
        read.position(0)
        val w = BufferUtils.createIntBuffer(1)
        val h = BufferUtils.createIntBuffer(1)
        val comp = BufferUtils.createIntBuffer(1)
        val image = STBImage.stbi_load_from_memory(read, w, h, comp, 0)
        val data = TextureData(image!!, w.get(), h.get(), comp.get())
        return data
    }

    /**
     * 从指定来源读取并解析 `readTextureFromJar` 数据；输入必须符合 `GlslUtil` 使用的资源或网络格式。
     *
     * 示例：`readTextureFromJar(id = id)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun readTextureFromJar(id: ResourceLocation): TextureData {
        val path = "assets/${id.namespace}/textures/${id.path}"
        val bytes = this::class.java.classLoader.getResourceAsStream(path)!!.readAllBytes()
        val read = ByteBuffer.allocateDirect(bytes.size)
        read.put(bytes)
        read.position(0)
        val w = BufferUtils.createIntBuffer(1)
        val h = BufferUtils.createIntBuffer(1)
        val comp = BufferUtils.createIntBuffer(1)
        val image = STBImage.stbi_load_from_memory(read, w, h, comp, 0)
        val data = TextureData(image!!, w.get(), h.get(), comp.get())
        return data
    }
}