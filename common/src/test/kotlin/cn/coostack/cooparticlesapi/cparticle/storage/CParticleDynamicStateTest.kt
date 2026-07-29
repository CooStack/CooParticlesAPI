package cn.coostack.cooparticlesapi.cparticle.storage

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleResolvedTexture
import cn.coostack.cooparticlesapi.cparticle.CParticleSprites
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKind
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.CParticleUv
import cn.coostack.cooparticlesapi.cparticle.simulate.CParticleCpuSimulator
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import net.minecraft.SharedConstants
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.Bootstrap
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CParticleDynamicStateTest {
    private val initialUv = CParticleSprites.UvRect(0f, 0f, 0.25f, 0.25f)

    /**
     * 初始化测试所需的 Minecraft 内置注册表。
     *
     * Example: 每个测试都可以安全构造默认的 [CParticle]。
     * Forbidden: 不要在此方法执行前访问依赖内置注册项的默认纹理来源。
     */
    @BeforeTest
    fun bootstrapRegistries() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    @Test
    fun `dynamic is the default and static particles do not retain their source`() {
        val store = CParticleStore(2)
        val dynamic = CParticle()
        val static = CParticle().apply { updateMode = CParticleUpdateMode.STATIC }

        val dynamicSlot = store.spawn(dynamic, Vec3.ZERO, initialUv, 15, 15)
        val staticSlot = store.spawn(static, Vec3.ZERO, initialUv, 15, 15)

        assertEquals(CParticleUpdateMode.DYNAMIC, dynamic.updateMode)
        assertSame(dynamic, store.dynamicSource(dynamicSlot))
        assertNull(store.dynamicSource(staticSlot))
        assertEquals(1, store.dynamicSourceCount)
        assertEquals(true, store.hasDynamicStorage)
    }

    @Test
    fun `dynamic sync patches visual fields without touching simulation fields`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply {
            pos = Vec3(1.0, 2.0, 3.0)
            velocity = Vec3(0.1, 0.2, 0.3)
            maxAge = 40
        }
        val slot = store.spawn(particle, Vec3.ZERO, initialUv, 15, 15)
        val base = slot * CParticleStore.STRIDE

        store.data[base] = 11f
        store.data[base + 1] = 12f
        store.data[base + 2] = 13f
        store.data[base + CParticleStore.OFF_VEL] = 21f
        store.data[base + CParticleStore.OFF_VEL + 1] = 22f
        store.data[base + CParticleStore.OFF_VEL + 2] = 23f

        particle.pos = Vec3(101.0, 102.0, 103.0)
        particle.velocity = Vec3(201.0, 202.0, 203.0)
        particle.color = Vector3f(0.2f, 0.4f, 0.6f)
        particle.alpha = 0.8f
        particle.uniformSize = false
        particle.weightSize = 0.7f
        particle.heightSize = 0.9f

        val animationBefore = store.data[base + CParticleStore.OFF_ANIMATION]
        val dirtyCount = store.prepareDynamicVisuals(0) { _, _ -> error("descriptor must not change") }

        assertEquals(1, dirtyCount)
        assertEquals(11f, store.data[base])
        assertEquals(12f, store.data[base + 1])
        assertEquals(13f, store.data[base + 2])
        assertEquals(21f, store.data[base + CParticleStore.OFF_VEL])
        assertEquals(22f, store.data[base + CParticleStore.OFF_VEL + 1])
        assertEquals(23f, store.data[base + CParticleStore.OFF_VEL + 2])
        assertEquals(0.7f, store.data[base + CParticleStore.OFF_SIZE])
        assertEquals(0.9f, store.data[base + CParticleStore.OFF_SIZE + 1])
        assertEquals(animationBefore, store.data[base + CParticleStore.OFF_ANIMATION])
        assertEquals(0.2f, store.data[base + CParticleStore.OFF_COLOR])
        assertEquals(0.8f, store.data[base + CParticleStore.OFF_COLOR + 3])
    }

    /**
     * 检查 emitter 的 DYNAMIC data 在生成后仍是外观数据源。
     *
     * Example: 修改原 data 的颜色和限速后，下一次准备会更新实例数据。
     * Forbidden: STATIC data 不应通过这条路径保留引用。
     */
    @Test
    fun `emitter dynamic data remains the live visual source`() {
        val emitted = ControlableCParticleData().apply {
            updateMode = CParticleUpdateMode.DYNAMIC
            color = Vector3f(1f, 0.5f, 0.25f)
            speedLimit = 4.0
        }
        val particle = CParticle.from(emitted)
        val store = CParticleStore(1)
        val slot = store.spawn(particle, Vec3.ZERO, initialUv, 15, 15)
        val base = slot * CParticleStore.STRIDE

        emitted.color.set(0.2f, 0.4f, 0.6f)
        emitted.speedLimit = 1.5
        val dirtyCount = store.prepareDynamicVisuals(0) { _, _ -> error("descriptor must not change") }

        assertEquals(1, dirtyCount)
        assertEquals(0.2f, store.data[base + CParticleStore.OFF_COLOR])
        assertEquals(1.5f, store.data[base + CParticleStore.OFF_SPEED_LIMIT])
    }

    /**
     * 检查同一 store 中的粒子分别使用自己的速度上限。
     *
     * Example: 限速 `1` 和 `3` 的粒子模拟后速度不同。
     * Forbidden: 模拟器不能只使用最后生成粒子的限速。
     */
    @Test
    fun `simulated particles keep independent speed limits inside one store`() {
        val store = CParticleStore(2)
        val slow = CParticle().apply {
            updateMode = CParticleUpdateMode.STATIC
            velocity = Vec3(4.0, 0.0, 0.0)
            speedLimit = 1f
        }
        val fast = CParticle().apply {
            updateMode = CParticleUpdateMode.STATIC
            velocity = Vec3(4.0, 0.0, 0.0)
            speedLimit = 3f
        }
        val slowSlot = store.spawn(slow, Vec3.ZERO, initialUv, 15, 15)
        val fastSlot = store.spawn(fast, Vec3.ZERO, initialUv, 15, 15)

        CParticleCpuSimulator.simulate(
            store,
            FloatArray(0),
            forceCount = 0,
            originX = 0.0,
            originY = 0.0,
            originZ = 0.0,
            speedLimit = 32f,
        )

        assertEquals(1f, store.data[slowSlot * CParticleStore.STRIDE + CParticleStore.OFF_VEL])
        assertEquals(3f, store.data[fastSlot * CParticleStore.STRIDE + CParticleStore.OFF_VEL])
    }

    @Test
    fun `unchanged source does not overwrite handle writes`() {
        val store = CParticleStore(1)
        val particle = CParticle()
        val slot = store.spawn(particle, Vec3.ZERO, initialUv, 15, 15)
        val colorOffset = slot * CParticleStore.STRIDE + CParticleStore.OFF_COLOR

        store.data[colorOffset] = 0.125f
        val dirtyCount = store.prepareDynamicVisuals(0) { _, _ -> error("descriptor must not change") }

        assertEquals(0, dirtyCount)
        assertEquals(0.125f, store.data[colorOffset])
    }

    @Test
    fun `source changes do not overwrite handle writes to sibling components`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply {
            uniformSize = false
            weightSize = 0.2f
            heightSize = 0.3f
            yaw = 0.4f
            pitch = 0.5f
            axis = Vec3(1.0, 2.0, 3.0)
            color = Vector3f(0.6f, 0.7f, 0.8f)
            alpha = 0.9f
        }
        val slot = store.spawn(particle, Vec3.ZERO, initialUv, 15, 15)
        val base = slot * CParticleStore.STRIDE

        store.data[base + CParticleStore.OFF_SIZE] = 9f
        store.data[base + CParticleStore.OFF_SIZE + 2] = 8f
        store.data[base + CParticleStore.OFF_AXIS] = 7f
        store.data[base + CParticleStore.OFF_COLOR] = 6f

        particle.heightSize = 0.35f
        particle.pitch = 0.55f
        particle.axis = Vec3(1.0, 2.5, 3.0)
        particle.alpha = 0.95f

        val dirtyCount = store.prepareDynamicVisuals(0) { _, _ -> error("descriptor must not change") }

        assertEquals(1, dirtyCount)
        assertEquals(9f, store.data[base + CParticleStore.OFF_SIZE])
        assertEquals(0.35f, store.data[base + CParticleStore.OFF_SIZE + 1])
        assertEquals(8f, store.data[base + CParticleStore.OFF_SIZE + 2])
        assertEquals(0.55f, store.data[base + CParticleStore.OFF_SIZE + 3])
        assertEquals(7f, store.data[base + CParticleStore.OFF_AXIS])
        assertEquals(2.5f, store.data[base + CParticleStore.OFF_AXIS + 1])
        assertEquals(6f, store.data[base + CParticleStore.OFF_COLOR])
        assertEquals(0.95f, store.data[base + CParticleStore.OFF_COLOR + 3])
    }

    @Test
    fun `dynamic age does not update the gpu animation descriptor`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply { maxAge = 20 }
        store.spawn(particle, Vec3.ZERO, initialUv, 15, 15)

        store.tickAges(writeBufferAge = false)
        store.publishDynamicAges()
        var descriptorResolved = false
        val dirtyCount = store.prepareDynamicVisuals(1) { _, _ ->
            descriptorResolved = true
            42
        }

        assertEquals(1, particle.age)
        assertFalse(descriptorResolved)
        assertEquals(0, dirtyCount)
    }

    @Test
    fun `dynamic descriptor changes only when sprite selection changes`() {
        val store = CParticleStore(1)
        val particle = CParticle()
        val slot = store.spawn(particle, Vec3.ZERO, initialUv, 15, 15)
        val sprite = ResourceLocation.fromNamespaceAndPath("test", "frame")
        particle.sprite = sprite

        var resolutions = 0
        val changed = store.prepareDynamicVisuals(0) { actualSprite, effectType ->
            resolutions++
            assertEquals(sprite, actualSprite)
            assertNull(effectType)
            73
        }

        assertEquals(1, changed)
        assertEquals(1, resolutions)
        assertEquals(73f, store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_ANIMATION])

        store.tickAges(writeBufferAge = false)
        store.publishDynamicAges()
        val unchanged = store.prepareDynamicVisuals(1) { _, _ ->
            error("age changes must not resolve the descriptor")
        }
        assertEquals(0, unchanged)
    }

    @Test
    fun `dynamic texture changes update descriptors inside the same binding`() {
        val store = CParticleStore(1)
        val particle = CParticle()
        val binding = CParticleTextureBindingKey.PARTICLE_ATLAS
        val slot = store.spawn(
            particle,
            Vec3.ZERO,
            animationId = 5,
            blockLight = 15,
            skyLight = 15,
            textureBindingKey = binding,
            textureGeneration = 4,
        )
        particle.sprite = ResourceLocation.fromNamespaceAndPath("test", "replacement")

        var resolutions = 0
        val dirtyCount = store.prepareDynamicVisuals(
            tick = 0,
            textureGeneration = 4,
            expectedBindingKey = binding,
            resolveTexture = {
                resolutions++
                CParticleResolvedTexture(
                    binding,
                    descriptorId = 73,
                    uv = CParticleUv.FULL,
                    animationId = null,
                    colorMultiplier = Vector3f(0.5f),
                )
            },
            onBindingMismatch = { _, _ -> error("binding must stay unchanged") },
        )

        assertEquals(1, dirtyCount)
        assertEquals(1, resolutions)
        assertEquals(73f, store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_ANIMATION])
        assertEquals(0.5f, store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_COLOR])
        assertTrue(store.isAlive(slot))
    }

    @Test
    fun `dynamic texture changes kill the slot when the binding changes`() {
        val store = CParticleStore(1)
        val particle = CParticle()
        val originalBinding = CParticleTextureBindingKey.PARTICLE_ATLAS
        val otherBinding = CParticleTextureBindingKey(
            CParticleTextureBindingKind.TEXTURE,
            ResourceLocation.fromNamespaceAndPath("test", "textures/other.png"),
        )
        val slot = store.spawn(
            particle,
            Vec3.ZERO,
            animationId = 5,
            blockLight = 15,
            skyLight = 15,
            textureBindingKey = originalBinding,
            textureGeneration = 2,
        )
        particle.sprite = ResourceLocation.fromNamespaceAndPath("test", "changed")
        var mismatch: CParticleTextureBindingKey? = null

        val dirtyCount = store.prepareDynamicVisuals(
            tick = 0,
            textureGeneration = 2,
            expectedBindingKey = originalBinding,
            resolveTexture = {
                CParticleResolvedTexture(otherBinding, 9, CParticleUv.FULL, null, Vector3f(1f))
            },
            onBindingMismatch = { _, actual -> mismatch = actual },
        )

        assertEquals(1, dirtyCount)
        assertEquals(otherBinding, mismatch)
        assertFalse(store.isAlive(slot))
        assertNull(store.dynamicSource(slot))
    }

    @Test
    fun `resource generation changes re-resolve a live dynamic descriptor once`() {
        val store = CParticleStore(1)
        val particle = CParticle()
        val binding = CParticleTextureBindingKey.PARTICLE_ATLAS
        val slot = store.spawn(
            particle,
            Vec3.ZERO,
            animationId = 5,
            blockLight = 15,
            skyLight = 15,
            textureBindingKey = binding,
            textureGeneration = 10,
        )
        var resolutions = 0

        val dirtyCount = store.prepareDynamicVisuals(
            tick = 0,
            textureGeneration = 11,
            expectedBindingKey = binding,
            resolveTexture = {
                resolutions++
                CParticleResolvedTexture(binding, 81, CParticleUv.FULL, null, Vector3f(1f))
            },
            onBindingMismatch = { _, _ -> error("binding must stay unchanged") },
        )

        assertEquals(1, dirtyCount)
        assertEquals(1, resolutions)
        assertEquals(81f, store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_ANIMATION])
    }

    @Test
    fun `kill clear and slot reuse release dynamic sources`() {
        val store = CParticleStore(1)
        val first = CParticle()
        val firstSlot = store.spawn(first, Vec3.ZERO, initialUv, 15, 15)

        store.kill(firstSlot)
        assertNull(store.dynamicSource(firstSlot))
        assertEquals(0, store.dynamicSourceCount)

        val static = CParticle().apply { updateMode = CParticleUpdateMode.STATIC }
        val reusedSlot = store.spawn(static, Vec3.ZERO, initialUv, 15, 15)
        assertEquals(firstSlot, reusedSlot)
        assertNull(store.dynamicSource(reusedSlot))

        store.clear()
        assertEquals(0, store.dynamicSourceCount)
    }

    @Test
    fun `killed slots stay queued for a flags-only GPU upload`() {
        val store = CParticleStore(2)
        val particle = CParticle().apply { updateMode = CParticleUpdateMode.STATIC }
        val slot = store.spawn(particle, Vec3.ZERO, initialUv, 15, 15)
        store.clearSpawned()

        store.kill(slot)
        store.kill(slot)

        assertEquals(1, store.killedCount)
        assertEquals(slot, store.killedSlots[0])
        assertFalse(store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS].toInt() and
                CParticleStore.FLAG_ALIVE != 0)

        val reusedSlot = store.spawn(particle, Vec3.ZERO, initialUv, 15, 15)
        assertEquals(slot, reusedSlot)
        assertTrue(store.data[slot * CParticleStore.STRIDE + CParticleStore.OFF_FLAGS].toInt() and
                CParticleStore.FLAG_ALIVE != 0)
        assertEquals(1, store.killedCount)

        val killedSlots = store.killedSlots
        store.clearKilled()
        assertEquals(0, store.killedCount)
        assertSame(killedSlots, store.killedSlots)

        store.kill(reusedSlot)
        assertEquals(1, store.killedCount)
        assertSame(killedSlots, store.killedSlots)
    }

    @Test
    fun `gpu lifecycle deaths do not enter the explicit kill queue`() {
        val store = CParticleStore(1)
        val particle = CParticle().apply {
            updateMode = CParticleUpdateMode.STATIC
            maxAge = 1
        }
        store.spawn(particle, Vec3.ZERO, initialUv, 15, 15)
        store.clearSpawned()

        store.tickAges(writeBufferAge = false)

        assertEquals(0, store.aliveCount)
        assertEquals(0, store.killedCount)
    }

    @Test
    fun `static only stores do not allocate dynamic snapshots`() {
        val store = CParticleStore(100_000)
        val particle = CParticle().apply { updateMode = CParticleUpdateMode.STATIC }

        store.spawn(particle, Vec3.ZERO, initialUv, 15, 15)

        assertEquals(false, store.hasDynamicStorage)
        assertEquals(0, store.dynamicSourceCount)
    }
}
