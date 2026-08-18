package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.server.ServerRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectGroup
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectManager
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import cn.coostack.cooparticlesapi.test.options.renderer.PostEffectDemoOptions
import cn.coostack.cooparticlesapi.test.options.renderer.PostEffectDemoOption
import cn.coostack.cooparticlesapi.test.options.renderer.world.DemoLightOrbRenderEntity
import cn.coostack.cooparticlesapi.test.options.renderer.world.DemoMaskBloomStraightLaserRenderEntity
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.levelgen.Heightmap
import org.joml.Vector3f
import kotlin.math.floor

/** 由地形后处理、MASK_BLOOM 核心和 MASK_BLOOM 竖直光束组成的 BlockTest。 */
class OrbitalRailgunBlockTestOption(
    private val player: Player
) : TestOption<OrbitalRailgunBlockTestOption> {
    private val target = player.pick(96.0, 0F, false).location
    private val terrainGroupId = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        "test/orbital_railgun/${player.uuid.toString().replace("-", "")}"
    )
    private var activeLevel: ServerLevel? = null
    private val appliedSurfacePositions = LinkedHashMap<Long, BlockPos?>()
    private val postEffect: PostEffectDemoOption = PostEffectDemoOptions.orbitalRailgun(player, target)
    private val orb = DemoLightOrbRenderEntity(
        player.level(),
        target.add(0.0, 14.0, 0.0),
        220
    ).apply {
        radius = 2.8F
        intensity = 1.8F
    }
    private val beam = DemoMaskBloomStraightLaserRenderEntity(
        player.level(),
        target
    ).apply {
        phaseTicks = 6
        lifetime = 210
        brightness = 1.25F
        maxRadius = 0.72F
        color = Vector3f(0.58F, 0.94F, 1.0F)
        updateBeam(
            target.add(0.0, 0.05, 0.0),
            target.add(0.0, 150.0, 0.0)
        )
    }

    override fun paramTarget(): OrbitalRailgunBlockTestOption = this

    override fun start() {
        val level = player.level() as? ServerLevel
            ?: error("OrbitalRailgunBlockTestOption requires a server-side player")
        activeLevel = level
        val initialSurface = surfaceSnapshot(level)
        appliedSurfacePositions.clear()
        appliedSurfacePositions.putAll(initialSurface)
        CooTerrainEffectManager.apply(
            level,
            CooTerrainEffectGroup(terrainGroupId, OrbitalRailgunTerrain.pipeline) {
                positions(initialSurface.values.filterNotNull())
                uniform(
                    "OrbitalCenter",
                    CooUniformValue.Vec3Value(
                        target.x.toFloat(),
                        target.y.toFloat(),
                        target.z.toFloat()
                    )
                )
                uniform("OrbitalRadius", 72F)
                uniform("OrbitalLineWidth", 0.55F)
                uniform("OrbitalColor", CooUniformValue.Vec3Value(0.58F, 0.94F, 1F))
                uniform("OrbitalDuration", 220F)
                duration(220L)
            }
        )
        postEffect.start()
        ServerRenderEntityManager.spawn(orb)
        ServerRenderEntityManager.spawn(beam)
    }

    override fun stop() {
        activeLevel?.let { level -> CooTerrainEffectManager.remove(level, terrainGroupId) }
        activeLevel = null
        appliedSurfacePositions.clear()
        postEffect.stop()
        orb.remove()
        beam.remove()
    }

    override fun isValid(): Boolean = postEffect.isValid()

    override fun onFailed() {
        stop()
    }

    override fun onSuccess() {
        stop()
    }

    override fun optionID(): String = "shader_effect/orbital_railgun"

    override fun doTick() {
        activeLevel?.let { level ->
            if (level.gameTime % SURFACE_SYNC_INTERVAL == 0L) {
                syncSurface(level)
            }
        }
        postEffect.doTick()
    }

    private fun surfaceSnapshot(level: ServerLevel): Map<Long, BlockPos?> {
        val radius = 72
        val centerX = floor(target.x).toInt()
        val centerZ = floor(target.z).toInt()
        return buildMap {
            for (offsetX in -radius..radius) {
                for (offsetZ in -radius..radius) {
                    if (offsetX * offsetX + offsetZ * offsetZ > radius * radius) continue
                    val worldX = centerX + offsetX
                    val worldZ = centerZ + offsetZ
                    val column = BlockPos(worldX, 0, worldZ)
                    if (!level.hasChunkAt(column)) continue
                    val key = BlockPos.asLong(worldX, 0, worldZ)
                    val surfaceY = level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        worldX,
                        worldZ
                    ) - 1
                    if (surfaceY < level.minBuildHeight) {
                        put(key, null)
                        continue
                    }
                    val position = BlockPos(worldX, surfaceY, worldZ)
                    val state = level.getBlockState(position)
                    put(
                        key,
                        position.takeUnless { state.isAir || !state.fluidState.isEmpty }?.immutable()
                    )
                }
            }
        }
    }

    private fun syncSurface(level: ServerLevel) {
        val current = surfaceSnapshot(level)
        val additions = ArrayList<BlockPos>()
        val removals = ArrayList<BlockPos>()
        current.forEach { (key, position) ->
            val previous = appliedSurfacePositions[key]
            if (previous == position) return@forEach
            previous?.let(removals::add)
            position?.let(additions::add)
        }
        if (removals.isNotEmpty()) {
            CooTerrainEffectManager.removePositions(level, terrainGroupId, removals)
        }
        if (additions.isNotEmpty()) {
            CooTerrainEffectManager.append(level, terrainGroupId, additions)
        }
        current.forEach { (key, position) -> appliedSurfacePositions[key] = position }
    }

    companion object {
        private const val SURFACE_SYNC_INTERVAL = 5L
    }

    override fun reviewDescription(): String =
        "观察地形环是否贴合 terrain、挖掘或新增方块后环是否实时补齐、核心和竖直光束是否由 MASK_BLOOM 提供，并确认转身后压暗仍保持"
}
