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
import net.minecraft.server.permissions.Permissions

/** 注册 CooParticlesAPI 管理命令及客户端 Status 会话控制命令。 */
object APICommand {
    /** 在指定 dispatcher 同时注册新根命令与兼容旧根命令。 */
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(root("cooparticles"))
        dispatcher.register(root("cooparticlesapi"))
    }

    /** 构造包含 clean 与 status 子树的权限受控命令根。 */
    private fun root(name: String): LiteralArgumentBuilder<CommandSourceStack> {
        return Commands.literal(name)
            .requires { source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
            .then(
                Commands.literal("clean")
                    .then(Commands.literal("all").executes {
                        CooParticlesAPI.clearTransientState()
                        1
                    })
            )
            .then(
                Commands.literal("status")
                    .then(Commands.literal("start").executes { context ->
                        sendStatusControl(context, PerformanceStatusControlAction.START)
                    })
                    .then(Commands.literal("stop").executes { context ->
                        sendStatusControl(context, PerformanceStatusControlAction.STOP)
                    })
                    .then(Commands.literal("gui").executes { context ->
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

}
