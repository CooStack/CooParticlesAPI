package cn.coostack.cooparticlesapi.barrages

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap

/**
 * 弹幕注册中心。
 *
 * 内部按 world + chunk 做空间索引: [collectClipBarrages] 只扫描与 AABB 相交的 chunk，
 * 而不是遍历所有弹幕。
 */
object BarrageManager {
    /** world -> (chunkLong -> 该 chunk 内的弹幕集合) */
    private val byWorld = ConcurrentHashMap<ServerLevel, ConcurrentHashMap<Long, MutableSet<Barrage>>>()

    /** 每个 barrage 当前所在的 chunkLong (用于移动时迁移)。 */
    private val chunkOf = ConcurrentHashMap<Barrage, Long>()

    /** 所有有效弹幕，O(1) 加/删，O(n) 遍历。 */
    private val all = ConcurrentHashMap.newKeySet<Barrage>()

    /** 与 [box] 相交的全部弹幕 (排除 noclip 状态)。 */
    fun collectClipBarrages(world: ServerLevel, box: AABB): List<Barrage> {
        val worldIdx = byWorld[world] ?: return emptyList()
        val res = ArrayList<Barrage>()
        val minX = box.minX.toInt() shr 4
        val maxX = box.maxX.toInt() shr 4
        val minZ = box.minZ.toInt() shr 4
        val maxZ = box.maxZ.toInt() shr 4
        for (cx in minX..maxX) for (cz in minZ..maxZ) {
            val bucket = worldIdx[ChunkPos.asLong(cx, cz)] ?: continue
            for (b in bucket) {
                if (!b.valid || b.noclip()) continue
                if (box.contains(b.loc) || box.intersects(b.hitBox.get().ofBox(b.loc))) {
                    res.add(b)
                }
            }
        }
        return res
    }

    fun spawn(barrage: Barrage) {
        if (!all.add(barrage)) return
        index(barrage)
        barrage.bindControl.get().spawn(barrage.world, barrage.loc)
        barrage.lunch = true
    }

    /**
     * DSL 入口: 不需要继承 [AbstractBarrage] 直接生成弹幕。
     *
     * @see BarrageSpec
     */
    fun spawn(world: ServerLevel, loc: Vec3, block: BarrageSpec.() -> Unit): LambdaBarrage {
        val spec = BarrageSpec(world, loc).apply(block)
        val barrage = spec.build()
        spawn(barrage)
        return barrage
    }

    /** 显式销毁。等价于让 `valid = false` + 移除控制器 + 从索引中删除。 */
    fun despawn(barrage: Barrage) {
        if (barrage is AbstractBarrage) {
            barrage.remove()
        }
        unindex(barrage)
        all.remove(barrage)
    }

    /** 销毁某个世界中的全部弹幕。 */
    fun despawnAll(world: ServerLevel) {
        val worldIdx = byWorld.remove(world) ?: return
        worldIdx.values.flatten().forEach {
            if (it is AbstractBarrage) it.remove()
            chunkOf.remove(it)
            all.remove(it)
        }
    }

    /** 遍历某个世界的全部弹幕。 */
    fun forEach(world: ServerLevel, action: (Barrage) -> Unit) {
        val worldIdx = byWorld[world] ?: return
        worldIdx.values.forEach { bucket -> bucket.forEach(action) }
    }

    /** 弹幕总数 (主要用于调试/监控)。 */
    fun count(): Int = all.size

    /**
     * 由外部 tick 钩子调用。每 tick 一次。
     */
    fun doTick() {
        val it = all.iterator()
        while (it.hasNext()) {
            val b = it.next()
            b.tick()
            if (!b.valid) {
                unindex(b)
                it.remove()
                continue
            }
            // 移动可能跨 chunk -> 重新建索引
            val newKey = chunkKey(b.loc.x, b.loc.z)
            val oldKey = chunkOf[b]
            if (oldKey != newKey) {
                if (oldKey != null) bucketOf(b.world, oldKey)?.remove(b)
                bucketOrCreate(b.world, newKey).add(b)
                chunkOf[b] = newKey
            }
        }
    }

    private fun index(barrage: Barrage) {
        val key = chunkKey(barrage.loc.x, barrage.loc.z)
        bucketOrCreate(barrage.world, key).add(barrage)
        chunkOf[barrage] = key
    }

    private fun unindex(barrage: Barrage) {
        val key = chunkOf.remove(barrage) ?: return
        bucketOf(barrage.world, key)?.remove(barrage)
    }

    private fun bucketOf(world: ServerLevel, key: Long): MutableSet<Barrage>? =
        byWorld[world]?.get(key)

    private fun bucketOrCreate(world: ServerLevel, key: Long): MutableSet<Barrage> {
        val worldIdx = byWorld.computeIfAbsent(world) { ConcurrentHashMap() }
        return worldIdx.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }
    }

    private fun chunkKey(x: Double, z: Double): Long =
        ChunkPos.asLong(x.toInt() shr 4, z.toInt() shr 4)
}
