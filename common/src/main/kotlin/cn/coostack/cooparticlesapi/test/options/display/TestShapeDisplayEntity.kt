package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.display.handle.DisplayEntityRegistryHelper
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 展示实体（自定义版）
 * 示例代码
 *
 * @constructor
 * TODO
 *
 * @param pos
 * @param world
 */
@CooAutoRegister
class TestShapeDisplayEntity(pos: Vec3, world: Level?) : DisplayEntity(pos, world) {
    companion object {
        private val LAYERED_GLOW_ID = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "glow_layered"
        )
    }

    @CodecField
    var direction = Vec3(0.0, 1.0, 0.0)
    override fun render(
        view: Matrix4f,
        proj: Matrix4f,
        modelMatrixStack: PoseStack,
        buffer: MultiBufferSource,
        delta: Float,
        camera: Camera
    ) {
        modelMatrixStack.pushPose()
        val provider = CooParticlesServices.PLATFORM.getRenderTypesProvider()
        val layeredConsumer = provider.layered(LAYERED_GLOW_ID)?.consumer(buffer)
        val consumer = layeredConsumer ?: buffer.getBuffer(provider.glow())
        renderCylinder(
            consumer,
            modelMatrixStack,
            5f,
            100f,
            10f,
            Vector3f(10f, 10f, 10f)
        )
        modelMatrixStack.popPose()
    }

    fun renderCylinder(
        consumer: VertexConsumer,
        stack: PoseStack,
        r: Float,
        tessellate: Float,
        height: Float,
        color: Vector3f
    ) {
        require(height > 0 && r > 0 && tessellate >= 2)
        val step = 2 * PI.toFloat() / tessellate
        var current = 0f
        while (current < 2 * PI) {
            val x1 = r * cos(current)
            val x2 = r * cos(current + step)
            val z1 = r * sin(current)
            val z2 = r * sin(current + step)
            addSquare(
                consumer, stack,
                Vector3f(x1, height, z1),
                Vector3f(x2, height, z2),
                Vector3f(x2, 0f, z2),
                Vector3f(x1, 0f, z1),
                color,
                Vector3f(x1, 0f, z1),
                LightTexture.FULL_BRIGHT
            )
            current += step
        }

    }

    fun addSquare(
        consumer: VertexConsumer,
        stack: PoseStack,
        weight: Float,
        heigh: Float,
        color: Vector3f,
        normal: Vector3f,
        light: Int
    ) {
        val left = Vector3f(0f, 0f, 0f)
        val leftUP = Vector3f(0f, heigh, 0f)
        val rightUP = Vector3f(weight, heigh, 0f)
        val right = Vector3f(weight, 0f, 0f)

        addVertex(consumer, stack, left, color, normal, 0f, 0f, light)
        addVertex(consumer, stack, right, color, normal, 0f, 1f, light)
        addVertex(consumer, stack, rightUP, color, normal, 1f, 1f, light)
        addVertex(consumer, stack, leftUP, color, normal, 1f, 0f, light)
    }

    fun addSquare(
        consumer: VertexConsumer,
        stack: PoseStack,
        p1: Vector3f,
        p2: Vector3f,
        p3: Vector3f,
        p4: Vector3f,
        color: Vector3f,
        normal: Vector3f,
        light: Int
    ) {
        RenderSystem.disableCull()

        addVertex(consumer, stack, p1, color, normal, 0f, 0f, light)
        addVertex(consumer, stack, p2, color, normal, 0f, 1f, light)
        addVertex(consumer, stack, p3, color, normal, 1f, 1f, light)
        addVertex(consumer, stack, p4, color, normal, 1f, 0f, light)
    }

    fun addVertex(
        consumer: VertexConsumer,
        stack: PoseStack,
        pos: Vector3f,
        color: Vector3f,
        normal: Vector3f,
        u: Float,
        v: Float,
        light: Int,
    ) {
        consumer.addVertex(stack.last(), pos.x, pos.y, pos.z)
            .setNormal(stack.last(), normal.x, normal.y, normal.z)
            .setColor(color.x, color.y, color.z, 1f)
            .setUv(u, v)
            .setUv1(1, 1)
            .setLight(light)
            .setOverlay(OverlayTexture.NO_OVERLAY)
    }


    override fun tick() {
        super.tick()
        //        super.tick()
//        lookAt(direction)
//        // 最近的玩家
//        if (world!!.isClientSide) {
//            return
//        }
//        val server = world as ServerLevel
//        val player = server.players().minByOrNull {
//            it.position().distanceTo(pos)
//        } ?: return
//
//        direction = player.eyePosition - pos
    }

    override fun getCodec(): StreamCodec<in RegistryFriendlyByteBuf, DisplayEntity> {
        return DisplayEntityRegistryHelper.generateCodec(this)
    }

}
