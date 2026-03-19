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


    fun getTestFromServer(user: Player): TestGroup? {
        return getTest(validGroupsServer, user)
    }

    fun getTestFromClient(user: Player): TestGroup? {
        return getTest(validGroupsClient, user)
    }

    fun getGamingTestFromServer(user: Player): GamingTestGroup? {
        return getTestFromServer(user) as? GamingTestGroup
    }

    fun startTest(id: String, user: Player): TestGroup? {
        if (!builders.containsKey(id)) {
            return null
        }
        val groups = if (user.level().isClientSide) {
            validGroupsClient
        } else {
            validGroupsServer
        }
        clearGroupsFor(groups, user)
        val group = builders[id]!!(user).build()
        groups.add(group)
        group.start()
        return group
    }

    fun completeCurrent(user: Player): Boolean {
        return getGamingTestFromServer(user)?.completeCurrent() != null
    }

    fun failCurrent(user: Player): Boolean {
        return getGamingTestFromServer(user)?.failCurrent() != null
    }

    fun jumpRelative(user: Player, offset: Int): Boolean {
        return getGamingTestFromServer(user)?.jumpRelative(offset) != null
    }

    fun jumpToFirst(user: Player): Boolean {
        return getGamingTestFromServer(user)?.jumpToFirst() != null
    }

    fun jumpToLast(user: Player): Boolean {
        return getGamingTestFromServer(user)?.jumpToLast() != null
    }

    fun clearServer() {
        clearGroups(validGroupsServer)
    }

    fun clearClient() {
        clearGroups(validGroupsClient)
    }

    fun clearServerFor(user: Player) {
        clearGroupsFor(validGroupsServer, user)
    }

    fun clearClientFor(user: Player) {
        clearGroupsFor(validGroupsClient, user)
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

    private fun getTest(groups: MutableSet<TestGroup>, user: Player): TestGroup? {
        val active = groups.find { it.getUser() === user }
        if (active != null) {
            return active
        }
        clearStaleMatches(groups, user)
        return null
    }

    private fun clearGroups(groups: MutableSet<TestGroup>) {
        if (groups.isEmpty()) {
            return
        }
        groups.toList().forEach(::cancelGroup)
        groups.clear()
    }

    private fun clearGroupsFor(groups: MutableSet<TestGroup>, user: Player) {
        removeMatchingGroups(groups) { it.getUser().uuid == user.uuid }
    }

    private fun clearStaleMatches(groups: MutableSet<TestGroup>, user: Player) {
        removeMatchingGroups(groups) { it.getUser().uuid == user.uuid && it.getUser() !== user }
    }

    private fun removeMatchingGroups(
        groups: MutableSet<TestGroup>,
        predicate: (TestGroup) -> Boolean
    ) {
        val matched = groups.filter(predicate)
        if (matched.isEmpty()) {
            return
        }
        matched.forEach { group ->
            cancelGroup(group)
            groups.remove(group)
        }
    }

    private fun cancelGroup(group: TestGroup) {
        if (group is GamingTestGroup) {
            group.cancel()
        }
    }
}
