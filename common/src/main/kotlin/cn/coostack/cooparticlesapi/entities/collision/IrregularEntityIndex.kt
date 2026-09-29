package cn.coostack.cooparticlesapi.entities.collision

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.EntityGetter
import net.minecraft.world.phys.AABB
import java.lang.ref.WeakReference

/**
 * 跟随两端原版实体索引维护不规则实体，不依赖固定范围扫描区块。
 * 集合使用按对象身份比较的弱引用，不延长实体或世界生命周期；查询仅检查当前世界。
 */
object IrregularEntityIndex {
    /** 两端实体的数字 ID 相同，不能作为共用索引的相等依据；锁只保护集合操作。 */
    private val entities = mutableListOf<WeakReference<Entity>>()

    /**
     * 原版成功加入实体索引后登记，普通实体忽略。
     * 仅供生命周期 Mixin 调用，不可用于登记尚未生成的实体。
     * @param entity 已进入原版索引的实体
     */
    @JvmStatic
    fun add(entity: Entity) {
        if (entity !is IrregularCollisionEntity) return
        synchronized(entities) {
            entities.removeAll { it.get() == null }
            if (entities.none { it.get() === entity }) entities.add(WeakReference(entity))
        }
    }

    /**
     * 原版索引卸载时注销，不依赖下一次 tick。
     * @param entity 原版索引实际移除的实体
     */
    @JvmStatic
    fun remove(entity: Entity) {
        synchronized(entities) {
            entities.removeAll { reference -> reference.get().let { it == null || it === entity } }
        }
    }

    /**
     * 从已加载实体中检查当前真实包围盒，调用方继续执行精确几何或射线。
     * 示例：`find(level, player, query)`。
     * @param view 查询所属世界
     * @param excluded 排除的查询来源，可为空
     * @param query 世界空间范围
     * @return 属于当前世界且包围盒相交的已加载不规则实体
     */
    @JvmStatic
    fun find(view: EntityGetter, excluded: Entity?, query: AABB): List<Entity> {
        val candidates = synchronized(entities) {
            entities.removeAll { it.get() == null }
            entities.mapNotNull { it.get() }
        }
        return candidates.filter {
            it !== excluded && it.level() === view && !it.isRemoved && it.boundingBox.intersects(query)
        }
    }
}
