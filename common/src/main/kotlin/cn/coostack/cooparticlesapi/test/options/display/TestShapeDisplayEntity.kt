package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.display.handle.DisplayEntityRegistryHelper
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.platform.CooClientServices
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.PoseStack.Pose
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.util.LightCoordsUtil
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
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
        private val LAYERED_GLOW_ID = Identifier.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "glow_layered"
        )
    }

    @CodecField
    var direction = Vec3(0.0, 1.0, 0.0)
    override fun submit(
        view: Matrix4f,
        proj: Matrix4f,
        modelMatrixStack: PoseStack,
        collector: SubmitNodeCollector,
        delta: Float,
        camera: CameraRenderState
    ) {
        val provider = CooClientServices.RENDER_TYPES_PROVIDER
        val draw: (Pose, VertexConsumer) -> Unit = { pose, consumer ->
            renderCylinder(consumer, pose, 5F, 100F, 10F, Vector3f(10F, 10F, 10F))
        }
        val layered = provider.layered(LAYERED_GLOW_ID)
        if (layered != null) {
            layered.submit(collector, modelMatrixStack, draw)
        } else {
            collector.submitCustomGeometry(modelMatrixStack, provider.glow()) { pose, consumer ->
                draw(pose, consumer)
            }
        }
    }

    fun renderCylinder(
        consumer: VertexConsumer,
        pose: Pose,
        r: Float,
        tessellate: Float,
        height: Float,
        color: Vector3f
    ) {
        require(height > 0 && r > 0 && tessellate >= 2)
        val step = 2 * PI.toFloat() / tessellate
        var current = 0F
        while (current < 2 * PI) {
            val x1 = r * cos(current)
            val x2 = r * cos(current + step)
            val z1 = r * sin(current)
            val z2 = r * sin(current + step)
            addSquare(
                consumer, pose,
                Vector3f(x1, height, z1),
                Vector3f(x2, height, z2),
                Vector3f(x2, 0F, z2),
                Vector3f(x1, 0F, z1),
                color,
                Vector3f(x1, 0F, z1),
                LightCoordsUtil.FULL_BRIGHT
            )
            current += step
        }

    }

    fun addSquare(
        consumer: VertexConsumer,
        pose: Pose,
        weight: Float,
        heigh: Float,
        color: Vector3f,
        normal: Vector3f,
        light: Int
    ) {
        val left = Vector3f(0F, 0F, 0F)
        val leftUP = Vector3f(0F, heigh, 0F)
        val rightUP = Vector3f(weight, heigh, 0F)
        val right = Vector3f(weight, 0F, 0F)

        addVertex(consumer, pose, left, color, normal, 0F, 0F, light)
        addVertex(consumer, pose, right, color, normal, 0F, 1F, light)
        addVertex(consumer, pose, rightUP, color, normal, 1F, 1F, light)
        addVertex(consumer, pose, leftUP, color, normal, 1F, 0F, light)
    }

    fun addSquare(
        consumer: VertexConsumer,
        pose: Pose,
        p1: Vector3f,
        p2: Vector3f,
        p3: Vector3f,
        p4: Vector3f,
        color: Vector3f,
        normal: Vector3f,
        light: Int
    ) {
        addVertex(consumer, pose, p1, color, normal, 0F, 0F, light)
        addVertex(consumer, pose, p2, color, normal, 0F, 1F, light)
        addVertex(consumer, pose, p3, color, normal, 1F, 1F, light)
        addVertex(consumer, pose, p4, color, normal, 1F, 0F, light)
    }

    fun addVertex(
        consumer: VertexConsumer,
        pose: Pose,
        pos: Vector3f,
        color: Vector3f,
        normal: Vector3f,
        u: Float,
        v: Float,
        light: Int,
    ) {
        consumer.addVertex(pose, pos.x, pos.y, pos.z)
            .setNormal(pose, normal.x, normal.y, normal.z)
            .setColor(color.x, color.y, color.z, 1F)
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
