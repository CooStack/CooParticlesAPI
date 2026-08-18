package cn.coostack.cooparticlesapi.coofx.render.compiled

import cn.coostack.cooparticlesapi.coofx.asset.CooFxAlphaMode as SourceAlphaMode
import cn.coostack.cooparticlesapi.coofx.asset.CooFxAnimationChannel
import cn.coostack.cooparticlesapi.coofx.asset.CooFxAnimationPath
import cn.coostack.cooparticlesapi.coofx.asset.CooFxCamera
import cn.coostack.cooparticlesapi.coofx.asset.CooFxClipLoopMode
import cn.coostack.cooparticlesapi.coofx.asset.CooFxFloat3Range
import cn.coostack.cooparticlesapi.coofx.asset.CooFxInterpolation
import cn.coostack.cooparticlesapi.coofx.asset.CooFxMaterial
import cn.coostack.cooparticlesapi.coofx.asset.CooFxMeshPrimitive
import cn.coostack.cooparticlesapi.coofx.asset.CooFxSourceAsset
import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxLocalPose
import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxPoseNode
import cn.coostack.cooparticlesapi.coofx.playback.pose.CooFxTransformTrack
import cn.coostack.cooparticlesapi.coofx.playback.track.CooFxTrack
import cn.coostack.cooparticlesapi.coofx.playback.track.CooFxTrackInterpolation
import cn.coostack.cooparticlesapi.coofx.render.batch.CooFxBatchTemplate
import cn.coostack.cooparticlesapi.coofx.render.batch.CooFxMeshBatchKey
import cn.coostack.cooparticlesapi.coofx.render.instance.CooFxMeshInstanceLayout
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * CooFX 首版 CPU 编译器。
 *
 * 编译器只接收导入后的不可变 source asset，并生成不含 GL handle 的 compiled package。
 * 首版只执行刚性节点、OPAQUE/MASK 材质和三角形 mesh；任何 morph、skin、VAT 或 BLEND
 * 能力都会在此边界明确拒绝，避免把元数据占位误报成运行时支持。
 */
