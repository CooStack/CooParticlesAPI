package cn.coostack.cooparticlesapi.listeners

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.blocks.TestControllerBlock
import cn.coostack.cooparticlesapi.blocks.TestControllerBlockEntity
import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import cn.coostack.cooparticlesapi.items.CooItems
import cn.coostack.cooparticlesapi.items.TestBlockBinding
import cn.coostack.cooparticlesapi.items.TestBlockBindings
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.AABB

@EventListener(CooParticlesConstants.MOD_ID, dist = DistType.CLIENT)
object TestBlockBinderHighlightListener {
    private const val HIDDEN_SCAN_CHUNK_RADIUS = 6

    @EventHandler
    fun onRender(event: ClientWorldRenderEvent) {
        if (event.stage != ClientWorldRenderEvent.RenderStage.AFTER_ENTITY) {
            return
        }
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val stack = InteractionHand.values()
            .map { hand -> player.getItemInHand(hand) }
            .firstOrNull { stack -> stack.item === CooItems.TEST_BLOCK_BINDER.getItem() } ?: return
        val dimension = event.world.dimension().location().toString()
        val bindings = TestBlockBindings.read(stack).filter { binding ->
            binding.dimension == dimension &&
                    (!event.world.isLoaded(binding.pos) ||
                            event.world.getBlockEntity(binding.pos) is TestControllerBlockEntity)
        }
        val boundSet = bindings.toHashSet()

        val camera = event.camera.position
        val consumer = event.buffer.getBuffer(RenderType.lines())
        bindings.forEach { binding ->
            val box = AABB(binding.pos).inflate(0.02).move(-camera.x, -camera.y, -camera.z)
            LevelRenderer.renderLineBox(event.poseStack, consumer, box, 0.0f, 1.0f, 0.0f, 1.0f)
        }
        hiddenUnboundControllers(event, dimension, boundSet).forEach { blockEntity ->
            val box = AABB(blockEntity.blockPos).inflate(0.02).move(-camera.x, -camera.y, -camera.z)
            LevelRenderer.renderLineBox(event.poseStack, consumer, box, 1.0f, 1.0f, 1.0f, 1.0f)
        }
    }

    private fun hiddenUnboundControllers(
        event: ClientWorldRenderEvent,
        dimension: String,
        boundSet: Set<TestBlockBinding>
    ): List<TestControllerBlockEntity> {
        val playerPos = Minecraft.getInstance().player?.blockPosition() ?: return emptyList()
        val centerChunkX = playerPos.x shr 4
        val centerChunkZ = playerPos.z shr 4
        val result = ArrayList<TestControllerBlockEntity>()
        for (chunkX in centerChunkX - HIDDEN_SCAN_CHUNK_RADIUS..centerChunkX + HIDDEN_SCAN_CHUNK_RADIUS) {
            for (chunkZ in centerChunkZ - HIDDEN_SCAN_CHUNK_RADIUS..centerChunkZ + HIDDEN_SCAN_CHUNK_RADIUS) {
                if (!event.world.hasChunk(chunkX, chunkZ)) {
                    continue
                }
                event.world.getChunk(chunkX, chunkZ).blockEntities.values.forEach { blockEntity ->
                    if (blockEntity !is TestControllerBlockEntity) {
                        return@forEach
                    }
                    val state = event.world.getBlockState(blockEntity.blockPos)
                    if (state.block !is TestControllerBlock || !state.getValue(TestControllerBlock.HIDDEN)) {
                        return@forEach
                    }
                    val binding = TestBlockBinding(dimension, blockEntity.blockPos.immutable())
                    if (binding !in boundSet) {
                        result += blockEntity
                    }
                }
            }
        }
        return result
    }
}
