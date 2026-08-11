package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelLayer
import org.joml.Vector4f
import java.io.InputStream

object ObjModelUtil {
    @JvmStatic
    /**
     * 从指定来源读取并解析 `parse` 数据；输入必须符合 `ObjModelUtil` 使用的资源或网络格式。
     *
     * 示例：`parse(source = source, layer = layer, color = color, flipV = flipV)`。
     *
     * @param source 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param flipV 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun parse(
        source: String,
        layer: RenderEntityModelLayer,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false
    ): RenderEntityModel {
        return ObjModelLoader.parse(source, color, flipV).buildModel(layer)
    }

    @JvmStatic
    /**
     * 从指定来源读取并解析 `parse` 数据；输入必须符合 `ObjModelUtil` 使用的资源或网络格式。
     *
     * 示例：`parse(input = input, layer = layer, color = color, flipV = flipV)`。
     *
     * @param input 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param layer 模型分组或地形渲染层，决定图元筛选及 RenderType 行为
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param flipV 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun parse(
        input: InputStream,
        layer: RenderEntityModelLayer,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false
    ): RenderEntityModel {
        return ObjModelLoader.parse(input, color, flipV).buildModel(layer)
    }

    @JvmStatic
    /**
     * 从指定来源读取并解析 `parseBuilder` 数据；输入必须符合 `ObjModelUtil` 使用的资源或网络格式。
     *
     * 示例：`parseBuilder(source = source, color = color, flipV = flipV)`。
     *
     * @param source 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param flipV 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun parseBuilder(
        source: String,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false
    ): RenderVertexBuilder {
        return ObjModelLoader.parse(source, color, flipV)
    }

    @JvmStatic
    /**
     * 从指定来源读取并解析 `parseBuilder` 数据；输入必须符合 `ObjModelUtil` 使用的资源或网络格式。
     *
     * 示例：`parseBuilder(input = input, color = color, flipV = flipV)`。
     *
     * @param input 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param color 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param flipV 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun parseBuilder(
        input: InputStream,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false
    ): RenderVertexBuilder {
        return ObjModelLoader.parse(input, color, flipV)
    }
}
