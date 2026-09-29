package cn.coostack.cooparticlesapi.entities

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.entities.structure.StructureModelEntity
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredEntityType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

/**
 * 收集 API 内置实体类型，两个加载器在各自注册阶段消费 [types]。
 * 新类型在此通过 [register] 声明；注册句柄复用同一个 EntityType 实例。
 */
object CooModEntityTypes {
    /** 提供给两个加载器注册的实体类型集合。 */
    val types = HashSet<CommonDeferredEntityType<*>>()


    /** 原有的渲染测试实体类型。 */
    val TEST_RENDER = register("test_render") { entityId ->
        EntityType.Builder.of(::TestRenderEntity, MobCategory.MISC)
            .sized(0.1f, 0.1f)
            .clientTrackingRange(16)
            .build(entityId.toString())
    }

    /** 静态结构不规则碰撞实体，两端共享注册实例。 */
    val STRUCTURE_MODEL = register("structure_model") { entityId ->
        EntityType.Builder.of(::StructureModelEntity, MobCategory.MISC)
            .sized(1F, 1F).clientTrackingRange(10).updateInterval(1).build(entityId.toString())
    }

    /**
     * 收集使用 API 命名空间的实体类型，供加载器注册阶段消费。
     * 必须在注册阶段前调用；实体的属性与客户端渲染器由对应入口另行注册。
     * @param id API 命名空间内的实体路径
     * @param T 被注册的实体类型
     * @param supplier 接收完整 ID 并构建实体类型的工厂
     * @return 延迟创建且复用同一实体类型实例的句柄
     */
    fun <T : Entity> register(
        id: String,
        supplier: (ResourceLocation) -> EntityType<T>
    ): CommonDeferredEntityType<T> {
        val entityId = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, id)
        val type =
            CommonDeferredEntityType(entityId) { supplier(entityId) }
        types.add(type)
        return type
    }
}
