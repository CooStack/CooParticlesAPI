package cn.coostack.cooparticlesapi.commands

import cn.coostack.cooparticlesapi.CooParticlesAPI
import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import cn.coostack.cooparticlesapi.network.packet.status.PacketPerformanceStatusControlS2C
import cn.coostack.cooparticlesapi.performance.PerformanceStatusControlAction
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

/** 注册 CooParticlesAPI 管理命令及客户端 Status 会话控制命令。 */
object APICommand {
    /** 在指定 dispatcher 同时注册新根命令与兼容旧根命令。 */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(root(ROOT_COMMAND))
        dispatcher.register(root(LEGACY_ROOT_COMMAND))
    }

    /** 构造包含 clean 与 status 子树的权限受控命令根。 */
    private fun root(name: String): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .requires { source -> source.hasPermission(2) }
            .then(
                Commands.literal(CLEAN_COMMAND)
                    .then(Commands.literal(ALL_COMMAND).executes {
                        CooParticlesAPI.clearTransientState()
                        1
                    })
            )
            .then(
                Commands.literal(STATUS_COMMAND)
                    .then(Commands.literal(START_COMMAND).executes { context ->
                        sendStatusControl(context, PerformanceStatusControlAction.START)
                    })
                    .then(Commands.literal(STOP_COMMAND).executes { context ->
                        sendStatusControl(context, PerformanceStatusControlAction.STOP)
                    })
                    .then(Commands.literal(GUI_COMMAND).executes { context ->
                        sendStatusControl(context, PerformanceStatusControlAction.GUI)
                    })
            )
    }

    /** 向执行命令的玩家客户端发送 Status 控制动作。 */
    private fun sendStatusControl(
        context: CommandContext<CommandSourceStack>,
        action: PerformanceStatusControlAction,
    ): Int {
        val source = context.source
        val player = source.playerOrException
        if (!CooServerPacketManager.sendTo(player, PacketPerformanceStatusControlS2C(action))) {
            source.sendFailure(Component.literal("CooParticles Status 控制包发送失败"))
            return 0
        }
        source.sendSuccess(
            { Component.literal("CooParticles Status: ${action.name.lowercase()}") },
            false,
        )
        return 1
    }

    /** 稳定命令注册 ID。 */
    private const val ROOT_COMMAND = "cooparticles"

    /** 兼容已有脚本和用户习惯的旧命令注册 ID。 */
    private const val LEGACY_ROOT_COMMAND = "cooparticlesapi"

    /** 清理子命令注册 ID。 */
    private const val CLEAN_COMMAND = "clean"

    /** 全部目标子命令注册 ID。 */
    private const val ALL_COMMAND = "all"

    /** Status 子命令注册 ID。 */
    private const val STATUS_COMMAND = "status"

    /** 开始采集子命令注册 ID。 */
    private const val START_COMMAND = "start"

    /** 停止采集子命令注册 ID。 */
    private const val STOP_COMMAND = "stop"

    /** 打开实时界面子命令注册 ID。 */
    private const val GUI_COMMAND = "gui"
}
