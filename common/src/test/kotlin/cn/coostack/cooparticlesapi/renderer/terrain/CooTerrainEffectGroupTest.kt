package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CooTerrainEffectGroupTest {
    private val pipeline = CooPipelines.block(
        ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "batch_test")
    ) {
        shader(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "terrain/original_texture"))
        uniform("Strength", 0F)
    }

    @Test
    fun `persistent and temporary groups both keep batch positions`() {
        val first = BlockPos(1, 64, 1)
        val second = BlockPos(2, 64, 1)
        val persistent = CooTerrainEffectGroup(
            ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "persistent_batch"),
            pipeline
        ) {
            positions(mapOf(first to 0L, second to 5L))
            uniform("Strength", 0.75F)
        }
        val temporary = CooTerrainEffectGroup(
            ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "temporary_batch"),
            pipeline
        ) {
            positions(listOf(first, second))
            duration(120L)
        }

        assertEquals(mapOf(first to 0L, second to 5L), persistent.definition.activationOffsets)
        assertEquals(CooUniformValue.FloatValue(0.75F), persistent.definition.uniforms.getValue("Strength"))
        assertNull(persistent.definition.durationTicks)
        assertEquals(setOf(first, second), temporary.definition.activationOffsets.keys)
        assertEquals(120L, temporary.definition.durationTicks)
    }
}
