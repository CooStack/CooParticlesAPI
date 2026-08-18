package cn.coostack.cooparticlesapi.renderer.terrain

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 锁定程序化 Terrain Mapping 的区域同步、渲染 ABI 和生命周期边界。 */
class CooTerrainMappingContractTest {
    @Test
    fun `manager synchronizes region parameters instead of positions`() {
        val manager = source("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingManager.kt")
        assertTrue("fun create(" in manager)
        assertTrue("region: CooTerrainMappingRegion" in manager)
        assertTrue("fun createTo(" in manager)
        assertTrue("fun pause(" in manager)
        assertTrue("fun resume(" in manager)
        assertTrue("fun syncTo(player: ServerPlayer, instanceId: ResourceLocation)" in manager)
        assertFalse("Iterable<BlockPos>" in manager)
        assertFalse("provider.positions" in manager)
        assertFalse("CooTerrainEffectManager.apply" in manager)
    }

    @Test
    fun `mapping packet carries tagged region and no block position payload`() {
        val packet = source("common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/server/PacketTerrainMappingS2C.kt")
        assertTrue("@CooAutoRegister" in packet)
        assertTrue("packet.region?.encode(buffer)" in packet)
        assertTrue("packet.pausedAt" in packet)
        assertTrue("pausedAt = instance.pausedAt" in packet)
        assertTrue("packet.pausedAt = if (buffer.readBoolean()) buffer.readVarLong() else null" in packet)
        assertFalse("writeBlockPos" in packet)
        assertFalse("writeTimedPositions" in packet)
    }

    @Test
    fun `client registry has no position reverse index`() {
        val registry = source("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingRegistry.kt")
        assertTrue("fun active(dimension: ResourceLocation, gameTime: Long)" in registry)
        assertTrue("CooTerrainMappingRegion" in registry)
        assertFalse("positionIndex" in registry)
        assertFalse("BlockPos" in registry)
    }

    @Test
    fun `terrain pipeline exposes stable mapping inputs`() {
        val pipeline = source("common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt")
        assertTrue("CooMappingRegion" in pipeline)
        assertTrue("CooMappingProgress" in pipeline)
        assertTrue("CooMappingDepthAvailable" in pipeline)
        assertTrue("fun resolveOverlayRenderTypes(state: BlockState, original: RenderType, pos: BlockPos): List<RenderType>" in pipeline)
        assertTrue("CooTerrainMappingRegistry.activeRenderPlan" in pipeline)
        assertTrue("CooTerrainMappingBatchKey" in pipeline)
        assertTrue("CooTerrainMappingRegistry::current" in pipeline)
        assertFalse("CooTerrainMappingRegistry.active(level.dimension().location(), level.gameTime).firstOrNull()" in pipeline)
    }

    @Test
    fun `mapping shader preserves base color and uses explicit zero mask`() {
        val shader = source("common/src/main/resources/assets/cooparticlesapi/shaders/core/terrain/procedural_mapping.fsh")
        assertTrue("BaseSampler" in shader)
        assertTrue("baseUv" in shader)
        assertTrue("TerrainDepth" in shader)
        assertTrue("CooMappingDepthAvailable" in shader)
        assertTrue("mask = 0.0" in shader)
        assertTrue("baseColor" in shader)
        assertFalse("discard" in shader)
        assertTrue("CooMappingComposition" in shader)
        assertTrue("outputAlpha *= mask" in shader)
        assertTrue("vec3 mappedColor = baseColor * mix" in shader)
    }

    @Test
    fun `platform providers expose composition transparency states`() {
        listOf(
            source("fabric/src/main/kotlin/cn/coostack/cooparticlesapi/platform/FabricRenderTypesProvider.kt"),
            source("neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/platform/NeoRenderTypesProvider.kt")
        ).forEach { provider ->
            assertTrue("CooTerrainEffectComposition.ALPHA_OVER" in provider)
            assertTrue("CooTerrainEffectComposition.ADDITIVE" in provider)
            assertTrue("RenderStateShard.ADDITIVE_TRANSPARENCY" in provider)
            assertTrue("CooTerrainMappingBatchKey" in provider)
        }
    }

    @Test
    fun `region and uniform updates do not request section geometry rebuilds`() {
        val registry = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingRegistry.kt"
        )
        val pipeline = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )
        assertTrue("fun topologyRevision(): Long" in registry)
        assertTrue("topologyChanged.incrementAndGet()" in registry)
        assertTrue("CooTerrainMappingRegistry.topologyRevision()" in pipeline)
        assertTrue("fun drainTopologyRegions(dimension: ResourceLocation)" in registry)
        assertTrue("requestMappingSectionRebuild(CooTerrainMappingRegistry.drainTopologyRegions(dimension))" in pipeline)
        assertFalse("val currentMappingRevision = CooTerrainMappingRegistry.revision()" in pipeline)
        assertTrue("updateUniforms" in registry)
    }

    @Test
    fun `mapping bounds include touching section boxes`() {
        val bounds = CooTerrainMappingBounds(
            minX = 0,
            minY = 0,
            minZ = 0,
            maxX = 15,
            maxY = 15,
            maxZ = 15
        )

        assertTrue(bounds.intersects(15, 0, 0, 30, 15, 15))
        assertFalse(bounds.intersects(16, 0, 0, 31, 15, 15))
    }

    @Test
    fun `mapping instance uniforms override terrain pipeline defaults`() {
        val pipeline = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )

        assertTrue("mapping?.uniforms?.forEach { (name, value) -> put(name, value) }" in pipeline)
    }

    private fun source(path: String): String {
        val requested = Path.of(path)
        if (Files.exists(requested)) return Files.readString(requested)
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            val candidate = cursor.resolve(path)
            if (Files.exists(candidate)) return Files.readString(candidate)
            cursor = cursor.parent
        }
        return Files.readString(requested)
    }
}
