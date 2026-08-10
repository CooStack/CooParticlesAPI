package cn.coostack.cooparticlesapi.renderer.post

import net.minecraft.resources.ResourceLocation

/**
 * 一次 Pipeline 后处理播放的内部执行快照。
 *
 * @property subject 本地 uniform provider 读取的对象；不写入网络状态
 * @property uniformOverrides 当前帧已解析的 `(pass, uniform)`；为空时按普通 provider 解析
 */
internal data class PostEffectInstance(
    val type: PostEffectType,
    val instanceId: String,
    val binding: PostEffectBinding,
    val lifecycle: PostEffectLifecycle,
    val params: PostEffectParams = PostEffectParams.EMPTY,
    val sourceId: String = "",
    val priority: Int = type.defaultPriority,
    val serverSynced: Boolean = false,
    val subject: Any = Unit,
    val uniformOverrides: Map<Pair<String, String>, PostEffectParamValue?> = emptyMap()
) {
    val progress: Float get() = lifecycle.progress
    val expired: Boolean get() = lifecycle.expired

    fun tick(): PostEffectInstance = copy(lifecycle = lifecycle.tick())

    fun toNetworkState(): SyncedPostEffectState {
        return SyncedPostEffectState(
            effectType = type.id,
            instanceId = instanceId,
            binding = binding,
            lifecycle = lifecycle,
            params = params,
            uniformNames = type.paramUniformNames,
            sourceId = sourceId,
            priority = priority
        )
    }
}

/** 服务端播放包携带的纯数据快照。 */
internal data class SyncedPostEffectState(
    val effectType: ResourceLocation,
    val instanceId: String,
    val binding: PostEffectBinding,
    val lifecycle: PostEffectLifecycle,
    val params: PostEffectParams,
    val uniformNames: Set<String>,
    val sourceId: String,
    val priority: Int
) {
    fun instantiate(serverSynced: Boolean = true): PostEffectInstance? {
        val type = PostEffectRuntimeRegistry.getType(effectType)
            ?.withParamUniforms(uniformNames)
            ?: return null
        return type.create(
            instanceId = instanceId,
            binding = binding,
            lifecycle = lifecycle,
            params = params,
            sourceId = sourceId,
            priority = priority,
            serverSynced = serverSynced
        )
    }
}
