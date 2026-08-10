package cn.coostack.cooparticlesapi.renderer.backend

import com.mojang.blaze3d.pipeline.RenderTarget
import net.minecraft.resources.ResourceLocation

/**
 * 一项命名场景资源。
 *
 * 它把逻辑上的 target id、调试标签和底层 RenderTarget 绑定到一起，
 * 方便 effect 和 backend 用统一名字拿资源。
 */
data class RenderSceneResource(
    /** 场景资源的逻辑 id，通常来自 `RenderSceneTargets`。 */
    val id: ResourceLocation,
    /** 面向日志和调试的可读标签。 */
    val label: String,
    /** 对应的底层 RenderTarget；为空时表示该资源当前只有逻辑声明。 */
    val target: RenderTarget? = null,
    /** FBO 的全部颜色 attachment；普通 Minecraft RenderTarget 只有 attachment 0。 */
    val colorTextureIds: List<Int> = target?.colorTextureId?.let(::listOf) ?: emptyList(),
    /** attachment 0 的兼容读取入口。 */
    val colorTextureId: Int? = colorTextureIds.firstOrNull(),
    /** 从 target 推导出的深度纹理 id。 */
    val depthTextureId: Int? = target?.depthTextureId
) {
    fun colorTextureId(attachment: Int): Int? {
        return colorTextureIds.getOrNull(attachment)
    }
}

/**
 * 一组按 `ResourceLocation` 编址的场景资源表。
 */
class RenderSceneResources private constructor(
    private val resources: LinkedHashMap<ResourceLocation, RenderSceneResource>
) {
    /**
     * 读取指定 id 对应的场景资源。
     */
    operator fun get(id: ResourceLocation): RenderSceneResource? {
        return resources[id]
    }

    /**
     * 判断当前资源表里是否存在某个 target。
     */
    fun contains(id: ResourceLocation): Boolean {
        return resources.containsKey(id)
    }

    /**
     * 返回当前全部资源条目，保留原有插入顺序。
     */
    fun entries(): Collection<RenderSceneResource> {
        return resources.values
    }

    companion object {
        /**
         * 用若干资源条目创建一份新的资源表。
         *
         * 若传入重复 id，后面的资源会覆盖前面的条目。
         */
        fun of(vararg resources: RenderSceneResource): RenderSceneResources {
            val resolved = LinkedHashMap<ResourceLocation, RenderSceneResource>(resources.size)
            resources.forEach { resource ->
                resolved[resource.id] = resource
            }
            return RenderSceneResources(resolved)
        }

        /**
         * 返回一个空资源表。
         */
        fun empty(): RenderSceneResources {
            return RenderSceneResources(linkedMapOf())
        }
    }
}
