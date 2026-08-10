package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectGroup
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainEffectManager
import cn.coostack.cooparticlesapi.renderer.terrain.CooTerrainPropagation
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import cn.coostack.cooparticlesapi.utils.BlockUtil
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation

/**
 * 演示地形颜色传播和恢复，不对方块类型做全局替换。
 * 每五个 tick 发现一层，客户端按帧插值颜色和效果强度。
 *
 * @property player 当前方块测试使用的模拟玩家，只负责提供服务端世界和传播中心。
 */
class BlockTexturePropagationTestOption(
    private val player: BlockTestPlayer
) : TestOption<BlockTestPlayer> {
    /** 传播中心，创建测试项时固定为模拟玩家所在方块。 */
    private val center: BlockPos = player.blockPos.immutable()

    /** 通用地形效果组 ID，同一测试宿主只维护一组批量位置。 */
    private val effectGroupId: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
        CooParticlesConstants.MOD_ID,
        "test/terrain_propagation/${center.asLong()}"
    )

    /** 每个方块首次被传播到达的服务端 tick。 */
    private val discoveredAt = LinkedHashMap<BlockPos, Long>()

    /** 分层传播状态，停止测试后释放。 */
    private var spread: BlockUtil.BlockStepSpareData? = null

    /** 本次测试开始时的服务端 tick。 */
    private var startedAt = 0L

    /** 从开始 tick 算起的已运行 tick 数。 */
    private var elapsed = 0L

    /** 创建新的分层传播状态并发布空快照。 */
    override fun start() {
        startedAt = player.level.gameTime
        elapsed = 0L
        discoveredAt.clear()
        spread = createSpread()
        CooTerrainEffectManager.apply(
            player.level,
            CooTerrainEffectGroup(effectGroupId, CooTerrainPropagation.pipeline) {}
        )
    }

    /** 停止传播并移除当前测试产生的效果组。 */
    override fun stop() {
        CooTerrainEffectManager.remove(player.level, effectGroupId)
        spread = null
        discoveredAt.clear()
    }

    /**
     * @return 传播和所有已发现方块的本地恢复阶段是否仍在有效时间内。
     * 每个方块从自己的发现 tick 开始运行 120 tick，因此测试总时长最多为 180 tick。
     */
    override fun isValid(): Boolean {
        val latestFinish = discoveredAt.values.maxOrNull()?.plus(TOTAL_TICKS)
            ?: (startedAt + PROPAGATION_TICKS)
        return player.level.gameTime < latestFinish
    }

    /** 失败时清理传播状态。 */
    override fun onFailed() = stop()

    /** 成功时清理传播状态。 */
    override fun onSuccess() = stop()

    /** @return 测试项注册 ID。 */
    override fun optionID(): String = "block-texture-propagation"

    /** 按服务端 tick 推进传播，并把新位置追加到通用效果组。 */
    override fun doTick() {
        val propagationStepTicks = 5L
        elapsed = (player.level.gameTime - startedAt).coerceAtLeast(0L)
        if (elapsed < PROPAGATION_TICKS && elapsed % propagationStepTicks == 0L) {
            val additions = spread?.stepOnMainThread(player.level).orEmpty()
                .map(BlockPos::immutable)
                .filter { position -> discoveredAt.putIfAbsent(position, startedAt + elapsed) == null }
            if (additions.isNotEmpty()) {
                CooTerrainEffectManager.append(player.level, effectGroupId, additions)
            }
        }
    }

    /** @return 该测试需要人工观察客户端方块颜色。 */
    override fun reviewMode(): TestReviewMode = TestReviewMode.MANUAL_VISUAL

    /** @return 人工复核时应观察的颜色阶段变化。 */
    override fun reviewDescription(): String =
        "观察方块由白色、铜红色、铜锈绿色渐变，再在恢复阶段回到原纹理"

    /** @return 与构造参数相同的模拟玩家。 */
    override fun paramTarget(): BlockTestPlayer = player

    /** 创建一段从中心开始的主线程传播。 */
    private fun createSpread(): BlockUtil.BlockStepSpareData {
        val propagationSteps = (PROPAGATION_TICKS / 5L).toInt()
        return BlockUtil.BlockStepSpareData(
            totalStep = propagationSteps,
            center = center,
            condition = { state -> !state.isAir && state.fluidState.isEmpty }
        )
    }

    companion object {
        private const val PROPAGATION_TICKS = 60L
        /** 单个方块从发现到恢复完成的总时长，与 [isValid] 共享。 */
        private const val TOTAL_TICKS = 120L
    }
}
