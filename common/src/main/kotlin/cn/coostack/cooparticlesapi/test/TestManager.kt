package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestGroupBuilder
import net.minecraft.world.entity.player.Player
import java.util.concurrent.ConcurrentHashMap

object TestManager {

    val builders = HashMap<String, (Player) -> TestGroupBuilder>()

    val validGroups = HashSet<TestGroup>()

    fun register(id: String, group: (Player) -> TestGroupBuilder) {
        builders[id] = group
    }

    fun startTest(id: String, user: Player): TestGroup? {
        if (!builders.containsKey(id)) {
            return null
        }
        val group = builders[id]!!(user).build()
        group.start()
        validGroups.add(group)
        return group
    }

    fun doTick() {
        val iter = validGroups.iterator()
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