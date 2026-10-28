package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.display.handle.DisplayEntityRegistryHelper
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.particles.control.RemoveReason
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.Math3DUtil
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.util.LightCoordsUtil
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import kotlin.math.PI
import kotlin.math.abs

@CooAutoRegister
class BarrageItemDisplayEntity(pos: Vec3, world: Level?) : DisplayEntity(pos, world) {
    /** 无世界的注册样本不读取尚未绑定的物品组件；网络解码会覆盖该字段。 */
    @CodecField
    var item = if (world == null) ItemStack.EMPTY else Items.DIAMOND_SWORD.defaultInstance

    @CodecField
    var isBlock = false


    @CodecField
    var rotation = Quaternionf()
    var prevRotation = Quaternionf()

    @CodecField
    var modelOffset = Quaternionf()

    override fun submit(
        view: Matrix4f,
        proj: Matrix4f,
        modelMatrixStack: PoseStack,
        collector: SubmitNodeCollector,
        delta: Float,
        camera: CameraRenderState
    ) {
        val offset = renderCenterOffset()
        modelMatrixStack.translate(-offset.x, -offset.y, -offset.z)
        MinecraftRendererUtil.applyAtPoint(
            offset, modelMatrixStack
        ) {
            rotate(rotation(delta))
        }
        collector.submitCustomGeometry(modelMatrixStack, RenderTypes.LINES) { pose, vertices ->
            vertices.addVertex(pose, 0F, 0F, -2F).setColor(255, 255, 255, 255)
            vertices.addVertex(pose, 0F, 0F, 2F).setColor(0, 0, 255, 255)
            vertices.addVertex(pose, 0F, 2F, 0F).setColor(255, 0, 0, 255)
            vertices.addVertex(pose, 0F, -2F, 0F).setColor(255, 255, 255, 255)
            vertices.addVertex(pose, 2F, 0F, 0F).setColor(0, 255, 0, 255)
            vertices.addVertex(pose, -2F, 0F, 0F).setColor(255, 255, 255, 255)
        }
        MinecraftRendererUtil.submitItem(
            item,
            ItemDisplayContext.NONE,
            world ?: return,
            null,
            0,
            modelMatrixStack,
            collector,
            LightCoordsUtil.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
        )
    }


    override fun getCodec(): StreamCodec<in RegistryFriendlyByteBuf, DisplayEntity> {
        return DisplayEntityRegistryHelper.generateCodec(this)
    }

    private fun lerp(delta: Float, min: Float, max: Float): Float {
        return GraphMathHelper.lerp(delta, min, max)
    }

    private fun rotation(delta: Float): Quaternionf {
        return GraphMathHelper.lerp(delta, prevRotation, rotation)
    }


    fun applyModelOffsetEuler(yawDeg: Float, pitchDeg: Float, rollDeg: Float) {

        val yaw = (-yawDeg) * (PI.toFloat() / 180F)
        val pitch = (-pitchDeg) * (PI.toFloat() / 180F)
        val roll = (rollDeg) * (PI.toFloat() / 180F)

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
        applyModelOffsetEuler(0F, -90F, 45F)
        rotateToPoint(rel.asRelative())

//        roll += 10f
//        rotateAsAxis(roll * PI / 180.0)

    }

    override fun rotateToPoint(to: RelativeLocation) {
        Math3DUtil.rotateQuatToPoint(rotation, to)
        rotation.mul(modelOffset).normalize()
    }

    override fun rotateAsAxis(radian: Double) {
        if (abs(radian) < 1e-12) return

        val dq = Quaternionf().rotateZ(radian.toFloat())

        rotation.mul(dq)

        rotation.normalize()
    }

    override fun remove(reason: RemoveReason) {
        remove()
    }
}
