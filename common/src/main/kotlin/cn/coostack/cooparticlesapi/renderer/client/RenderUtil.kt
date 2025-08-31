package cn.coostack.cooparticlesapi.renderer.client

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import org.joml.Matrix4fStack

object RenderUtil {
    @JvmStatic
    fun setRenderStackWithEntity(
        stack: Matrix4fStack,
        entity: RenderEntity,
        tickDelta: Float
    ): Matrix4fStack {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position.toVector3f()
        val last = entity.lastRenderPos.toVector3f()
        val now = entity.pos.toVector3f()
        val x = Mth.lerp(tickDelta, last.x, now.x)
        val y = Mth.lerp(tickDelta, last.y, now.y)
        val z = Mth.lerp(tickDelta, last.z, now.z)
        stack.translate(x - camera.x, y - camera.y, z - camera.z)
        return stack
    }
}