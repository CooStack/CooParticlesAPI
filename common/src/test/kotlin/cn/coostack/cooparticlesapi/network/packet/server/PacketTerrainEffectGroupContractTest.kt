package cn.coostack.cooparticlesapi.network.packet.server

import cn.coostack.cooparticlesapi.annotations.packet.CooPacketRegistry
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectGroupSnapshot
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PacketTerrainEffectGroupContractTest {
    @Test
    fun `group packet shares pipeline data and delta encodes positions`() {
        val source = projectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/server/" +
                "PacketTerrainEffectGroupS2C.kt"
        ).readText()

        assertTrue("REPLACE ->" in source)
        assertTrue("APPEND -> writeTimedPositions" in source)
        assertTrue("REMOVE_POSITIONS -> writePositions" in source)
        assertTrue("UPDATE_UNIFORMS -> writeUniforms" in source)
        assertTrue("writeVarLong(packet.revision)" in source)
        assertTrue("writeVarLong(packet.sequence)" in source)
        assertTrue("writeResourceLocation(packet.pipelineId)" in source)
        assertTrue("writeUniforms(buffer, packet.uniforms)" in source)
        assertTrue("val origin = entries.first().key" in source)
        assertTrue("zigZag(position.x - origin.x)" in source)
        assertTrue("zigZag(activation - activationBase)" in source)
        assertFalse("PacketTerrainPropagationS2C" in source)
    }

    @Test
    fun `group packet codec round trips positions and shared uniforms`() {
        CooPacketRegistry.register(PacketTerrainEffectGroupS2C::class.java)
        val positions = linkedMapOf(
            BlockPos(-32, 64, 48) to 100L,
            BlockPos(-31, 65, 49) to 105L,
            BlockPos(128, -32, -96) to 110L
        )
        val source = PacketTerrainEffectGroupS2C.replace(
            CooTerrainEffectGroupSnapshot(
                dimension = ResourceLocation.withDefaultNamespace("overworld"),
                id = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "codec_group"),
                pipelineId = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "codec_pipeline"),
                startedAt = 100L,
                expiresAt = 240L,
                activations = positions,
                uniforms = allUniformValues().mapIndexed { index, value -> "Uniform$index" to value }.toMap(),
                sequence = 7L,
                revision = 11L
            )
        )

        val encoded = CooPacketRegistry.encode(source)
        val decoded = CooPacketRegistry.decode(source.id(), encoded) as PacketTerrainEffectGroupS2C

        assertEquals(source.dimension, decoded.dimension)
        assertEquals(source.groupId, decoded.groupId)
        assertEquals(source.pipelineId, decoded.pipelineId)
        assertEquals(source.startedAt, decoded.startedAt)
        assertEquals(source.expiresAt, decoded.expiresAt)
        assertEquals(source.sequence, decoded.sequence)
        assertEquals(source.revision, decoded.revision)
        assertEquals(source.positions, decoded.positions)
        assertEquals(source.uniforms, decoded.uniforms)

        val removed = setOf(BlockPos(-32, 64, 48), BlockPos(128, -32, -96))
        val removePacket = PacketTerrainEffectGroupS2C.removePositions(
            source.dimension,
            source.groupId,
            12L,
            removed
        )
        val decodedRemove = CooPacketRegistry.decode(
            removePacket.id(),
            CooPacketRegistry.encode(removePacket)
        ) as PacketTerrainEffectGroupS2C
        assertEquals(removed, decodedRemove.positions.keys)
        assertEquals(12L, decodedRemove.revision)
        assertTrue(decodedRemove.uniforms.isEmpty())

        val updatedUniforms = mapOf("Strength" to CooUniformValue.FloatValue(0.25F))
        val uniformPacket = PacketTerrainEffectGroupS2C.updateUniforms(
            source.dimension,
            source.groupId,
            13L,
            updatedUniforms
        )
        val decodedUniforms = CooPacketRegistry.decode(
            uniformPacket.id(),
            CooPacketRegistry.encode(uniformPacket)
        ) as PacketTerrainEffectGroupS2C
        assertEquals(updatedUniforms, decodedUniforms.uniforms)
        assertEquals(13L, decodedUniforms.revision)
        assertTrue(decodedUniforms.positions.isEmpty())
    }

    private fun allUniformValues(): List<CooUniformValue> {
        val values = buildList {
            add(CooUniformValue.BoolValue(true))
            add(CooUniformValue.IntValue(-17))
            add(CooUniformValue.UIntValue(UInt.MAX_VALUE))
            add(CooUniformValue.FloatValue(0.75F))
            add(CooUniformValue.DoubleValue(-123.5))
            add(CooUniformValue.Vec2Value(1F, 2F))
            add(CooUniformValue.Vec3Value(1F, 2F, 3F))
            add(CooUniformValue.Vec4Value(1F, 2F, 3F, 4F))
            for (size in 2..4) {
                add(CooUniformValue.IVecValue(List(size) { it - 2 }))
                add(CooUniformValue.UVecValue(List(size) { it.toUInt() }))
                add(CooUniformValue.BVecValue(List(size) { it % 2 == 0 }))
                add(CooUniformValue.DVecValue(List(size) { it + 0.25 }))
            }
            for (columns in 2..4) {
                for (rows in 2..4) {
                    add(CooUniformValue.MatValue(columns, rows, List(columns * rows) { it + 0.5F }))
                    add(CooUniformValue.DMatValue(columns, rows, List(columns * rows) { it + 0.25 }))
                }
            }
            add(CooUniformValue.SamplerValue(3))
            add(CooUniformValue.ImageValue(5))
        }
        return values + values.map { CooUniformValue.ArrayValue(it, it) }
    }

    private fun projectFile(relativePath: String): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor.resolve(relativePath)
            cursor = cursor.parent
        }
        error("找不到仓库根目录：${System.getProperty("user.dir")}")
    }
}
