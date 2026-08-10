package cn.coostack.cooparticlesapi.blocks

import cn.coostack.cooparticlesapi.items.TestBlockBinding
import cn.coostack.cooparticlesapi.items.TestBlockBindings
import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenBoundTestSelectionScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.test.TestManager
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

object TestControllerBlockAccess {
    fun dimensionId(level: Level): String {
        return level.dimension().location().toString()
    }

    fun find(server: MinecraftServer, dimension: String, pos: BlockPos): TestControllerBlockEntity? {
        val level = getLevel(server, dimension) ?: return null
        if (!level.isLoaded(pos)) {
            return null
        }
        return level.getBlockEntity(pos) as? TestControllerBlockEntity
    }

    fun openControllerScreen(player: ServerPlayer, blockEntity: TestControllerBlockEntity) {
        val level = blockEntity.level ?: return
        CooServerPacketManager.sendTo(
            player,
            PacketOpenTestControllerScreenS2C.fromBlockEntity(
                dimensionId(level),
                blockEntity,
                blockEntity.registeredGroupIds()
            )
        )
    }

    fun openBoundSelection(player: ServerPlayer, stack: ItemStack) {
        val removed = pruneInvalidLoadedBindings(player, stack)
        if (removed > 0) {
            player.sendSystemMessage(Component.literal("已移除 $removed 个失效测试方块绑定"))
        }

        val bindings = TestBlockBindings.read(stack)
        if (bindings.isEmpty()) {
            player.sendSystemMessage(Component.literal("测试绑定器没有绑定任何测试方块"))
            return
        }

        val resolved = bindings.map { binding ->
            val blockEntity = find(player.server, binding.dimension, binding.pos)
            val currentIndex = blockEntity?.currentIndex() ?: 0
            BoundControllerEntry(
                dimension = binding.dimension,
                pos = binding.pos,
                groupId = blockEntity?.groupId?.ifBlank { "未配置" } ?: "未加载",
                status = blockEntity?.statusText() ?: "区块未加载或方块不存在",
                currentIndex = currentIndex,
                currentOptionId = if (currentIndex > 0) {
                    blockEntity?.optionIds()?.getOrNull(currentIndex - 1).orEmpty()
                } else {
                    ""
                },
                optionCount = blockEntity?.optionCount() ?: 0,
                loaded = blockEntity != null,
                running = blockEntity?.isRunning() ?: false
            )
        }

        if (resolved.size == 1 && resolved.first().loaded) {
            val only = resolved.first()
            val blockEntity = find(player.server, only.dimension, only.pos) ?: return
            openControllerScreen(player, blockEntity)
            return
        }

        CooServerPacketManager.sendTo(player, PacketOpenBoundTestSelectionScreenS2C.fromEntries(resolved))
    }

    fun stopBoundTests(player: ServerPlayer, stack: ItemStack): Int {
        var stopped = 0
        TestBlockBindings.read(stack).forEach { binding ->
            val blockEntity = find(player.server, binding.dimension, binding.pos) ?: return@forEach
            if (blockEntity.isRunning()) {
                blockEntity.stopTest()
                stopped++
            }
        }
        return stopped
    }

    fun toggleBoundTests(player: ServerPlayer, stack: ItemStack): BoundToggleResult {
        val removed = pruneInvalidLoadedBindings(player, stack)
        val blockEntities = TestBlockBindings.read(stack)
            .mapNotNull { binding -> find(player.server, binding.dimension, binding.pos) }
        val running = blockEntities.filter { blockEntity -> blockEntity.isRunning() }
        if (running.isNotEmpty()) {
            running.forEach { blockEntity -> blockEntity.stopTest() }
            return BoundToggleResult(started = 0, stopped = running.size, removed = removed)
        }

        val started = blockEntities.count { blockEntity -> blockEntity.startTest() }
        return BoundToggleResult(started = started, stopped = 0, removed = removed)
    }

    fun pruneInvalidLoadedBindings(player: ServerPlayer, stack: ItemStack): Int {
        val bindings = TestBlockBindings.read(stack)
        if (bindings.isEmpty()) {
            return 0
        }

        val retained = bindings.filterNot { binding -> isInvalidLoadedBinding(player.server, binding) }
        val removed = bindings.size - retained.size
        if (removed > 0) {
            TestBlockBindings.replaceAll(stack, retained)
        }
        return removed
    }

    private fun isInvalidLoadedBinding(server: MinecraftServer, binding: TestBlockBinding): Boolean {
        val level = getLevel(server, binding.dimension) ?: return true
        if (!level.isLoaded(binding.pos)) {
            return false
        }
        return level.getBlockEntity(binding.pos) !is TestControllerBlockEntity
    }

    private fun getLevel(server: MinecraftServer, dimension: String): ServerLevel? {
        val location = ResourceLocation.tryParse(dimension) ?: return null
        val key = ResourceKey.create(Registries.DIMENSION, location)
        return server.getLevel(key)
    }
}

data class BoundControllerEntry(
    val dimension: String,
    val pos: BlockPos,
    val groupId: String,
    val status: String,
    val currentIndex: Int,
    val currentOptionId: String,
    val optionCount: Int,
    val loaded: Boolean,
    val running: Boolean,
)

data class BoundToggleResult(
    val started: Int,
    val stopped: Int,
    val removed: Int,
)