class CooFxAssetCompiler {
    /** 把一个 source asset 编译成可供渲染线程上传的 CPU 包。 */
    fun compile(source: CooFxSourceAsset): CooFxCompiledRenderPackage {
        require(source.schemaVersion == 1) { "只支持 CooFX schemaVersion 1" }
        require(source.deformationMetadata.skinCount == 0) { "首版 compiler 不执行 skin" }
        require(source.deformationMetadata.morphTargetCount == 0) { "首版 compiler 不执行 morph" }
        require(!source.deformationMetadata.hasAnimatedWeights) { "首版 compiler 不执行 weights 动画" }
        require(source.deformationMetadata.vatExtensionIds.isEmpty()) { "首版 compiler 不执行 VAT" }
        require(source.meshes.isNotEmpty()) { "CooFX asset 必须包含 mesh" }

        val topology = topology(source)
        val needsDefaultMaterial = source.materials.isEmpty() || source.meshes.any { mesh ->
            mesh.primitives.any { primitive -> primitive.material == null }
        }
        val sourceMaterials = buildList {
            addAll(source.materials)
            if (needsDefaultMaterial) {
                add(
                    CooFxMaterial(
                        name = "coofx_default",
                        baseColorFactor = listOf(1F, 1F, 1F, 1F),
                        baseColorTexture = null,
                        alphaMode = SourceAlphaMode.OPAQUE,
                        alphaCutoff = 0.5F,
                        doubleSided = false,
                        emissiveFactor = listOf(0F, 0F, 0F),
                        emissiveTexture = null,
                        emissiveStrength = 1F,
                    )
                )
            }
        }
        val defaultMaterialIndex = sourceMaterials.lastIndex.takeIf { needsDefaultMaterial }
        val materials = sourceMaterials.mapIndexed { index, material ->
            require(material.alphaMode != SourceAlphaMode.BLEND) { "material[$index] 的 BLEND 首版不可执行" }
            val alphaMode = when (material.alphaMode) {
                SourceAlphaMode.OPAQUE -> CooFxAlphaMode.OPAQUE
                SourceAlphaMode.MASK -> CooFxAlphaMode.MASK
                SourceAlphaMode.BLEND -> error("BLEND 已在前置校验拒绝")
            }
            CooFxCompiledMaterial(
                id = "material_$index",
                baseColorTexture = material.baseColorTexture,
                baseColorFactor = CooFxColor4(
                    red = material.baseColorFactor[0],
                    green = material.baseColorFactor[1],
                    blue = material.baseColorFactor[2],
                    alpha = material.baseColorFactor[3],
                ),
                alphaMode = alphaMode,
                alphaCutoff = if (alphaMode == CooFxAlphaMode.MASK) material.alphaCutoff else 0F,
                cullMode = if (material.doubleSided) CooFxCullMode.NONE else CooFxCullMode.BACK,
                depthTest = CooFxDepthTest.LESS_OR_EQUAL,
                depthWrite = true,
                blendMode = CooFxBlendMode.DISABLED,
                lightMode = CooFxLightMode.WORLD,
                emissiveTexture = material.emissiveTexture,
                emissiveFactor = CooFxColor3(
                    red = material.emissiveFactor[0],
                    green = material.emissiveFactor[1],
                    blue = material.emissiveFactor[2],
                ),
                emissiveStrength = material.emissiveStrength,
            )
        }
        val poseNodes = topology.order.mapIndexed { newIndex, oldIndex ->
            val node = source.nodes[oldIndex]
            CooFxPoseNode(
                parentIndex = topology.parents[newIndex],
                bindPose = CooFxLocalPose(
                    translation = Vector3f(node.translation[0], node.translation[1], node.translation[2]),
                    rotation = Quaternionf(node.rotation[0], node.rotation[1], node.rotation[2], node.rotation[3]),
                    scale = Vector3f(node.scale[0], node.scale[1], node.scale[2]),
                ),
                bindMatrix = node.matrix?.let { values -> Matrix4f().set(values.toFloatArray()) },
            )
        }
        val primitives = mutableListOf<CooFxCompiledPrimitive>()
        val primitivesByMesh = List(source.meshes.size) { mutableListOf<Int>() }
        source.meshes.forEachIndexed { meshIndex, mesh ->
            mesh.primitives.forEachIndexed { primitiveIndex, primitive ->
                require(primitive.morphTargetSemantics.isEmpty()) { "mesh[$meshIndex].primitive[$primitiveIndex] 包含未执行 morph" }
                val materialIndex = primitive.material ?: requireNotNull(defaultMaterialIndex)
                require(materialIndex in materials.indices) { "primitive material 索引越界" }
                val compiled = compilePrimitive(source, meshIndex, primitiveIndex, primitive, materialIndex, topology)
                primitivesByMesh[meshIndex] += primitives.size
                primitives += compiled
            }
        }
        require(primitives.isNotEmpty()) { "CooFX asset 必须包含 primitive" }
        val modelPrimitiveNodeBindings = topology.order.flatMapIndexed { nodeIndex, sourceNodeIndex ->
            val meshIndex = source.nodes[sourceNodeIndex].mesh ?: return@flatMapIndexed emptyList()
            primitivesByMesh[meshIndex].map { primitiveIndex ->
                CooFxPrimitiveNodeBinding(primitiveIndex, nodeIndex)
            }
        }
        require(modelPrimitiveNodeBindings.isNotEmpty()) { "CooFX scene 必须包含至少一个 mesh node" }
        val usedCameraIds = mutableSetOf<String>()
        val cameras = topology.order.mapIndexedNotNull { nodeIndex, sourceNodeIndex ->
            val sourceNode = source.nodes[sourceNodeIndex]
            val sourceCamera = sourceNode.camera?.let { cameraIndex -> source.cameras[cameraIndex] }
                ?: return@mapIndexedNotNull null
            val baseId = sourceNode.name?.takeIf { value -> value.isNotBlank() }
                ?: sourceCamera.name?.takeIf { value -> value.isNotBlank() }
                ?: sourceCamera.id
            val cameraId = if (usedCameraIds.add(baseId)) baseId else "$baseId@$nodeIndex"
            usedCameraIds += cameraId
            CooFxCompiledCamera(
                id = cameraId,
                name = sourceCamera.name,
                nodeIndex = nodeIndex,
                projection = when (sourceCamera) {
                    is CooFxCamera.Perspective -> CooFxCompiledCameraProjection.Perspective(
                        yfovRadians = sourceCamera.yfovRadians,
                        aspectRatio = sourceCamera.aspectRatio,
                        znear = sourceCamera.znear,
                        zfar = sourceCamera.zfar,
                    )
                    is CooFxCamera.Orthographic -> CooFxCompiledCameraProjection.Orthographic(
                        xmag = sourceCamera.xmag,
                        ymag = sourceCamera.ymag,
                        znear = sourceCamera.znear,
                        zfar = sourceCamera.zfar,
                    )
                },
            )
        }

        val clipDefinitions = if (source.clips.isEmpty()) {
            source.animations.indices.map { animationIndex ->
                val animation = source.animations[animationIndex]
                Triple(
                    animation.name?.takeIf { it.isNotBlank() } ?: "clip_$animationIndex",
                    animationIndex,
                    CooFxCompiledLoopMode.ONCE,
                )
            }
        } else {
            source.clips.map { clip ->
                val loopMode = when (clip.loopMode) {
                    CooFxClipLoopMode.ONCE -> CooFxCompiledLoopMode.ONCE
                    CooFxClipLoopMode.LOOP -> CooFxCompiledLoopMode.LOOP
                    CooFxClipLoopMode.PING_PONG -> CooFxCompiledLoopMode.PING_PONG
                }
                Triple(clip.id, clip.animation, loopMode)
            }
        }
        val clips = clipDefinitions.map { (clipId, animationIndex, loopMode) ->
            val animation = source.animations[animationIndex]
            val channelsByNode = animation.channels.groupBy { channel -> topology.remap[channel.node] }
            val tracks = channelsByNode.entries.sortedBy(Map.Entry<Int, *>::key).map { (nodeIndex, channels) ->
                val sourceNodeIndex = topology.order[nodeIndex]
                require(source.nodes[sourceNodeIndex].matrix == null) {
                    "animation[$animationIndex] 不能以 matrix node[$sourceNodeIndex] 为目标"
                }
                require(channels.map { channel -> channel.path }.distinct().size == channels.size) {
                    "animation[$animationIndex] 的 node[$sourceNodeIndex] 包含重复 transform path"
                }
                CooFxTransformTrack(
                    nodeIndex = nodeIndex,
                    translation = channels.singleOrNull { it.path == CooFxAnimationPath.TRANSLATION }?.let(::compileTrack),
                    rotation = channels.singleOrNull { it.path == CooFxAnimationPath.ROTATION }?.let(::compileTrack),
                    scale = channels.singleOrNull { it.path == CooFxAnimationPath.SCALE }?.let(::compileTrack),
                )
            }
            CooFxCompiledClipMetadata(
                id = clipId,
                durationSeconds = animation.channels.flatMap { it.inputSeconds }.maxOrNull()
                    ?: error("动画没有关键帧"),
                animatedNodeIndices = tracks.map(CooFxTransformTrack::nodeIndex),
                loopMode = loopMode,
                transformTracks = tracks,
            )
        }
        val emitters = source.emitters.map { emitter ->
            val sourceNodeIndex = emitter.node ?: run {
                val meshReference = emitter.mesh ?: error("emitter ${emitter.id} 必须声明 node 或 mesh")
                val matches = source.nodes.indices.filter { nodeIndex -> source.nodes[nodeIndex].mesh == meshReference }
                require(matches.size == 1) {
                    "emitter ${emitter.id} 的 mesh 必须恰好由一个节点引用；请显式声明 node"
                }
                matches.single()
            }
            val nodeMeshIndex = source.nodes[sourceNodeIndex].mesh
                ?: error("emitter ${emitter.id} 的 node 没有 mesh")
            require(emitter.mesh == null || emitter.mesh == nodeMeshIndex) {
                "emitter ${emitter.id} 的 node 与 mesh 引用不一致"
            }
            require(nodeMeshIndex in primitivesByMesh.indices && primitivesByMesh[nodeMeshIndex].isNotEmpty()) {
                "emitter ${emitter.id} 引用了没有 primitive 的 mesh"
            }
            require(emitter.count >= 0 && emitter.delayTicks >= 0 && emitter.lifetimeTicks > 0) {
                "emitter ${emitter.id} 的生命周期参数非法"
            }
            val primitiveIndices = primitivesByMesh[nodeMeshIndex].toList()
            val nodeIndex = topology.remap[sourceNodeIndex]
            CooFxCompiledEmitter(
                id = emitter.id,
                primitiveIndex = primitiveIndices.first(),
                defaultClipIndex = if (clips.isEmpty()) null else 0,
                primitiveIndices = primitiveIndices,
                primitiveNodeBindings = primitiveIndices.map { primitiveIndex ->
                    CooFxPrimitiveNodeBinding(primitiveIndex, nodeIndex)
                },
            )
        }
        val batchTemplates = primitives.mapIndexed { primitiveIndex, primitive ->
            val material = materials[primitive.materialIndex]
            val key = CooFxMeshBatchKey(
                generation = 1L,
                pipelineId = ResourceLocation.fromNamespaceAndPath(source.resource.namespace, "coofx/world"),
                worldNodeId = "world",
                primitiveId = primitive.id,
                vertexLayoutVersion = primitive.vertexLayout.version,
                indexType = primitive.indexType,
                materialId = material.id,
                baseColorTexture = material.baseColorTexture,
                samplerKey = "default",
                shaderVariant = "coofx_mesh",
                deformationMode = CooFxDeformationMode.RIGID,
                alphaMode = material.alphaMode,
                alphaCutoffBucket = (material.alphaCutoff * 255F).toInt().coerceIn(0, 255),
                cullMode = material.cullMode,
                depthTest = material.depthTest,
                depthWrite = material.depthWrite,
                blendMode = material.blendMode,
                lightMode = material.lightMode,
                backendCapabilitySignature = "gl32",
                instanceLayoutVersion = CooFxMeshInstanceLayout.VERSION,
            )
            CooFxBatchTemplate(key, primitiveIndex, primitive.materialIndex)
        }
        val digest = digest(source, primitives, materials, clips, emitters)
        return CooFxCompiledRenderPackage(
            id = source.resource,
            sourceSchemaVersion = source.schemaVersion,
            compilerVersion = "coofx-compiler-v3",
            contentDigest = digest,
            nodeParents = topology.parents,
            topologicalNodeOrder = topology.order.map { oldIndex -> topology.remap[oldIndex] },
            poseNodes = poseNodes,
            clips = clips,
            primitives = primitives,
            materials = materials,
            emitters = emitters,
            batchTemplates = batchTemplates,
            warnings = emptyList(),
            modelPrimitiveNodeBindings = modelPrimitiveNodeBindings,
            cameras = cameras,
        )
    }

