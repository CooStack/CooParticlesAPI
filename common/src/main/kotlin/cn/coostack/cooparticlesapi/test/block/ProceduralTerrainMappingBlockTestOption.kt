package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingRegion
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/** 验证程序化球形 Mapping 的展开、持续外移和收缩生命周期。 */
class ProceduralTerrainMappingBlockTestOption(
    private val player: Player
) : TestOption<ProceduralTerrainMappingBlockTestOption> {
    private val center = player.pick(96.0, 0F, false).location + Vec3(0.0, 1.0, 0.0)
    private val instanceId = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        "test/procedural_terrain_mapping/${player.uuid.toString().replace("-", "")}"
    )
    private var activeLevel: ServerLevel? = null
    private var startedAt = 0L

    override fun paramTarget(): ProceduralTerrainMappingBlockTestOption = this

    override fun start() {
        val level = player.level() as? ServerLevel
            ?: error("ProceduralTerrainMappingBlockTestOption requires a server-side player")
        activeLevel = level
        startedAt = level.gameTime
        CooTerrainMappingManager.create(
            level,
            ProceduralTerrainMappingTerrain.mapping.id,
            instanceId,
            CooTerrainMappingRegion.Sphere(center, 96.0)
        ) {
            uniforms(initialUniforms())
            priority(1)
            duration(TOTAL_TICKS)
        }
    }

    override fun stop() {
        activeLevel?.let { level -> CooTerrainMappingManager.remove(level, instanceId) }
        activeLevel = null
    }

    override fun isValid(): Boolean {
        val level = activeLevel ?: return false
        return player.level() === level && level.gameTime - startedAt < TOTAL_TICKS
    }

    override fun onFailed() {
        stop()
    }

    override fun onSuccess() {
        stop()
    }

    override fun optionID(): String = OPTION_ID

    override fun doTick() {
        val level = activeLevel ?: return
        if (player.level() !== level) {
            stop()
            return
        }
        val elapsed = level.gameTime - startedAt
        if (elapsed >= TOTAL_TICKS) {
            stop()
            return
        }

        val phase = phase(elapsed)
        CooTerrainMappingManager.updateRegion(
            level,
            instanceId,
            CooTerrainMappingRegion.Sphere(center, phase.regionRadius)
        )
        CooTerrainMappingManager.updateUniforms(level, instanceId, phase.uniforms)
    }

    override fun reviewMode(): TestReviewMode = TestReviewMode.MANUAL_VISUAL

    override fun reviewDescription(): String =
        "观察球形 Mapping 是否经历展开、持续向外移动的径向发光环和收缩，并确认整个过程没有重建实例"

    private fun initialUniforms(): Map<String, CooUniformValue> = mapOf(
        "RingRadius" to CooUniformValue.FloatValue(4F),
        "RingWidth" to CooUniformValue.FloatValue(0.8F),
        "RingIntensity" to CooUniformValue.FloatValue(0F),
        "RingColor" to CooUniformValue.Vec3Value(0.32F, 0.82F, 1F),
        "RingProgress" to CooUniformValue.FloatValue(0F)
    )

    private fun phase(elapsed: Long): MappingPhase {
        return when {
            elapsed < EXPAND_TICKS -> {
                val progress = elapsed.toFloat() / EXPAND_TICKS.toFloat()
                MappingPhase(
                    32.0 + 64.0 * progress,
                    4.0F + 68.0F * progress,
                    0.25F + 0.75F * progress,
                    progress
                )
            }
            elapsed < EXPAND_TICKS + SUSTAIN_TICKS -> {
                val progress = (elapsed - EXPAND_TICKS).toFloat() / SUSTAIN_TICKS.toFloat()
                MappingPhase(
                    96.0,
                    18.0F + 70.0F * progress,
                    1F,
                    progress
                )
            }
            else -> {
                val progress = (elapsed - EXPAND_TICKS - SUSTAIN_TICKS).toFloat() / SHRINK_TICKS.toFloat()
                MappingPhase(
                    96.0,
                    88.0F - 80.0F * progress,
                    1F - progress,
                    1F - progress
                )
            }
        }
    }

    private data class MappingPhase(
        val regionRadius: Double,
        val ringRadius: Float,
        val intensity: Float,
        val progress: Float
    ) {
        val uniforms: Map<String, CooUniformValue> = mapOf(
            "RingRadius" to CooUniformValue.FloatValue(ringRadius),
            "RingWidth" to CooUniformValue.FloatValue(0.8F),
            "RingIntensity" to CooUniformValue.FloatValue(intensity),
            "RingColor" to CooUniformValue.Vec3Value(0.32F, 0.82F, 1F),
            "RingProgress" to CooUniformValue.FloatValue(progress)
        )
    }

    companion object {
        private const val OPTION_ID = "shader_effect/procedural_terrain_mapping"
        private const val EXPAND_TICKS = 60L
        private const val SUSTAIN_TICKS = 120L
        private const val SHRINK_TICKS = 60L
        private const val TOTAL_TICKS = EXPAND_TICKS + SUSTAIN_TICKS + SHRINK_TICKS
    }
}
