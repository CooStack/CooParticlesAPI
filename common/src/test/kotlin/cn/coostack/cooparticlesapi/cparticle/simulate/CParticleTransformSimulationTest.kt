package cn.coostack.cooparticlesapi.cparticle.simulate

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.storage.CParticleStore
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals

class CParticleTransformSimulationTest {
    @Test
    fun `world gravity is converted back into local storage`() {
        val store = CParticleStore(1)
        val slot = store.spawn(
            CParticle().apply {
                pos = Vec3(1.0, 0.0, 0.0)
                velocity = Vec3.ZERO
            },
            Vec3.ZERO,
            0,
            15,
            15,
        )
        store.clearSpawned()
        val packed = FloatArray(CParticleForce.STRIDE)
        CParticleForce.Gravity(1.0).pack(packed, 0, Vec3.ZERO)
        val transform = Matrix4f()
            .translation(10f, 0f, 0f)
            .rotateZ((PI / 2.0).toFloat())
            .scale(2f)

        CParticleCpuSimulator.simulate(
            store,
            packed,
            1,
            0.0,
            0.0,
            0.0,
            32f,
            null,
            transform,
            Matrix4f(transform).invertAffine(),
        )

        val base = slot * CParticleStore.STRIDE
        assertEquals(0.5f, store.data[base], 1e-5f)
        assertEquals(0f, store.data[base + 1], 1e-5f)
        assertEquals(1f, store.data[base + CParticleStore.OFF_PREV], 1e-5f)
        assertEquals(-0.5f, store.data[base + CParticleStore.OFF_VEL], 1e-5f)
        assertEquals(0f, store.data[base + CParticleStore.OFF_VEL + 1], 1e-5f)
    }
}
