package cn.coostack.cooparticlesapi.coofx.asset

import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxAssetCompiler
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledLoopMode
import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxLightMode
import net.minecraft.resources.ResourceLocation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.joml.Vector3f
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CooFxAssetImporterTest {
    @Test
    fun `分离 gltf 可解析真实三角形和动画`() {
        val files = mutableMapOf<ResourceLocation, ByteArray>()
        val asset = id("coofx/example.coofx.json")
        files[asset] = cooJson("models/triangle.gltf").encodeToByteArray()
        files[id("coofx/models/triangle.gltf")] = gltfJson().encodeToByteArray()
        files[id("coofx/models/triangle.bin")] = triangleBuffer()

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        val imported = assertNotNull(result.asset)
        assertEquals(listOf(0, 1, 2), imported.meshes.single().primitives.single().indices)
        assertEquals(9, imported.meshes.single().primitives.single().positions.size)
        assertEquals(CooFxAnimationPath.TRANSLATION, imported.animations.single().channels.single().path)
    }

    @Test
    fun `Blender 导出的名称引用和数值范围可进入规范资产`() {
        val asset = id("coofx/blender.coofx.json")
        val blenderJson = """{"${'$'}schema":"cooparticlesapi:coofx/schema/v1","schemaVersion":1,"coordinateSystem":"coofx_rh_y_up_z_south","assetSeed":"0123456789abcdef","model":"models/triangle.gltf","emitters":[{"id":"burst","mesh":"TriangleMesh","node":"TriangleNode","count":2,"delayTicks":1,"lifetimeTicks":20,"velocity":{"min":[-0.1,0.2,-0.1],"max":[0.1,0.4,0.1]},"rotationRadians":{"min":[0.0,0.0,0.0],"max":[0.0,3.14,0.0]},"scale":{"min":[0.5,0.5,0.5],"max":[1.5,1.5,1.5]}}],"requiredExtensions":[],"extensions":{}}"""
        val namedGltf = gltfJson()
            .replace("\"meshes\":[{", "\"meshes\":[{\"name\":\"TriangleMesh\",")
            .replace("\"nodes\":[{\"mesh\":0}]", "\"nodes\":[{\"name\":\"TriangleNode\",\"mesh\":0}]")
        val files = mapOf(
            asset to blenderJson.encodeToByteArray(),
            id("coofx/models/triangle.gltf") to namedGltf.encodeToByteArray(),
            id("coofx/models/triangle.bin") to triangleBuffer(),
        )

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        val emitter = assertNotNull(result.asset).emitters.single()
        assertEquals(0, emitter.mesh)
        assertEquals(0, emitter.node)
        assertEquals(-0.1F, emitter.velocity.minimum.x)
        assertEquals(1.5F, emitter.scale.maximum.z)
    }

    @Test
    fun `Blender clip 与 material 覆盖可进入规范资产`() {
        val asset = id("coofx/blender-exporter-contract.coofx.json")
        val cooBytes = requireNotNull(
            javaClass.getResourceAsStream("/coofx/blender-exporter-contract.coofx.json")
        ).use { input -> input.readBytes() }
        val gltf = gltfJson()
            .replace("\"meshes\":[{", "\"meshes\":[{\"name\":\"TriangleMesh\",")
            .replace("\"nodes\":[{\"mesh\":0}]", "\"nodes\":[{\"name\":\"TriangleNode\",\"mesh\":0}]")
            .replace("\"mode\":4", "\"mode\":4,\"material\":0")
            .replace(
                "\"scene\":0,\"animations\"",
                "\"scene\":0,\"materials\":[{\"name\":\"SparkMaterial\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[0.2,0.4,0.6,1.0]}}],\"animations\"",
            )
            .replace("\"animations\":[{", "\"animations\":[{\"name\":\"BurstAnim\",")
        val files = mapOf(
            asset to cooBytes,
            id("coofx/models/triangle.gltf") to gltf.encodeToByteArray(),
            id("coofx/models/triangle.bin") to triangleBuffer(),
        )

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        val imported = assertNotNull(result.asset)
        assertEquals("burst_loop", imported.clips.single().id)
        assertEquals(CooFxClipLoopMode.LOOP, imported.clips.single().loopMode)
        assertEquals(id("textures/coofx/spark.png"), imported.materials.single().baseColorTexture)
        assertEquals(CooFxAlphaMode.MASK, imported.materials.single().alphaMode)
        assertEquals(0.25F, imported.materials.single().alphaCutoff)
        assertTrue(imported.materials.single().doubleSided)
        assertEquals(listOf(0.2F, 0.4F, 0.6F, 1F), imported.materials.single().baseColorFactor)
        val compiled = CooFxAssetCompiler().compile(imported)
        assertEquals("burst_loop", compiled.clips.single().id)
        assertEquals(CooFxCompiledLoopMode.LOOP, compiled.clips.single().loopMode)
        assertEquals(0.2F, compiled.materials.single().baseColorFactor.red)
    }

    @Test
    fun `Blender 实际导出的纯模型资产可直接编译`() {
        val asset = ResourceLocation.fromNamespaceAndPath(
            "cooparticlesapi",
            "coofx/test/test_moudles.coofx.json",
        )
        val importer = CooFxAssetImporter { resource ->
            val classpath = "assets/${resource.namespace}/${resource.path}"
            requireNotNull(javaClass.classLoader.getResourceAsStream(classpath)).use { input -> input.readBytes() }
        }

        val result = importer.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        val imported = assertNotNull(result.asset)
        assertTrue(imported.emitters.isEmpty())
        assertEquals(listOf(0.37031966F, 1F, 0.3294356F), imported.materials.first().emissiveFactor)
        assertEquals(10.999999F, imported.materials.first().emissiveStrength)
        val compiled = CooFxAssetCompiler().compile(imported)
        assertEquals(3, compiled.modelPrimitiveNodeBindings.size)
        val emissiveMaterial = compiled.materials.first()
        assertEquals(0.37031966F, emissiveMaterial.emissiveFactor.red)
        assertEquals(1F, emissiveMaterial.emissiveFactor.green)
        assertEquals(0.3294356F, emissiveMaterial.emissiveFactor.blue)
        assertEquals(10.999999F, emissiveMaterial.emissiveStrength)
        assertEquals(CooFxLightMode.WORLD, emissiveMaterial.lightMode)
    }


    @Test
    fun `people 入口资源可编译静态模型和 Camera`() {
        val asset = ResourceLocation.fromNamespaceAndPath(
            "cooparticlesapi",
            "coofx/people/people.coofx.json",
        )
        val importer = CooFxAssetImporter { resource ->
            val classpath = "assets/${resource.namespace}/${resource.path}"
            requireNotNull(javaClass.classLoader.getResourceAsStream(classpath)).use { input -> input.readBytes() }
        }

        val result = importer.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        val compiled = CooFxAssetCompiler().compile(assertNotNull(result.asset))
        assertTrue(compiled.clips.isEmpty())
        assertTrue(compiled.modelPrimitiveNodeBindings.isNotEmpty())
        assertNotNull(compiled.resolveCamera("Camera"))
    }

    @Test
    fun `camera control fixture preserves clip and moving camera keyframes`() {
        val asset = ResourceLocation.fromNamespaceAndPath(
            "cooparticlesapi",
            "coofx/camera-control/camera-control.coofx.json",
        )
        val importer = CooFxAssetImporter { resource ->
            val classpath = "assets/${resource.namespace}/${resource.path}"
            requireNotNull(javaClass.classLoader.getResourceAsStream(classpath)).use { input -> input.readBytes() }
        }

        val result = importer.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        val imported = assertNotNull(result.asset)
        val animation = imported.animations.single()
        assertEquals("CameraTrack", animation.name)
        val channel = animation.channels.single()
        assertEquals(CooFxAnimationPath.TRANSLATION, channel.path)
        assertTrue(channel.inputSeconds.size >= 2, "camera fixture must contain at least two keyframes")
        assertEquals(listOf(0F, 1F), channel.inputSeconds)
        assertEquals(listOf("camera_track"), imported.clips.map { clip -> clip.id })

        val compiled = CooFxAssetCompiler().compile(imported)
        val camera = compiled.resolveCamera("CameraControl")
        assertNotNull(camera)
        val start = compiled.nodeWorldMatrix(0, camera.nodeIndex, 0F).getTranslation(Vector3f())
        val end = compiled.nodeWorldMatrix(0, camera.nodeIndex, 1F).getTranslation(Vector3f())
        assertNotEquals(start, end, "camera trajectory must change between t=0 and t=1")
    }
    @Test
    fun `KHR emissive strength 扩展可进入规范材质`() {
        val asset = id("coofx/emissive-strength.coofx.json")
        val gltf = gltfJson()
            .replace("\"mode\":4", "\"mode\":4,\"material\":0")
            .replace(
                "\"asset\":{\"version\":\"2.0\"}",
                "\"asset\":{\"version\":\"2.0\"},\"extensionsUsed\":[\"KHR_materials_emissive_strength\"],\"extensionsRequired\":[\"KHR_materials_emissive_strength\"]",
            )
            .replace(
                "\"scene\":0,\"animations\"",
                "\"scene\":0,\"materials\":[{\"emissiveFactor\":[0.25,0.5,1.0],\"extensions\":{\"KHR_materials_emissive_strength\":{\"emissiveStrength\":4.0}}}],\"animations\"",
            )
        val files = mapOf(
            asset to cooJson("models/triangle.gltf").encodeToByteArray(),
            id("coofx/models/triangle.gltf") to gltf.encodeToByteArray(),
            id("coofx/models/triangle.bin") to triangleBuffer(),
        )

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        val material = assertNotNull(result.asset).materials.single()
        assertEquals(listOf(0.25F, 0.5F, 1F), material.emissiveFactor)
        assertEquals(4F, material.emissiveStrength)
        assertFalse(result.asset.extensionMetadata.gltf.containsKey("KHR_materials_emissive_strength"))
        assertFalse(result.diagnostics.any { diagnostic -> diagnostic.code == "gltf.extension.metadata" })
    }

    @Test
    fun `材质纹理声明非零 texCoord 时明确拒绝`() {
        val asset = id("coofx/nonzero-texcoord.coofx.json")
        val gltf = gltfJson()
            .replace(
                "\"asset\":{\"version\":\"2.0\"}",
                "\"asset\":{\"version\":\"2.0\"},\"images\":[{\"uri\":\"emissive.png\"}],\"textures\":[{\"source\":0}]",
            )
            .replace("\"mode\":4", "\"mode\":4,\"material\":0")
            .replace(
                "\"scene\":0,\"animations\"",
                "\"scene\":0,\"materials\":[{\"emissiveTexture\":{\"index\":0,\"texCoord\":1}}],\"animations\"",
            )
        val files = mapOf(
            asset to cooJson("models/triangle.gltf").encodeToByteArray(),
            id("coofx/models/triangle.gltf") to gltf.encodeToByteArray(),
            id("coofx/models/triangle.bin") to triangleBuffer(),
            id("coofx/models/emissive.png") to ByteArray(0),
        )

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertFalse(result.isSuccess)
        assertTrue(result.diagnostics.single().message.contains("texCoord=1"))
    }

    @Test
    fun `glTF 多摄像机描述和 node 绑定会进入 compiled package`() {
        val asset = id("coofx/camera-scene.coofx.json")
        val gltf = gltfJson()
            .replace(
                "\"nodes\":[{\"mesh\":0}],\"scenes\":[{\"nodes\":[0]}]",
                "\"nodes\":[{\"mesh\":0},{\"name\":\"CameraMain\",\"camera\":0,\"translation\":[1.0,2.0,3.0]}],\"scenes\":[{\"nodes\":[0,1]}]",
            )
            .replace(
                "\"scene\":0,\"animations\"",
                "\"scene\":0,\"cameras\":[{\"name\":\"MainLens\",\"type\":\"perspective\",\"perspective\":{\"yfov\":0.7853982,\"znear\":0.1,\"zfar\":512.0}}],\"animations\"",
            )
            .replace(
                "\"node\":0,\"path\":\"translation\"",
                "\"node\":1,\"path\":\"translation\"",
            )
        val files = mapOf(
            asset to cooJson("models/triangle.gltf").encodeToByteArray(),
            id("coofx/models/triangle.gltf") to gltf.encodeToByteArray(),
            id("coofx/models/triangle.bin") to triangleBuffer(),
        )

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        val imported = assertNotNull(result.asset)
        assertEquals(1, imported.cameras.size)
        val camera = imported.cameras.single() as CooFxCamera.Perspective
        assertEquals("MainLens", camera.name)
        assertEquals(0, imported.nodes[1].camera)
        assertTrue(result.diagnostics.none { diagnostic -> diagnostic.code == "gltf.camera.bind_pose" })
        val compiled = CooFxAssetCompiler().compile(imported)
        assertEquals("CameraMain", compiled.cameras.single().id)
        assertEquals(1, compiled.cameras.single().nodeIndex)
        assertEquals(1, compiled.clips.size)
        val start = compiled.nodeWorldMatrix(0, compiled.cameras.single().nodeIndex, 0F).getTranslation(Vector3f())
        val end = compiled.nodeWorldMatrix(0, compiled.cameras.single().nodeIndex, 1F).getTranslation(Vector3f())
        assertNotEquals(start.x, end.x, "camera trajectory must change over one second")
    }

    @Test
    fun `相机没有动画时明确诊断为 bind pose`() {
        val asset = id("coofx/static-camera.coofx.json")
        val staticGltf = gltfJson()
            .substringBefore(",\"animations\"")
            .replace(
                "\"nodes\":[{\"mesh\":0}],\"scenes\":[{\"nodes\":[0]}],\"scene\":0",
                "\"nodes\":[{\"mesh\":0},{\"name\":\"StaticCamera\",\"camera\":0}],\"scenes\":[{\"nodes\":[0,1]}],\"scene\":0,\"cameras\":[{\"name\":\"StaticLens\",\"type\":\"perspective\",\"perspective\":{\"yfov\":0.7853982,\"znear\":0.1,\"zfar\":512.0}}]",
            ) + "}"
        val files = mapOf(
            asset to cooJson("models/static-camera.gltf").encodeToByteArray(),
            id("coofx/models/static-camera.gltf") to staticGltf.encodeToByteArray(),
            id("coofx/models/triangle.bin") to triangleBuffer(),
        )

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        assertTrue(result.diagnostics.any { diagnostic -> diagnostic.code == "gltf.camera.bind_pose" })
        val imported = assertNotNull(result.asset)
        val compiled = CooFxAssetCompiler().compile(imported)
        assertTrue(compiled.clips.isEmpty(), "静态 camera 不应伪造 clip")
        val camera = compiled.cameras.single()
        val bindPose = compiled.nodeWorldMatrix(-1, camera.nodeIndex, 37.5F)
            .getTranslation(Vector3f())
        assertEquals(0F, bindPose.x)
        assertEquals(0F, bindPose.y)
        assertEquals(0F, bindPose.z)
    }

    @Test
    fun `没有 emitter 的纯模型资产会保留全部 scene node 绑定`() {
        val asset = id("coofx/model-only.coofx.json")
        val coo = cooJson("models/triangle.gltf")
            .replace("\"emitters\":[{\"id\":\"burst\",\"mesh\":0,\"count\":2,\"lifetimeTicks\":20}]", "\"emitters\":[]")
        val gltf = gltfJson().replace(
            "\"nodes\":[{\"mesh\":0}],\"scenes\":[{\"nodes\":[0]}]",
            "\"nodes\":[{\"name\":\"First\",\"mesh\":0},{\"name\":\"Second\",\"mesh\":0}],\"scenes\":[{\"nodes\":[0,1]}]",
        )
        val files = mapOf(
            asset to coo.encodeToByteArray(),
            id("coofx/models/triangle.gltf") to gltf.encodeToByteArray(),
            id("coofx/models/triangle.bin") to triangleBuffer(),
        )

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        val imported = assertNotNull(result.asset)
        assertTrue(imported.emitters.isEmpty())
        val compiled = CooFxAssetCompiler().compile(imported)
        assertEquals(2, compiled.modelPrimitiveNodeBindings.size)
        assertEquals(listOf(0, 1), compiled.modelPrimitiveNodeBindings.map { binding -> binding.nodeIndex })
    }

    @Test
    fun `glb 的 BIN chunk 与 JSON chunk 共用解析路径`() {
        val files = mutableMapOf<ResourceLocation, ByteArray>()
        val asset = id("coofx/example.coofx.json")
        files[asset] = cooJson("models/triangle.glb").encodeToByteArray()
        files[id("coofx/models/triangle.glb")] = glb(gltfJson(bufferUri = null), triangleBuffer())

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        assertEquals(3, assertNotNull(result.asset).meshes.single().primitives.single().indices.size)
    }

    @Test
    fun `选定 scene 只保留可达节点网格和动画通道`() {
        val asset = id("coofx/scene.coofx.json")
        val gltf = multiSceneGltf()
        val selectedCoo = cooJson("models/scene.gltf")
            .replace("\"model\":\"models/scene.gltf\"", "\"model\":\"models/scene.gltf\",\"scene\":0")
        val files = mapOf(
            asset to selectedCoo.encodeToByteArray(),
            id("coofx/models/scene.gltf") to gltf.encodeToByteArray(),
            id("coofx/models/triangle.bin") to triangleBuffer(),
        )

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        val imported = assertNotNull(result.asset)
        assertEquals(listOf("SelectedRoot", "SelectedMesh"), imported.nodes.map(CooFxNode::name))
        assertEquals(1, imported.meshes.size)
        assertEquals(listOf(1), imported.animations.single().channels.map(CooFxAnimationChannel::node))
        assertTrue(result.diagnostics.any { it.code == "gltf.animation.outside_scene" })
    }

    @Test
    fun `发射器不能引用选定 scene 之外的节点或网格`() {
        val asset = id("coofx/outside-scene.coofx.json")
        val baseCoo = cooJson("models/scene.gltf")
            .replace("\"model\":\"models/scene.gltf\"", "\"model\":\"models/scene.gltf\",\"scene\":0")
        val cases = listOf(
            baseCoo.replace("\"mesh\":0", "\"mesh\":0,\"node\":2"),
            baseCoo.replace("\"mesh\":0", "\"mesh\":1"),
        )
        cases.forEach { outsideEmitter ->
            val files = mapOf(
                asset to outsideEmitter.encodeToByteArray(),
                id("coofx/models/scene.gltf") to multiSceneGltf().encodeToByteArray(),
                id("coofx/models/triangle.bin") to triangleBuffer(),
            )

            val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

            assertFalse(result.isSuccess)
            assertTrue(result.diagnostics.any { it.message.contains("不属于选定 scene") }, result.diagnostics.toString())
        }
    }

    @Test
    fun `GLB 内嵌 image bufferView 会按源资产边界显式拒绝`() {
        val asset = id("coofx/embedded-image.coofx.json")
        val gltf = gltfJson(bufferUri = null).replace(
            "\"scene\":0,\"animations\"",
            "\"scene\":0,\"images\":[{\"bufferView\":0,\"mimeType\":\"image/png\"}],\"textures\":[{\"source\":0}],\"materials\":[{\"pbrMetallicRoughness\":{\"baseColorTexture\":{\"index\":0}}}],\"animations\"",
        )
        val files = mapOf(
            asset to cooJson("models/embedded-image.glb").encodeToByteArray(),
            id("coofx/models/embedded-image.glb") to glb(gltf, triangleBuffer()),
        )

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertFalse(result.isSuccess)
        assertTrue(result.diagnostics.single().message.contains("image[0] 不支持内嵌 bufferView"))
    }

    @Test
    fun `非 required 扩展元数据会保留并产生诊断`() {
        val asset = id("coofx/extensions.coofx.json")
        val coo = cooJson("models/triangle.gltf").replace(
            "\"extensions\":{}",
            "\"extensions\":{\"cooparticlesapi:blender_export\":{\"unitScale\":1.0}}",
        )
        val gltf = gltfJson().replace(
            "\"asset\":{\"version\":\"2.0\"}",
            "\"asset\":{\"version\":\"2.0\"},\"extensionsUsed\":[\"EXT_demo_metadata\"],\"extensions\":{\"EXT_demo_metadata\":{\"source\":\"fixture\"}}",
        )
        val files = mapOf(
            asset to coo.encodeToByteArray(),
            id("coofx/models/triangle.gltf") to gltf.encodeToByteArray(),
            id("coofx/models/triangle.bin") to triangleBuffer(),
        )

        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)

        assertTrue(result.isSuccess, result.diagnostics.toString())
        val metadata = assertNotNull(result.asset).extensionMetadata
        assertTrue(metadata.cooFx.getValue(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "blender_export")).contains("unitScale"))
        assertTrue(metadata.gltf.getValue("EXT_demo_metadata")?.contains("fixture") == true)
        assertTrue(result.diagnostics.any { it.code == "coofx.extension.metadata" })
        assertTrue(result.diagnostics.any { it.code == "gltf.extension.metadata" })
    }

    @Test
    fun `未知版本 required extension 与路径逃逸均显式失败`() {
        val cases = listOf(
            cooJson("models/triangle.gltf").replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
            cooJson("models/triangle.gltf").replace("\"requiredExtensions\":[]", "\"requiredExtensions\":[\"demo:unknown\"]"),
            cooJson("../triangle.gltf"),
        )
        cases.forEach { json ->
            val asset = id("coofx/example.coofx.json")
            val result = CooFxAssetImporter { json.encodeToByteArray() }.import(asset)
            assertFalse(result.isSuccess)
            assertTrue(result.diagnostics.any { it.severity == CooFxDiagnosticSeverity.ERROR })
        }
    }

    @Test
    fun `非 TRIANGLES primitive 不会被静默导入`() {
        val asset = id("coofx/example.coofx.json")
        val files = mapOf(
            asset to cooJson("models/triangle.gltf").encodeToByteArray(),
            id("coofx/models/triangle.gltf") to gltfJson().replace("\"mode\":4", "\"mode\":5").encodeToByteArray(),
            id("coofx/models/triangle.bin") to triangleBuffer(),
        )
        val result = CooFxAssetImporter { resource -> files.getValue(resource) }.import(asset)
        assertFalse(result.isSuccess)
        assertTrue(result.diagnostics.single().message.contains("TRIANGLES"))
    }

    private fun cooJson(model: String) = """{"${'$'}schema":"cooparticlesapi:coofx/schema/v1","schemaVersion":1,"coordinateSystem":"coofx_rh_y_up_z_south","assetSeed":"0123456789abcdef","model":"$model","emitters":[{"id":"burst","mesh":0,"count":2,"lifetimeTicks":20}],"requiredExtensions":[],"extensions":{}}"""

    private fun gltfJson(bufferUri: String? = "triangle.bin"): String {
        val uri = bufferUri?.let { ",\"uri\":\"$it\"" }.orEmpty()
        return """{"asset":{"version":"2.0"},"buffers":[{"byteLength":76$uri}],"bufferViews":[{"buffer":0,"byteOffset":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":6},{"buffer":0,"byteOffset":44,"byteLength":8},{"buffer":0,"byteOffset":52,"byteLength":24}],"accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},{"bufferView":1,"componentType":5123,"count":3,"type":"SCALAR"},{"bufferView":2,"componentType":5126,"count":2,"type":"SCALAR"},{"bufferView":3,"componentType":5126,"count":2,"type":"VEC3"}],"meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1,"mode":4}]}],"nodes":[{"mesh":0}],"scenes":[{"nodes":[0]}],"scene":0,"animations":[{"samplers":[{"input":2,"output":3,"interpolation":"LINEAR"}],"channels":[{"sampler":0,"target":{"node":0,"path":"translation"}}]}]}"""
    }

    private fun multiSceneGltf(): String {
        val base = gltfJson()
        val mesh = base.substringAfter("\"meshes\":[").substringBefore("],\"nodes\"")
        return base
            .replace("\"meshes\":[$mesh]", "\"meshes\":[$mesh,$mesh]")
            .replace(
                "\"nodes\":[{\"mesh\":0}],\"scenes\":[{\"nodes\":[0]}]",
                "\"nodes\":[{\"name\":\"SelectedRoot\",\"children\":[1]},{\"name\":\"SelectedMesh\",\"mesh\":0},{\"name\":\"OtherSceneMesh\",\"mesh\":1}],\"scenes\":[{\"nodes\":[0]},{\"nodes\":[2]}]",
            )
            .replace(
                "\"channels\":[{\"sampler\":0,\"target\":{\"node\":0,\"path\":\"translation\"}}]",
                "\"channels\":[{\"sampler\":0,\"target\":{\"node\":1,\"path\":\"translation\"}},{\"sampler\":0,\"target\":{\"node\":2,\"path\":\"translation\"}}]",
            )
    }

    private fun triangleBuffer(): ByteArray = ByteBuffer.allocate(76).order(ByteOrder.LITTLE_ENDIAN).apply {
        listOf(0F, 0F, 0F, 1F, 0F, 0F, 0F, 1F, 0F).forEach(::putFloat)
        putShort(0).putShort(1).putShort(2)
        position(44)
        putFloat(0F).putFloat(1F)
        putFloat(0F).putFloat(0F).putFloat(0F)
        putFloat(1F).putFloat(0F).putFloat(0F)
    }.array()

    private fun glb(json: String, binary: ByteArray): ByteArray {
        val jsonBytes = json.encodeToByteArray()
        val paddedJson = jsonBytes + ByteArray((4 - jsonBytes.size % 4) % 4) { 0x20 }
        val paddedBinary = binary + ByteArray((4 - binary.size % 4) % 4)
        return ByteBuffer.allocate(12 + 8 + paddedJson.size + 8 + paddedBinary.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(0x46546c67).putInt(2).putInt(capacity())
            putInt(paddedJson.size).putInt(0x4e4f534a).put(paddedJson)
            putInt(paddedBinary.size).putInt(0x004e4942).put(paddedBinary)
        }.array()
    }

    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("test", path)
}
