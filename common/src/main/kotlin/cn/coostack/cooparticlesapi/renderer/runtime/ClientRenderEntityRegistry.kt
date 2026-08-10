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
 * - `rendererFactory`：客户端如何惰性创建该类型共享的 renderer。
 *
 * 一般流程是：
 * 1. 先注册 codec。
 * 2. 再为同一个 id 注册 renderer。
 */
object ClientRenderEntityRegistry {
    private val types = LinkedHashMap<ResourceLocation, ClientRenderEntityType>()
    private val automaticEntityClasses = LinkedHashMap<ResourceLocation, Class<out RenderEntity>>()

    /** 每个已注册 RenderEntity id 当前使用的共享 renderer。 */
    private val renderers = LinkedHashMap<ResourceLocation, RenderEntityRenderer<out RenderEntity>>()

    /**
     * 直接注册一个完整的客户端类型定义。
     *
     * @param id RenderEntity 类型的唯一注册 id
     * @param type 该类型使用的 codec 和可选 renderer 工厂
     * @throws IllegalArgumentException 当同一个 id 被重复注册时抛出
     */
    @Synchronized
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
     *
     * @param id RenderEntity 类型的唯一注册 id
     * @param codec 把同步数据解码为客户端实体的 codec
     * @param rendererFactory 惰性创建共享 renderer 的工厂；为空时该类型只支持解码
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
     * 替换工厂会使该 id 的共享 renderer 缓存失效；已经创建的运行时实例仍使用原 renderer。
     *
     * @param id 已注册 codec 的 RenderEntity 类型 id
     * @param rendererFactory 惰性创建共享 renderer 的无参工厂
     * @throws IllegalStateException 当 codec 尚未注册时抛出
     */
    @Synchronized
    fun registerRenderer(id: ResourceLocation, rendererFactory: () -> RenderEntityRenderer<out RenderEntity>) {
        val existing = types[id]
            ?: throw IllegalStateException("RenderEntity codec not registered: $id")
        types[id] = existing.copy(rendererFactory = rendererFactory)
        renderers.remove(id)
    }

    /**
     * 在自动注册器完成全部校验后，一次提交本轮新增或补全的类型。
     *
     * @param registrations 键为 RenderEntity 注册 id，值为已校验的客户端类型及其实体类
     */
    @Synchronized
    internal fun applyRegistrations(registrations: Map<ResourceLocation, AutomaticClientRenderEntityType>) {
        registrations.forEach { (id, registration) ->
            val existing = types[id]
            val existingEntityClass = automaticEntityClasses[id]
            check(existing == null || existingEntityClass == registration.entityClass) {
                "RenderEntity id ownership changed during automatic registration: $id"
            }
        }
        registrations.forEach { (id, registration) ->
            types[id] = registration.type
            automaticEntityClasses[id] = registration.entityClass
            renderers.remove(id)
        }
    }

    /**
     * 返回自动注册条目绑定的实体类型；手动条目没有该元数据。
     *
     * @param id 待查询的 RenderEntity 注册 id
     * @return 自动注册的实体类；条目不存在或由手动注册时返回 `null`
     */
    @Synchronized
    internal fun getAutomaticEntityClass(id: ResourceLocation): Class<out RenderEntity>? {
        return automaticEntityClasses[id]
    }

    /**
     * 读取指定 id 的客户端类型定义。
     *
     * 返回 `null` 表示该 RenderEntity 还没有被客户端注册。
     *
     * @param id 待查询的 RenderEntity 注册 id
     * @return 已注册的客户端类型定义；不存在时返回 `null`
     */
    @Synchronized
    fun get(id: ResourceLocation): ClientRenderEntityType? {
        return types[id]
    }

    /**
     * 惰性创建并返回指定 RenderEntity 类型共享的 renderer。
     *
     * 同一个注册 id 在注册表被清空或替换前始终返回同一实例，避免实体创建时重复构建
     * pipeline、shader 描述和 renderer 级资源。
     *
     * 示例：`ClientRenderEntityRegistry.resolveRenderer(renderEntityId)`。
     *
     * @param id 已注册的 RenderEntity 类型 id
     * @return 共享 renderer；类型不存在或没有注册 renderer 时返回 `null`
     */
    @Synchronized
    fun resolveRenderer(id: ResourceLocation): RenderEntityRenderer<out RenderEntity>? {
        renderers[id]?.let { return it }
        val factory = types[id]?.rendererFactory ?: return null
        return factory().also { renderer ->
            renderers[id] = renderer
        }
    }

    /**
     * 清空注册表。
     *
     * 主要用于测试、重载或重新初始化场景。
     * 该操作只清除类型、自动注册元数据和共享 renderer 缓存，不会移除管理器中的活跃实体实例。
     */
    @Synchronized
    fun clear() {
        types.clear()
        automaticEntityClasses.clear()
        renderers.clear()
    }
}

/** 自动注册器提交给客户端类型表的完整条目。 */
internal data class AutomaticClientRenderEntityType(
    val type: ClientRenderEntityType,
    val entityClass: Class<out RenderEntity>
)
