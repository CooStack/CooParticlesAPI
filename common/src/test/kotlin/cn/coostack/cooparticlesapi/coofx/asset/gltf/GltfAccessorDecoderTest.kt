package cn.coostack.cooparticlesapi.coofx.asset.gltf

import cn.coostack.cooparticlesapi.coofx.asset.CooFxAssetImporter
import net.minecraft.resources.ResourceLocation
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GltfAccessorDecoderTest {
    @Test
    fun `U32 索引走无损整数读取路径`() {
        val files = files(normalized = false)
        val result = CooFxAssetImporter { resource -> files.getValue(resource) }
            .import(id("coofx/example.coofx.json"))

        assertEquals(listOf(2, 1, 0), result.asset?.meshes?.single()?.primitives?.single()?.indices)
    }

    @Test
    fun `索引 accessor 拒绝 normalized 声明`() {
        val files = files(normalized = true)
        val result = CooFxAssetImporter { resource -> files.getValue(resource) }
            .import(id("coofx/example.coofx.json"))

        assertFalse(result.isSuccess)
    }

    private fun files(normalized: Boolean): Map<ResourceLocation, ByteArray> {
        val asset = id("coofx/example.coofx.json")
        val model = id("coofx/models/triangle.gltf")
        val binary = id("coofx/models/triangle.bin")
        val normalizedField = if (normalized) ",\"normalized\":true" else ""
        val gltf = """{"asset":{"version":"2.0"},"buffers":[{"byteLength":76,"uri":"triangle.bin"}],"bufferViews":[{"buffer":0,"byteLength":36},{"buffer":0,"byteOffset":36,"byteLength":12}],"accessors":[{"bufferView":0,"componentType":5126,"count":3,"type":"VEC3"},{"bufferView":1,"componentType":5125,"count":3,"type":"SCALAR"}],"meshes":[{"primitives":[{"attributes":{"POSITION":0},"indices":1,"mode":4}]}],"nodes":[{"mesh":0}],"scenes":[{"nodes":[0]}],"scene":0}"""
        val coo = """{"${'$'}schema":"cooparticlesapi:coofx/schema/v1","schemaVersion":1,"coordinateSystem":"coofx_rh_y_up_z_south","assetSeed":"0123456789abcdef","model":"models/triangle.gltf","emitters":[],"requiredExtensions":[],"extensions":{}}"""
        val bytes = ByteBuffer.allocate(76).order(ByteOrder.LITTLE_ENDIAN).apply {
            listOf(0F, 0F, 0F, 1F, 0F, 0F, 0F, 1F, 0F).forEach(::putFloat)
            putInt(2).putInt(1).putInt(0)
        }.array()
        val adjusted = if (normalized) gltf.replace("\"type\":\"SCALAR\"", "\"type\":\"SCALAR\"$normalizedField") else gltf
        return mapOf(asset to coo.encodeToByteArray(), model to adjusted.encodeToByteArray(), binary to bytes)
    }

    private fun id(path: String) = ResourceLocation.fromNamespaceAndPath("test", path)
}
