package cn.coostack.cooparticlesapi.coofx.asset

import cn.coostack.cooparticlesapi.coofx.asset.gltf.GltfAccessorDecoder
import cn.coostack.cooparticlesapi.coofx.asset.gltf.optionalInt
import cn.coostack.cooparticlesapi.coofx.asset.gltf.requiredInt
import cn.coostack.cooparticlesapi.coofx.asset.gltf.requiredString
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class CooFxAssetImporter(
    private val resources: CooFxResourceProvider,
) {
    fun import(assetResource: ResourceLocation): CooFxImportResult {
        val diagnostics = mutableListOf<CooFxDiagnostic>()
        val asset = runCatching {
            val root = parseObject(resources.read(assetResource), "CooFX JSON")
            validateCooDocument(root, assetResource)
            val modelResource = parseResource(root.requiredString("model"), assetResource)
            val gltfData = readGltf(modelResource)
            validateGltf(gltfData.document)
            val buffers = readBuffers(gltfData, modelResource)
            normalize(assetResource, root, modelResource, gltfData.document, buffers, diagnostics)
        }.getOrElse { failure ->
            diagnostics += CooFxDiagnostic(
                severity = CooFxDiagnosticSeverity.ERROR,
                code = "coofx.import.failed",
                assetResource = assetResource,
                pointer = "",
                message = failure.message ?: "导入时发生未知错误",
            )
            null
        }
        return CooFxImportResult(asset?.takeIf { diagnostics.none { it.severity == CooFxDiagnosticSeverity.ERROR } }, diagnostics)
    }

    private fun validateCooDocument(root: JsonObject, asset: ResourceLocation) {
        require(root.requiredInt("schemaVersion") == 1) { "不支持 schemaVersion，仅接受整数 1" }
        require(root.requiredString("coordinateSystem") == "coofx_rh_y_up_z_south") { "不支持 coordinateSystem" }
        val seed = root.requiredString("assetSeed")
        require(seed.matches(Regex("[0-9a-f]{16}"))) { "assetSeed 必须是 16 位小写十六进制字符串" }
        root.getAsJsonArray("requiredExtensions")?.forEach { extension ->
            throw IllegalArgumentException("未知 CooFX required extension：${extension.asString}")
        }
        root.getAsJsonObject("extensions")?.keySet()?.forEach { id ->
            require(ResourceLocation.tryParse(id) != null) { "非法 extension ID：$id" }
        }
        require(asset.path.endsWith(".coofx.json")) { "CooFX 入口资源必须以 .coofx.json 结尾" }
    }

    private fun validateGltf(document: JsonObject) {
        require(document.getAsJsonObject("asset")?.requiredString("version") == "2.0") { "只支持 glTF 2.0" }
        document.getAsJsonArray("extensionsRequired")?.forEach { extension ->
            throw IllegalArgumentException("未知或不支持的 glTF required extension：${extension.asString}")
        }
        require(!document.has("cameras")) { "首版不支持 glTF camera" }
    }

    private fun normalize(
        assetResource: ResourceLocation,
        coo: JsonObject,
        modelResource: ResourceLocation,
        gltf: JsonObject,
        buffers: List<ByteArray>,
        diagnostics: MutableList<CooFxDiagnostic>,
    ): CooFxSourceAsset {
        val decoder = GltfAccessorDecoder(gltf, buffers)
        val textures = resolveTextures(gltf, modelResource)
        val materials = gltf.array("materials").mapIndexed { index, element ->
            runCatching { normalizeMaterial(element.asJsonObject, textures) }.getOrElse { failure ->
                throw IllegalArgumentException("material[$index]：${failure.message}")
            }
        }
        var morphTargets = 0
        val meshes = gltf.array("meshes").mapIndexed { meshIndex, element ->
            val mesh = element.asJsonObject
            CooFxMesh(
                name = mesh.stringOrNull("name"),
                primitives = mesh.array("primitives").mapIndexed { primitiveIndex, primitiveElement ->
                    val primitive = primitiveElement.asJsonObject
                    require(primitive.optionalInt("mode", 4) == 4) { "mesh[$meshIndex].primitive[$primitiveIndex] 仅支持 TRIANGLES" }
                    val attributes = primitive.getAsJsonObject("attributes")
                        ?: throw IllegalArgumentException("primitive 缺少 attributes")
                    val unsupported = attributes.keySet().filterNot { it in setOf("POSITION", "NORMAL", "TEXCOORD_0", "COLOR_0") }
                    require(unsupported.isEmpty()) { "不支持顶点属性：${unsupported.joinToString()}" }
                    val positions = decoder.decode(attributes.requiredInt("POSITION"))
                    require(positions.componentCount == 3) { "POSITION 必须为 VEC3" }
                    val targets = primitive.array("targets").map { target -> target.asJsonObject.keySet().toSet() }
                    morphTargets += targets.size
                    if (targets.isNotEmpty()) {
                        diagnostics += diagnostic(assetResource, modelResource, "gltf.morph.metadata", "/meshes/$meshIndex/primitives/$primitiveIndex/targets", "已保留 morph target 元数据，首版运行时不执行", meshIndex)
                    }
                    CooFxMeshPrimitive(
                        positions = positions.values,
                        normals = decodeOptional(attributes, "NORMAL", decoder, 3),
                        texCoords = decodeOptional(attributes, "TEXCOORD_0", decoder, 2),
                        colors = attributes.get("COLOR_0")?.asInt?.let { decoder.decode(it).values },
                        indices = primitive.get("indices")?.asInt?.let { decoder.decodeIndices(it) }
                            ?: List(positions.count) { it },
                        material = primitive.get("material")?.asInt,
                        morphTargetSemantics = targets,
                    )
                },
                weights = mesh.floatList("weights"),
            )
        }
        val nodes = gltf.array("nodes").mapIndexed { index, element -> normalizeNode(element.asJsonObject, index) }
        validateNodeGraph(nodes)
        val animations = normalizeAnimations(gltf, decoder, assetResource, modelResource, diagnostics)
        val skinCount = gltf.array("skins").size()
        if (skinCount > 0) {
            diagnostics += diagnostic(assetResource, modelResource, "gltf.skin.metadata", "/skins", "已保留 skin 数量和节点引用，首版运行时不执行")
        }
        val vatIds = coo.getAsJsonObject("extensions")?.keySet()
            ?.filter { it.contains("vat", ignoreCase = true) }
            ?.mapNotNull(ResourceLocation::tryParse)
            ?.toSet()
            .orEmpty()
        if (vatIds.isNotEmpty()) {
            diagnostics += CooFxDiagnostic(CooFxDiagnosticSeverity.WARNING, "coofx.vat.metadata", assetResource, "/extensions", "已保留 VAT extension 标识，首版运行时不执行")
        }
        return CooFxSourceAsset(
            resource = assetResource,
            schemaVersion = 1,
            assetSeed = coo.requiredString("assetSeed").toULong(16),
            modelResource = modelResource,
            scene = coo.optionalInt("scene", gltf.optionalInt("scene", 0)),
            nodes = nodes,
            meshes = meshes,
            materials = materials,
            animations = animations,
            emitters = normalizeEmitters(coo),
            deformationMetadata = CooFxDeformationMetadata(skinCount, morphTargets, animations.any { animation -> animation.channels.any { it.path == CooFxAnimationPath.WEIGHTS } }, vatIds),
        )
    }

    private fun normalizeAnimations(
        gltf: JsonObject,
        decoder: GltfAccessorDecoder,
        asset: ResourceLocation,
        model: ResourceLocation,
        diagnostics: MutableList<CooFxDiagnostic>,
    ): List<CooFxAnimation> = gltf.array("animations").mapIndexed { animationIndex, element ->
        val animation = element.asJsonObject
        val samplers = animation.array("samplers")
        CooFxAnimation(
            name = animation.stringOrNull("name"),
            channels = animation.array("channels").mapIndexed { channelIndex, channelElement ->
                val channel = channelElement.asJsonObject
                val samplerIndex = channel.requiredInt("sampler")
                val sampler = samplers.getOrNull(samplerIndex)?.asJsonObject
                    ?: throw IllegalArgumentException("animation sampler 索引越界")
                val target = channel.getAsJsonObject("target") ?: throw IllegalArgumentException("animation channel 缺少 target")
                val path = when (target.requiredString("path")) {
                    "translation" -> CooFxAnimationPath.TRANSLATION
                    "rotation" -> CooFxAnimationPath.ROTATION
                    "scale" -> CooFxAnimationPath.SCALE
                    "weights" -> CooFxAnimationPath.WEIGHTS
                    else -> throw IllegalArgumentException("不支持 animation path")
                }
                if (path == CooFxAnimationPath.WEIGHTS) {
                    diagnostics += diagnostic(asset, model, "gltf.animation.weights", "/animations/$animationIndex/channels/$channelIndex", "weights 动画仅保留元数据，首版运行时不执行", animationIndex)
                }
                val input = decoder.decode(sampler.requiredInt("input"))
                val output = decoder.decode(sampler.requiredInt("output"))
                require(input.componentCount == 1) { "动画输入必须为 SCALAR" }
                require(input.values.zipWithNext().all { (first, second) -> second > first }) { "动画时间必须严格递增" }
                CooFxAnimationChannel(
                    node = target.requiredInt("node"),
                    path = path,
                    interpolation = when (sampler.get("interpolation")?.asString ?: "LINEAR") {
                        "STEP" -> CooFxInterpolation.STEP
                        "LINEAR" -> CooFxInterpolation.LINEAR
                        "CUBICSPLINE" -> CooFxInterpolation.CUBICSPLINE
                        else -> throw IllegalArgumentException("不支持 animation interpolation")
                    },
                    inputSeconds = input.values,
                    outputValues = output.values,
                    outputComponentCount = output.componentCount,
                )
            },
        )
    }

    private fun normalizeMaterial(material: JsonObject, textures: List<ResourceLocation?>): CooFxMaterial {
        val pbr = material.getAsJsonObject("pbrMetallicRoughness")
        val textureIndex = pbr?.getAsJsonObject("baseColorTexture")?.get("index")?.asInt
        return CooFxMaterial(
            name = material.stringOrNull("name"),
            baseColorFactor = pbr?.floatList("baseColorFactor").takeUnless { it.isNullOrEmpty() } ?: listOf(1F, 1F, 1F, 1F),
            baseColorTexture = textureIndex?.let { textures.getOrNull(it) ?: throw IllegalArgumentException("texture 索引越界") },
            alphaMode = when (material.get("alphaMode")?.asString ?: "OPAQUE") {
                "OPAQUE" -> CooFxAlphaMode.OPAQUE
                "MASK" -> CooFxAlphaMode.MASK
                "BLEND" -> CooFxAlphaMode.BLEND
                else -> throw IllegalArgumentException("未知 alphaMode")
            },
            alphaCutoff = (material.get("alphaCutoff")?.asFloat ?: 0.5F).also { require(it.isFinite()) },
            doubleSided = material.get("doubleSided")?.asBoolean ?: false,
            emissiveFactor = material.floatList("emissiveFactor").ifEmpty { listOf(0F, 0F, 0F) },
        )
    }

    private fun normalizeNode(node: JsonObject, index: Int): CooFxNode {
        require(!(node.has("matrix") && (node.has("translation") || node.has("rotation") || node.has("scale")))) { "node[$index] 不能同时声明 matrix 和 TRS" }
        return CooFxNode(
            name = node.stringOrNull("name"),
            children = node.intList("children"),
            mesh = node.get("mesh")?.asInt,
            skin = node.get("skin")?.asInt,
            matrix = node.floatList("matrix").takeIf { it.isNotEmpty() },
            translation = node.floatList("translation").ifEmpty { listOf(0F, 0F, 0F) },
            rotation = node.floatList("rotation").ifEmpty { listOf(0F, 0F, 0F, 1F) },
            scale = node.floatList("scale").ifEmpty { listOf(1F, 1F, 1F) },
        )
    }

    private fun normalizeEmitters(coo: JsonObject): List<CooFxEmitter> = coo.array("emitters").map { element ->
        val emitter = element.asJsonObject
        CooFxEmitter(
            id = emitter.requiredString("id"),
            node = emitter.get("node")?.asInt,
            mesh = emitter.get("mesh")?.asInt,
            count = emitter.requiredInt("count").also { require(it >= 0) { "emitter count 不能为负数" } },
            delayTicks = emitter.optionalInt("delayTicks", 0).also { require(it >= 0) { "emitter delayTicks 不能为负数" } },
            lifetimeTicks = emitter.requiredInt("lifetimeTicks").also { require(it >= 0) { "emitter lifetimeTicks 不能为负数" } },
        )
    }

    private fun resolveTextures(gltf: JsonObject, model: ResourceLocation): List<ResourceLocation?> {
        val images = gltf.array("images").mapIndexed { index, element ->
            val image = element.asJsonObject
            require(!image.has("bufferView")) { "image[$index] 不支持内嵌 bufferView" }
            val uri = image.requiredString("uri")
            require(uri.endsWith(".png", ignoreCase = true)) { "首版 image 只支持 PNG" }
            resolveCooFxResource(model, uri) ?: throw IllegalArgumentException("image[$index] 路径非法")
        }
        return gltf.array("textures").map { element -> images.getOrNull(element.asJsonObject.requiredInt("source")) }
    }

    private fun readBuffers(data: GltfData, model: ResourceLocation): List<ByteArray> = data.document.array("buffers").mapIndexed { index, element ->
        val buffer = element.asJsonObject
        val uri = buffer.get("uri")?.asString
        val bytes = if (uri == null) {
            require(index == 0 && data.binaryChunk != null) { "无 URI buffer 仅允许使用 GLB BIN chunk" }
            data.binaryChunk
        } else {
            require(!uri.startsWith("data:", ignoreCase = true)) { "不支持 data URI" }
            val resource = resolveCooFxResource(model, uri) ?: throw IllegalArgumentException("buffer[$index] 路径非法")
            resources.read(resource)
        }
        require(bytes.size >= buffer.requiredInt("byteLength")) { "buffer[$index] 长度不足" }
        bytes
    }

    private fun readGltf(resource: ResourceLocation): GltfData {
        val bytes = resources.read(resource)
        return if (resource.path.endsWith(".glb", ignoreCase = true)) parseGlb(bytes) else GltfData(parseObject(bytes, "glTF JSON"), null)
    }

    private fun parseGlb(bytes: ByteArray): GltfData {
        require(bytes.size >= 20) { "GLB 文件过短" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buffer.int == 0x46546c67) { "GLB magic 不正确" }
        require(buffer.int == 2) { "只支持 GLB 2.0" }
        require(buffer.int == bytes.size) { "GLB 声明长度与实际长度不一致" }
        var json: JsonObject? = null
        var binary: ByteArray? = null
        while (buffer.remaining() >= 8) {
            val length = buffer.int
            val type = buffer.int
            require(length >= 0 && length <= buffer.remaining()) { "GLB chunk 越界" }
            val chunk = ByteArray(length)
            buffer.get(chunk)
            when (type) {
                0x4e4f534a -> require(json == null) { "GLB 包含多个 JSON chunk" }.also { json = parseObject(chunk, "GLB JSON") }
                0x004e4942 -> require(binary == null) { "GLB 包含多个 BIN chunk" }.also { binary = chunk }
            }
        }
        require(buffer.remaining() == 0) { "GLB 尾部存在不完整 chunk" }
        return GltfData(requireNotNull(json) { "GLB 缺少 JSON chunk" }, binary)
    }

    private fun parseObject(bytes: ByteArray, label: String): JsonObject {
        val text = bytes.toString(StandardCharsets.UTF_8).trimEnd('\u0000', ' ', '\n', '\r', '\t')
        return JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException("$label 顶层必须是对象")
    }

    private fun parseResource(reference: String, base: ResourceLocation): ResourceLocation {
        return resolveCooFxResource(base, reference) ?: throw IllegalArgumentException("model 路径非法或发生目录逃逸")
    }

    private fun validateNodeGraph(nodes: List<CooFxNode>) {
        nodes.forEachIndexed { index, node -> node.children.forEach { require(it in nodes.indices) { "node[$index] 子节点索引越界" } } }
        val state = IntArray(nodes.size)
        fun visit(index: Int) {
            require(state[index] != 1) { "node 层级包含循环" }
            if (state[index] == 2) return
            state[index] = 1
            nodes[index].children.forEach(::visit)
            state[index] = 2
        }
        nodes.indices.forEach(::visit)
    }

    private fun decodeOptional(attributes: JsonObject, semantic: String, decoder: GltfAccessorDecoder, components: Int): List<Float>? {
        return attributes.get(semantic)?.asInt?.let { decoder.decode(it).also { decoded -> require(decoded.componentCount == components) { "$semantic 分量数量不正确" } }.values }
    }

    private fun diagnostic(asset: ResourceLocation, model: ResourceLocation, code: String, pointer: String, message: String, objectIndex: Int? = null) =
        CooFxDiagnostic(CooFxDiagnosticSeverity.WARNING, code, asset, pointer, message, model, objectIndex)

    private data class GltfData(val document: JsonObject, val binaryChunk: ByteArray?)
}

private fun JsonObject.array(name: String): JsonArray = getAsJsonArray(name) ?: JsonArray()
private fun JsonObject.stringOrNull(name: String): String? = get(name)?.takeIf { !it.isJsonNull }?.asString
private fun JsonObject.floatList(name: String): List<Float> = getAsJsonArray(name)?.map { value -> value.asFloat.also { require(it.isFinite()) { "$name 包含非有限数" } } }.orEmpty()
private fun JsonObject.intList(name: String): List<Int> = getAsJsonArray(name)?.map { it.asInt }.orEmpty()
private fun <T> Iterable<T>.getOrNull(index: Int): T? = if (index < 0) null else elementAtOrNull(index)
