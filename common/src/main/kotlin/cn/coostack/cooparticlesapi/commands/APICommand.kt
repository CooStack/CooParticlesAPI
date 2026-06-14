package cn.coostack.cooparticlesapi.commands

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.display.DisplayEntityManager
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSource
import net.minecraft.commands.Commands

object APICommand {

    fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            LiteralArgumentBuilder.literal<CommandSource>("cleanapi")
                .then(
                    LiteralArgumentBuilder.literal<CommandSource>("display")
                        .executes {
                            CooParticlesAPIClient.clearTransientClientState()
                            1
                        }
                )
        )
    }

}