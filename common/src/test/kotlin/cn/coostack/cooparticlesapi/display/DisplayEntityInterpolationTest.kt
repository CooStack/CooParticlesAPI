package cn.coostack.cooparticlesapi.display

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.Bootstrap
import net.minecraft.SharedConstants
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayEntityInterpolationTest {
    @BeforeTest
    fun bootstrapRegistries() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    @Test
    fun `remote state keeps its interpolation origin through the next client tick`() {
        val entity = TestDisplayEntity(Vec3.ZERO)

        entity.applyRemoteState(Vec3(10.0, 4.0, -2.0), 90f, 40f, 20f, 3f)
        entity.tick()

        assertEquals(Vec3(5.0, 2.0, -1.0), entity.position(0.5f))
        assertEquals(45f, entity.yaw(0.5f))
        assertEquals(20f, entity.pitch(0.5f))
        assertEquals(10f, entity.roll(0.5f))
        assertEquals(2f, entity.scale(0.5f))
    }

    @Test
    fun `full update uses the same interpolation path`() {
        val entity = TestDisplayEntity(Vec3.ZERO)
        val update = TestDisplayEntity(Vec3(8.0, 2.0, 4.0)).apply {
            yaw = 60f
            scale = 2f
        }

        entity.update(update)
        entity.tick()

        assertEquals(Vec3(4.0, 1.0, 2.0), entity.position(0.5f))
        assertEquals(30f, entity.yaw(0.5f))
        assertEquals(1.5f, entity.scale(0.5f))
    }

    private class TestDisplayEntity(pos: Vec3) : DisplayEntity(pos, null) {
        override fun render(
            view: Matrix4f,
            proj: Matrix4f,
            modelMatrixStack: PoseStack,
            buffer: MultiBufferSource,
            delta: Float,
            camera: Camera,
        ) = Unit

        override fun getCodec(): StreamCodec<in RegistryFriendlyByteBuf, DisplayEntity> {
            throw UnsupportedOperationException()
        }
    }
}
