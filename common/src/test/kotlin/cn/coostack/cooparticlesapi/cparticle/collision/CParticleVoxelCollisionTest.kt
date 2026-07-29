package cn.coostack.cooparticlesapi.cparticle.collision

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleCpuSimulator
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 验证 CParticle CPU/GPU 共用的方块占用网格碰撞数学。
 *
 * Example: 完整方块命中会修正位置并移除速度的法线分量。
 * Forbidden: 这些测试不声明复杂 `VoxelShape` 的精确行为。
 */
class CParticleVoxelCollisionTest {
    /**
     * 检查共享网格在分桶边缘仍能覆盖 emitter 的活动范围。
     *
     * Example: 测试 emitter 的 2.4 格生成半径和 9 格力场范围不会立即落到网格外。
     * Forbidden: 这个断言不代表粒子离开 64 格网格后仍有碰撞。
     */
    @Test
    fun `collision grid keeps a useful margin around every system origin`() {
        assertEquals(24, CParticleBlockCollisionGrid.DEFAULT_RANGE)
        assertTrue(CParticleBlockCollisionGrid.DEFAULT_RANGE >= 12)
        assertEquals(64, CParticleBlockCollisionGrid.SIZE)
        assertEquals(32 * 1024, CParticleBlockCollisionGrid.WORD_COUNT * Int.SIZE_BYTES)
        assertTrue("traversal < uCollisionSize * 3" in shaderSource())
        assertTrue(
            "collisionGrid?.size" in projectSource(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/simulate/CParticleGpuSimulator.kt"
            )
        )
    }

    /**
     * 验证发射器声明的范围会改变网格尺寸和对齐边界。
     *
     * Example: `range=40` 为一个 16 格原点分桶提供两侧各 40 格余量。
     * Forbidden: 不同范围不能因为最小坐标恰好相同而复用同一网格。
     */
    @Test
    fun `collision grid spec follows the declared emitter range`() {
        val origin = Vec3(17.5, -0.5, 31.75)
        val defaultSpec = CParticleBlockCollisionGridManager.gridSpecFor(origin, 24)
        val expandedSpec = CParticleBlockCollisionGridManager.gridSpecFor(origin, 40)

        assertEquals(64, defaultSpec.size)
        assertEquals(-8, defaultSpec.minX)
        assertEquals(-40, defaultSpec.minY)
        assertEquals(-8, defaultSpec.minZ)
        assertEquals(96, expandedSpec.size)
        assertEquals(-24, expandedSpec.minX)
        assertEquals(-56, expandedSpec.minY)
        assertEquals(-24, expandedSpec.minZ)
        assertFalse(defaultSpec == expandedSpec)

        val smallSameMin = CParticleBlockCollisionGridManager.gridSpecFor(Vec3(17.0, 17.0, 17.0), 16)
        val largeSameMin = CParticleBlockCollisionGridManager.gridSpecFor(Vec3(33.0, 33.0, 33.0), 32)
        assertEquals(smallSameMin.minX, largeSameMin.minX)
        assertEquals(smallSameMin.minY, largeSameMin.minY)
        assertEquals(smallSameMin.minZ, largeSameMin.minZ)
        assertFalse(smallSameMin == largeSameMin)
    }

    /**
     * 验证实例尺寸控制位图边界和 DDA 穿越范围。
     *
     * Example: 扩展到 96 格的网格可以命中旧 64 格边界之外的方块。
     * Forbidden: 查询实例网格时不能继续读取固定 [CParticleBlockCollisionGrid.SIZE]。
     */
    @Test
    fun `voxel trace uses the collision grid instance size`() {
        val grid = CParticleBlockCollisionGrid(0, 0, 0, 96)
        grid.setOccupiedForTest(80, 0, 0, true)
        val result = FloatArray(CParticleVoxelCollision.RESULT_SIZE)

        assertTrue(CParticleVoxelCollision.trace(grid, 79.5f, 0.5f, 0.5f, 1f, 0f, 0f, result))
        assertEquals(0.5f, result[CParticleVoxelCollision.RESULT_TIME], 1e-6f)
        assertTrue(grid.isOccupied(80, 0, 0))
        assertFalse(grid.isOccupied(96, 0, 0))
    }

    /**
     * 验证 DDA 返回首次进入方块的时间和朝外法线。
     *
     * Example: 从 X=0.5 移动一个方块，在 X=1 表面得到 `t=0.5`。
     * Forbidden: 起点所在单元不能同时标记为占用。
     */
    @Test
    fun `voxel trace reports first occupied cell and outward normal`() {
        val grid = CParticleBlockCollisionGrid(0, 0, 0)
        grid.setOccupiedForTest(1, 0, 0, true)
        val result = FloatArray(CParticleVoxelCollision.RESULT_SIZE)

        assertTrue(CParticleVoxelCollision.trace(grid, 0.5f, 0.5f, 0.5f, 1f, 0f, 0f, result))
        assertEquals(0.5f, result[CParticleVoxelCollision.RESULT_TIME], 1e-6f)
        assertEquals(-1f, result[CParticleVoxelCollision.RESULT_NORMAL])
    }

