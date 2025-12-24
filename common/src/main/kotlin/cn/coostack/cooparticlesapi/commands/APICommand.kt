package cn.coostack.cooparticlesapi.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSource
import net.minecraft.commands.Commands

object APICommand {

    fun register(dispatcher: CommandDispatcher<CommandSource>) {

        dispatcher.register(
            LiteralArgumentBuilder.literal<CommandSource>("clear")
                .executes {
                    1
                }

        )

    }

}