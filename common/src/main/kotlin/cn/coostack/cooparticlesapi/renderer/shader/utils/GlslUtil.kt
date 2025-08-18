package cn.coostack.cooparticlesapi.renderer.shader.utils

import cn.coostack.cooparticlesapi.renderer.shader.data.TextureData
import cn.coostack.cooparticlesapi.renderer.shader.glsl.IdentifierShader
import net.minecraft.resources.ResourceLocation
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBImage
import java.nio.ByteBuffer

object GlslUtil {
    fun readGlslCodeFromJar(name: String): String {
        val path = "assets/shaders/$name"
        val stream = this::class.java.classLoader.getResourceAsStream(path) ?: return ""
        return stream.use {
            it.readAllBytes().decodeToString()
        }
    }

    fun readGlslCodeFromJar(id: ResourceLocation): String {
        val path = "assets/${id.namespace}/shaders/${id.path}"
        val stream = this::class.java.classLoader.getResourceAsStream(path) ?: return ""
        return stream.use {
            it.readAllBytes().decodeToString()
        }
    }

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