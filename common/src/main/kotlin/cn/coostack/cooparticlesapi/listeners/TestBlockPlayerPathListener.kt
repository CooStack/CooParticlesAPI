package cn.coostack.cooparticlesapi.listeners

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.blocks.TestControllerBlockEntity
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import cn.coostack.cooparticlesapi.items.CooItems
import cn.coostack.cooparticlesapi.items.TestBlockBindings
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import kotlin.math.sqrt

/**
 * 在手持测试方块绑定器时绘制动态玩家的路径和朝向。
 *
 * 示例：动态位置显示青色路径，当前采样点显示红色小框。
 * 禁止在客户端渲染回调中修改方块实体或测试配置。
 */
@EventListener(CooParticlesConstants.MOD_ID)
object TestBlockPlayerPathListener {
    private const val PATH_SAMPLES = 64

    /**
     * 绘制当前维度内已绑定控制器的动态预览。
     *
     * 示例：绑定器放在任意一只手时都会显示同一组路径。
     * 禁止在非 [ClientWorldRenderEvent.RenderStage.AFTER_ENTITY] 阶段重复提交顶点。
     *
     * @param event 世界渲染事件
     */
    @EventHandler
    fun onRender(event: ClientWorldRenderEvent) {
        if (event.stage != ClientWorldRenderEvent.RenderStage.AFTER_ENTITY) return
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val stack = InteractionHand.values()
            .map { hand -> player.getItemInHand(hand) }
            .firstOrNull { it.item === CooItems.TEST_BLOCK_BINDER.getItem() } ?: return
        val dimension = event.world.dimension().location().toString()
        val camera = event.camera.position
        val consumer = event.buffer.getBuffer(RenderType.lines())
        val partialTick = event.delta.getGameTimeDeltaPartialTick(true)
        TestBlockBindings.read(stack)
            .asSequence()
            .filter { it.dimension == dimension && event.world.isLoaded(it.pos) }
            .mapNotNull { event.world.getBlockEntity(it.pos) as? TestControllerBlockEntity }
            .forEach { blockEntity ->
                drawPreview(event.poseStack, consumer, blockEntity, camera, partialTick)
            }
    }

    private fun drawPreview(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        blockEntity: TestControllerBlockEntity,
        camera: Vec3,
        partialTick: Float,
    ) {
        val origin = Vec3.atCenterOf(blockEntity.blockPos)
        if (blockEntity.playerPositionDynamic) {
            var previous = origin.add(blockEntity.playerPositionTrack.sampleTimeline(0.0))
            for (index in 1..PATH_SAMPLES) {
                val tick = blockEntity.playerPositionTrack.durationTicks.toDouble() * index / PATH_SAMPLES.toDouble()
                val next = origin.add(blockEntity.playerPositionTrack.sampleTimeline(tick))
                drawLine(poseStack, consumer, previous, next, camera, 80, 190, 255)
                previous = next
            }
        }

        val positionOffset = if (blockEntity.playerPositionDynamic) {
            blockEntity.playerPositionTrack.sample(blockEntity.positionAnimationElapsedTicks(partialTick))
        } else {
            blockEntity.playerOffset
        }
        val point = origin.add(positionOffset)
        val pointBox = AABB.ofSize(point, 0.14, 0.14, 0.14).move(-camera.x, -camera.y, -camera.z)
        LevelRenderer.renderLineBox(poseStack, consumer, pointBox, 1.0f, 0.1f, 0.1f, 1.0f)

        val forward = if (blockEntity.playerForwardDynamic) {
            normalize(blockEntity.playerForwardTrack.sample(blockEntity.forwardAnimationElapsedTicks(partialTick)))
        } else {
            normalize(blockEntity.playerForward)
        }
        drawArrow(poseStack, consumer, point, forward, camera)
    }

    private fun drawArrow(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        origin: Vec3,
        forward: Vec3,
        camera: Vec3,
    ) {
        val tip = origin.add(forward.scale(1.5))
        drawLine(poseStack, consumer, origin, tip, camera, 255, 90, 40)
        var side = forward.cross(Vec3(0.0, 1.0, 0.0))
        if (side.lengthSqr() <= 1.0E-8) side = forward.cross(Vec3(1.0, 0.0, 0.0))
        side = normalize(side)
        val vertical = normalize(forward.cross(side))
        val base = tip.subtract(forward.scale(0.32))
        drawLine(poseStack, consumer, tip, base.add(side.scale(0.22)), camera, 255, 90, 40)
        drawLine(poseStack, consumer, tip, base.subtract(side.scale(0.22)), camera, 255, 90, 40)
        drawLine(poseStack, consumer, tip, base.add(vertical.scale(0.22)), camera, 255, 90, 40)
        drawLine(poseStack, consumer, tip, base.subtract(vertical.scale(0.22)), camera, 255, 90, 40)
    }

    private fun drawLine(
        poseStack: PoseStack,
        consumer: VertexConsumer,
        start: Vec3,
        end: Vec3,
        camera: Vec3,
        red: Int,
        green: Int,
        blue: Int,
    ) {
        val relativeStart = start.subtract(camera)
        val relativeEnd = end.subtract(camera)
        val delta = relativeEnd.subtract(relativeStart)
        val length = delta.length()
        if (length <= 1.0E-7) return
        val normal = delta.scale(1.0 / length)
        val pose = poseStack.last().pose()
        consumer.addVertex(pose, relativeStart.x.toFloat(), relativeStart.y.toFloat(), relativeStart.z.toFloat())
            .setColor(red, green, blue, 255)
            .setNormal(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
        consumer.addVertex(pose, relativeEnd.x.toFloat(), relativeEnd.y.toFloat(), relativeEnd.z.toFloat())
            .setColor(red, green, blue, 255)
            .setNormal(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
    }

    private fun normalize(value: Vec3): Vec3 {
        val length = sqrt(value.x * value.x + value.y * value.y + value.z * value.z)
        return if (!length.isFinite() || length <= 1.0E-7) Vec3(0.0, 0.0, 1.0) else value.scale(1.0 / length)
    }
}
