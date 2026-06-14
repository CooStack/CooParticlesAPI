package cn.coostack.cooparticlesapi.entities

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredEntityType
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import java.util.function.Supplier

/**
 * 这个实体类型是用来做着色器 渲染等模型测试用的
 * 从而从中找到一些灵感 并更新自定义的渲染管道
 */
object CooModEntityTypes {
    val types = HashSet<CommonDeferredEntityType<*>>()


    val TEST_RENDER = register("test_render") { entityId ->
        EntityType.Builder.of(::TestRenderEntity, MobCategory.MISC)
            .sized(0.1f, 0.1f)
            .clientTrackingRange(16)
            .build(entityId.toString())
    }

    fun <T : Entity> register(
        id: String,
        supplier: (ResourceLocation) -> EntityType<T>
    ): CommonDeferredEntityType<T> {
        val entityId = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, id)
        val type =
            CommonDeferredEntityType(entityId, Supplier { supplier(entityId) })
        types.add(type)
        return type
    }
}
 