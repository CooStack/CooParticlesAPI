package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelLayer
import org.joml.Vector4f
import java.io.InputStream

object ObjModelUtil {
    @JvmStatic
    fun parse(
        source: String,
        layer: RenderEntityModelLayer,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false
    ): RenderEntityModel {
        return ObjModelLoader.parse(source, color, flipV).buildModel(layer)
    }

    @JvmStatic
    fun parse(
        input: InputStream,
        layer: RenderEntityModelLayer,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false
    ): RenderEntityModel {
        return ObjModelLoader.parse(input, color, flipV).buildModel(layer)
    }

    @JvmStatic
    fun parseBuilder(
        source: String,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false
    ): RenderVertexBuilder {
        return ObjModelLoader.parse(source, color, flipV)
    }

    @JvmStatic
    fun parseBuilder(
        input: InputStream,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false
    ): RenderVertexBuilder {
        return ObjModelLoader.parse(input, color, flipV)
    }
}