    private fun compileTrack(channel: CooFxAnimationChannel): CooFxTrack = CooFxTrack(
        timesSeconds = channel.inputSeconds.toFloatArray(),
        keyframeData = channel.outputValues.toFloatArray(),
        componentCount = channel.outputComponentCount,
        interpolation = when (channel.interpolation) {
            CooFxInterpolation.STEP -> CooFxTrackInterpolation.STEP
            CooFxInterpolation.LINEAR -> CooFxTrackInterpolation.LINEAR
            CooFxInterpolation.CUBICSPLINE -> CooFxTrackInterpolation.CUBICSPLINE
        },
        quaternion = channel.path == CooFxAnimationPath.ROTATION,
    )

    private fun compilePrimitive(
        source: CooFxSourceAsset,
        meshIndex: Int,
        primitiveIndex: Int,
        primitive: CooFxMeshPrimitive,
        materialIndex: Int,
        topology: Topology,
    ): CooFxCompiledPrimitive {
        val vertexCount = primitive.positions.size / 3
        require(vertexCount > 0 && primitive.positions.size % 3 == 0) { "POSITION 数据布局非法" }
        val hasNormals = primitive.normals != null
        val hasTexCoords = primitive.texCoords != null
        val hasColors = primitive.colors != null
        val attributes = buildList {
            add(CooFxVertexAttribute(CooFxVertexSemantic.POSITION, CooFxVertexComponentType.FLOAT, 3, 0))
            var offset = 12
            if (hasNormals) {
                add(CooFxVertexAttribute(CooFxVertexSemantic.NORMAL, CooFxVertexComponentType.FLOAT, 3, offset))
                offset += 12
            }
            if (hasTexCoords) {
                add(CooFxVertexAttribute(CooFxVertexSemantic.TEXCOORD_0, CooFxVertexComponentType.FLOAT, 2, offset))
                offset += 8
            }
            if (hasColors) add(CooFxVertexAttribute(CooFxVertexSemantic.COLOR_0, CooFxVertexComponentType.FLOAT, primitive.colors!!.size / vertexCount, offset))
        }
        val stride = attributes.maxOf { it.byteOffset + it.componentType.byteSize * it.componentCount }
        val vertices = ByteBuffer.allocate(vertexCount * stride).order(ByteOrder.LITTLE_ENDIAN)
        repeat(vertexCount) { vertex ->
            putComponents(vertices, primitive.positions, vertex * 3, 3)
            if (hasNormals) putComponents(vertices, primitive.normals!!, vertex * 3, 3)
            if (hasTexCoords) putComponents(vertices, primitive.texCoords!!, vertex * 2, 2)
            if (hasColors) putComponents(vertices, primitive.colors!!, vertex * (primitive.colors!!.size / vertexCount), primitive.colors!!.size / vertexCount)
        }
        val maxIndex = primitive.indices.maxOrNull() ?: error("primitive 没有索引")
        val indexType = if (maxIndex <= 65535) CooFxIndexType.UNSIGNED_SHORT else CooFxIndexType.UNSIGNED_INT
        val indexBytes = ByteBuffer.allocate(primitive.indices.size * indexType.byteSize).order(ByteOrder.LITTLE_ENDIAN)
        primitive.indices.forEach { index -> if (indexType == CooFxIndexType.UNSIGNED_SHORT) indexBytes.putShort(index.toShort()) else indexBytes.putInt(index) }
        return CooFxCompiledPrimitive(
            id = "mesh_${meshIndex}_primitive_$primitiveIndex",
            vertexLayout = CooFxVertexLayout(1, stride, attributes),
            vertexBytes = vertices.array(),
            indexType = indexType,
            indexBytes = indexBytes.array(),
            drawRange = CooFxDrawRange(0, primitive.indices.size),
            materialIndex = materialIndex,
            deformationPlan = CooFxDeformationPlan(CooFxDeformationMode.RIGID, topology.nodeForMesh[meshIndex]),
        )
    }

