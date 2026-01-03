package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.annotations.display.DisplayEntityRegister
import cn.coostack.cooparticlesapi.annotations.display.DisplayField
import cn.coostack.cooparticlesapi.annotations.display.handle.DisplayEntityHelper
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.mixin.events.world.client.ItemRendererInvoker
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
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

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
@DisplayEntityRegister
class TestBlockDisplayEntity(pos: Vec3, world: Level?) : DisplayEntity(pos, world) {
    @DisplayField
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
        val renderer = Minecraft.getInstance().itemRenderer
        val item = Items.WHITE_CONCRETE.defaultInstance
        val seaLantern = renderer.getModel(item, world, null, 1)
        val invoker = renderer as ItemRendererInvoker
        val consumer = buffer.getBuffer(
            RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS)
        )
        val solid = buffer.getBuffer(
            RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS)
        )
        // 左下角 到 右上角
        MinecraftRendererUtil.applyAtPoint(renderCenterOffset(), modelMatrixStack) {
            scale(5f, 5f, 5f)
        }
        invoker.renderModel(
            seaLantern,
            item,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            modelMatrixStack,
            consumer
        )
        invoker.renderModel(
            seaLantern,
            item,
            LightTexture.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
            modelMatrixStack,
            solid
        )
        // 左下角
        modelMatrixStack.popPose()
        MinecraftRendererUtil.applyAtPoint(renderCenterOffset(), modelMatrixStack) {
            buffer.getBuffer(RenderType.LINES)
                .addVertex(modelMatrixStack.last(), 0f, 0f, -2f)
                .setNormal(modelMatrixStack.last(), 0f, 1f, 0f)
                .setColor(255, 255, 255, 255)
                .setLight(LightTexture.FULL_BRIGHT)
                .addVertex(modelMatrixStack.last(), 0f, 0f, 2f)
                .setNormal(modelMatrixStack.last(), 0f, 1f, 0f)
                .setColor(255, 255, 128, 255)
                .setLight(LightTexture.FULL_BRIGHT)
        }

    }


    override fun tick() {
        super.tick()
        lookAt(direction)
        // 最近的玩家
        if (world!!.isClientSide) {
            return
        }
        val server = world as ServerLevel
        val player = server.players().minByOrNull {
            it.position().distanceTo(pos)
        } ?: return

        direction = player.eyePosition - pos
    }

    override fun getCodec(): StreamCodec<FriendlyByteBuf, DisplayEntity> {
        return DisplayEntityHelper.generateCodec(this)
    }

}