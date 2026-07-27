package cn.coostack.cooparticlesapi.cparticle.collision

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.Vec3
import kotlin.math.floor

/**
 * 按客户端世界和 system 原点分区复用 CParticle 方块碰撞网格。
 *
 * Example: 同一 emitter 因纹理拆成多个 system 时仍只构建一份占用位图。
 * Forbidden: 不要跨世界保留网格；[beginTick] 会在世界身份变化时清理。
 */
internal object CParticleBlockCollisionGridManager {
    /** 当前网格所属的客户端世界实例。 */
    private var level: ClientLevel? = null

    /** 当前 CParticle manager tick。 */
    private var tick = Int.MIN_VALUE

    /** 以对齐后的最小坐标为键的共享网格。 */
    private val grids = HashMap<Triple<Int, Int, Int>, CParticleBlockCollisionGrid>()

    /**
     * 开始一次 CParticle system 更新。
     *
     * Example: manager 在遍历 system 前传入当前客户端世界。
     * Forbidden: 世界为 `null` 时不能继续使用旧网格。
     *
     * @param activeLevel 当前客户端世界；断线时为 `null`
     * @param managerTick 当前 manager tick
     */
    fun beginTick(activeLevel: ClientLevel?, managerTick: Int) {
        if (level !== activeLevel) {
            clear()
            level = activeLevel
        }
        tick = managerTick
    }

    /**
     * 获取并刷新覆盖指定 system 原点的共享网格。
     *
     * Example: 只有 store 中存在碰撞粒子时才调用。
     * Forbidden: [beginTick] 未提供世界时返回 `null`，不会构造空占位资源。
     *
     * @param origin CParticle system 世界原点
     * @return 可供本 tick CPU/GPU 模拟读取的网格
     */
    fun gridFor(origin: Vec3): CParticleBlockCollisionGrid? {
        val activeLevel = level ?: return null
        val minX = alignedMin(origin.x)
        val minY = alignedMin(origin.y)
        val minZ = alignedMin(origin.z)
        val grid = grids.getOrPut(Triple(minX, minY, minZ)) {
            CParticleBlockCollisionGrid(minX, minY, minZ)
        }
        grid.lastUsedTick = tick
        grid.refreshIfNeeded(activeLevel)
        return grid
    }

    /**
     * 释放本 tick 未再使用的网格。
     *
     * Example: 最后一批碰撞粒子死亡后，其 SSBO 在当前 tick 末尾销毁。
     * Forbidden: 必须在渲染线程调用，因为淘汰可能删除 GL buffer。
     */
    fun endTick() {
        val iterator = grids.values.iterator()
        while (iterator.hasNext()) {
            val grid = iterator.next()
            if (grid.lastUsedTick != tick) {
                grid.release()
                iterator.remove()
            }
        }
    }

    /**
     * 清理全部世界占用数据和 GL 缓冲。
     *
     * Example: 断线、换维度或 CParticle 全量释放时调用。
     * Forbidden: 清理后旧 grid 引用不可再次绑定。
     */
    fun clear() {
        grids.values.forEach(CParticleBlockCollisionGrid::release)
        grids.clear()
        level = null
    }

    /** 把世界坐标映射到带边缘余量的共享网格最小坐标。 */
    private fun alignedMin(value: Double): Int {
        val block = floor(value).toInt()
        val bucket = Math.floorDiv(block, CParticleBlockCollisionGrid.ORIGIN_BUCKET_SIZE)
        return bucket * CParticleBlockCollisionGrid.ORIGIN_BUCKET_SIZE -
                CParticleBlockCollisionGrid.EDGE_MARGIN
    }
}
