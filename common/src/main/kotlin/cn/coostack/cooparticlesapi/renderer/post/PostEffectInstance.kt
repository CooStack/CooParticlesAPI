package cn.coostack.cooparticlesapi.renderer.post

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import java.util.UUID

data class PostEffectInstance(
    val type: PostEffectType,
    val instanceId: String,
    val binding: PostEffectBinding,
    val lifecycle: PostEffectLifecycle,
    val params: PostEffectParams = PostEffectParams.EMPTY,
    val sourceId: String = "",
    val priority: Int = type.defaultPriority,
    val serverSynced: Boolean = false
) {
    val progress: Float get() = lifecycle.progress
    val expired: Boolean get() = lifecycle.expired

    fun tick(): PostEffectInstance = copy(lifecycle = lifecycle.tick())
    fun bindScreen(): PostEffectInstance = copy(binding = PostEffectBinding.Screen)
    fun bindScreen(x: Float, y: Float): PostEffectInstance = copy(binding = PostEffectBinding.ScreenPoint(x, y))
    fun bindWorld(x: Double, y: Double, z: Double, level: ResourceLocation? = null): PostEffectInstance =
        copy(binding = PostEffectBinding.WorldPos(level, x, y, z))

    fun bindEntity(entityId: Int): PostEffectInstance = copy(binding = PostEffectBinding.Entity(entityId))
    fun bindPlayer(playerId: UUID): PostEffectInstance = copy(binding = PostEffectBinding.Player(playerId))
    fun bindBlock(pos: BlockPos, level: ResourceLocation? = null): PostEffectInstance =
        copy(binding = PostEffectBinding.Block(level, pos))

    fun bindBlock(
        pos: BlockPos,
        level: ResourceLocation? = null,
        offset: PostEffectParamValue.Vec3
    ): PostEffectInstance =
        copy(binding = PostEffectBinding.Block(level, pos, offset))

    fun bindItem(itemId: ResourceLocation? = null, context: PostEffectItemContext): PostEffectInstance =
        copy(binding = PostEffectBinding.Item(itemId, context))

    fun bindCustom(key: ResourceLocation, payload: ByteArray = ByteArray(0)): PostEffectInstance =
        copy(binding = PostEffectBinding.Custom(key, payload))

    fun duration(ticks: Int): PostEffectInstance = copy(lifecycle = lifecycle.copy(durationTicks = ticks))
    fun lifecycle(lifecycle: PostEffectLifecycle): PostEffectInstance = copy(lifecycle = lifecycle)
    fun priority(value: Int): PostEffectInstance = copy(priority = value)
    fun source(id: String): PostEffectInstance = copy(sourceId = id)
    fun param(name: String, value: PostEffectParamValue): PostEffectInstance = copy(params = params.plus(name, value))
    fun params(block: PostEffectParamsBuilder.() -> Unit): PostEffectInstance =
        copy(params = PostEffectParamsBuilder().apply(block).build())

    fun toNetworkState(): SyncedPostEffectState {
        return SyncedPostEffectState(
            effectType = type.id,
            instanceId = instanceId,
            binding = binding,
            lifecycle = lifecycle,
            params = params,
            sourceId = sourceId,
            priority = priority
        )
    }
}

data class SyncedPostEffectState(
    val effectType: ResourceLocation,
    val instanceId: String,
    val binding: PostEffectBinding,
    val lifecycle: PostEffectLifecycle,
    val params: PostEffectParams,
    val sourceId: String,
    val priority: Int
) {
    fun instantiate(serverSynced: Boolean = true): PostEffectInstance? {
        val type = CooPostEffectTypes.get(effectType) ?: return null
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
