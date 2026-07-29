package cn.coostack.cooparticlesapi.network.particle.composition

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleSprites
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestCParticleComposition
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 验证 composition 分批生成 GPU 粒子时正确区分世界坐标和槽位局部坐标。
 *
 * 示例：整组 GPU 变换启用后，显示器仍收到粒子的真实世界坐标。
 * 禁止把未经逆变换的世界坐标直接写入已有整组矩阵的 system。
 */
class ParticleCompositionGpuSpawnPositionTest {
    /**
     * 验证移动后的 managed GPU 粒子只应用一次整组平移。
     *
     * 示例：粒子在世界坐标 `(17, 25, 31)` 采样环境，槽位中保存相对 system 的 `(2, 3, 4)`。
     * 禁止在 GPU 模式回落后忽略 system 中仍然生效的 [CParticleSystem.groupTransform]。
     */
    @Test
    fun `managed gpu spawn keeps the initial system origin after composition moves`() {
        val initialPosition = Vec3(10.0, 20.0, 30.0)
        val movedPosition = Vec3(15.0, 22.0, 27.0)
        val relative = RelativeLocation(2.0, 3.0, 4.0)
        val system = CParticleSystem(
            name = "composition-spawn-position-test",
            capacity = 2,
            layer = CParticleRenderLayer.OPAQUE,
            mode = CParticleSystemMode.SCRIPTED,
        ).apply {
            setOriginIfEmpty(initialPosition)
        }
        val composition = TestCParticleComposition(initialPosition, null)
        composition.setPositionWithoutToggle(movedPosition)
        assertEquals(
            Vec3(17.0, 25.0, 31.0),
            composition.resolveCParticleSpawnPosition(relative, system),
        )
        setGpuTransformActive(composition, true)
        system.groupTransform.translation(
            (movedPosition.x - initialPosition.x).toFloat(),
            (movedPosition.y - initialPosition.y).toFloat(),
            (movedPosition.z - initialPosition.z).toFloat(),
        )

        val spawnPosition = composition.resolveCParticleSpawnPosition(relative, system)
        assertEquals(
            Vec3(17.0, 25.0, 31.0),
            spawnPosition,
        )
        assertEquals(
            Vec3(12.0, 23.0, 34.0),
            composition.resolveCParticleStoragePosition(relative, system),
        )
        assertEquals(
            Vec3(17.0, 25.0, 31.0),
            composition.resolveCParticleSpawnPosition(relative, null),
        )

        val slot = system.spawn(
            CParticle().apply {
                pos = spawnPosition
                light = 15
            },
            CParticleSprites.UvRect(0f, 0f, 1f, 1f),
        )
        assertEquals(
            Vec3(17.0, 25.0, 31.0),
            system.scriptedGetPos(slot, system.store.generations[slot]),
        )
        val firstBase = slot * CParticleStore.STRIDE
        assertEquals(2f, system.store.data[firstBase])
        assertEquals(3f, system.store.data[firstBase + 1])
        assertEquals(4f, system.store.data[firstBase + 2])

        setGpuTransformActive(composition, false)
        val fallbackRelative = RelativeLocation(-1.0, 1.0, 2.0)
        val fallbackPosition = composition.resolveCParticleSpawnPosition(fallbackRelative, system)
        assertNull(composition.resolveCParticleStoragePosition(fallbackRelative, system))
        val fallbackSlot = system.spawn(
            CParticle().apply {
                pos = fallbackPosition
                light = 15
            },
            CParticleSprites.UvRect(0f, 0f, 1f, 1f),
        )
        assertEquals(Vec3(14.0, 23.0, 29.0), fallbackPosition)
        assertEquals(
            fallbackPosition,
            system.scriptedGetPos(fallbackSlot, system.store.generations[fallbackSlot]),
        )
    }

    /**
     * 修改测试实例的整组变换状态，用于覆盖客户端分批生成时的内部阶段。
     *
     * 示例：传入 `true` 模拟首批 GPU 粒子生成完成后的状态。
     * 禁止在生产代码中通过反射修改该字段。
     *
     * @param composition 待设置的测试 composition
     * @param active 是否启用整组 GPU 变换
     */
    private fun setGpuTransformActive(composition: ParticleComposition, active: Boolean) {
        ParticleComposition::class.java.getDeclaredField("gpuTransformActive")
            .apply { isAccessible = true }
            .setBoolean(composition, active)
    }
}
