package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

/** 验证 Mapping 渲染计划不会退化为首个实例，并保持组合顺序。 */
class CooTerrainMappingCompositionTest {
    @Test
    fun `render plan keeps deterministic order and suppresses later replace layers`() {
        val dimension = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
        CooTerrainMappingRegistry.clear()
        CooTerrainMappingRegistry.install(instance("low", 1, 5, CooTerrainEffectComposition.REPLACE, dimension))
        CooTerrainMappingRegistry.install(instance("alpha", 3, 7, CooTerrainEffectComposition.ALPHA_OVER, dimension))
        CooTerrainMappingRegistry.install(instance("add", 2, 7, CooTerrainEffectComposition.ADDITIVE, dimension))
        CooTerrainMappingRegistry.install(instance("later", 0, 6, CooTerrainEffectComposition.REPLACE, dimension))

        val plan = CooTerrainMappingRegistry.activeRenderPlan(dimension, 10L)

        assertEquals(listOf("alpha", "add", "low"), plan.map { it.instanceId.path })
    }

    @Test
    fun `render plan keeps all compositional layers when no replace exists`() {
        val dimension = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
        CooTerrainMappingRegistry.clear()
        CooTerrainMappingRegistry.install(instance("alpha", 1, 1, CooTerrainEffectComposition.ALPHA_OVER, dimension))
        CooTerrainMappingRegistry.install(instance("add", 1, 2, CooTerrainEffectComposition.ADDITIVE, dimension))

        val plan = CooTerrainMappingRegistry.activeRenderPlan(dimension, 10L)

        assertEquals(listOf("add", "alpha"), plan.map { it.instanceId.path })
    }

    @Test
    fun `paused mappings are absent from the render plan without removal`() {
        val dimension = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
        CooTerrainMappingRegistry.clear()
        CooTerrainMappingRegistry.install(
            instance("paused", 1, 1, CooTerrainEffectComposition.REPLACE, dimension, pausedAt = 4L)
        )

        assertEquals(emptyList(), CooTerrainMappingRegistry.activeRenderPlan(dimension, 10L))
        assertEquals(
            4L,
            CooTerrainMappingRegistry.current(
                CooTerrainMappingBatchKey(
                    dimension,
                    ResourceLocation.fromNamespaceAndPath("test", "paused"),
                    CooTerrainEffectComposition.REPLACE
                )
            )?.pausedAt
        )
    }

    @Test
    fun `expired mappings are absent from the render plan`() {
        val dimension = ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
        CooTerrainMappingRegistry.clear()
        CooTerrainMappingRegistry.install(instance("expired", 1, 1, CooTerrainEffectComposition.REPLACE, dimension, 5L))

        assertEquals(emptyList(), CooTerrainMappingRegistry.activeRenderPlan(dimension, 5L))
    }

    private fun instance(
        id: String,
        priority: Int,
        sequence: Long,
        composition: CooTerrainEffectComposition,
        dimension: ResourceLocation,
        expiresAt: Long? = null,
        pausedAt: Long? = null
    ): CooTerrainMappingInstance {
        return CooTerrainMappingInstance(
            ResourceLocation.fromNamespaceAndPath("test", id),
            ResourceLocation.fromNamespaceAndPath("test", "mapping"),
            dimension,
            CooTerrainMappingRegion.Sphere(Vec3.ZERO, 1.0),
            emptyMap(),
            priority,
            composition,
            0L,
            expiresAt,
            sequence,
            sequence,
            pausedAt
        )
    }
}
