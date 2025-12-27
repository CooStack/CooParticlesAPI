package cn.coostack.cooparticlesapi.test.options.event

import net.minecraft.world.entity.player.Player

class TestChildEvent(player: Player, val id: String) : TestEvent(player) {
}