package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.display.handle.DisplayEntityRegistryHelper
import cn.coostack.cooparticlesapi.display.DisplayEntity
import cn.coostack.cooparticlesapi.extend.minus
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.util.LightCoordsUtil
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemDisplayContext
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
@CooAutoRegister
class TestBlockDisplayEntity(pos: Vec3, world: Level?) : DisplayEntity(pos, world) {
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
        val level = world ?: return
        val item = Items.CONCRETE.white().defaultInstance
        MinecraftRendererUtil.applyAtPoint(renderCenterOffset(), modelMatrixStack) {
            scale(5F, 5F, 5F)
        }
        MinecraftRendererUtil.submitItem(
            item,
            ItemDisplayContext.NONE,
            level,
            null,
            1,
            modelMatrixStack,
            collector,
            LightCoordsUtil.FULL_BRIGHT,
            OverlayTexture.NO_OVERLAY,
        )
        MinecraftRendererUtil.applyAtPoint(renderCenterOffset(), modelMatrixStack) {
            collector.submitCustomGeometry(modelMatrixStack, RenderTypes.LINES) { pose, vertices ->
                vertices.addVertex(pose, 0F, 0F, -2F).setColor(255, 255, 255, 255)
                vertices.addVertex(pose, 0F, 0F, 2F).setColor(255, 255, 128, 255)
            }
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

    override fun getCodec(): StreamCodec<in RegistryFriendlyByteBuf, DisplayEntity> {
        return DisplayEntityRegistryHelper.generateCodec(this)
    }

}
