package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectComposition
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainMappingRegion
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/** 验证程序化球形 Mapping 的 GPU 端扩张黑色遮罩生命周期。 */
class ProceduralTerrainMappingBlockTestOption(
    private val player: Player
) : TestOption<ProceduralTerrainMappingBlockTestOption> {
    /** Mapping 测试以启动玩家脚下为球心，避免准星命中远处方块造成位置误解。 */
    private val center = player.position() + Vec3(0.0, 0.5, 0.0)
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
            priority(1)
            composition(CooTerrainEffectComposition.ADDITIVE)
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
        if (player.level() !== level || level.gameTime - startedAt >= TOTAL_TICKS) {
            stop()
        }
    }

    override fun reviewMode(): TestReviewMode = TestReviewMode.MANUAL_VISUAL

    override fun reviewDescription(): String =
        "观察黑色圆形遮罩是否在 96 格地形范围内持续扩张；RenderEntity、第一人称手臂和单方块 terrain 效果不应被覆盖"

    companion object {
        private const val OPTION_ID = "shader_effect/procedural_terrain_mapping"
        private const val TOTAL_TICKS = 240L
    }
}
