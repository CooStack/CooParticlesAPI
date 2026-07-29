package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import cn.coostack.cooparticlesapi.cparticle.compat.CParticleControlable
import cn.coostack.cooparticlesapi.network.particle.composition.ParticleComposition
import cn.coostack.cooparticlesapi.test.options.particle.composition.TestCParticleComposition
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 验证 SCRIPTED system 在整组变换期间生成新槽位时的出生帧位置。
 *
 * 示例：composition 移动后分批加入的粒子不会从上一 tick 的整组位置滑入。
 * 禁止只比较槽位局部坐标；shader 会分别应用前后两份整组矩阵。
 */
class CParticleScriptedSpawnInterpolationTest {
    /**
     * 验证新槽位与已有槽位共用同一段整组矩阵插值。
     *
     * 示例：整组从 0 平移到 5 时，局部坐标 2 的新粒子应从 2 平滑移动到 7。
     * 禁止把新粒子的上一端点强行改成 7，否则它会与正在插值的已有粒子脱节。
     */
    @Test
    fun `new slot shares group interpolation with existing slots`() {
        val system = CParticleSystem(
            name = "scripted-spawn-interpolation-test",
            capacity = 2,
            layer = CParticleRenderLayer.OPAQUE,
            mode = CParticleSystemMode.SCRIPTED,
        )
        val existingSlot = system.spawn(
            CParticle().apply {
                pos = Vec3.ZERO
                light = 15
            },
            CParticleSprites.UvRect(0f, 0f, 1f, 1f),
        )

        system.groupTransform.translation(5f, 0f, 0f)
        val slot = system.spawn(
            CParticle().apply {
                pos = Vec3(7.0, 0.0, 0.0)
                light = 15
            },
            CParticleSprites.UvRect(0f, 0f, 1f, 1f),
        )
        val base = slot * CParticleStore.STRIDE
        val previousRendered = system.previousGroupTransform.transformPosition(
            Vector3f(
                system.store.data[base + CParticleStore.OFF_PREV],
                system.store.data[base + CParticleStore.OFF_PREV + 1],
                system.store.data[base + CParticleStore.OFF_PREV + 2],
            )
        )
        val currentRendered = system.groupTransform.transformPosition(
            Vector3f(
                system.store.data[base],
                system.store.data[base + 1],
                system.store.data[base + 2],
            )
        )
        val existingBase = existingSlot * CParticleStore.STRIDE
        val existingPreviousRendered = system.previousGroupTransform.transformPosition(
            Vector3f(
                system.store.data[existingBase + CParticleStore.OFF_PREV],
                system.store.data[existingBase + CParticleStore.OFF_PREV + 1],
                system.store.data[existingBase + CParticleStore.OFF_PREV + 2],
            )
        )
        val existingCurrentRendered = system.groupTransform.transformPosition(
            Vector3f(
                system.store.data[existingBase],
                system.store.data[existingBase + 1],
                system.store.data[existingBase + 2],
            )
        )

        assertEquals(Vector3f(2f, 0f, 0f), previousRendered)
        assertEquals(Vector3f(7f, 0f, 0f), currentRendered)
        assertEquals(Vector3f(0f, 0f, 0f), existingPreviousRendered)
        assertEquals(Vector3f(5f, 0f, 0f), existingCurrentRendered)
    }

    /**
     * 验证新槽位在任意可逆仿射变换下仍与整组一起插值。
     *
     * 示例：前后矩阵都包含旋转和缩放时，两端分别使用同一个局部坐标映射。
     * 禁止把上一端点反算到当前世界位置；这会让新粒子在首帧停止旋转。
     */
    @Test
    fun `new slot follows arbitrary invertible group transforms`() {
        val system = CParticleSystem(
            name = "scripted-spawn-affine-interpolation-test",
            capacity = 2,
            layer = CParticleRenderLayer.OPAQUE,
            mode = CParticleSystemMode.SCRIPTED,
        )
        system.groupTransform
            .translation(1f, 2f, -3f)
            .rotateY(0.4f)
            .scale(0.8f, 1.1f, 1.4f)
        system.tick()
        system.groupTransform
            .translation(-2f, 4f, 1f)
            .rotateZ(-0.3f)
            .scale(0.001f)
        system.tick()
        system.store.spawn(
            CParticle().apply {
                pos = Vec3.ZERO
                light = 15
            },
            system.origin,
            CParticleSprites.UvRect(0f, 0f, 1f, 1f),
            15,
            15,
        )

        val local = Vector3f(2f, -1f, 0.5f)
        system.groupTransform
            .translation(-4f, 3f, 6f)
            .rotateX(-0.25f)
            .rotateZ(0.7f)
            .scale(0.8f, 1.1f, 1.4f)
        val world = system.groupTransform.transformPosition(Vector3f(local))
        val slot = system.spawn(
            CParticle().apply {
                pos = Vec3(world.x.toDouble(), world.y.toDouble(), world.z.toDouble())
                light = 15
            },
            CParticleSprites.UvRect(0f, 0f, 1f, 1f),
        )
        val base = slot * CParticleStore.STRIDE
        val expectedPrevious = system.currentGroupTransform.transformPosition(Vector3f(local))
        val previousRendered = system.currentGroupTransform.transformPosition(
            Vector3f(
                system.store.data[base + CParticleStore.OFF_PREV],
                system.store.data[base + CParticleStore.OFF_PREV + 1],
                system.store.data[base + CParticleStore.OFF_PREV + 2],
            )
        )
        val currentRendered = system.groupTransform.transformPosition(
            Vector3f(
                system.store.data[base],
                system.store.data[base + 1],
                system.store.data[base + 2],
            )
        )

        assertVectorEquals(expectedPrevious, previousRendered)
        assertVectorEquals(world, currentRendered)
    }

