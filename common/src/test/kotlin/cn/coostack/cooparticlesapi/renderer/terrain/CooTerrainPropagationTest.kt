package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CooTerrainPropagationTest {
    private val center = BlockPos(0, 64, 0)
    private val dimension = ResourceLocation.withDefaultNamespace("overworld")
    private val groupId = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "test_group")

    @AfterEach
    fun cleanup() {
        CooTerrainEffectRegistry.clear()
    }

    @Test
    fun `propagation timeline runs in one terrain batch shader`() {
        val fragment = projectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/propagation.fsh"
        ).readText()
        val vertex = projectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/block_effect.vsh"
        ).readText()

        assertTrue("flat in float effectElapsedTicks" in fragment)
        assertTrue("smoothstep(0.0, 1.0" in fragment)
        listOf("10.0", "20.0", "30.0", "60.0", "70.0", "80.0", "90.0").forEach {
            assertTrue(it in fragment)
        }
        listOf("100.0", "110.0", "120.0").forEach {
            assertFalse(it in fragment)
        }
        assertTrue("uniform float CooGameTime" in vertex)
        assertTrue("int activationTick" in vertex)
        assertTrue("effectElapsedTicks = mod(" in vertex)
        val propagationSource = projectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPropagation.kt"
        ).readText()
        assertFalse("terrainEffect" in propagationSource)
    }

    @Test
    fun `effect groups append positions and keep shared pipeline`() {
        val pipeline = CooPipelines.block(
            ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "group_pipeline")
        ) {
            shader(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "terrain/original_texture"))
        }
        val snapshot = CooTerrainEffectGroupSnapshot(
            dimension = dimension,
            id = groupId,
            pipelineId = pipeline.id,
            startedAt = 100L,
            expiresAt = null,
            activations = mapOf(center to 100L),
            uniforms = emptyMap(),
            sequence = 1L,
            revision = 1L
        )

        CooTerrainEffectRegistry.install(snapshot)
        assertSame(pipeline, CooTerrainEffectRegistry.groupAt(dimension, center, 100L)?.pipeline)
        assertEquals(setOf(center), CooTerrainEffectRegistry.drainChangedPositions(dimension))

        val late = center.east()
        CooTerrainEffectRegistry.append(dimension, groupId, 2L, mapOf(late to 105L))
        assertNull(CooTerrainEffectRegistry.groupAt(dimension, late, 104L))
        assertSame(pipeline, CooTerrainEffectRegistry.groupAt(dimension, late, 105L)?.pipeline)
        assertEquals(105L, CooTerrainEffectRegistry.activationAt(dimension, late, pipeline, 105L))
        assertEquals(setOf(late), CooTerrainEffectRegistry.drainChangedPositions(dimension))
    }

    @Test
    fun `later group wins and temporary group expires locally`() {
        val first = CooPipelines.block(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "first_group")) {
            shader(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "terrain/original_texture"))
        }
        val second = CooPipelines.block(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "second_group")) {
            shader(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "terrain/original_texture"))
        }
        CooTerrainEffectRegistry.install(
            CooTerrainEffectGroupSnapshot(
                dimension,
                ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "first"),
                first.id,
                100L,
                null,
                mapOf(center to 100L),
                emptyMap(),
                sequence = 1L,
                revision = 1L
            )
        )
        CooTerrainEffectRegistry.install(
            CooTerrainEffectGroupSnapshot(
                dimension,
                ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "second"),
                second.id,
                100L,
                110L,
                mapOf(center to 100L),
                emptyMap(),
                sequence = 2L,
                revision = 1L
            )
        )

        assertSame(second, CooTerrainEffectRegistry.groupAt(dimension, center, 109L)?.pipeline)
        assertEquals(
            listOf(second, first),
            CooTerrainEffectRegistry.groupsAt(dimension, center, 109L).map { it.pipeline }
        )
        CooTerrainEffectRegistry.advance(dimension, 110L)
        assertSame(first, CooTerrainEffectRegistry.groupAt(dimension, center, 110L)?.pipeline)
        assertEquals(setOf(center), CooTerrainEffectRegistry.drainChangedPositions(dimension))
    }

    @Test
    fun `group updates shared uniforms and removes only requested positions`() {
        val pipeline = CooPipelines.block(
            ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "editable_group")
        ) {
            shader(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "terrain/original_texture"))
            uniform("Strength", 1F)
        }
        val east = center.east()
        CooTerrainEffectRegistry.install(
            CooTerrainEffectGroupSnapshot(
                dimension,
                groupId,
                pipeline.id,
                100L,
                null,
                mapOf(center to 100L, east to 100L),
                mapOf("Strength" to CooUniformValue.FloatValue(0.5F)),
                sequence = 1L,
                revision = 1L
            )
        )
        CooTerrainEffectRegistry.drainChangedPositions(dimension)

        CooTerrainEffectRegistry.updateUniforms(
            dimension,
            groupId,
            2L,
            mapOf("Strength" to CooUniformValue.FloatValue(0.25F))
        )
        val updated = CooTerrainEffectRegistry.groupAt(dimension, center, 100L)?.pipeline
        assertEquals(
            CooUniformValue.FloatValue(0.25F),
            updated?.resolveUniform("Strength", Any())
        )
        assertEquals(setOf(center, east), CooTerrainEffectRegistry.drainChangedPositions(dimension))

        CooTerrainEffectRegistry.removePositions(dimension, groupId, 3L, setOf(center))
        assertNull(CooTerrainEffectRegistry.groupAt(dimension, center, 100L))
        assertSame(updated, CooTerrainEffectRegistry.groupAt(dimension, east, 100L)?.pipeline)
        assertEquals(setOf(center), CooTerrainEffectRegistry.drainChangedPositions(dimension))
    }

    @Test
    fun `stale revisions cannot overwrite or resurrect a removed group`() {
        val pipeline = CooPipelines.block(
            ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "revision_group")
        ) {
            shader(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "terrain/original_texture"))
        }
        CooTerrainEffectRegistry.install(
            CooTerrainEffectGroupSnapshot(
                dimension,
                groupId,
                pipeline.id,
                100L,
                null,
                mapOf(center to 100L),
                emptyMap(),
                sequence = 1L,
                revision = 10L
            )
        )

        val late = center.east()
        CooTerrainEffectRegistry.append(dimension, groupId, 11L, mapOf(late to 105L))
        CooTerrainEffectRegistry.append(dimension, groupId, 9L, mapOf(center.west() to 95L))
        assertNull(CooTerrainEffectRegistry.groupAt(dimension, center.west(), 110L))

        CooTerrainEffectRegistry.remove(dimension, groupId, 12L)
        CooTerrainEffectRegistry.append(dimension, groupId, 11L, mapOf(center.above() to 110L))
        assertNull(CooTerrainEffectRegistry.groupAt(dimension, center, 110L))
        assertNull(CooTerrainEffectRegistry.groupAt(dimension, center.above(), 110L))
    }

    @Test
    fun `newer no-op revision still rejects older mutations`() {
        val pipeline = CooPipelines.block(
            ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "no_op_revision_group")
        ) {
            shader(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "terrain/original_texture"))
        }
        CooTerrainEffectRegistry.install(
            CooTerrainEffectGroupSnapshot(
                dimension,
                groupId,
                pipeline.id,
                100L,
                null,
                mapOf(center to 100L),
                emptyMap(),
                sequence = 1L,
                revision = 1L
            )
        )

        CooTerrainEffectRegistry.append(dimension, groupId, 3L, mapOf(center to 100L))
        CooTerrainEffectRegistry.removePositions(dimension, groupId, 2L, setOf(center))
        assertSame(pipeline, CooTerrainEffectRegistry.groupAt(dimension, center, 100L)?.pipeline)

        CooTerrainEffectRegistry.updateUniforms(dimension, groupId, 5L, emptyMap())
        CooTerrainEffectRegistry.remove(dimension, groupId, 4L)
        assertSame(pipeline, CooTerrainEffectRegistry.groupAt(dimension, center, 100L)?.pipeline)
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