    private fun putComponents(buffer: ByteBuffer, values: List<Float>, offset: Int, count: Int) {
        repeat(count) { component -> buffer.putFloat(values[offset + component]) }
    }

    private fun topology(source: CooFxSourceAsset): Topology {
        val parents = IntArray(source.nodes.size) { -1 }
        source.nodes.forEachIndexed { index, node ->
            node.children.forEach { child ->
                require(child in source.nodes.indices) { "node[$index] 的 child 索引越界：$child" }
                require(child != index) { "node[$index] 不能把自身声明为 child" }
                require(parents[child] == -1) { "节点不能拥有多个父节点" }
                parents[child] = index
            }
        }
        val order = mutableListOf<Int>()
        val visitStates = ByteArray(source.nodes.size)
        fun visit(index: Int) {
            when (visitStates[index].toInt()) {
                1 -> error("节点拓扑包含循环，涉及 node[$index]")
                2 -> return
            }
            visitStates[index] = 1
            parents[index].takeIf { it >= 0 }?.let(::visit)
            visitStates[index] = 2
            order += index
        }
        source.nodes.indices.forEach(::visit)
        val remap = IntArray(source.nodes.size)
        order.forEachIndexed { newIndex, oldIndex -> remap[oldIndex] = newIndex }
        val remappedParents = IntArray(order.size) { newIndex -> parents[order[newIndex]].takeIf { it >= 0 }?.let { remap[it] } ?: -1 }
        val nodeForMesh = IntArray(source.meshes.size) { meshIndex -> source.nodes.indexOfFirst { it.mesh == meshIndex }.let { if (it < 0) 0 else remap[it] } }
        return Topology(order.toList(), remappedParents.toList(), remap, nodeForMesh)
    }

