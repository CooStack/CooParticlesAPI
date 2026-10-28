package cn.coostack.cooparticlesapi.listeners

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.blocks.TestControllerBlockEntity
import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import cn.coostack.cooparticlesapi.items.CooItems
import cn.coostack.cooparticlesapi.items.TestBlockBindings
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import com.mojang.blaze3d.vertex.PoseStack.Pose
import com.mojang.blaze3d.vertex.VertexConsumer
import kotlin.math.sqrt

/**
 * 在手持测试方块绑定器时绘制动态玩家的路径和朝向。
 *
 * 示例：动态位置显示青色路径，当前采样点显示红色小框。
 * 禁止在客户端渲染回调中修改方块实体或测试配置。
 */
@EventListener(CooParticlesConstants.MOD_ID, dist = DistType.CLIENT)
object TestBlockPlayerPathListener {

    /**
     * 绘制当前维度内已绑定控制器的动态预览。
     *
     * 示例：绑定器放在任意一只手时都会显示同一组路径。
     * 禁止在其他渲染回调重复提交相同路径。
     *
     * @param event 世界渲染事件
     */
    @EventHandler
    fun onRender(event: ClientWorldRenderEvent) {
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val stack = InteractionHand.values()
            .map { hand -> player.getItemInHand(hand) }
            .firstOrNull { it.item === CooItems.TEST_BLOCK_BINDER.getItem() } ?: return
        val dimension = event.world.dimension().identifier().toString()
        val camera = event.camera.pos
        val partialTick = event.delta
        val blockEntities = TestBlockBindings.read(stack)
            .asSequence()
            .filter { it.dimension == dimension && event.world.isLoaded(it.pos) }
            .mapNotNull { event.world.getBlockEntity(it.pos) as? TestControllerBlockEntity }
            .toList()
        MinecraftRendererUtil.submitLines(event.collector, event.poseStack) { pose, consumer ->
            blockEntities.forEach { blockEntity ->
                drawPreview(pose, consumer, blockEntity, camera, partialTick)
            }
        }
        blockEntities.forEach { blockEntity ->
            val origin = Vec3.atCenterOf(blockEntity.blockPos)
            val positionOffset = if (blockEntity.playerPositionDynamic) {
                blockEntity.playerPositionTrack.sample(blockEntity.positionAnimationElapsedTicks(partialTick))
            } else {
                blockEntity.playerOffset
            }
            MinecraftRendererUtil.submitLineBox(
                event.collector,
                event.poseStack,
                AABB.ofSize(origin.add(positionOffset), 0.14, 0.14, 0.14),
                camera,
                1F,
                0.1F,
                0.1F,
                1F,
            )
        }
    }

    private fun drawPreview(
        pose: Pose,
        consumer: VertexConsumer,
        blockEntity: TestControllerBlockEntity,
        camera: Vec3,
        partialTick: Float,
    ) {
        val origin = Vec3.atCenterOf(blockEntity.blockPos)
        if (blockEntity.playerPositionDynamic) {
            val pathSamples = 64
            var previous = origin.add(blockEntity.playerPositionTrack.sampleTimeline(0.0))
            for (index in 1..pathSamples) {
                val tick = blockEntity.playerPositionTrack.durationTicks.toDouble() * index / pathSamples.toDouble()
                val next = origin.add(blockEntity.playerPositionTrack.sampleTimeline(tick))
                drawLine(pose, consumer, previous, next, camera, 80, 190, 255)
                previous = next
            }
        }

        val positionOffset = if (blockEntity.playerPositionDynamic) {
            blockEntity.playerPositionTrack.sample(blockEntity.positionAnimationElapsedTicks(partialTick))
        } else {
            blockEntity.playerOffset
        }
        val point = origin.add(positionOffset)
        val forward = if (blockEntity.playerForwardDynamic) {
            normalize(blockEntity.playerForwardTrack.sample(blockEntity.forwardAnimationElapsedTicks(partialTick)))
        } else {
            normalize(blockEntity.playerForward)
        }
        drawArrow(pose, consumer, point, forward, camera)
    }

    private fun drawArrow(
        pose: Pose,
        consumer: VertexConsumer,
        origin: Vec3,
        forward: Vec3,
        camera: Vec3,
    ) {
        val tip = origin.add(forward.scale(1.5))
        drawLine(pose, consumer, origin, tip, camera, 255, 90, 40)
        var side = forward.cross(Vec3(0.0, 1.0, 0.0))
        if (side.lengthSqr() <= 1.0E-8) side = forward.cross(Vec3(1.0, 0.0, 0.0))
        side = normalize(side)
        val vertical = normalize(forward.cross(side))
        val base = tip.subtract(forward.scale(0.32))
        drawLine(pose, consumer, tip, base.add(side.scale(0.22)), camera, 255, 90, 40)
        drawLine(pose, consumer, tip, base.subtract(side.scale(0.22)), camera, 255, 90, 40)
        drawLine(pose, consumer, tip, base.add(vertical.scale(0.22)), camera, 255, 90, 40)
        drawLine(pose, consumer, tip, base.subtract(vertical.scale(0.22)), camera, 255, 90, 40)
    }

    private fun drawLine(
        pose: Pose,
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
