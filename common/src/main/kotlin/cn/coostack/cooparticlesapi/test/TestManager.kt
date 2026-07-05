package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestGroupBuilder
import cn.coostack.cooparticlesapi.test.api.TestOptionParamSpec
import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import cn.coostack.cooparticlesapi.test.block.BlockTestPlayer
import cn.coostack.cooparticlesapi.test.block.builtin.BlockAPITestGroupBuilder
import net.minecraft.world.entity.player.Player

object TestManager {

    val builders = linkedMapOf<String, (Player) -> TestGroupBuilder>()

    val validGroupsServer = HashSet<TestGroup>()
    val validGroupsClient = HashSet<TestGroup>()
    private var builtinsRegistered = false

    fun register(id: String, group: (Player) -> TestGroupBuilder) {
        builders[id] = group
    }

    fun registerBuiltins() {
        if (builtinsRegistered) {
            return
        }
        builtinsRegistered = true
        register(BlockAPITestGroupBuilder.ID) { BlockAPITestGroupBuilder(it) }
        register(APITestGroupBuilder.ID) {
            APITestGroupBuilder(it)
        }
    }

    fun registeredIds(): List<String> {
        return builders.keys.toList()
    }

    fun registeredBlockIds(user: BlockTestPlayer): List<String> {
        return builders.keys.filter { id -> buildBlock(id, user) != null }
    }

    fun registeredBlockIds(user: Player): List<String> {
        return registeredBlockIds(user as? BlockTestPlayer ?: BlockTestPlayer(user))
    }

    fun contains(id: String): Boolean {
        return builders.containsKey(id)
    }

    fun containsBlock(id: String, user: BlockTestPlayer): Boolean {
        return buildBlock(id, user) != null
    }

    fun containsBlock(id: String, user: Player): Boolean {
        return buildBlock(id, user) != null
    }

    fun build(id: String, user: Player): TestGroup? {
        return builders[id]?.invoke(user)?.build()
    }

    fun buildBlock(id: String, user: BlockTestPlayer): BlockTestGroup? {
        return build(id, user) as? BlockTestGroup
    }

    fun buildBlock(id: String, user: Player): BlockTestGroup? {
        return build(id, user as? BlockTestPlayer ?: BlockTestPlayer(user)) as? BlockTestGroup
    }

    fun optionCount(id: String, user: BlockTestPlayer): Int {
        return buildBlock(id, user)?.optionCount() ?: 0
    }

    fun optionCount(id: String, user: Player): Int {
        return buildBlock(id, user)?.optionCount() ?: 0
    }

    fun optionIds(id: String, user: BlockTestPlayer): List<String> {
        return buildBlock(id, user)?.optionIds() ?: emptyList()
    }

    fun optionIds(id: String, user: Player): List<String> {
        return buildBlock(id, user)?.optionIds() ?: emptyList()
    }

    fun optionParamSpecs(id: String, user: BlockTestPlayer): List<List<TestOptionParamSpec<*>>> {
        return buildBlock(id, user)?.optionParamSpecs() ?: emptyList()
    }

    fun optionParamSpecs(id: String, user: Player): List<List<TestOptionParamSpec<*>>> {
        return buildBlock(id, user)?.optionParamSpecs() ?: emptyList()
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
        val group = build(id, user) ?: return null
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
