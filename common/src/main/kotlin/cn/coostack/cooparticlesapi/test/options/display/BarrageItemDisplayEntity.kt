package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.display.handle.DisplayEntityHelper
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.utils.GraphMathHelper
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

@CooAutoRegister
class BarrageItemDisplayEntity(pos: Vec3, world: Level?) : DisplayEntity(pos, world) {
    @CodecField
    var item = Items.DIAMOND_SWORD.defaultInstance

    @CodecField
    var isBlock = false

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

        modelMatrixStack.pushPose()
        MinecraftRendererUtil.applyAtPoint(
            offset, modelMatrixStack
        ) {
            MinecraftRendererUtil.applyRotation(
                this,
                yaw(delta),
                pitch(delta),
                roll(delta) + if (isBlock) 0f else 45f
            )
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
        modelMatrixStack.popPose()
    }


    override fun getCodec(): StreamCodec<FriendlyByteBuf, DisplayEntity> {
        return DisplayEntityHelper.generateCodec(this)
    }

    private fun lerp(delta: Float, min: Float, max: Float): Float {
        return GraphMathHelper.lerp(delta, min, max)
    }

    override fun tick() {
        super.tick()
        manageRotation = false
        pitch += 10
        pitch %= 360
    }

}