    /**
     * 验证生成槽位后，同一 tick 内继续修改整组矩阵时仍保留完整插值区间。
     *
     * 示例：`addMultiple` 后紧接着旋转，新增粒子应从生成时矩阵平滑旋转到最终矩阵。
     * 禁止把 previous 改成最终位置；这会让持续新增的粒子每 tick 闪到旋转前方。
     */
    @Test
    fun `new slot keeps group interpolation when transform changes after spawn`() {
        val system = CParticleSystem(
            name = "scripted-spawn-final-transform-test",
            capacity = 1,
            layer = CParticleRenderLayer.OPAQUE,
            mode = CParticleSystemMode.SCRIPTED,
        )
        val local = Vector3f(2f, -1f, 0.5f)
        system.groupTransform
            .translation(3f, 1f, -2f)
            .rotateY(0.25f)
        val spawnWorld = system.groupTransform.transformPosition(Vector3f(local))
        val slot = system.spawn(
            CParticle().apply {
                pos = Vec3(spawnWorld.x.toDouble(), spawnWorld.y.toDouble(), spawnWorld.z.toDouble())
                light = 15
            },
            CParticleSprites.UvRect(0f, 0f, 1f, 1f),
        )

        system.groupTransform.rotateZ(0.4f)
        val expectedWorld = system.groupTransform.transformPosition(Vector3f(local))
        prepareGroupTransformForTick(system)
        rollPrevForUnwritten(system)

        val base = slot * CParticleStore.STRIDE
        val previousRendered = system.previousGroupTransform.transformPosition(
            Vector3f(
                system.store.data[base + CParticleStore.OFF_PREV],
                system.store.data[base + CParticleStore.OFF_PREV + 1],
                system.store.data[base + CParticleStore.OFF_PREV + 2],
            )
        )
        val currentRendered = system.currentGroupTransform.transformPosition(
            Vector3f(
                system.store.data[base],
                system.store.data[base + 1],
                system.store.data[base + 2],
            )
        )

        assertVectorEquals(spawnWorld, previousRendered)
        assertVectorEquals(expectedWorld, currentRendered)
    }

    /** 同 tick 主动移动新槽位时，保留句柄写入产生的位置插值。 */
    @Test
    fun `new slot scripted position write keeps its local interpolation`() {
        val system = CParticleSystem(
            name = "scripted-spawn-position-write-test",
            capacity = 1,
            layer = CParticleRenderLayer.OPAQUE,
            mode = CParticleSystemMode.SCRIPTED,
        )
        val slot = system.spawn(
            CParticle().apply {
                pos = Vec3.ZERO
                light = 15
            },
            CParticleSprites.UvRect(0f, 0f, 1f, 1f),
        )

        system.scriptedSetPos(slot, system.store.generations[slot], Vec3(10.0, 0.0, 0.0))
        prepareGroupTransformForTick(system)
        rollPrevForUnwritten(system)

        val base = slot * CParticleStore.STRIDE
        assertEquals(0f, system.store.data[base + CParticleStore.OFF_PREV])
        assertEquals(10f, system.store.data[base])
    }

