package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.client.MinecraftClient
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.math.MathHelper

object RenderUtil {
    @JvmStatic
    fun setRenderStackWithEntity(
        stack: MatrixStack,
        entity: RenderEntity,
        tickDelta: Float
    ): MatrixStack {
        val camera = MinecraftClient.getInstance().gameRenderer.camera.pos
        val last = entity.lastRenderPos.toVector3f()
        val now = entity.pos.toVector3f()
        val x = MathHelper.lerp(tickDelta, last.x, now.x)
        val y = MathHelper.lerp(tickDelta, last.y, now.y)
        val z = MathHelper.lerp(tickDelta, last.z, now.z)
        stack.translate(x - camera.x, y - camera.y, z - camera.z)
        return stack
    }

}