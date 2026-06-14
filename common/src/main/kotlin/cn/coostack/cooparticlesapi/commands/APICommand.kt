package cn.coostack.cooparticlesapi.commands

import cn.coostack.cooparticlesapi.CooParticlesAPI
import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

object APICommand {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("cooparticlesapi")
                .requires { it.hasPermission(2) }
                .then(
                    Commands.literal("clean")
                        .then(Commands.literal("all").executes {
                            CooParticlesAPI.clearTransientState()
                            1
                        })
                )
        )
    }
}