    /**
     * 验证序列生成期间重复刷新 GPU 模式不会清除整组插值历史。
     *
     * 示例：新增第二个槽位后 refresh 保留已有的 previous/current 矩阵。
     * 禁止每次 `displayEntry` 都快照整个 system；这会让持续移动退化为逐 tick 跳变。
     */
    @Test
    fun `repeated composition refresh preserves group interpolation history`() {
        val system = CParticleSystem(
            name = "composition-refresh-interpolation-test",
            capacity = 1,
            layer = CParticleRenderLayer.OPAQUE,
            mode = CParticleSystemMode.SCRIPTED,
        )
        val slot = system.spawn(
            CParticle().apply {
                pos = Vec3.ZERO
                light = 15
            },
            CParticleSprites.UvRect(0f, 0f, 1f, 1f),
        )
        val composition = TestCParticleComposition(Vec3(5.0, 0.0, 0.0), null)
        val firstUuid = UUID.randomUUID()
        val first = CParticleControlable(
            system,
            slot,
            system.store.generations[slot],
            firstUuid,
            null,
        )
        composition.particles[firstUuid] = first
        managedSystems(composition).add(system)
        setManagedParticleCount(composition, 1)

        refreshGpuTransformMode(composition)
        system.previousGroupTransform.translation(4f, 0f, 0f)
        system.currentGroupTransform.translation(5f, 0f, 0f)
        composition.teleportTo(Vec3(6.0, 0.0, 0.0))

        val secondUuid = UUID.randomUUID()
        composition.particles[secondUuid] = CParticleControlable(
            system,
            slot,
            system.store.generations[slot],
            secondUuid,
            null,
        )
        setManagedParticleCount(composition, 2)
        refreshGpuTransformMode(composition)

        assertEquals(4f, system.previousGroupTransform.m30())
        assertEquals(5f, system.currentGroupTransform.m30())
        assertEquals(6f, system.groupTransform.m30())
    }

    /**
     * 按浮点容差比较两个三维向量。
     *
     * 示例：矩阵正逆变换后的坐标允许 `1.0E-5` 以内误差。
     * 禁止用于需要逐位相等的整数编码断言。
     *
     * @param expected 预期向量
     * @param actual 实际向量
     */
    private fun assertVectorEquals(expected: Vector3f, actual: Vector3f) {
        assertEquals(expected.x, actual.x, 1.0E-5f)
        assertEquals(expected.y, actual.y, 1.0E-5f)
        assertEquals(expected.z, actual.z, 1.0E-5f)
    }

    /**
     * 读取 composition 内部管理的 GPU systems，用于构造刷新边界。
     *
     * 示例：测试把一个 SCRIPTED system 登记为 managed system。
     * 禁止生产代码通过反射修改该集合。
     *
     * @param composition 测试 composition
     * @return 内部 managed system 集合
     */
    @Suppress("UNCHECKED_CAST")
    private fun managedSystems(composition: ParticleComposition): MutableSet<CParticleSystem> {
        return ParticleComposition::class.java.getDeclaredField("managedCParticleSystems")
            .apply { isAccessible = true }
            .get(composition) as MutableSet<CParticleSystem>
    }

    /**
     * 设置测试 composition 的 managed 粒子数量。
     *
     * 示例：新增一个模拟槽位后把数量从 1 更新为 2。
     * 禁止生产代码绕过 [ParticleComposition] 的节点注册流程。
     *
     * @param composition 测试 composition
     * @param count managed 粒子数量
     */
    private fun setManagedParticleCount(composition: ParticleComposition, count: Int) {
        ParticleComposition::class.java.getDeclaredField("managedCParticleCount")
            .apply { isAccessible = true }
            .setInt(composition, count)
    }

    /**
     * 调用受保护的 GPU 模式刷新方法。
     *
     * 示例：模拟 [cn.coostack.cooparticlesapi.network.particle.composition.SequencedParticleComposition]
     * 在每个 `displayEntry` 后触发刷新。
     * 禁止生产代码通过反射调用该生命周期方法。
     *
     * @param composition 测试 composition
     */
    private fun refreshGpuTransformMode(composition: ParticleComposition) {
        ParticleComposition::class.java.getDeclaredMethod("refreshGpuTransformMode")
            .apply { isAccessible = true }
            .invoke(composition)
    }

    /** 只推进整组矩阵，避免单元测试进入需要 OpenGL 上下文的上传阶段。 */
    private fun prepareGroupTransformForTick(system: CParticleSystem) {
        CParticleSystem::class.java.getDeclaredMethod("prepareGroupTransformForTick")
            .apply { isAccessible = true }
            .invoke(system)
    }

    /** 执行 scripted 槽位的 previous 收敛步骤。 */
    private fun rollPrevForUnwritten(system: CParticleSystem) {
        CParticleSystem::class.java.getDeclaredMethod("rollPrevForUnwritten")
            .apply { isAccessible = true }
            .invoke(system)
    }
}
