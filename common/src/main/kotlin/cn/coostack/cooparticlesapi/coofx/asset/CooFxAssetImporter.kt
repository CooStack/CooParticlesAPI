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
        val supportedFields = setOf(
            "\$schema",
            "schemaVersion",
            "coordinateSystem",
            "assetSeed",
            "model",
            "scene",
            "clips",
            "materials",
            "emitters",
            "requiredExtensions",
            "extensions",
        )
        val unknownFields = root.keySet() - supportedFields
        require(unknownFields.isEmpty()) { "CooFX 顶层包含未知字段：${unknownFields.sorted().joinToString()}" }
        require(root.requiredString("\$schema") == "cooparticlesapi:coofx/schema/v1") { "不支持 CooFX schema ID" }
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
            require(extension.asString == "KHR_materials_emissive_strength") {
                "未知或不支持的 glTF required extension：${extension.asString}"
            }
        }
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
        val gltfMaterials = gltf.array("materials")
        val baseMaterials = gltfMaterials.mapIndexed { index, element ->
            runCatching { normalizeMaterial(element.asJsonObject, textures) }.getOrElse { failure ->
                throw IllegalArgumentException("material[$index]：${failure.message}")
            }
        }
        val materials = applyMaterialOverrides(coo, assetResource, gltfMaterials, baseMaterials)
        val sourceCameras = gltf.array("cameras").mapIndexed { index, element ->
            normalizeCamera(element.asJsonObject, index)
        }
        val sourceNodes = gltf.array("nodes").mapIndexed { index, element -> normalizeNode(element.asJsonObject, index) }
        val sourceMeshes = gltf.array("meshes")
        val sourceSkinCount = gltf.array("skins").size()
        validateNodeGraph(sourceNodes)
        sourceNodes.forEachIndexed { index, node ->
            require(node.mesh == null || node.mesh in 0 until sourceMeshes.size()) { "node[$index] mesh 索引越界" }
            require(node.skin == null || node.skin in 0 until sourceSkinCount) { "node[$index] skin 索引越界" }
            require(node.camera == null || node.camera in sourceCameras.indices) { "node[$index] camera 索引越界" }
        }
        val scenes = gltf.array("scenes")
        val scene = coo.optionalInt("scene", gltf.optionalInt("scene", 0))
        require(scene in 0 until scenes.size()) { "scene 索引越界" }
        scenes.forEachIndexed { sceneIndex, element ->
            element.asJsonObject.intList("nodes").forEach { nodeIndex ->
                require(nodeIndex in sourceNodes.indices) { "scene[$sceneIndex] node 索引越界" }
            }
        }
        val selectedNodeIndices = collectReachableNodes(scenes[scene].asJsonObject, sourceNodes)
        val nodeRemap = selectedNodeIndices.withIndex().associate { (newIndex, oldIndex) -> oldIndex to newIndex }
        val selectedMeshIndices = selectedNodeIndices.mapNotNull { sourceNodes[it].mesh }.distinct().sorted()
        val meshRemap = selectedMeshIndices.withIndex().associate { (newIndex, oldIndex) -> oldIndex to newIndex }
        val selectedCameraIndices = selectedNodeIndices.mapNotNull { sourceNodes[it].camera }.distinct().sorted()
        val cameraRemap = selectedCameraIndices.withIndex().associate { (newIndex, oldIndex) -> oldIndex to newIndex }
        val cameras = selectedCameraIndices.map { cameraIndex -> sourceCameras[cameraIndex] }
        var morphTargets = 0
        val meshes = selectedMeshIndices.map { meshIndex ->
            normalizeMesh(
                mesh = sourceMeshes[meshIndex].asJsonObject,
                meshIndex = meshIndex,
                decoder = decoder,
                materials = materials,
                assetResource = assetResource,
                modelResource = modelResource,
                diagnostics = diagnostics,
            ).also { normalized -> morphTargets += normalized.morphTargetCount }.mesh
        }
        val nodes = selectedNodeIndices.map { oldIndex ->
            val node = sourceNodes[oldIndex]
            node.copy(
                children = node.children.map { child -> nodeRemap.getValue(child) },
                mesh = node.mesh?.let { meshIndex -> meshRemap.getValue(meshIndex) },
                camera = node.camera?.let { cameraIndex -> cameraRemap.getValue(cameraIndex) },
            )
        }
        val skinCount = nodes.mapNotNull(CooFxNode::skin).distinct().size
        val animations = normalizeAnimations(gltf, decoder, sourceNodes.size, assetResource, modelResource, diagnostics)
            .mapIndexed { animationIndex, animation ->
                animation.copy(
                    channels = animation.channels.mapIndexedNotNull { channelIndex, channel ->
                        val selectedNode = nodeRemap[channel.node]
                        if (selectedNode == null) {
                            diagnostics += diagnostic(
                                assetResource,
                                modelResource,
                                "gltf.animation.outside_scene",
                                "/animations/$animationIndex/channels/$channelIndex",
                                "动画通道目标不属于选定 scene，已从规范资产移除",
                                animationIndex,
                            )
                            null
                        } else {
                            channel.copy(node = selectedNode)
                        }
                    },
                )
            }
        animations.forEachIndexed { animationIndex, animation ->
            animation.channels.forEachIndexed { channelIndex, channel ->
                require(channel.path == CooFxAnimationPath.WEIGHTS || nodes[channel.node].matrix == null) {
                    "animation[$animationIndex].channel[$channelIndex] 不能以 matrix node 为 transform 目标"
                }
            }
        }
        val clips = normalizeClips(coo, animations)
        val cameraNodeIndices = nodes.mapIndexedNotNull { nodeIndex, node ->
            node.camera?.let { nodeIndex }
        }.toSet()
        val animatedNodeIndices = animations
            .flatMap { animation -> animation.channels }
            .filter { channel -> channel.path != CooFxAnimationPath.WEIGHTS }
            .map { channel -> channel.node }
            .toSet()
        val parentIndices = IntArray(nodes.size) { -1 }
        nodes.forEachIndexed { nodeIndex, node ->
            node.children.forEach { childIndex -> parentIndices[childIndex] = nodeIndex }
        }
        fun hasAnimatedAncestor(nodeIndex: Int): Boolean {
            var current = nodeIndex
            while (current >= 0) {
                if (current in animatedNodeIndices) return true
                current = parentIndices[current]
            }
            return false
        }
        if (cameraNodeIndices.isNotEmpty() && cameraNodeIndices.none(::hasAnimatedAncestor)) {
            diagnostics += diagnostic(
                assetResource,
                modelResource,
                "gltf.camera.bind_pose",
                "/nodes",
                "选定 scene 包含 camera，但 camera 节点及其祖先没有变换动画；camera 将保持 bind pose",
            )
        }
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
            scene = scene,
            nodes = nodes,
            meshes = meshes,
            materials = materials,
            animations = animations,
            emitters = normalizeEmitters(
                coo,
                sourceNodes.map(CooFxNode::name),
                sourceMeshes.map { element -> element.asJsonObject.stringOrNull("name") },
                nodeRemap,
                meshRemap,
            ),
            deformationMetadata = CooFxDeformationMetadata(skinCount, morphTargets, animations.any { animation -> animation.channels.any { it.path == CooFxAnimationPath.WEIGHTS } }, vatIds),
            cameras = cameras,
            clips = clips,
            extensionMetadata = normalizeExtensionMetadata(coo, gltf, assetResource, modelResource, diagnostics),
        )
    }

    private fun normalizeMesh(
        mesh: JsonObject,
        meshIndex: Int,
        decoder: GltfAccessorDecoder,
        materials: List<CooFxMaterial>,
        assetResource: ResourceLocation,
        modelResource: ResourceLocation,
        diagnostics: MutableList<CooFxDiagnostic>,
    ): NormalizedMesh {
        var morphTargetCount = 0
        val normalized = CooFxMesh(
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
                val normals = decodeOptional(attributes, "NORMAL", decoder, 3, positions.count)
                val texCoords = decodeOptional(attributes, "TEXCOORD_0", decoder, 2, positions.count)
                val colors = attributes.get("COLOR_0")?.asInt?.let { accessorIndex ->
                    decoder.decode(accessorIndex).also { decoded ->
                        require(decoded.componentCount == 3 || decoded.componentCount == 4) { "COLOR_0 必须为 VEC3 或 VEC4" }
                        require(decoded.count == positions.count) { "COLOR_0 accessor count 必须与 POSITION 一致" }
                    }.values
                }
                val indices = primitive.get("indices")?.asInt?.let(decoder::decodeIndices)
                    ?: List(positions.count) { it }
                require(indices.all { it in 0 until positions.count }) { "primitive 索引超出顶点范围" }
                val material = primitive.get("material")?.asInt
                require(material == null || material in materials.indices) { "primitive material 索引越界" }
                val targets = primitive.array("targets").map { target -> target.asJsonObject.keySet().toSet() }
                morphTargetCount += targets.size
                if (targets.isNotEmpty()) {
                    diagnostics += diagnostic(
                        assetResource,
                        modelResource,
                        "gltf.morph.metadata",
                        "/meshes/$meshIndex/primitives/$primitiveIndex/targets",
                        "已保留 morph target 元数据，首版运行时不执行",
                        meshIndex,
                    )
                }
                CooFxMeshPrimitive(
                    positions = positions.values,
                    normals = normals,
                    texCoords = texCoords,
                    colors = colors,
                    indices = indices,
                    material = material,
                    morphTargetSemantics = targets,
                )
            },
            weights = mesh.floatList("weights"),
        )
        return NormalizedMesh(normalized, morphTargetCount)
    }

    private fun collectReachableNodes(scene: JsonObject, nodes: List<CooFxNode>): List<Int> {
        val reachable = linkedSetOf<Int>()
        fun visit(index: Int) {
            if (!reachable.add(index)) return
            nodes[index].children.forEach(::visit)
        }
        scene.intList("nodes").forEach(::visit)
        return reachable.sorted()
    }

    private fun normalizeAnimations(
        gltf: JsonObject,
        decoder: GltfAccessorDecoder,
        nodeCount: Int,
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
                val node = target.requiredInt("node")
                require(node in 0 until nodeCount) { "animation target node 索引越界" }
                val interpolation = when (sampler.get("interpolation")?.asString ?: "LINEAR") {
                    "STEP" -> CooFxInterpolation.STEP
                    "LINEAR" -> CooFxInterpolation.LINEAR
                    "CUBICSPLINE" -> CooFxInterpolation.CUBICSPLINE
                    else -> throw IllegalArgumentException("不支持 animation interpolation")
                }
                val input = decoder.decode(sampler.requiredInt("input"))
                val output = decoder.decode(sampler.requiredInt("output"))
                require(input.componentCount == 1) { "动画输入必须为 SCALAR" }
                require(input.values.zipWithNext().all { (first, second) -> second > first }) { "动画时间必须严格递增" }
                val expectedComponents = when (path) {
                    CooFxAnimationPath.TRANSLATION, CooFxAnimationPath.SCALE -> 3
                    CooFxAnimationPath.ROTATION -> 4
                    CooFxAnimationPath.WEIGHTS -> output.componentCount
                }
                require(output.componentCount == expectedComponents) { "animation output 分量数量与 target path 不一致" }
                val sampleMultiplier = if (interpolation == CooFxInterpolation.CUBICSPLINE) 3 else 1
                require(output.count == input.count * sampleMultiplier) { "animation output keyframe 布局与 input/interpolation 不一致" }
                CooFxAnimationChannel(
                    node = node,
                    path = path,
                    interpolation = interpolation,
                    inputSeconds = input.values,
                    outputValues = output.values,
                    outputComponentCount = output.componentCount,
                )
            },
        )
    }

    private fun applyMaterialOverrides(
        coo: JsonObject,
        assetResource: ResourceLocation,
        gltfMaterials: JsonArray,
        baseMaterials: List<CooFxMaterial>,
    ): List<CooFxMaterial> {
        val result = baseMaterials.toMutableList()
        val materialNames = gltfMaterials.map { element -> element.asJsonObject.stringOrNull("name") }
        val overridden = mutableSetOf<Int>()
        val ids = mutableSetOf<String>()
        coo.array("materials").forEachIndexed { index, element ->
            val override = element.asJsonObject
            val id = override.requiredString("id")
            require(id.isNotBlank() && ids.add(id)) { "CooFX material id 不能为空或重复：$id" }
            val reference = override.get("gltfMaterial")
                ?: throw IllegalArgumentException("CooFX material[$index] 缺少 gltfMaterial")
            val materialIndex = if (reference.isJsonPrimitive && reference.asJsonPrimitive.isNumber) {
                reference.asInt.also { require(it in result.indices) { "CooFX material[$index] gltfMaterial 索引越界" } }
            } else {
                require(reference.isJsonPrimitive && reference.asJsonPrimitive.isString) {
                    "CooFX material[$index] gltfMaterial 必须是索引或 glTF 名称"
                }
                val materialName = reference.asString
                val matches = materialNames.indices.filter { materialNames[it] == materialName }
                require(matches.size == 1) {
                    if (matches.isEmpty()) {
                        "CooFX material[$index] 找不到 glTF material：$materialName"
                    } else {
                        "CooFX material[$index] glTF material 名称不唯一：$materialName"
                    }
                }
                matches.single()
            }
            require(overridden.add(materialIndex)) { "同一个 glTF material 不能被重复覆盖：$materialIndex" }
            val current = result[materialIndex]
            val alphaMode = when (override.get("alphaMode")?.asString ?: current.alphaMode.name) {
                "OPAQUE" -> CooFxAlphaMode.OPAQUE
                "MASK" -> CooFxAlphaMode.MASK
                "BLEND" -> CooFxAlphaMode.BLEND
                else -> throw IllegalArgumentException("CooFX material[$index] alphaMode 不受支持")
            }
            val texture = override.get("baseColorTexture")?.asString?.let { referencePath ->
                parseResource(referencePath, assetResource)
            } ?: current.baseColorTexture
            result[materialIndex] = current.copy(
                baseColorTexture = texture,
                alphaMode = alphaMode,
                alphaCutoff = (override.get("alphaCutoff")?.asFloat ?: current.alphaCutoff).also {
                    require(it.isFinite() && it in 0F..1F) { "CooFX material[$index] alphaCutoff 必须在 0 到 1" }
                },
                doubleSided = override.get("doubleSided")?.asBoolean ?: current.doubleSided,
            )
        }
        return result
    }

    private fun normalizeMaterial(material: JsonObject, textures: List<ResourceLocation?>): CooFxMaterial {
        val pbr = material.getAsJsonObject("pbrMetallicRoughness")
        val baseColorTextureInfo = pbr?.getAsJsonObject("baseColorTexture")
        val emissiveTextureInfo = material.getAsJsonObject("emissiveTexture")
        listOf(baseColorTextureInfo, emissiveTextureInfo).forEach { textureInfo ->
            val texCoord = textureInfo?.get("texCoord")?.asInt ?: 0
            require(texCoord == 0) { "当前只支持纹理 TEXCOORD_0，未实现 texCoord=$texCoord" }
        }
        val textureIndex = baseColorTextureInfo?.get("index")?.asInt
        val emissiveTextureIndex = emissiveTextureInfo?.get("index")?.asInt
        val emissiveStrength = material.getAsJsonObject("extensions")
            ?.getAsJsonObject("KHR_materials_emissive_strength")
            ?.get("emissiveStrength")
            ?.asFloat
            ?: 1F
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
            emissiveTexture = emissiveTextureIndex?.let {
                textures.getOrNull(it) ?: throw IllegalArgumentException("emissive texture 索引越界")
            },
            emissiveStrength = emissiveStrength,
        )
    }

    private fun normalizeCamera(camera: JsonObject, index: Int): CooFxCamera {
        val id = "camera_$index"
        val name = camera.stringOrNull("name")
        return when (camera.requiredString("type")) {
            "perspective" -> {
                val perspective = camera.getAsJsonObject("perspective")
                    ?: throw IllegalArgumentException("camera[$index] 缺少 perspective")
                CooFxCamera.Perspective(
                    id = id,
                    name = name,
                    yfovRadians = perspective.get("yfov")?.asFloat
                        ?: throw IllegalArgumentException("camera[$index] 缺少 perspective.yfov"),
                    aspectRatio = perspective.get("aspectRatio")?.asFloat,
                    znear = perspective.get("znear")?.asFloat
                        ?: throw IllegalArgumentException("camera[$index] 缺少 perspective.znear"),
                    zfar = perspective.get("zfar")?.asFloat,
                )
            }
            "orthographic" -> {
                val orthographic = camera.getAsJsonObject("orthographic")
                    ?: throw IllegalArgumentException("camera[$index] 缺少 orthographic")
                CooFxCamera.Orthographic(
                    id = id,
                    name = name,
                    xmag = orthographic.get("xmag")?.asFloat
                        ?: throw IllegalArgumentException("camera[$index] 缺少 orthographic.xmag"),
                    ymag = orthographic.get("ymag")?.asFloat
                        ?: throw IllegalArgumentException("camera[$index] 缺少 orthographic.ymag"),
                    znear = orthographic.get("znear")?.asFloat
                        ?: throw IllegalArgumentException("camera[$index] 缺少 orthographic.znear"),
                    zfar = orthographic.get("zfar")?.asFloat
                        ?: throw IllegalArgumentException("camera[$index] 缺少 orthographic.zfar"),
                )
            }
            else -> throw IllegalArgumentException("camera[$index] type 必须是 perspective 或 orthographic")
        }
    }

    private fun normalizeNode(node: JsonObject, index: Int): CooFxNode {
        require(!(node.has("matrix") && (node.has("translation") || node.has("rotation") || node.has("scale")))) { "node[$index] 不能同时声明 matrix 和 TRS" }
        val matrix = node.floatList("matrix").takeIf { it.isNotEmpty() }
        val translation = node.floatList("translation").ifEmpty { listOf(0F, 0F, 0F) }
        val rotation = node.floatList("rotation").ifEmpty { listOf(0F, 0F, 0F, 1F) }
        val scale = node.floatList("scale").ifEmpty { listOf(1F, 1F, 1F) }
        require(matrix == null || matrix.size == 16) { "node[$index] matrix 必须包含 16 个分量" }
        require(translation.size == 3) { "node[$index] translation 必须包含 3 个分量" }
        require(rotation.size == 4) { "node[$index] rotation 必须包含 4 个分量" }
        require(scale.size == 3) { "node[$index] scale 必须包含 3 个分量" }
        require(listOfNotNull(matrix).flatten().all(Float::isFinite) &&
            translation.all(Float::isFinite) && rotation.all(Float::isFinite) && scale.all(Float::isFinite)) {
            "node[$index] 变换分量必须为有限数"
        }
        return CooFxNode(
            name = node.stringOrNull("name"),
            children = node.intList("children"),
            mesh = node.get("mesh")?.asInt,
            skin = node.get("skin")?.asInt,
            matrix = matrix,
            translation = translation,
            rotation = rotation,
            scale = scale,
            camera = node.get("camera")?.asInt,
        )
    }

    private fun normalizeClips(coo: JsonObject, animations: List<CooFxAnimation>): List<CooFxClip> {
        val clips = coo.array("clips").mapIndexed { index, element ->
            val clip = element.asJsonObject
            val reference = clip.get("animation")
                ?: throw IllegalArgumentException("clip[$index] 缺少 animation")
            val animationIndex = if (reference.isJsonPrimitive && reference.asJsonPrimitive.isNumber) {
                reference.asInt.also { require(it in animations.indices) { "clip[$index] animation 索引越界" } }
            } else {
                require(reference.isJsonPrimitive && reference.asJsonPrimitive.isString) {
                    "clip[$index] animation 必须是索引或 glTF 名称"
                }
                val animationName = reference.asString
                val matches = animations.indices.filter { animations[it].name == animationName }
                require(matches.size == 1) {
                    if (matches.isEmpty()) {
                        "clip[$index] 找不到 glTF animation：$animationName"
                    } else {
                        "clip[$index] glTF animation 名称不唯一：$animationName"
                    }
                }
                matches.single()
            }
            val loopMode = when (clip.get("loopMode")?.asString ?: "ONCE") {
                "ONCE" -> CooFxClipLoopMode.ONCE
                "LOOP" -> CooFxClipLoopMode.LOOP
                "PING_PONG" -> CooFxClipLoopMode.PING_PONG
                else -> throw IllegalArgumentException("clip[$index] loopMode 不受支持")
            }
            CooFxClip(
                id = clip.requiredString("id"),
                animation = animationIndex,
                loopMode = loopMode,
            )
        }
        require(clips.map(CooFxClip::id).distinct().size == clips.size) { "clip id 不能重复" }
        return clips
    }

    private fun normalizeExtensionMetadata(
        coo: JsonObject,
        gltf: JsonObject,
        assetResource: ResourceLocation,
        modelResource: ResourceLocation,
        diagnostics: MutableList<CooFxDiagnostic>,
    ): CooFxExtensionMetadata {
        val cooFxExtensions = coo.getAsJsonObject("extensions")?.entrySet()?.associate { (id, payload) ->
            requireNotNull(ResourceLocation.tryParse(id)) to payload.toString()
        }.orEmpty()
        cooFxExtensions.keys.sortedBy(ResourceLocation::toString).forEach { id ->
            diagnostics += CooFxDiagnostic(
                severity = CooFxDiagnosticSeverity.WARNING,
                code = "coofx.extension.metadata",
                assetResource = assetResource,
                pointer = "/extensions/$id",
                message = "已保留非 required CooFX extension 元数据，当前运行时不执行：$id",
            )
        }

        val gltfPayloads = gltf.getAsJsonObject("extensions")?.entrySet()?.associate { (id, payload) ->
            id to payload.toString()
        }.orEmpty()
        val gltfExtensionIds = buildSet {
            gltf.getAsJsonArray("extensionsUsed")?.forEach { extension -> add(extension.asString) }
            addAll(gltfPayloads.keys)
        }.filterNot { id -> id == "KHR_materials_emissive_strength" }
        val gltfExtensions = gltfExtensionIds.sorted().associateWith(gltfPayloads::get)
        gltfExtensions.keys.forEach { id ->
            diagnostics += diagnostic(
                assetResource,
                modelResource,
                "gltf.extension.metadata",
                "/extensions/$id",
                "已保留非 required glTF extension 元数据，当前运行时不执行：$id",
            )
        }
        return CooFxExtensionMetadata(cooFxExtensions, gltfExtensions)
    }

    private fun normalizeEmitters(
        coo: JsonObject,
        nodeNames: List<String?>,
        meshNames: List<String?>,
        nodeRemap: Map<Int, Int>,
        meshRemap: Map<Int, Int>,
    ): List<CooFxEmitter> = coo.array("emitters").mapIndexed { index, element ->
        val emitter = element.asJsonObject
        val node = resolveSelectedIndex(emitter, "node", nodeNames, nodeRemap, index)
        val mesh = resolveSelectedIndex(emitter, "mesh", meshNames, meshRemap, index)
        CooFxEmitter(
            id = emitter.requiredString("id"),
            node = node,
            mesh = mesh,
            count = emitter.requiredInt("count").also { require(it >= 0) { "emitter count 不能为负数" } },
            delayTicks = emitter.optionalInt("delayTicks", 0).also { require(it >= 0) { "emitter delayTicks 不能为负数" } },
            lifetimeTicks = emitter.requiredInt("lifetimeTicks").also { require(it > 0) { "emitter lifetimeTicks 必须大于零" } },
            velocity = parseFloat3Range(emitter, "velocity", 0F),
            rotationRadians = parseFloat3Range(emitter, "rotationRadians", 0F),
            scale = parseFloat3Range(emitter, "scale", 1F),
        )
    }

    private fun resolveSelectedIndex(
        owner: JsonObject,
        field: String,
        names: List<String?>,
        remap: Map<Int, Int>,
        emitterIndex: Int,
    ): Int? {
        val value = owner.get(field) ?: return null
        if (value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
            val sourceIndex = value.asInt
            require(sourceIndex in names.indices) { "emitter[$emitterIndex] $field 索引越界" }
            return remap[sourceIndex]
                ?: throw IllegalArgumentException("emitter[$emitterIndex] $field 不属于选定 scene")
        }
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            "emitter[$emitterIndex] $field 必须是索引或 glTF 名称"
        }
        val name = value.asString
        val sourceMatches = names.indices.filter { names[it] == name }
        val selectedMatches = sourceMatches.filter(remap::containsKey)
        require(selectedMatches.size == 1) {
            when {
                selectedMatches.size > 1 -> "emitter[$emitterIndex] $field 名称在选定 scene 中不唯一：$name"
                sourceMatches.isNotEmpty() -> "emitter[$emitterIndex] $field 不属于选定 scene：$name"
                else -> "emitter[$emitterIndex] $field 找不到 glTF 名称：$name"
            }
        }
        return remap.getValue(selectedMatches.single())
    }

    private fun parseFloat3Range(owner: JsonObject, field: String, defaultValue: Float): CooFxFloat3Range {
        val range = owner.getAsJsonObject(field) ?: return CooFxFloat3Range(
            minimum = CooFxFloat3(defaultValue, defaultValue, defaultValue),
            maximum = CooFxFloat3(defaultValue, defaultValue, defaultValue),
        )
        return CooFxFloat3Range(
            minimum = parseFloat3(range.getAsJsonArray("min"), "$field.min"),
            maximum = parseFloat3(range.getAsJsonArray("max"), "$field.max"),
        )
    }

    private fun parseFloat3(values: JsonArray?, label: String): CooFxFloat3 {
        require(values != null && values.size() == 3) { "$label 必须包含三个分量" }
        return CooFxFloat3(
            values[0].asFloat,
            values[1].asFloat,
            values[2].asFloat,
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
        return when {
            resource.path.endsWith(".glb", ignoreCase = true) -> parseGlb(bytes)
            resource.path.endsWith(".gltf", ignoreCase = true) -> GltfData(parseObject(bytes, "glTF JSON"), null)
            else -> throw IllegalArgumentException("model 只允许引用 .gltf 或 .glb")
        }
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

    private fun decodeOptional(
        attributes: JsonObject,
        semantic: String,
        decoder: GltfAccessorDecoder,
        components: Int,
        vertexCount: Int,
    ): List<Float>? = attributes.get(semantic)?.asInt?.let { accessorIndex ->
        decoder.decode(accessorIndex).also { decoded ->
            require(decoded.componentCount == components) { "$semantic 分量数量不正确" }
            require(decoded.count == vertexCount) { "$semantic accessor count 必须与 POSITION 一致" }
        }.values
    }

    private fun diagnostic(asset: ResourceLocation, model: ResourceLocation, code: String, pointer: String, message: String, objectIndex: Int? = null) =
        CooFxDiagnostic(CooFxDiagnosticSeverity.WARNING, code, asset, pointer, message, model, objectIndex)

    private data class GltfData(val document: JsonObject, val binaryChunk: ByteArray?)

    private data class NormalizedMesh(
        val mesh: CooFxMesh,
        val morphTargetCount: Int,
    )
}

private fun JsonObject.array(name: String): JsonArray = getAsJsonArray(name) ?: JsonArray()
private fun JsonObject.stringOrNull(name: String): String? = get(name)?.takeIf { !it.isJsonNull }?.asString
private fun JsonObject.floatList(name: String): List<Float> = getAsJsonArray(name)?.map { value -> value.asFloat.also { require(it.isFinite()) { "$name 包含非有限数" } } }.orEmpty()
private fun JsonObject.intList(name: String): List<Int> = getAsJsonArray(name)?.map { it.asInt }.orEmpty()
private fun <T> Iterable<T>.getOrNull(index: Int): T? = if (index < 0) null else elementAtOrNull(index)
