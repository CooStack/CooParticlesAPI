package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestGroupBuilder
import net.minecraft.world.entity.player.Player

object TestManager {

    val builders = HashMap<String, (Player) -> TestGroupBuilder>()

    val validGroupsServer = HashSet<TestGroup>()
    val validGroupsClient = HashSet<TestGroup>()

    fun register(id: String, group: (Player) -> TestGroupBuilder) {
        builders[id] = group
    }

    fun startTest(id: String, user: Player): TestGroup? {
        if (!builders.containsKey(id)) {
            return null
        }
        val group = builders[id]!!(user).build()
        if (user.level().isClientSide) {
            validGroupsClient.add(group)
        } else {
            validGroupsServer.add(group)
        }
        group.start()
        return group
    }

    fun doTickServer() {
        val iter = validGroupsServer.iterator()
        while (iter.hasNext()) {
            val group = iter.next()
            if (group.isDone()) {
                iter.remove()
                continue
            }
            group.doTick()
        }
    }

    fun doTickClient() {
        val iter = validGroupsClient.iterator()
        while (iter.hasNext()) {
            val group = iter.next()
            if (group.isDone()) {
                iter.remove()
                continue
            }
            group.doTick()
        }
    }
}