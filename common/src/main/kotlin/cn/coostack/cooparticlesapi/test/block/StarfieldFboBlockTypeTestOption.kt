package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import cn.coostack.cooparticlesapi.network.packet.server.PacketBindStarfieldFboBlockS2C
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import java.util.UUID

/**
 * 把玩家 [Player.position] 所在方块的类型临时绑定到星空 FBO Pipeline。
 *
 * 同一方块的全部 BlockState 都会命中。绑定会保留到人工复核结束，随后无论通过、失败、跳过
 * 还是取消，都只撤销本次测试创建的规则。
 *
 * @property player 当前方块测试提供的玩家
 */
class StarfieldFboBlockTypeTestOption(
    private val player: Player
) : TestOption<Player> {
    /** 当前测试的客户端临时绑定 ID。 */
    private val bindingId = UUID.randomUUID()

    /** 当前测试所在的服务端世界；清理后为 `null`。 */
    private var activeLevel: ServerLevel? = null

    /** 当前测试同步到客户端的方块类型载体。 */
    private var boundState = Blocks.AIR.defaultBlockState()

    /** 测试保持运行的截止 tick，为客户端接收绑定并重建 section 预留时间。 */
    private var expiresAt = Long.MIN_VALUE

    /** 读取玩家位置处的方块类型，并把绑定包广播给同维度客户端。 */
    override fun start() {
        val level = player.level() as? ServerLevel
            ?: error("StarfieldFboBlockTypeTestOption requires a server-side player")
        activeLevel = level
        val targetPos = BlockPos.containing(player.position())
        boundState = level.getBlockState(targetPos)
        expiresAt = level.gameTime + 20L
        CooServerPacketManager.sendWorlds(
            level,
            PacketBindStarfieldFboBlockS2C(boundState, bindingId, true)
        )
    }

    /** 测试结束时先保留绑定，等待人工复核结果。 */
    override fun stop() = Unit

    /** @return 客户端绑定和 section 重建的等待时间尚未结束时返回 `true`。 */
    override fun isValid(): Boolean = activeLevel?.gameTime?.let { gameTime -> gameTime < expiresAt } == true

    /** 失败、跳过或取消时撤销当前测试的临时绑定。 */
    override fun onFailed() = clearBinding()

    /** 通过人工复核后撤销当前测试的临时绑定。 */
    override fun onSuccess() = clearBinding()

    /** @return 方块测试框架使用的稳定测试项 ID。 */
    override fun optionID(): String = OPTION_ID

    /** 方块类型绑定不需要逐 tick 更新。 */
    override fun doTick() = Unit

    /** @return 该测试需要人工观察方块的 FBO 着色与状态变化。 */
    override fun reviewMode(): TestReviewMode = TestReviewMode.MANUAL_VISUAL

    /** @return 人工复核时应验证的方块类型匹配行为。 */
    override fun reviewDescription(): String =
        "观察模拟玩家 position 所在类型的全部方块状态都显示星空 FBO；结束测试后恢复原绑定"

    /** @return 与构造参数相同的玩家。 */
    override fun paramTarget(): Player = player

    /** 撤销当前测试的临时绑定；重复调用不会再次发送。 */
    private fun clearBinding() {
        activeLevel?.let { level ->
            CooServerPacketManager.sendWorlds(
                level,
                PacketBindStarfieldFboBlockS2C(boundState, bindingId, false)
            )
        }
        activeLevel = null
    }

    companion object {
        private const val OPTION_ID = "starfield-fbo-block-type"
    }
}
