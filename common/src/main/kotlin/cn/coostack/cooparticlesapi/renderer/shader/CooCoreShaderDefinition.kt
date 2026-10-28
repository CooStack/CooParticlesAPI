package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceProvider

/**
 * 旧 core JSON 的独立数据描述，不持有 GPU 程序，也不冒充已删除的 ShaderInstance。
 *
 * @property vertex 顶点程序资源，相对 shaders 目录
 * @property fragment 片元程序资源，相对 shaders 目录
 * @property uniforms 每次绘制前恢复的默认 uniform，随后由节点值覆盖
 * @property samplers 按声明顺序分配的默认纹理单元
 * @property textures JSON 显式声明的纹理文件；没有文件的 sampler 由调用方绑定
 * @property blend 原 core 描述中的混合设置，缺省为不混合
 */
internal data class CooCoreShaderDefinition(
    val vertex: Identifier,
    val fragment: Identifier,
    val uniforms: Map<String, CooUniformValue>,
    val samplers: List<String>,
    val textures: Map<String, Identifier>,
    val blend: JsonObject?,
) {
    companion object {
        /**
         * 优先读取命名空间自身的 core JSON，同时兼容旧版桥接目录。
         * @param resources 当前资源包来源
         * @param id core 程序标识
         * @return 已验证的源阶段与默认参数
         * @throws IllegalArgumentException 描述缺失、参数类型无效或值数量不匹配
         */
        fun load(resources: ResourceProvider, id: Identifier): CooCoreShaderDefinition {
            val locations = listOf(
                Identifier.fromNamespaceAndPath(id.namespace, "shaders/core/${id.path}.json"),
                Identifier.withDefaultNamespace("shaders/core/${id.namespace}/${id.path}.json"),
            )
            val resource = locations.firstNotNullOfOrNull { resources.getResource(it).orElse(null) }
                ?: throw IllegalArgumentException("Core shader JSON 不存在：$id")
            return resource.openAsReader().use { parse(JsonParser.parseReader(it).asJsonObject) }
        }

        /** 解析声明而不访问图形上下文，单值默认参数按旧 JSON 规则扩展到声明数量。 */
        fun parse(json: JsonObject): CooCoreShaderDefinition {
            val uniforms = json.getAsJsonArray("uniforms")?.associate { element ->
                val uniform = element.asJsonObject
                val name = uniform.getAsJsonPrimitive("name").asString
                val type = uniform.getAsJsonPrimitive("type").asString
                val count = uniform.getAsJsonPrimitive("count").asInt
                require(count in 1..4096) { "Core uniform 数量无效：$name" }
                val provided = uniform.getAsJsonArray("values").map { it.asDouble }
                require(provided.size == 1 || provided.size == count) { "Core uniform 默认值数量不匹配：$name" }
                require(provided.all(Double::isFinite)) { "Core uniform 默认值必须有限：$name" }
                val values = if (provided.size == 1) List(count) { provided.single() } else provided
                name to when (type) {
                    "int" -> if (count == 1) CooUniformValue.IntValue(values.single().toInt())
                        else if (count <= 4) CooUniformValue.IVecValue(values.map(Double::toInt))
                        else CooUniformValue.ArrayValue(values.map { CooUniformValue.IntValue(it.toInt()) })
                    "float" -> when (count) {
                        1 -> CooUniformValue.FloatValue(values.single().toFloat())
                        2 -> CooUniformValue.Vec2Value(values[0].toFloat(), values[1].toFloat())
                        3 -> CooUniformValue.Vec3Value(values[0].toFloat(), values[1].toFloat(), values[2].toFloat())
                        4 -> CooUniformValue.Vec4Value(values[0].toFloat(), values[1].toFloat(), values[2].toFloat(), values[3].toFloat())
                        else -> CooUniformValue.ArrayValue(values.map { CooUniformValue.FloatValue(it.toFloat()) })
                    }
                    "matrix2x2", "matrix3x3", "matrix4x4" -> {
                        val size = type.removePrefix("matrix").substringBefore('x').toInt()
                        require(count == size * size) { "Core 矩阵尺寸无效：$name" }
                        CooUniformValue.MatValue(size, size, values.map(Double::toFloat))
                    }
                    else -> throw IllegalArgumentException("不支持的 Core uniform 类型：$type")
                }
            }.orEmpty()
            val samplerObjects = json.getAsJsonArray("samplers")?.map { it.asJsonObject }.orEmpty()
            val samplers = samplerObjects.map { it.getAsJsonPrimitive("name").asString }
            require(samplers.distinct().size == samplers.size) { "Core sampler 名称不能重复" }
            val textures = samplerObjects.mapNotNull { sampler ->
                sampler.getAsJsonPrimitive("file")?.asString?.let {
                    val texture = Identifier.parse(it)
                    sampler.getAsJsonPrimitive("name").asString to Identifier.fromNamespaceAndPath(
                        texture.namespace, texture.path.removePrefix("textures/"),
                    )
                }
            }.toMap()
            return CooCoreShaderDefinition(
                stage(json.getAsJsonPrimitive("vertex").asString, "vsh"),
                stage(json.getAsJsonPrimitive("fragment").asString, "fsh"),
                uniforms, samplers, textures, json.getAsJsonObject("blend"),
            )
        }

        /** core 名称按原版命名空间规则解析，扩展名只添加一次。 */
        private fun stage(text: String, extension: String): Identifier {
            val id = Identifier.parse(text)
            val path = id.path.removeSuffix(".$extension")
            return Identifier.fromNamespaceAndPath(id.namespace, "core/$path.$extension")
        }
    }
}
