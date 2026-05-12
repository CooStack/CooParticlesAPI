package cn.coostack.cooparticlesapi.data.cache

import net.minecraft.world.entity.Entity
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.getOrPut

abstract class EntityCacheManager {

    val entities = ConcurrentHashMap<Entity, EntityCacher>()

    fun getOrCreate(entity: Entity): EntityCacher {
        return entities.getOrPut(entity) { EntityCacher() }
    }

    fun remove(entity: Entity) {
        entities.remove(entity)
    }

}