    /**
     * 验证 CPU fallback 与传统 emitter 常用碰撞响应一致。
     *
     * Example: 命中 X 面后 X 速度归零，切向 Y 速度保留。
     * Forbidden: 未设置 blockCollision flag 的粒子不能被网格阻挡。
     */
    @Test
    fun `cpu simulator fixes hit position and removes normal velocity`() {
        val grid = CParticleBlockCollisionGrid(0, 0, 0)
        grid.setOccupiedForTest(1, 0, 0, true)
        val store = CParticleStore(2)
        val colliding = CParticle().apply {
            pos = Vec3(0.5, 0.5, 0.5)
            velocity = Vec3(1.0, 0.2, 0.0)
            blockCollision = true
        }
        val free = CParticle().apply {
            pos = Vec3(0.5, 2.5, 0.5)
            velocity = Vec3(1.0, 0.2, 0.0)
        }
        val collidingSlot = store.spawn(colliding, Vec3.ZERO, 0, 15, 15)
        val freeSlot = store.spawn(free, Vec3.ZERO, 0, 15, 15)

        CParticleCpuSimulator.simulate(
            store,
            FloatArray(0),
            0,
            0.0,
            0.0,
            0.0,
            32f,
            grid,
        )

        val collidingBase = collidingSlot * CParticleStore.STRIDE
        assertEquals(0.93f, store.data[collidingBase], 1e-6f)
        assertEquals(0.6f, store.data[collidingBase + 1], 1e-6f)
        assertEquals(0f, store.data[collidingBase + CParticleStore.OFF_VEL], 1e-6f)
        assertEquals(0.2f, store.data[collidingBase + CParticleStore.OFF_VEL + 1], 1e-6f)

        val freeBase = freeSlot * CParticleStore.STRIDE
        assertEquals(1.5f, store.data[freeBase], 1e-6f)
        assertEquals(2.7f, store.data[freeBase + 1], 1e-6f)
        assertFalse(store.data[freeBase + CParticleStore.OFF_FLAGS].toInt() and
                CParticleStore.FLAG_BLOCK_COLLISION != 0)
    }

    /**
     * 验证碰撞粒子计数随槽位生命周期变化。
     *
     * Example: 最后一个碰撞粒子被 kill 后 system 不再准备网格。
     * Forbidden: 非碰撞粒子不能增加计数。
     */
    @Test
    fun `store tracks only living block collision particles`() {
        val store = CParticleStore(2)
        val collisionSlot = store.spawn(
            CParticle().apply { blockCollision = true },
            Vec3.ZERO,
            0,
            15,
            15,
        )
        store.spawn(CParticle(), Vec3.ZERO, 0, 15, 15)

        assertEquals(1, store.blockCollisionCount)
        store.kill(collisionSlot)
        assertEquals(0, store.blockCollisionCount)
    }

    /**
     * 验证 DYNAMIC 只切换实例碰撞位，不创建 controller 或改变实例布局。
     *
     * Example: 调用方保留 CParticle 后可在下一次视觉准备阶段关闭碰撞。
     * Forbidden: STATIC 粒子不会观察生成后的字段修改。
     */
    @Test
    fun `dynamic particle can toggle collision flag without respawn`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply {
            updateMode = CParticleUpdateMode.DYNAMIC
        }
        val slot = store.spawn(particle, Vec3.ZERO, 0, 15, 15)
        val flagsOffset = slot * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS

        particle.blockCollision = true
        assertEquals(1, store.prepareDynamicVisuals(0) { _, _ -> 0 })
        assertEquals(1, store.blockCollisionCount)
        assertTrue(store.data[flagsOffset].toInt() and CParticleStore.FLAG_BLOCK_COLLISION != 0)

        particle.blockCollision = false
        assertEquals(1, store.prepareDynamicVisuals(1) { _, _ -> 0 })
        assertEquals(0, store.blockCollisionCount)
        assertFalse(store.data[flagsOffset].toInt() and CParticleStore.FLAG_BLOCK_COLLISION != 0)
    }

    /** 读取 compute shader 源码，验证动态网格契约。 */
    private fun shaderSource(): String = projectSource(
        "common/src/main/resources/assets/cooparticlesapi/shaders/core/compute/cparticle_sim.comp"
    )

    /** 从仓库根目录读取契约测试所需的源码。 */
    private fun projectSource(relativePath: String): String {
        var cursor = java.nio.file.Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null && !java.nio.file.Files.exists(cursor.resolve("settings.gradle"))) {
            cursor = cursor.parent
        }
        return java.nio.file.Files.readString(cursor.resolve(relativePath))
    }
}
