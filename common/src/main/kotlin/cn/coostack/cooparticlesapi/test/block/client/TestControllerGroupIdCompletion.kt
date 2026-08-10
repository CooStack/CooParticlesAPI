package cn.coostack.cooparticlesapi.test.block.client

import net.minecraft.resources.ResourceLocation

/**
 * 按注册顺序提取测试组使用的 MOD 命名空间。
 *
 * 示例：`demo:first`、`demo:second` 只产生一个 `demo`。
 *
 * @param registeredIds 服务端同步的测试组资源 ID
 * @return 去重后按注册顺序排列的 MOD_ID
 */
internal fun testControllerModIds(registeredIds: List<ResourceLocation>): List<String> {
    return registeredIds.map { id -> id.namespace }.distinct()
}

/**
 * 搜索 MOD_ID 补全项；完整命中后停止补全，以便焦点切换到 path。
 *
 * @param registeredIds 服务端同步的测试组资源 ID
 * @param modIdInput MOD_ID 输入框的当前文本
 * @return 未完整命中时可用的 MOD_ID 候选
 */
internal fun testControllerModIdSuggestions(
    registeredIds: List<ResourceLocation>,
    modIdInput: String
): List<String> {
    val modIds = testControllerModIds(registeredIds)
    val input = modIdInput.trim()
    if (modIds.any { modId -> modId.equals(input, ignoreCase = true) }) {
        return emptyList()
    }
    return searchTestControllerSuggestions(modIds, input)
}

/**
 * 返回完整 MOD_ID 下已经注册的测试组 path。
 *
 * MOD_ID 仅输入一部分时返回空列表，path 补全不会提前开始。
 *
 * @param registeredIds 服务端同步的测试组资源 ID
 * @param modIdInput MOD_ID 输入框的当前文本
 * @return MOD_ID 完整匹配时可用的 path
 */
internal fun testControllerPaths(registeredIds: List<ResourceLocation>, modIdInput: String): List<String> {
    val namespace = testControllerModIds(registeredIds)
        .firstOrNull { modId -> modId.equals(modIdInput.trim(), ignoreCase = true) }
        ?: return emptyList()
    return registeredIds.asSequence()
        .filter { id -> id.namespace == namespace }
        .map { id -> id.path }
        .toList()
}

/**
 * 查找完整 MOD_ID 下可直接写入输入框的唯一 path。
 *
 * @param registeredIds 服务端同步的测试组资源 ID
 * @param modIdInput MOD_ID 输入框的当前文本
 * @return 仅有一个已注册 path 时返回该 path，否则返回 `null`
 */
internal fun testControllerSinglePath(registeredIds: List<ResourceLocation>, modIdInput: String): String? {
    return testControllerPaths(registeredIds, modIdInput).singleOrNull()
}

/**
 * 将 MOD_ID 与 path 规范化为完整资源 ID。
 *
 * 已注册的大小写写法会恢复为注册值；未注册但语法合法的 ID 仍会保留。
 *
 * @param registeredIds 服务端同步的测试组资源 ID
 * @param modIdInput MOD_ID 输入框的当前文本
 * @param pathInput path 输入框的当前文本
 * @return 语法合法的完整资源 ID；输入无效时返回 `null`
 */
internal fun testControllerGroupId(
    registeredIds: List<ResourceLocation>,
    modIdInput: String,
    pathInput: String
): ResourceLocation? {
    val rawModId = modIdInput.trim()
    val rawPath = pathInput.trim()
    if (rawModId.isBlank() || rawPath.isBlank()) {
        return null
    }
    val modId = testControllerModIds(registeredIds)
        .firstOrNull { candidate -> candidate.equals(rawModId, ignoreCase = true) }
        ?: rawModId
    val path = testControllerPaths(registeredIds, modId)
        .firstOrNull { candidate -> candidate.equals(rawPath, ignoreCase = true) }
        ?: rawPath
    return ResourceLocation.tryParse("$modId:$path")
}

/**
 * 检查当前 MOD_ID 文本是否能组成资源命名空间。
 *
 * @param modIdInput MOD_ID 输入框的当前文本
 * @return 文本可作为资源命名空间时返回 `true`
 */
internal fun isTestControllerModIdValid(modIdInput: String): Boolean {
    val modId = modIdInput.trim()
    if (modId.isBlank()) {
        return false
    }
    return ResourceLocation.tryParse("$modId:placeholder")?.namespace == modId
}