    private fun digest(
        source: CooFxSourceAsset,
        primitives: List<CooFxCompiledPrimitive>,
        materials: List<CooFxCompiledMaterial>,
        clips: List<CooFxCompiledClipMetadata>,
        emitters: List<CooFxCompiledEmitter>,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.updateString("coofx-compiler-v3")
        digest.updateString(source.resource.toString())
        digest.updateInt(source.schemaVersion)
        digest.updateLong(source.assetSeed.toLong())
        digest.updateString(source.modelResource.toString())
        digest.updateInt(source.scene)
        source.nodes.forEach { node ->
            digest.updateNullableString(node.name)
            digest.updateIntList(node.children)
            digest.updateNullableInt(node.mesh)
            digest.updateNullableInt(node.skin)
            digest.updateNullableInt(node.camera)
            digest.updateNullableFloatList(node.matrix)
            digest.updateFloatList(node.translation)
            digest.updateFloatList(node.rotation)
            digest.updateFloatList(node.scale)
        }
        source.cameras.forEach { camera ->
            digest.updateString(camera.id)
            digest.updateNullableString(camera.name)
            when (camera) {
                is CooFxCamera.Perspective -> {
                    digest.updateString("perspective")
                    digest.updateFloat(camera.yfovRadians)
                    digest.updateNullableFloat(camera.aspectRatio)
                    digest.updateFloat(camera.znear)
                    digest.updateNullableFloat(camera.zfar)
                }
                is CooFxCamera.Orthographic -> {
                    digest.updateString("orthographic")
                    digest.updateFloat(camera.xmag)
                    digest.updateFloat(camera.ymag)
                    digest.updateFloat(camera.znear)
                    digest.updateFloat(camera.zfar)
                }
            }
        }
        source.meshes.forEach { mesh ->
            digest.updateNullableString(mesh.name)
            digest.updateFloatList(mesh.weights)
            mesh.primitives.forEach { primitive ->
                digest.updateFloatList(primitive.positions)
                digest.updateNullableFloatList(primitive.normals)
                digest.updateNullableFloatList(primitive.texCoords)
                digest.updateNullableFloatList(primitive.colors)
                digest.updateIntList(primitive.indices)
                digest.updateNullableInt(primitive.material)
                digest.updateInt(primitive.morphTargetSemantics.size)
                primitive.morphTargetSemantics.forEach { semantics ->
                    digest.updateStringList(semantics.sorted())
                }
            }
        }
        source.materials.forEach { material ->
            digest.updateNullableString(material.name)
            digest.updateFloatList(material.baseColorFactor)
            digest.updateNullableString(material.baseColorTexture?.toString())
            digest.updateString(material.alphaMode.name)
            digest.updateFloat(material.alphaCutoff)
            digest.updateBoolean(material.doubleSided)
            digest.updateFloatList(material.emissiveFactor)
            digest.updateNullableString(material.emissiveTexture?.toString())
            digest.updateFloat(material.emissiveStrength)
        }
        source.animations.forEach { animation ->
            digest.updateNullableString(animation.name)
            animation.channels.forEach { channel ->
                digest.updateInt(channel.node)
                digest.updateString(channel.path.name)
                digest.updateString(channel.interpolation.name)
                digest.updateFloatList(channel.inputSeconds)
                digest.updateFloatList(channel.outputValues)
                digest.updateInt(channel.outputComponentCount)
            }
        }
        source.clips.forEach { clip ->
            digest.updateString(clip.id)
            digest.updateInt(clip.animation)
            digest.updateString(clip.loopMode.name)
        }
        source.emitters.forEach { emitter ->
            digest.updateString(emitter.id)
            digest.updateNullableInt(emitter.node)
            digest.updateNullableInt(emitter.mesh)
            digest.updateInt(emitter.count)
            digest.updateInt(emitter.delayTicks)
            digest.updateInt(emitter.lifetimeTicks)
            digest.updateFloat3Range(emitter.velocity)
            digest.updateFloat3Range(emitter.rotationRadians)
            digest.updateFloat3Range(emitter.scale)
        }
        digest.updateInt(source.deformationMetadata.skinCount)
        digest.updateInt(source.deformationMetadata.morphTargetCount)
        digest.updateBoolean(source.deformationMetadata.hasAnimatedWeights)
        digest.updateStringList(source.deformationMetadata.vatExtensionIds.map(ResourceLocation::toString).sorted())

        primitives.forEach { primitive ->
            digest.updateString(primitive.id)
            digest.updateInt(primitive.vertexLayout.version)
            digest.updateInt(primitive.vertexLayout.strideBytes)
            primitive.vertexLayout.attributes.forEach { attribute ->
                digest.updateString(attribute.semantic.name)
                digest.updateString(attribute.componentType.name)
                digest.updateInt(attribute.componentCount)
                digest.updateInt(attribute.byteOffset)
            }
            digest.updateBytes(primitive.vertexBytes.copyBytes())
            digest.updateString(primitive.indexType.name)
            digest.updateBytes(primitive.indexBytes.copyBytes())
            digest.updateInt(primitive.drawRange.firstIndex)
            digest.updateInt(primitive.drawRange.indexCount)
            digest.updateInt(primitive.drawRange.baseVertex)
            digest.updateInt(primitive.materialIndex)
            digest.updateString(primitive.deformationPlan.mode.name)
            digest.updateInt(primitive.deformationPlan.primitiveNodeIndex)
            digest.updateNullableString(primitive.deformationPlan.diagnostic)
        }
        materials.forEach { material ->
            digest.updateString(material.id)
            digest.updateNullableString(material.baseColorTexture?.toString())
            digest.updateFloat(material.baseColorFactor.red)
            digest.updateFloat(material.baseColorFactor.green)
            digest.updateFloat(material.baseColorFactor.blue)
            digest.updateFloat(material.baseColorFactor.alpha)
            digest.updateNullableString(material.emissiveTexture?.toString())
            digest.updateFloat(material.emissiveFactor.red)
            digest.updateFloat(material.emissiveFactor.green)
            digest.updateFloat(material.emissiveFactor.blue)
            digest.updateFloat(material.emissiveStrength)
            digest.updateString(material.alphaMode.name)
            digest.updateFloat(material.alphaCutoff)
            digest.updateString(material.cullMode.name)
            digest.updateString(material.depthTest.name)
            digest.updateBoolean(material.depthWrite)
            digest.updateString(material.blendMode.name)
            digest.updateString(material.lightMode.name)
        }
        clips.forEach { clip ->
            digest.updateString(clip.id)
            digest.updateFloat(clip.durationSeconds)
            digest.updateIntList(clip.animatedNodeIndices)
            digest.updateString(clip.loopMode.name)
        }
        emitters.forEach { emitter ->
            digest.updateString(emitter.id)
            digest.updateInt(emitter.primitiveIndex)
            digest.updateIntList(emitter.primitiveIndices)
            digest.updateNullableInt(emitter.defaultClipIndex)
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun MessageDigest.updateBytes(value: ByteArray) {
        updateInt(value.size)
        update(value)
    }

    private fun MessageDigest.updateString(value: String) {
        updateBytes(value.encodeToByteArray())
    }

    private fun MessageDigest.updateNullableString(value: String?) {
        updateBoolean(value != null)
        value?.let { updateString(it) }
    }

    private fun MessageDigest.updateBoolean(value: Boolean) {
        update((if (value) 1 else 0).toByte())
    }

    private fun MessageDigest.updateInt(value: Int) {
        update(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putInt(value).array())
    }

    private fun MessageDigest.updateLong(value: Long) {
        update(ByteBuffer.allocate(Long.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).array())
    }

    private fun MessageDigest.updateFloat(value: Float) {
        updateInt(value.toRawBits())
    }

    private fun MessageDigest.updateNullableInt(value: Int?) {
        updateBoolean(value != null)
        value?.let { updateInt(it) }
    }

    private fun MessageDigest.updateNullableFloat(value: Float?) {
        updateBoolean(value != null)
        value?.let { updateFloat(it) }
    }

    private fun MessageDigest.updateFloatList(values: List<Float>) {
        updateInt(values.size)
        values.forEach { updateFloat(it) }
    }

    private fun MessageDigest.updateNullableFloatList(values: List<Float>?) {
        updateBoolean(values != null)
        values?.let { updateFloatList(it) }
    }

    private fun MessageDigest.updateIntList(values: List<Int>) {
        updateInt(values.size)
        values.forEach { updateInt(it) }
    }

    private fun MessageDigest.updateStringList(values: List<String>) {
        updateInt(values.size)
        values.forEach { updateString(it) }
    }

    private fun MessageDigest.updateFloat3Range(range: CooFxFloat3Range) {
        updateFloat(range.minimum.x)
        updateFloat(range.minimum.y)
        updateFloat(range.minimum.z)
        updateFloat(range.maximum.x)
        updateFloat(range.maximum.y)
        updateFloat(range.maximum.z)
    }

    private data class Topology(
        val order: List<Int>,
        val parents: List<Int>,
        val remap: IntArray,
        val nodeForMesh: IntArray,
    )
}
