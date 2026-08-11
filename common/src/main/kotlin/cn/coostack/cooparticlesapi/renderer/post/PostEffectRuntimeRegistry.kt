package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectDescriptor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectExecutor
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectRegistry

/**
 * 把 post effect 类型接入通用 [RenderEffectRegistry] 的桥。
 *
 * 新 Pipeline 编译后会调用 [registerType]。frame-post 阶段收到对应 descriptor 时，
 * generic executor 会取出 [PostEffectInstance]，
 * 交给 [PostEffectFrameExecutor] 生成 pass plan 并执行。
 *
 * 这个对象替代调用方为每个 post type 单独注册一个 RenderEffectExecutor 的样板。
 */
internal object PostEffectRuntimeRegistry {
    private val types = LinkedHashMap<net.minecraft.resources.ResourceLocation, PostEffectType>()
    private val genericExecutor = RenderEffectExecutor { context, effects ->
        val instances = effects.mapNotNull { it.toPostEffectInstance() }
        if (instances.isEmpty()) {
            return@RenderEffectExecutor
        }
        PostEffectFrameExecutor.execute(context, instances)
    }

    /** 客户端初始化入口：把启动阶段已编译的 Pipeline 类型接入执行器。 */
    fun initOnClient() {
        types.values.forEach { type ->
            registerType(type)
        }
    }

    /** 把单个 post type 的 id 映射到通用 post executor。 */
    internal fun registerType(type: PostEffectType) {
        types[type.id] = type
        RenderEffectRegistry.register(type.id, genericExecutor)
    }

    /**
     * 从 `PostEffectRuntimeRegistry` 当前维护的状态中读取 `getType` 结果，不创建新的渲染资源。
     *
     * 示例：`getType(id = id)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @return 匹配当前条件的对象或状态；可空返回值表示没有可用结果
     */
    internal fun getType(id: net.minecraft.resources.ResourceLocation): PostEffectType? = types[id]

    /**
     * 执行 `PostEffectRuntimeRegistry` 定义的 `containsType` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`containsType(id = id)`。
     *
     * @param id 用于定位目标资源、实体或运行时实例的唯一标识
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    internal fun containsType(id: net.minecraft.resources.ResourceLocation): Boolean = id in types

    private fun RenderEffectDescriptor.toPostEffectInstance(): PostEffectInstance? {
        return payload as? PostEffectInstance
    }
}
