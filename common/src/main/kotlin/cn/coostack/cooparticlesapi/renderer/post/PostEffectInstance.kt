package cn.coostack.cooparticlesapi.renderer.post

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * 一个正在运行或即将发送的 post effect 实例。
 *
 * [PostEffectType] 是“效果类型定义”，[PostEffectInstance] 是“某一次效果播放”。
 * 同一个 type 可以创建很多 instance，每个 instance 有自己的生命周期、绑定位置、参数和优先级。
 *
 * 常见用法：
 *
 * ```kotlin
 * val shockwave = BuiltinPostEffectTypes.SHOCKWAVE.create()
 *     .bindBlock(pos, level.dimension().location(), PostEffectParamValue.Vec3Value(0.5, 1.0, 0.5))
 *     .duration(30)
 *     .params {
 *         float("radius", 0.35f)
 *         float("strength", 0.08f)
 *     }
 *
 * CooPostEffects.server.spawn(serverLevel, shockwave)
 * ```
 *
 * 这个类替代了调用方手动维护“当前效果 age/progress、packet 状态、绑定目标、参数表”的样板。
 */
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
    /** 生命周期归一化进度，范围 0..1。 */
    val progress: Float get() = lifecycle.progress
    /** 是否已经到达 duration，应从 client runtime 中移除。 */
    val expired: Boolean get() = lifecycle.expired

    /** 推进一 tick；client runtime 每 tick 调用。 */
    fun tick(): PostEffectInstance = copy(lifecycle = lifecycle.tick())
    /** 绑定整屏效果。 */
    fun bindScreen(): PostEffectInstance = copy(binding = PostEffectBinding.Screen)
    /** 绑定屏幕归一化坐标。 */
    fun bindScreen(x: Float, y: Float): PostEffectInstance = copy(binding = PostEffectBinding.ScreenPoint(x, y))
    /** 绑定世界坐标。 */
    fun bindWorld(x: Double, y: Double, z: Double, level: ResourceLocation? = null): PostEffectInstance =
        copy(binding = PostEffectBinding.WorldPos(level, x, y, z))

    /** 绑定当前客户端世界中的实体 id。 */
    fun bindEntity(entityId: Int): PostEffectInstance = copy(binding = PostEffectBinding.Entity(entityId))
    /** 绑定玩家 UUID。 */
    fun bindPlayer(playerId: UUID): PostEffectInstance = copy(binding = PostEffectBinding.Player(playerId))
    /** 绑定方块中心。 */
    fun bindBlock(pos: BlockPos, level: ResourceLocation? = null): PostEffectInstance =
        copy(binding = PostEffectBinding.Block(level, pos))

    /** 绑定方块内的指定偏移点。 */
    fun bindBlock(
        pos: BlockPos,
        level: ResourceLocation? = null,
        offset: PostEffectParamValue.Vec3Value
    ): PostEffectInstance =
        copy(binding = PostEffectBinding.Block(level, pos, offset))

    /** 绑定物品语义。 */
    fun bindItem(itemId: ResourceLocation? = null, context: PostEffectItemContext): PostEffectInstance =
        copy(binding = PostEffectBinding.Item(itemId, context))

    /** 绑定自定义业务语义。默认 backend 不解析 payload，只负责同步保存。 */
    fun bindCustom(key: ResourceLocation, payload: ByteArray = ByteArray(0)): PostEffectInstance =
        copy(binding = PostEffectBinding.Custom(key, payload))

    /** 设置实例总时长。 */
    fun duration(ticks: Int): PostEffectInstance = copy(lifecycle = lifecycle.copy(durationTicks = ticks))
    /** 直接替换完整生命周期。 */
    fun lifecycle(lifecycle: PostEffectLifecycle): PostEffectInstance = copy(lifecycle = lifecycle)
    /** 设置执行优先级。数值含义由 RenderEffectGraph 排序策略决定。 */
    fun priority(value: Int): PostEffectInstance = copy(priority = value)
    /** 标记来源 id，便于调试、合并或业务侧追踪。 */
    fun source(id: String): PostEffectInstance = copy(sourceId = id)
    /** 添加或覆盖一个参数。 */
    fun param(name: String, value: PostEffectParamValue): PostEffectInstance = copy(params = params.plus(name, value))
    /** 使用 builder 批量设置参数。 */
    fun params(block: PostEffectParamsBuilder.() -> Unit): PostEffectInstance =
        copy(params = PostEffectParamsBuilder().apply(block).build())

    /** 转成网络同步状态。服务端发送时使用，客户端收到后再恢复成 instance。 */
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

/**
 * post effect 的网络同步状态。
 *
 * 它只保存跨网络需要传输的数据，不保存 [PostEffectType] 对象本身。
 * 客户端收到后通过 [effectType] 去 [CooPostEffectTypes] 查找已注册类型。
 */
data class SyncedPostEffectState(
    val effectType: ResourceLocation,
    val instanceId: String,
    val binding: PostEffectBinding,
    val lifecycle: PostEffectLifecycle,
    val params: PostEffectParams,
    val sourceId: String,
    val priority: Int
) {
    /** 用客户端已注册的 [PostEffectType] 恢复运行时实例；类型未注册时返回 null。 */
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
