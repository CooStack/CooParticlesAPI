package cn.coostack.cooparticlesapi.event.events.world.client

import cn.coostack.cooparticlesapi.event.events.world.ClientWorldEvent
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.Vec3

/**
 * 这里要同时对齐 fabric 和 neoforge的事件线
 *
 * 这里采用转发fabric/neoforge的事件实现
 *
 * 为了更好的兼容性所做的牺牲！
 *
 * @property poseStack 当前提交使用的姿势
 * @property collector 当前渲染阶段的命令提交收集器
 * @property camera 摄像头
 * @property delta 当前世界帧的 tick 插值
 * @param world 渲染的世界类型
 */
class ClientWorldRenderEvent(
    world: ClientLevel,
    val poseStack: PoseStack,
    val collector: SubmitNodeCollector,
    val worldRenderer: LevelRenderer,
    val camera: CameraRenderState,
    val delta: Float
) : ClientWorldEvent(world) {
    /**
     * 在事件持有的姿势栈中将世界位置转换为相机相对位置。
     *
     * @param pos 目标世界坐标
     * @param invoker 在变换范围内追加姿势变换的操作
     */
    fun transformTo(pos: Vec3, invoker: PoseStack.() -> Unit) {
        MinecraftRendererUtil.transformTo(camera.pos, pos, poseStack, invoker)
    }

}
