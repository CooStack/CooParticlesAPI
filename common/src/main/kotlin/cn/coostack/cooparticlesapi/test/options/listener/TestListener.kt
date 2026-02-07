package cn.coostack.cooparticlesapi.test.options.listener

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.event.api.EventPriority
import cn.coostack.cooparticlesapi.test.options.event.TestChildEvent
import cn.coostack.cooparticlesapi.test.options.event.TestEvent
import net.minecraft.network.chat.Component

@EventListener(CooParticlesConstants.MOD_ID)
class TestListener {
    @EventHandler
    fun onTestEvent(event: TestEvent) {
        val player = event.player
        player.sendSystemMessage(Component.literal("父类 event 执行"))
    }

    @EventHandler
    fun onTestChild(event: TestChildEvent) {
        val player = event.player
        player.sendSystemMessage(Component.literal("子类 event 执行 id: ${event.id}"))
    }

    @EventHandler(EventPriority.LOW)
    fun onTestChildCanceled(event: TestChildEvent) {
        event.isInterrupted = true
    }


//    /**
//     * 这是一个生成展示实体的样例
//     * 主要是实现了实体模型旋转的矩阵，
//     * 然后找一下怎么对齐模型到几何中心
//     *
//     * @param event
//     */
//    @EventHandler
//    fun onTestRendererEntity(event: ClientWorldRenderEvent) {
//        if (event.stage != ClientWorldRenderEvent.RenderStage.AFTER_ENTITY) {
//            return
//        }
//        val trident = Minecraft.getInstance().entityModels.bakeLayer(
//            ModelLayers.TRIDENT
//        )
//        val player = Minecraft.getInstance().player ?: return
//        val buffer = event.buffer
//        val stack = event.poseStack
//        val delta = event.delta.getGameTimeDeltaPartialTick(true)
//        val oldPos = Vec3(
//            player.xOld,
//            player.yOld,
//            player.zOld
//        )
//        val current = player.position()
//        val lerp = GraphMathHelper.lerp(delta, oldPos, current)
//        val rad = Math.toRadians(event.world.gameTime.toDouble() + delta) * 2
//        val loop = abs(sin(rad) * 20)
//        MinecraftRendererUtil.transformTo(
//            event.camera, lerp + Vec3(0.0, 4.0, 0.0), stack
//        ) {
//            stack.pushPose()
//            val renderer = Minecraft.getInstance().itemRenderer
//            val item = Items.SEA_LANTERN.defaultInstance
//            val diamondBlock = renderer.getModel(item, event.world, null, 1)
//            val invoker = renderer as ItemRendererInvoker
//            val point = Vec3(0.5, 0.5, 0.5)
//            this.translate(-point.x, -point.y, -point.z)
//            pushPose()
//            MinecraftRendererUtil.applyAtPoint(
//                point, this
//            ) {
//                MinecraftRendererUtil.applyRotation(stack, 90f, 90f, event.world.gameTime.toFloat() * 5f % 360)
//            }
//            MinecraftRendererUtil.applyAtPoint(
//                Vec3(0.5, 0.5, 1.0), this
//            ) {
//                this.scale(0.5f, 0.5f, 2F)
//            }
//            val consumer = buffer.getBuffer(
//                RenderType.entityTranslucentEmissive(InventoryMenu.BLOCK_ATLAS)
//            )
//
//            invoker.renderModel(
//                diamondBlock,
//                item,
//                LightTexture.FULL_BRIGHT,
//                OverlayTexture.NO_OVERLAY,
//                stack,
//                consumer
//            )
//            popPose()
//
//            pushPose()
//            translate(0.0, 0.1, 0.0)
//            MinecraftRendererUtil.applyAtPoint(
//                point, this
//            ) {
//                MinecraftRendererUtil.applyRotation(stack, 90f, 90f, event.world.gameTime.toFloat() * 5f % 360)
//            }
//            MinecraftRendererUtil.applyAtPoint(
//                Vec3(0.5, 0.5, 1.0), this
//            ) {
//                this.scale(0.7f, 0.7f, 2.5F)
//            }
//            invoker.renderModel(
//                renderer.getModel(Items.GLASS.defaultInstance, event.world, null, 1),
//                Items.GLASS.defaultInstance,
//                LightTexture.FULL_BRIGHT,
//                OverlayTexture.NO_OVERLAY,
//                stack,
//                consumer
//            )
//            popPose()
//            stack.popPose()
//            buffer.getBuffer(RenderType.LINES)
//                .addVertex(stack.last(), 0f, 0f, 0f)
//                .setNormal(stack.last(), 0f, 1f, 0f)
//                .setColor(255, 255, 255, 255)
//                .setLight(LightEngine.MAX_LEVEL)
//                .addVertex(stack.last(), 0f, 1f, 0f)
//                .setNormal(stack.last(), 0f, 1f, 0f)
//                .setColor(255, 255, 0, 255)
//                .setLight(LightEngine.MAX_LEVEL)
//        }
//    }

//    @EventHandler
//    fun onTestItemBreak(event: PlayerItemDestroyEvent) {
//        val player = event.player
//        player.sendSystemMessage(Component.literal("物品破坏事件 :${event.original}"))
//    }

//    @EventHandler
//    fun onTestBlockPlace(event: EntityPrePlaceBlockEvent) {
//        event.isCancelled = true
//        event.entity.sendSystemMessage(Component.literal("不准放! + ${event.entity.level().isClientSide}"))
//    }
//
//    @EventHandler
//    fun onTestBlockBreak(event: PlayerBlockBreakEvent) {
//        event.isCancelled = true
//        event.player.sendSystemMessage(Component.literal("不准破坏!"))
//    }

//    @EventHandler
//    fun onPlayerDeath(event: ServerPlayerDeathEvent) {
//        event.player.sendSystemMessage(Component.literal("菜! + "))
//        event.isCancelled = true
//        event.player.health = 1f
//    }
//
//    @EventHandler
//    fun onPlayerRespawn(event: ServerPlayerRespawnEvent) {
//        event.player.sendSystemMessage(Component.literal("你活了"))
//    }


}