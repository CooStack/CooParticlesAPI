package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.display.handle.DisplayEntityHelper
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.extend.PIF
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.atan2

@CooAutoRegister
class BarrageItemDisplayEntity(pos: Vec3, world: Level?) : DisplayEntity(pos, world) {
    @CodecField
    var item = Items.DIAMOND_SWORD.defaultInstance

    @CodecField
    var isBlock = false


    @CodecField
    var rotation = Quaternionf()
    var prevRotation = Quaternionf()

    @CodecField
    var modelOffset = Quaternionf()

    override fun render(
        view: Matrix4f,
        proj: Matrix4f,
        modelMatrixStack: PoseStack,
        buffer: MultiBufferSource,
        delta: Float,
        camera: Camera
    ) {
        val itemRenderer = Minecraft.getInstance().itemRenderer
        val model = itemRenderer.getModel(item, world, null, 0)
        val offset = renderCenterOffset()
        modelMatrixStack.translate(-offset.x, -offset.y, -offset.z)
        modelMatrixStack.pushPose()
        MinecraftRendererUtil.applyAtPoint(
            offset, modelMatrixStack
        ) {
            modelMatrixStack.mulPose(rotation(delta))
            buffer.getBuffer(RenderType.LINES)
                // Z轴 蓝色
                .addVertex(modelMatrixStack.last(), 0f, 0f, -2f)
                .setNormal(modelMatrixStack.last(), 0f, 1f, 0f)
                .setColor(255, 255, 255, 255)
                .setLight(LightTexture.FULL_BRIGHT)
                .addVertex(modelMatrixStack.last(), 0f, 0f, 2f)
                .setNormal(modelMatrixStack.last(), 0f, 1f, 0f)
                .setColor(0, 0, 255, 255)
                .setLight(LightTexture.FULL_BRIGHT)
                // Y轴 红色
                .addVertex(modelMatrixStack.last(), 0f, 2f, 0f)
                .setNormal(modelMatrixStack.last(), 0f, 1f, 0f)
                .setColor(255, 0, 0, 255)
                .setLight(LightTexture.FULL_BRIGHT)
                .addVertex(modelMatrixStack.last(), 0f, -2f, 0f)
                .setNormal(modelMatrixStack.last(), 0f, 1f, 0f)
                .setColor(255, 255, 255, 255)
                .setLight(LightTexture.FULL_BRIGHT)
                // X轴 绿色
                .addVertex(modelMatrixStack.last(), 2f, 0f, 0f)
                .setNormal(modelMatrixStack.last(), 0f, 1f, 0f)
                .setColor(0, 255, 0, 255)
                .setLight(LightTexture.FULL_BRIGHT)
                .addVertex(modelMatrixStack.last(), -2f, 0f, 0f)
                .setNormal(modelMatrixStack.last(), 0f, 1f, 0f)
                .setColor(255, 255, 255, 255)
                .setLight(LightTexture.FULL_BRIGHT)
        }
        modelMatrixStack.popPose()
        MinecraftRendererUtil.applyAtPoint(
            offset, modelMatrixStack
        ) {
            modelMatrixStack.mulPose(rotation(delta))
        }

        MinecraftRendererUtil.renderItemModel(
            itemRenderer,
            item,
            modelMatrixStack,
            model,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            buffer.getBuffer(RenderType.cutout())
        )
    }


    override fun getCodec(): StreamCodec<FriendlyByteBuf, DisplayEntity> {
        return DisplayEntityHelper.generateCodec(this)
    }

    private fun lerp(delta: Float, min: Float, max: Float): Float {
        return GraphMathHelper.lerp(delta, min, max)
    }

    private fun rotation(delta: Float): Quaternionf {
        return GraphMathHelper.lerp(delta, prevRotation, rotation)
    }


    fun applyModelOffsetEuler(yawDeg: Float, pitchDeg: Float, rollDeg: Float) {

        val yaw = (-yawDeg) * (Math.PI.toFloat() / 180f)
        val pitch = (-pitchDeg) * (Math.PI.toFloat() / 180f)
        val roll = (rollDeg) * (Math.PI.toFloat() / 180f)

        modelOffset.identity()
            .rotateY(yaw)
            .rotateX(pitch)
            .rotateZ(roll)
            .normalize()
    }

    override fun tick() {
        super.tick()
        manageRotation = false
        prevRotation.set(rotation)

        val player = world!!.getEntitiesOfClass(Player::class.java, AABB.ofSize(pos, 36.0, 36.0, 36.0)) {
            true
        }.lastOrNull() ?: return

        val rel = player.eyePosition - pos
        applyModelOffsetEuler(0f, -90f, 45f)
        rotateToPoint(rel.asRelative())

//        roll += 10f
//        rotateAsAxis(roll * PI / 180.0)

    }

    override fun rotateToPoint(to: RelativeLocation) {
        Math3DUtil.rotateQuatToPoint(rotation, to)
        rotation.mul(modelOffset).normalize()
    }

    override fun rotateAsAxis(radian: Double) {
        if (kotlin.math.abs(radian) < 1e-12) return

        val dq = Quaternionf().rotateZ(radian.toFloat())

        rotation.mul(dq)

        rotation.normalize()
    }

    override fun remove(reason: RemoveReason) {
        remove()
    }
}