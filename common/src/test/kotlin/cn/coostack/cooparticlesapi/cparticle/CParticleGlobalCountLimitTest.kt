package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 检查 CParticle 全局数量上限是否覆盖 system 的公开存储入口。
 *
 * Example: 未注册的两个 system 也必须共享同一份额度。
 * Forbidden: 不能只统计 [CParticleSystemManager] 内部注册的 system。
 */
class CParticleGlobalCountLimitTest {
    /**
     * 验证生成占用额度，死亡和清空归还额度。
     *
     * Example: 一个粒子死亡后，另一个 system 可以立即复用这份额度。
     * Forbidden: `system.store.spawn(...)` 不能绕过全局上限。
     */
    @Test
    fun `unregistered systems share and release the global particle budget`() {
        val previousLimit = CParticleSystemManager.particleCountLimit
        val firstSystem = createSystem("global-limit-test-first")
        val secondSystem = createSystem("global-limit-test-second")
        val baseline = CParticleSystemManager.totalAlive()

        try {
            CParticleSystemManager.configureParticleCountLimit(baseline + 1)

            val firstSlot = spawn(firstSystem, maxAge = 1)
            assertTrue(firstSlot >= 0)
            assertEquals(baseline + 1, CParticleSystemManager.totalAlive())
            assertEquals(-1, spawn(secondSystem))

            firstSystem.store.tickAges(writeBufferAge = false)
            assertEquals(baseline, CParticleSystemManager.totalAlive())

            val replacementSlot = spawn(secondSystem)
            assertTrue(replacementSlot >= 0)
            secondSystem.store.kill(replacementSlot)
            assertEquals(baseline, CParticleSystemManager.totalAlive())

            assertTrue(spawn(secondSystem) >= 0)
            secondSystem.store.clear()
            assertEquals(baseline, CParticleSystemManager.totalAlive())
        } finally {
            firstSystem.store.clear()
            secondSystem.store.clear()
            CParticleSystemManager.configureParticleCountLimit(previousLimit)
        }
    }

    /**
     * 创建不注册到 manager 的测试 system。
     *
     * Example: `createSystem("first")` 返回一个容量为 2 的模拟池。
     * Forbidden: 不要通过 manager 创建，否则无法覆盖未注册 system 的绕过路径。
     *
     * @param name 测试 system 名称
     * @return 未注册的 CParticle system
     */
    private fun createSystem(name: String): CParticleSystem = CParticleSystem(
        name,
        2,
        CParticleRenderLayer.TRANSLUCENT,
        CParticleSystemMode.SIMULATED,
    )

    /**
     * 从 system 的公开 store 入口生成测试粒子。
     *
     * Example: `spawn(system, maxAge = 1)` 创建一个下一 tick 到期的粒子。
     * Forbidden: 不要改走 [CParticleSystem.spawn]，本测试需要覆盖低层公开入口。
     *
     * @param system 接收粒子的 system
     * @param maxAge 粒子最大存活 tick
     * @return 分配的槽位，受限或池满时为 `-1`
     */
    private fun spawn(system: CParticleSystem, maxAge: Int = 20): Int {
        val particle = CParticle().apply { this.maxAge = maxAge }
        return system.store.spawn(particle, Vec3.ZERO, 0, 15, 15)
    }
}
