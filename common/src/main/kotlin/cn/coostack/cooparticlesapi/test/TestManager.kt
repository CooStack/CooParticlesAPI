package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.extend.ofID
import cn.coostack.cooparticlesapi.test.api.TestGroup
import cn.coostack.cooparticlesapi.test.api.TestGroupBuilder
import cn.coostack.cooparticlesapi.test.api.TestOptionParamSpec
import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import cn.coostack.cooparticlesapi.test.block.BlockTestPlayer
import cn.coostack.cooparticlesapi.test.block.builtin.BlockAPITestGroupBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player

/**
 * 注册并运行测试组。
 *
 * 注册 ID 使用完整的 [ResourceLocation]，不同 MOD 可以安全地复用相同 path。
 */
object TestManager {

    /** 按资源 ID 保存测试组构建器，迭代顺序与注册顺序一致。 */
    val builders = linkedMapOf<ResourceLocation, (Player) -> TestGroupBuilder>()

    val validGroupsServer = HashSet<TestGroup>()
    val validGroupsClient = HashSet<TestGroup>()
    private var builtinsRegistered = false

    /**
     * 注册一个测试组构建器。
     *
     * @param id 包含 MOD 命名空间和 path 的测试组 ID
     * @param group 根据玩家创建测试组构建器的工厂
     */
    fun register(id: ResourceLocation, group: (Player) -> TestGroupBuilder) {
        builders[id] = group
    }

    /** 注册 CooParticlesAPI 自带的普通测试组和方块测试组。 */
    fun registerBuiltins() {
        if (builtinsRegistered) {
            return
        }
        builtinsRegistered = true
        register(ofID(BlockAPITestGroupBuilder.ID)) {
            BlockAPITestGroupBuilder(it)
        }
        register(ofID(APITestGroupBuilder.ID)) {
            APITestGroupBuilder(it)
        }
    }

    /** @return 按注册顺序排列的全部测试组资源 ID */
    fun registeredIds(): List<ResourceLocation> {
        return builders.keys.toList()
    }

    /**
     * @param user 用于验证构建结果的方块测试玩家
     * @return 可为该玩家构建的测试组资源 ID
     */
    fun registeredBlockIds(user: BlockTestPlayer): List<ResourceLocation> {
        return builders.keys.filter { id -> buildBlock(id, user) != null }
    }

    /**
     * @param user 用于验证构建结果的玩家
     * @return 可为该玩家构建的方块测试组资源 ID
     */
    fun registeredBlockIds(user: Player): List<ResourceLocation> {
        return registeredBlockIds(user as? BlockTestPlayer ?: BlockTestPlayer(user))
    }

    /**
     * @param id 待检查的测试组资源 ID
     * @return 指定资源 ID 是否已经注册
     */
    fun contains(id: ResourceLocation): Boolean {
        return builders.containsKey(id)
    }

    /**
     * @param id 待检查的测试组资源 ID
     * @param user 用于构建的方块测试玩家
     * @return 该 ID 是否能构建方块测试组
     */
    fun containsBlock(id: ResourceLocation, user: BlockTestPlayer): Boolean {
        return buildBlock(id, user) != null
    }

    /**
     * @param id 待检查的测试组资源 ID
     * @param user 用于构建的玩家
     * @return 该 ID 是否能构建方块测试组
     */
    fun containsBlock(id: ResourceLocation, user: Player): Boolean {
        return buildBlock(id, user) != null
    }

    /**
     * @param id 待构建的测试组资源 ID
     * @param user 测试组使用的玩家
     * @return 已构建的测试组；ID 未注册时返回 `null`
     */
    fun build(id: ResourceLocation, user: Player): TestGroup? {
        return builders[id]?.invoke(user)?.build()
    }

    /**
     * @param id 待构建的测试组资源 ID
     * @param user 测试组使用的方块测试玩家
     * @return 已构建的方块测试组；目标不是方块测试组时返回 `null`
     */
    fun buildBlock(id: ResourceLocation, user: BlockTestPlayer): BlockTestGroup? {
        return build(id, user) as? BlockTestGroup
    }

    /**
     * @param id 待构建的测试组资源 ID
     * @param user 测试组使用的玩家
     * @return 已构建的方块测试组；目标不是方块测试组时返回 `null`
     */
    fun buildBlock(id: ResourceLocation, user: Player): BlockTestGroup? {
        return build(id, user as? BlockTestPlayer ?: BlockTestPlayer(user)) as? BlockTestGroup
    }

    /**
     * @param id 方块测试组资源 ID
     * @param user 测试组使用的方块测试玩家
     * @return 测试项数量，无法构建时返回 `0`
     */
    fun optionCount(id: ResourceLocation, user: BlockTestPlayer): Int {
        return buildBlock(id, user)?.optionCount() ?: 0
    }

    /**
     * @param id 方块测试组资源 ID
     * @param user 测试组使用的玩家
     * @return 测试项数量，无法构建时返回 `0`
     */
    fun optionCount(id: ResourceLocation, user: Player): Int {
        return buildBlock(id, user)?.optionCount() ?: 0
    }

    /**
     * @param id 方块测试组资源 ID
     * @param user 测试组使用的方块测试玩家
     * @return 测试项 ID，无法构建时返回空列表
     */
    fun optionIds(id: ResourceLocation, user: BlockTestPlayer): List<String> {
        return buildBlock(id, user)?.optionIds() ?: emptyList()
    }

    /**
     * @param id 方块测试组资源 ID
     * @param user 测试组使用的玩家
     * @return 测试项 ID，无法构建时返回空列表
     */
    fun optionIds(id: ResourceLocation, user: Player): List<String> {
        return buildBlock(id, user)?.optionIds() ?: emptyList()
    }

    /**
     * @param id 方块测试组资源 ID
     * @param user 测试组使用的方块测试玩家
     * @return 参数定义，无法构建时返回空列表
     */
    fun optionParamSpecs(id: ResourceLocation, user: BlockTestPlayer): List<List<TestOptionParamSpec<*>>> {
        return buildBlock(id, user)?.optionParamSpecs() ?: emptyList()
    }

    /**
     * @param id 方块测试组资源 ID
     * @param user 测试组使用的玩家
     * @return 参数定义，无法构建时返回空列表
     */
    fun optionParamSpecs(id: ResourceLocation, user: Player): List<List<TestOptionParamSpec<*>>> {
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

    /**
     * 为玩家启动指定测试组，并清理该玩家已有的测试。
     *
     * @param id 待启动的测试组资源 ID
     * @param user 运行测试的玩家
     * @return 已启动的测试组；ID 未注册或构建失败时返回 `null`
     */
    fun startTest(id: ResourceLocation, user: Player): TestGroup? {
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
