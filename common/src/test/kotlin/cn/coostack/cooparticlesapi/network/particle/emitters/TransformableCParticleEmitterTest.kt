package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.emitter.handle.ParticleEmittersRegistryHelper
import cn.coostack.cooparticlesapi.api.DirtyProperty
import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.api.controler.SerializableData
import cn.coostack.cooparticlesapi.cparticle.compat.TransformableCParticleEmitterBridge
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.SharedConstants
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.Bootstrap
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.UUID
import kotlin.math.PI
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TransformableCParticleEmitterTest {
    @BeforeTest
    fun bootstrapRegistries() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    @Test
    fun `direct position assignment marks emitter dirty`() {
        val emitter = TestTransformableEmitter(Vec3.ZERO, null)

        emitter.pos = Vec3(1.0, 2.0, 3.0)
        emitter.pos = Vec3(1.0, 2.0, 3.0)

        assertEquals(1, emitter.dirtyCount)
    }

    @Test
    fun `all emitter bases use dirty position delegate without changing constructor contract`() {
        listOf(
            ClassEmitters::class.java,
            ClassParticleEmitters::class.java,
            TransformableCParticleEmitter::class.java,
        ).forEach { type ->
            val positionDelegate = type.declaredFields.single { it.name == "posState" }

            assertEquals(DirtyProperty::class.java, positionDelegate.type)
            type.getDeclaredConstructor(Vec3::class.java, Level::class.java)
        }
    }

    @Test
    fun `default teleport marks emitter dirty once`() {
        val emitter = DirtyTrackingClassEmitter(Vec3.ZERO, null)

        emitter.teleportTo(Vec3(1.0, 2.0, 3.0))

        assertEquals(1, emitter.dirtyCount)
    }

    @Test
    fun `emitter codec decode does not mark position dirty`() {
        val original = DirtyTrackingClassEmitter(Vec3(1.0, 2.0, 3.0), null)
        val buffer = registryBuffer()

        original.getCodec().encode(buffer, original)
        val decoded = original.getCodec().decode(buffer) as DirtyTrackingClassEmitter

        assertEquals(original.pos, decoded.pos)
        assertEquals(0, decoded.dirtyCount)
    }

    @Test
    fun `client state update does not mark position dirty`() {
        val transformable = TestTransformableEmitter(Vec3.ZERO, null)
        val incomingTransformable = TestTransformableEmitter(Vec3(1.0, 2.0, 3.0), null)
        val classEmitter = DirtyTrackingClassEmitter(Vec3.ZERO, null)
        val incomingClassEmitter = DirtyTrackingClassEmitter(Vec3(4.0, 5.0, 6.0), null)

        transformable.update(incomingTransformable)
        classEmitter.update(incomingClassEmitter)

        assertEquals(incomingTransformable.pos, transformable.pos)
        assertEquals(0, transformable.dirtyCount)
        assertEquals(incomingClassEmitter.pos, classEmitter.pos)
        assertEquals(0, classEmitter.dirtyCount)
    }

    @Test
    fun `local particles follow translation emitter rotation and scale increments`() {
        val emitter = TestTransformableEmitter(Vec3(10.0, 20.0, 30.0), null)
        val origin = Vec3(8.0, 18.0, 28.0)
        val localParticle = Vector3f(1f, 0f, 0f)

        val initial = TransformableCParticleEmitterBridge.localGroupTransform(emitter, origin)
            .transformPosition(Vector3f(localParticle))
        assertVectorEquals(Vector3f(3f, 2f, 2f), initial)

        emitter.translate(0.0, 1.0, 0.0)
        val moved = TransformableCParticleEmitterBridge.localGroupTransform(emitter, origin)
            .transformPosition(Vector3f(localParticle))
        assertVectorEquals(Vector3f(3f, 3f, 2f), moved)

        emitter.rotateEmitter(Quaternionf().rotateZ((PI / 2.0).toFloat()))
        emitter.scaleBy(2.0)
        val transformed = TransformableCParticleEmitterBridge.localGroupTransform(emitter, origin)
            .transformPosition(Vector3f(localParticle))
        assertVectorEquals(Vector3f(2f, 5f, 2f), transformed)
    }

    @Test
    fun `world mode uses ordinary world coordinates`() {
        val emitter = TestTransformableEmitter(Vec3(10.0, 20.0, 30.0), null).apply {
            space = CParticleEmitterSpace.WORLD
            emitterRotation = Quaternionf().rotateZ((PI / 2.0).toFloat())
            scale = 4.0
        }

        val worldPosition = TransformableCParticleEmitterBridge.resolveWorldPosition(
            emitter,
            Vec3(11.0, 22.0, 33.0),
            RelativeLocation(2.0, 3.0, 4.0),
        )

        assertEquals(Vec3(13.0, 25.0, 37.0), worldPosition)
    }

    @Test
    fun `particle rotation changes spawn direction without changing existing emitter space`() {
        val emitter = TestTransformableEmitter(Vec3.ZERO, null).apply {
            particleRotation = Quaternionf().rotateZ((PI / 2.0).toFloat())
            scale = 3.0
        }
        val before = TransformableCParticleEmitterBridge.localGroupTransform(emitter, Vec3.ZERO)
            .transformDirection(Vector3f(1f, 0f, 0f))
        val storageVelocity = TransformableCParticleEmitterBridge.resolveStorageVelocity(
            emitter,
            Vec3(1.0, 0.0, 0.0),
        )
        val worldVelocity = TransformableCParticleEmitterBridge.localGroupTransform(emitter, Vec3.ZERO)
            .transformDirection(storageVelocity.toVector3f())
        val worldPosition = TransformableCParticleEmitterBridge.resolveWorldPosition(
            emitter,
            Vec3.ZERO,
            RelativeLocation(1.0, 0.0, 0.0),
        )

        assertVectorEquals(Vector3f(3f, 0f, 0f), before)
        assertVectorEquals(Vector3f(0f, 3f, 0f), worldVelocity)
        assertVec3Equals(Vec3(0.0, 3.0, 0.0), worldPosition!!)
    }

    @Test
    fun `emitter rotation rotates existing particle space`() {
        val emitter = TestTransformableEmitter(Vec3.ZERO, null)
        val local = Vector3f(1f, 0f, 0f)

        emitter.rotateEmitter(Quaternionf().rotateZ((PI / 2.0).toFloat()))

        val transformed = TransformableCParticleEmitterBridge.localGroupTransform(emitter, Vec3.ZERO)
            .transformDirection(Vector3f(local))
        assertVectorEquals(Vector3f(0f, 1f, 0f), transformed)
    }

    @Test
    fun `spawn transform composes particle direction inside rotated emitter space`() {
        val emitter = TestTransformableEmitter(Vec3.ZERO, null).apply {
            particleRotation = Quaternionf().rotateZ((PI / 2.0).toFloat())
            emitterRotation = Quaternionf().rotateZ(PI.toFloat())
        }

        val worldPosition = TransformableCParticleEmitterBridge.resolveWorldPosition(
            emitter,
            Vec3.ZERO,
            RelativeLocation(1.0, 0.0, 0.0),
        )
        val storageVelocity = TransformableCParticleEmitterBridge.resolveStorageVelocity(
            emitter,
            Vec3(1.0, 0.0, 0.0),
        )
        val worldVelocity = TransformableCParticleEmitterBridge.localGroupTransform(emitter, Vec3.ZERO)
            .transformDirection(storageVelocity.toVector3f())

        assertVec3Equals(Vec3(0.0, -1.0, 0.0), worldPosition!!)
        assertVectorEquals(Vector3f(0f, -1f, 0f), worldVelocity)
    }

    @Test
    fun `emitter rotation does not change local birth direction`() {
        val emitter = TestTransformableEmitter(Vec3.ZERO, null)
        val before = TransformableCParticleEmitterBridge.resolveStorageVelocity(
            emitter,
            Vec3(1.0, 0.0, 0.0),
        )

        emitter.rotateEmitter(Quaternionf().rotateZ((PI / 2.0).toFloat()))
        val after = TransformableCParticleEmitterBridge.resolveStorageVelocity(
            emitter,
            Vec3(1.0, 0.0, 0.0),
        )

        assertVec3Equals(before, after)
        assertVec3Equals(Vec3(1.0, 0.0, 0.0), after)
    }

    @Test
    fun `interpolated world path offset is converted into emitter local space`() {
        val emitter = TestTransformableEmitter(Vec3(10.0, 20.0, 30.0), null).apply {
            emitterRotation = Quaternionf().rotateZ((PI / 2.0).toFloat())
            scale = 2.0
        }

        val worldPosition = TransformableCParticleEmitterBridge.resolveWorldPosition(
            emitter,
            Vec3(12.0, 20.0, 30.0),
            RelativeLocation(0.0, 0.0, 0.0),
        )

        assertVec3Equals(Vec3(12.0, 20.0, 30.0), worldPosition!!)
    }

    @Test
    fun `auto codec round trips transform and codec fields`() {
        val original = TestTransformableEmitter(Vec3(1.0, 2.0, 3.0), null).apply {
            tick = 14
            maxTick = -1
            delay = 3
            uuid = UUID.fromString("00000000-0000-0000-0000-000000000123")
            playing = true
            space = CParticleEmitterSpace.WORLD
            emitterRotation = Quaternionf().rotateXYZ(0.2f, 0.3f, 0.4f)
            particleRotation = Quaternionf().rotateXYZ(0.5f, 0.6f, 0.7f)
            scale = 2.5
            gravity = 0.08
            airDensity = 1.2
            mass = 4.0
            enableInterpolator = true
            emittersInterpolator.setRefiner(7.0)
            marker = 42
            delegatedMarker = 84
        }
        val codec = ParticleEmittersRegistryHelper.generateCodec(original)
        val buffer = registryBuffer()

        codec.encode(buffer, original)
        val decoded = codec.decode(buffer) as TestTransformableEmitter

        assertEquals(original.pos, decoded.pos)
        assertEquals(original.tick, decoded.tick)
        assertEquals(original.maxTick, decoded.maxTick)
        assertEquals(original.delay, decoded.delay)
        assertEquals(original.uuid, decoded.uuid)
        assertEquals(original.playing, decoded.playing)
        assertEquals(original.space, decoded.space)
        assertEquals(original.scale, decoded.scale)
        assertEquals(original.gravity, decoded.gravity)
        assertEquals(original.airDensity, decoded.airDensity)
        assertEquals(original.mass, decoded.mass)
        assertEquals(original.emittersInterpolator.refinerCount, decoded.emittersInterpolator.refinerCount)
        assertEquals(42, decoded.marker)
        assertEquals(84, decoded.delegatedMarker)
        assertEquals(0, decoded.dirtyCount)
        assertEquals(original.emitterRotation.x, decoded.emitterRotation.x, 1e-6f)
        assertEquals(original.emitterRotation.y, decoded.emitterRotation.y, 1e-6f)
        assertEquals(original.emitterRotation.z, decoded.emitterRotation.z, 1e-6f)
        assertEquals(original.emitterRotation.w, decoded.emitterRotation.w, 1e-6f)
        assertFalse(original.emitterRotation === decoded.emitterRotation)
        assertEquals(original.particleRotation.x, decoded.particleRotation.x, 1e-6f)
        assertEquals(original.particleRotation.y, decoded.particleRotation.y, 1e-6f)
        assertEquals(original.particleRotation.z, decoded.particleRotation.z, 1e-6f)
        assertEquals(original.particleRotation.w, decoded.particleRotation.w, 1e-6f)
        assertFalse(original.particleRotation === decoded.particleRotation)
    }

    @Test
    fun `scale rejects singular transforms`() {
        val emitter = TestTransformableEmitter(Vec3.ZERO, null)
        assertFailsWith<IllegalArgumentException> { emitter.scale(0.0) }
        assertFailsWith<IllegalArgumentException> { emitter.scaleBy(-1.0) }
        assertFailsWith<IllegalArgumentException> {
            emitter.emitterRotation = Quaternionf(0f, 0f, 0f, 0f)
        }
    }

    @Test
    fun `new emitter implements the interface without class emitter inheritance`() {
        assertTrue(ParticleEmitters::class.java.isAssignableFrom(TransformableCParticleEmitter::class.java))
        assertFalse(ClassParticleEmitters::class.java.isAssignableFrom(TransformableCParticleEmitter::class.java))
    }

    private fun assertVectorEquals(expected: Vector3f, actual: Vector3f) {
        assertEquals(expected.x, actual.x, 1e-5f)
        assertEquals(expected.y, actual.y, 1e-5f)
        assertEquals(expected.z, actual.z, 1e-5f)
    }

    private fun assertVec3Equals(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-5)
        assertEquals(expected.y, actual.y, 1e-5)
        assertEquals(expected.z, actual.z, 1e-5)
    }

    private fun registryBuffer(): RegistryFriendlyByteBuf {
        val byteBuf = Class.forName("io.netty.buffer.Unpooled")
            .getMethod("buffer")
            .invoke(null)
        val registryAccess = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
        return RegistryFriendlyByteBuf::class.java.constructors
            .single { it.parameterCount == 2 }
            .newInstance(byteBuf, registryAccess) as RegistryFriendlyByteBuf
    }

    class TestTransformableEmitter(pos: Vec3, world: Level?) :
        AutoTransformableCParticleEmitter(pos, world) {
        @CodecField
        var marker: Int = 0
        var delegatedMarker by dirty(0)
        var dirtyCount = 0

        override fun markDirty() {
            dirtyCount++
        }

        override fun doTick() {}

        override fun genParticles(
            lerpProgress: Float,
        ): List<Pair<ControlableCParticleData, RelativeLocation>> = emptyList()
    }

    class DirtyTrackingClassEmitter(pos: Vec3, world: Level?) : AutoEmitters(pos, world) {
        var dirtyCount = 0

        override fun markDirty() {
            dirtyCount++
        }

        override fun doTick() {}

        override fun genControls(
            lerpProgress: Float,
        ): List<Pair<SerializableData, RelativeLocation>> = emptyList()

        override fun singleControlableAction(
            controler: Controlable<*>,
            data: SerializableData,
            spawnPos: RelativeLocation,
            spawnWorld: Level,
            particleLerpProgress: Float,
            posLerpProgress: Float,
        ) {}
    }
}
