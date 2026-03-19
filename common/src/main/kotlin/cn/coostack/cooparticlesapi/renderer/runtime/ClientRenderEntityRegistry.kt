package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

/**
 * 客户端侧的 RenderEntity 类型表。
 *
 * 它维护两类元数据：
 * - `codec`：收到同步包后如何把字节流解码回临时实体。
 * - `rendererFactory`：客户端如何为该实体创建 renderer/runtime 包装。
 *
 * 一般流程是：
 * 1. 先注册 codec。
 * 2. 再为同一个 id 注册 renderer。
 */
object ClientRenderEntityRegistry {
    private val types = LinkedHashMap<ResourceLocation, ClientRenderEntityType>()

    /**
     * 直接注册一个完整的客户端类型定义。
     *
     * @throws IllegalArgumentException 当同一个 id 被重复注册时抛出
     */
    fun register(id: ResourceLocation, type: ClientRenderEntityType) {
        if (types.containsKey(id)) {
            throw IllegalArgumentException(id.toString())
        }
        types[id] = type
    }

    /**
     * 用 codec 和可选 rendererFactory 注册一个类型。
     *
     * 这是最常见的入口，适合在初始化阶段一次性把“解码能力 + 渲染能力”都挂上去。
     */
    fun register(
        id: ResourceLocation,
        codec: StreamCodec<FriendlyByteBuf, RenderEntity>,
        rendererFactory: (() -> RenderEntityRenderer<out RenderEntity>)? = null
    ) {
        register(id, ClientRenderEntityType(codec, rendererFactory))
    }

    /**
     * 给已经存在的 codec 条目补充 renderer 工厂。
     *
     * 这个方法适合 codec 自动注册、renderer 手动注册的拆分流程。
     *
     * @throws IllegalStateException 当 codec 尚未注册时抛出
     */
    fun registerRenderer(id: ResourceLocation, rendererFactory: () -> RenderEntityRenderer<out RenderEntity>) {
        val existing = types[id]
            ?: throw IllegalStateException("RenderEntity codec not registered: $id")
        types[id] = existing.copy(rendererFactory = rendererFactory)
    }

    /**
     * 读取指定 id 的客户端类型定义。
     *
     * 返回 `null` 表示该 RenderEntity 还没有被客户端注册。
     */
    fun get(id: ResourceLocation): ClientRenderEntityType? {
        return types[id]
    }

    /**
     * 清空注册表。
     *
     * 主要用于测试、重载或重新初始化场景。
     */
    fun clear() {
        types.clear()
    }
}
