package cn.coostack.cooparticlesapi.renderer.terrain

import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.readText
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
        assertTrue("CooTerrainMappingShaderAbi.REGION" in pipeline)
        assertTrue("CooTerrainMappingShaderAbi.REGION_SIZE" in pipeline)
        assertTrue("CooTerrainMappingShaderAbi.REGION_TYPE" in pipeline)
        assertTrue("CooTerrainMappingShaderAbi.PROGRESS" in pipeline)
        assertTrue("CooMappingDepthAvailable" in pipeline)
        assertTrue("fun resolveOverlayRenderTypes(state: BlockState, original: RenderType, pos: BlockPos): List<RenderType>" in pipeline)
        assertTrue("CooTerrainMappingRegistry.activeRenderPlan" in pipeline)
        assertTrue("CooTerrainMappingBatchKey" in pipeline)
        assertTrue("CooTerrainMappingRegistry::current" in pipeline)
        assertFalse("CooTerrainMappingRegistry.active(level.dimension().location(), level.gameTime).firstOrNull()" in pipeline)
    }

    @Test
    fun `persistent mappings use full region progress`() {
        val pipeline = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )

        assertTrue("duration == null -> 1F" in pipeline)
        assertTrue("mapping == null -> 0F" in pipeline)
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
    fun `screen mapping shader supports sphere box and cylinder signed distance`() {
        val shader = source(
            "common/src/main/resources/assets/cooparticlesapi/shaders/post/procedural_mapping_screen.fsh"
        )

        assertTrue("uniform vec3 CooMappingRegionSize" in shader)
        assertTrue("uniform int CooMappingRegionType" in shader)
        assertTrue("if (CooMappingRegionType == 1)" in shader)
        assertTrue("if (CooMappingRegionType == 2)" in shader)
        assertTrue("mappingSignedDistance" in shader)
        assertTrue("smoothstep(-featherWidth, 0.0, signedDistance)" in shader)
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
    fun `region changes rebuild affected sections while uniform updates reuse geometry`() {
        val registry = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingRegistry.kt"
        )
        val pipeline = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )
        assertTrue("fun topologyRevision(): Long" in registry)
        assertTrue("topologyChanged.incrementAndGet()" in registry)
        assertTrue("instance.region," in registry)
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

    @Test
    fun `screen only mappings bypass section geometry and use post collection`() {
        val registry = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingRegistry.kt"
        )
        val builders = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/pipeline/CooPipelineBuilders.kt"
        )
        val pipeline = source(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainPipelineManager.kt"
        )

        assertTrue("fun screenOnly()" in builders)
        assertTrue("private fun requiresTerrainGeometry" in registry)
        assertTrue("collectScreenOnlyMappingPostEffects" in pipeline)
        assertTrue("if (draws.isEmpty())" in pipeline)
        assertTrue("CooPipelineNodeKind.WORLD" in registry)
    }

    @Test
    fun `screen only mapping documentation uses an exact fragment resource path`() {
        val documentation = source("docs/terrain-mapping.md")

        assertTrue("fragment(id(\"post/domain_screen.fsh\"))" in documentation)
        assertFalse("fragment(id(\"post/domain_screen\"))" in documentation)
    }

    @Test
    fun `coplanar attachment and final overlays enable polygon offset`() {
        val outputState = source(
            "common/src/main/java/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainRenderStateShard.java"
        )
        val offsetStage = outputState.substringAfter("private static boolean isOffsetStageActive()")
        assertTrue("private static boolean isOffsetStageActive()" in outputState)
        assertTrue("CooTerrainPipelineManager.isTerrainAttachmentCaptureActive()" in offsetStage)
        assertTrue("CooTerrainPipelineManager.isFinalCompositeTerrainOverlayActive()" in offsetStage)
        assertTrue("glIsEnabled(GL_POLYGON_OFFSET_FILL)" in outputState)
        assertTrue("glGetFloat(GL_POLYGON_OFFSET_FACTOR)" in outputState)
        assertTrue("glPolygonOffset(previousFactor[0], previousUnits[0])" in outputState)
        assertTrue("if (previousEnabled[0])" in outputState)
        assertFalse("return true;" in offsetStage.substringBefore("}"))
    }

    private fun source(path: String): String {
        val requested = Path(path)
        if (requested.exists()) return requested.readText()
        var cursor = Path(System.getProperty("user.dir")).absolute()
        while (cursor.parent != null) {
            val candidate = cursor.resolve(path)
            if (candidate.exists()) return candidate.readText()
            cursor = cursor.parent
        }
        return requested.readText()
    }
}
