package cn.coostack.cooparticlesapi.event.events.world.client

import cn.coostack.cooparticlesapi.event.events.world.WorldEvent
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.DeltaTracker
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

/**
 * 这里要同时对齐 fabric 和 neoforge的事件线
 *
 * 这里采用转发fabric/neoforge的事件实现
 *
 * 为了更好的兼容性所做的牺牲！
 *
 * @property stage 渲染阶段
 * @property viewMatrix 观察矩阵
 * @property projectMatrix 透视矩阵
 * @property poseStack 变换矩阵stack
 * @property buffer 渲染buffer
 * @property camera 摄像头
 * @property delta tick插值器
 * @param world 渲染的世界类型
 */
class ClientWorldRenderEvent(
    world: ClientLevel,
    val stage: RenderStage,
    val viewMatrix: Matrix4f,
    val projectMatrix: Matrix4f,
    val poseStack: PoseStack,
    val buffer: MultiBufferSource,
    val worldRenderer: LevelRenderer,
    val camera: Camera,
    val delta: DeltaTracker
) : WorldEvent(world) {
    /**
     * 后面会兼容其他的阶段
     *
     * 目前还没找到同时兼容NF 和 Fabric的方案
     */
    enum class RenderStage {
        /**
         * 在渲染实体之后执行
         * 对标Fabric的 AFTER_ENTITY 和 NeoForged的 AFTER_ENTITY
         */
        AFTER_ENTITY,

    }

    fun transformTo(pos: Vec3, invoker: PoseStack.() -> Unit) {
        MinecraftRendererUtil.transformTo(camera, pos, poseStack, invoker)
    }

}