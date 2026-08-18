package cn.coostack.cooparticlesapi.supports.sound

import cn.coostack.cooparticlesapi.CooParticlesAPI
import net.minecraft.SharedConstants
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.Bootstrap
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import sun.misc.Unsafe
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 验证服务端声音实例生命周期和停止型淡出的衔接。 */
class SoundInstanceLifecycleTest {
    @BeforeTest
    fun bootstrapMinecraft() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    @AfterTest
    fun cleanUp() {
        ServerSoundManager.clear()
        CooParticlesAPI.scheduler.clear()
    }

    @Test
    fun `builder uses default lifetime and accepts infinite lifetime`() {
        val builder = SoundInstanceBuilder(
            ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "lifecycle_test"),
            SoundSource.MASTER,
        )

        assertEquals(SoundInstanceSpec.DEFAULT_LIFETIME, builder.buildSpec().lifetime)
        assertEquals(-1, builder.lifetime(-1).buildSpec().lifetime)
        assertFailsWith<IllegalArgumentException> {
            builder.lifetime(-2)
        }
    }

    @Test
    fun `finite lifetime stops and removes instance at configured tick`() {
        val instance = createInstance("finite")
        instance.lifetime = 2
        ServerSoundManager.spawn(instance)

        ServerSoundManager.tick()
        assertEquals(1, instance.currentAge)
        assertFalse(instance.isStopped)
        assertTrue(ServerSoundManager.isPlaying(instance.key))

        ServerSoundManager.tick()
        assertEquals(2, instance.currentAge)
        assertTrue(instance.isStopped)
        assertFalse(ServerSoundManager.isPlaying(instance.key))
    }

    @Test
    fun `infinite lifetime remains until fade out finishes`() {
        val instance = createInstance("infinite")
        instance.lifetime = -1
        ServerSoundManager.spawn(instance)

        repeat(120) {
            ServerSoundManager.tick()
        }
        assertEquals(0, instance.currentAge)
        assertTrue(ServerSoundManager.isPlaying(instance.key))

        instance.fadeOut(2)
        repeat(3) {
            CooParticlesAPI.scheduler.doTick()
            ServerSoundManager.tick()
        }

        assertEquals(0F, instance.volumeMultiplier)
        assertTrue(instance.isStopped)
        assertFalse(ServerSoundManager.isPlaying(instance.key))
    }

    private fun createInstance(key: String): ServerManagedSoundInstance {
        return ServerManagedSoundInstance(
            key = key,
            soundId = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "lifecycle_test"),
            source = SoundSource.MASTER,
            world = allocateServerLevel(),
            initialPos = Vec3.ZERO,
        )
    }

    private fun allocateServerLevel(): ServerLevel {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        return (field.get(null) as Unsafe).allocateInstance(ServerLevel::class.java) as ServerLevel
    }
}
