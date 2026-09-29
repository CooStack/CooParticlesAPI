package cn.coostack.cooparticlesapi.entities.collision

import net.minecraft.world.phys.AABB

/**
 * 以最长轴中位数划分不可变框集合，减少表面采样和碰撞查询中的无关形状检查。
 * @param indices 当前节点的框下标
 * @property boxes 几何拥有的只读框集合
 */
internal class CollisionBoxTree(indices: List<Int>, private val boxes: List<OrientedBox>) {
    /** 当前节点包含的世界范围。 */
    private val bounds = indices.map { boxes[it].bounds }.reduce(AABB::minmax)
    /** 叶节点持有的框下标，内部节点为空。 */
    private val entries: List<Int>
    /** 两个子树，叶节点均为空。 */
    private val children: List<CollisionBoxTree>

    init {
        if (indices.size <= 8) {
            entries = indices
            children = emptyList()
        } else {
            entries = emptyList()
            val axis = listOf(bounds.xsize, bounds.ysize, bounds.zsize).indices.maxBy {
                listOf(bounds.xsize, bounds.ysize, bounds.zsize)[it]
            }
            val sorted = indices.sortedBy {
                val center = boxes[it].center
                when (axis) { 0 -> center.x; 1 -> center.y; else -> center.z }
            }
            children = listOf(CollisionBoxTree(sorted.take(sorted.size / 2), boxes),
                CollisionBoxTree(sorted.drop(sorted.size / 2), boxes))
        }
    }

    /** 追加与查询范围相交的下标；结果容器由本次查询独占。 */
    fun query(query: AABB, result: MutableList<Int>) {
        if (!bounds.intersects(query)) return
        for (index in entries) if (boxes[index].bounds.intersects(query)) result.add(index)
        children.forEach { it.query(query, result) }
    }
}
