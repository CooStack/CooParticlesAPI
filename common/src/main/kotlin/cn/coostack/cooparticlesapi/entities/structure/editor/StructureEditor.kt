package cn.coostack.cooparticlesapi.entities.structure.editor

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.entities.structure.StructureModelSettings
import cn.coostack.cooparticlesapi.entities.structure.StructureSnapshot
import cn.coostack.cooparticlesapi.entities.structure.StructureModelEntity
import cn.coostack.cooparticlesapi.entities.structure.StructureModels
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.event.events.packet.CooPacketReceiveEvent
import cn.coostack.cooparticlesapi.event.events.server.ServerStoppedEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerDisconnectEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.ServerPlayerRespawnEvent
import cn.coostack.cooparticlesapi.event.events.server.ServerPostTickEvent
import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.nbt.CompoundTag
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.HitResult
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.ClipContext
import java.util.UUID

/** 结构编辑器的服务器入口；管理玩家选区、原版模板保存和原位模型转换，不改动旧模型编辑流程。 */
@EventListener
object StructureEditor {
    /** 客户端预览与服务器射线共用的选点距离，单位为格。 */
    const val SELECTION_REACH = 3.0
    /** 独立于结构模型编辑器的选点工具，注册及所有输入入口共享实例。 */
    private val itemEntry = CooParticlesServices.COO_REGISTRY.register(CommonDeferredRegistry(
        BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "structure_editor")
    ) { Item(Item.Properties().stacksTo(1)) })
    /** 平台延迟注册后的选点工具实例。 */
    val ITEM: Item get() = itemEntry.get()
    /** 仅服务器线程读写；退出、复活、切换维度和关闭服务器时清理。 */
    private val sessions = mutableMapOf<UUID, StructureEditorSession>()

    /** 注册协议和选区生命周期回调，由 StructureModels.init 调用一次。 */
    fun init() {}

    /** 只在服务端消费选区操作。 */
    @EventHandler
    fun packet(event: CooPacketReceiveEvent) {
        val packet = event.packet as? StructureSelectionPayload ?: return
        if (event.side == CooPacketReceiveEvent.Side.SERVER) event.sender?.let { receive(it, packet.data) }
    }

    /** 退出连接即释放该玩家会话。 */
    @EventHandler
    fun disconnect(event: PlayerDisconnectEvent) { sessions.remove(event.player.uuid) }

    /** 集成服务器再次启动时不得复用上次选区。 */
    @EventHandler
    fun stop(event: ServerStoppedEvent) { sessions.clear() }

    /** 复活后的新玩家对象使用空选区。 */
    @EventHandler
    fun respawn(event: ServerPlayerRespawnEvent) { (event.player as? ServerPlayer)?.let(::clear) }

    /** 两个平台均在服务端 tick 后检查维度，进入新维度前的包仍会在 receive 中拒绝。 */
    @EventHandler
    fun tick(event: ServerPostTickEvent) {
        event.server.playerList.players.forEach { player ->
            if (sessions[player.uuid]?.dimension?.let { it != player.level().dimension().location() } == true) clear(player)
        }
    }

    /**
     * 判断玩家是否在任一手持有选点工具，不包含模型属性编辑器。
     * @param player 当前逻辑侧玩家
     * @return 是否接管该玩家的选点输入；权限另由服务端操作入口判断
     */
    @JvmStatic
    fun isHolding(player: Player): Boolean =
        player.mainHandItem.`is`(ITEM) || player.offhandItem.`is`(ITEM)

    /** 只接受操作意图；坐标由服务器射线决定，保存必须携带当前选区版本。 */
    private fun receive(player: ServerPlayer, data: CompoundTag) {
        if (!isHolding(player) || !player.isAlive || player.isSpectator) {
            if (data.hasUUID("Revision")) send(player, CompoundTag().apply {
                putString("Action", "result")
                putUUID("Revision", data.getUUID("Revision"))
                putString("Message", "请保持存活并手持结构编辑器")
            })
            return
        }
        val action = data.getString("Action")
        if (action == "clear") { clear(player); return }
        val state = sessions.getOrPut(player.uuid) { StructureEditorSession(player.level().dimension().location()) }
        if (state.dimension != player.level().dimension().location()) { clear(player); return }
        val result = runCatching {
            when (action) {
                "first", "second" -> select(player, state, action == "first", data)
                "open" -> {
                    requireNotNull(state.selection) { "请先确定两个角点" }
                    require((player.isCreative && player.hasPermissions(2))) { "与原版结构方块一致，保存需要创造模式和管理员权限" }
                    send(player, state.packet("open"))
                }
                "save", "draft" -> {
                    require(data.hasUUID("Revision") && data.getUUID("Revision") == state.revision) { "选区已改变，请重新按 G 打开" }
                    require((player.isCreative && player.hasPermissions(2))) { "保存需要创造模式和管理员权限" }
                    if (action == "save") {
                        require(!player.cooldowns.isOnCooldown(ITEM)) { "请稍后重试" }
                        player.cooldowns.addCooldown(ITEM, 10)
                    }
                    save(player, state, data.getCompound("Draft"), action == "save")
                    send(player, state.packet("state"))
                    send(player, CompoundTag().apply {
                        putString("Action", "result")
                        putUUID("Revision", data.getUUID("Revision"))
                        putBoolean("Success", true)
                        putString("Message", if (action == "draft") "选区设置已保留"
                            else if (state.draft.getBoolean("Convert")) "结构已保存并原位转换" else "结构已保存")
                    })
                }
            }
        }
        result.onFailure {
            val response = CompoundTag()
            response.putString("Action", "result")
            if (data.hasUUID("Revision")) response.putUUID("Revision", data.getUUID("Revision"))
            response.putString("Message", it.message ?: "结构操作失败")
            send(player, response)
            player.displayClientMessage(Component.literal(it.message ?: "结构操作失败"), true)
        }
    }

    /** 左右键均优先选择命中方块；未命中时取眼睛沿视线三格处，负坐标使用向下取整。 */
    private fun select(player: ServerPlayer, state: StructureEditorSession, first: Boolean, input: CompoundTag) {
        val yaw = input.getFloat("Yaw")
        val pitch = input.getFloat("Pitch")
        require(yaw.isFinite() && pitch.isFinite() && pitch in -90F..90F) { "视线参数无效" }
        // 客户端只提供当次点击的朝向，服务器用自身眼睛位置重新射线，避免旋转包晚于点击包导致错选。
        val end = player.eyePosition.add(Vec3.directionFromRotation(pitch, yaw).scale(SELECTION_REACH))
        val hit = player.serverLevel().clip(ClipContext(player.eyePosition, end,
            ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
        val pos = if (hit.type == HitResult.Type.BLOCK) hit.blockPos
            else BlockPos.containing(end)
        require(player.serverLevel().isInWorldBounds(pos) && player.serverLevel().worldBorder.isWithinBounds(pos)) { "选点超出世界范围" }
        if (first) state.first = pos.immutable() else state.second = pos.immutable()
        state.revision = UUID.randomUUID()
        // 修改任一点后，旧尺寸和偏移不应暗中扩大新选区；保留名称与显示偏好。
        val previous = state.draft
        state.draft = CompoundTag().apply {
            putString("Name", previous.getString("Name"))
            putBoolean("IncludeEntities", previous.getBoolean("IncludeEntities"))
            putBoolean("ShowAir", previous.getBoolean("ShowAir"))
            putBoolean("ShowBox", !previous.contains("ShowBox") || previous.getBoolean("ShowBox"))
        }
        send(player, state.packet("state"))
    }

    /** 清理固定点并同步空状态；不修改玩家已经保存的模板或生成的模型。 */
    private fun clear(player: ServerPlayer) {
        sessions.remove(player.uuid)
        send(player, StructureEditorSession(player.level().dimension().location()).packet("state"))
    }

    /** 校验原版保存参数，生成模板后先落盘；只有明确选择转换才修改世界。 */
    private fun save(player: ServerPlayer, state: StructureEditorSession, input: CompoundTag, write: Boolean) {
        val original = requireNotNull(state.selection) { "请先确定两个角点" }
        val offset = BlockPos(input.getInt("OffsetX"), input.getInt("OffsetY"), input.getInt("OffsetZ"))
        val size = BlockPos(input.getInt("SizeX"), input.getInt("SizeY"), input.getInt("SizeZ"))
        val selected = original.adjusted(offset, size)
        val nameText = input.getString("Name").trim()
        require(nameText.length <= 256) { "结构名称过长" }
        val draft = CompoundTag().apply {
            putString("Name", nameText)
            putInt("OffsetX", offset.x); putInt("OffsetY", offset.y); putInt("OffsetZ", offset.z)
            putInt("SizeX", size.x); putInt("SizeY", size.y); putInt("SizeZ", size.z)
            putBoolean("IncludeEntities", input.getBoolean("IncludeEntities"))
            putBoolean("ShowAir", input.getBoolean("ShowAir"))
            putBoolean("ShowBox", input.getBoolean("ShowBox"))
            putBoolean("Convert", input.getBoolean("Convert"))
        }
        if (!write) { state.draft = draft; return }
        val name = requireNotNull(ResourceLocation.tryParse(nameText)) { "请输入有效的结构 ID" }
        require(name.path.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "结构路径无效" }
        val world = player.serverLevel()
        require(selected.box.distanceToSqr(player.position()) <= 96.0 * 96.0) { "距离选区过远" }
        require(world.isInWorldBounds(selected.origin) && world.isInWorldBounds(selected.second)) { "选区超出建筑高度" }
        for (pos in BlockPos.betweenClosed(selected.origin, selected.second)) {
            require(world.hasChunkAt(pos)) { "选区存在未加载区块" }
            require(world.worldBorder.isWithinBounds(pos) && world.mayInteract(player, pos)) { "选区包含不可修改的位置" }
        }
        val template = StructureTemplate()
        template.fillFromWorld(world, selected.origin, selected.size, draft.getBoolean("IncludeEntities"), Blocks.STRUCTURE_VOID)
        template.author = player.gameProfile.name
        // 转换限制先检查，纯保存仍完全使用原版模板能力，不受实体的 8192 方块限制。
        val model = if (draft.getBoolean("Convert")) StructureSnapshot.fromTemplate(world.registryAccess(), template) else null
        val manager = world.structureManager
        val cached = manager.getOrCreate(name)
        val previous = cached.save(CompoundTag())
        cached.load(world.registryAccess().lookupOrThrow(Registries.BLOCK), template.save(CompoundTag()))
        val saved = runCatching { check(manager.save(name)) { "无法写入结构文件，方块未改动" } }
        if (saved.isFailure) {
            cached.load(world.registryAccess().lookupOrThrow(Registries.BLOCK), previous)
            saved.getOrThrow()
        }
        if (model != null) runCatching { convert(world, selected, name, model) }
            .getOrElse { throw IllegalStateException("结构文件已保存为 $name，但转换失败：${it.message}", it) }
        state.draft = draft
        state.revision = UUID.randomUUID()
    }

    /** 按原始最小角生成模型；清除失败时恢复方块实体及库存，不产生重复掉落。 */
    private fun convert(world: ServerLevel, selected: StructureSelection, name: ResourceLocation, snapshot: StructureSnapshot) {
        val backups = BlockPos.betweenClosed(selected.origin, selected.second).map { mutable ->
            val pos = mutable.immutable()
            SelectionBlockBackup(pos, world.getBlockState(pos), world.getBlockEntity(pos)?.saveWithFullMetadata(world.registryAccess()))
        }.filter { !it.state.isAir || it.data != null }
        val model = StructureModelEntity(StructureModels.ENTITY, world)
        val center = snapshot.defaultPivot
        model.setPos(Vec3.atLowerCornerOf(selected.origin))
        model.configure(name.toString(), snapshot, StructureModelSettings(pivot = listOf(center.x, center.y, center.z)))
        check(world.addFreshEntity(model)) { "模型实体生成失败，原方块未改动" }
        val flags = Block.UPDATE_CLIENTS or Block.UPDATE_KNOWN_SHAPE or Block.UPDATE_SUPPRESS_DROPS
        val changed = mutableListOf<SelectionBlockBackup>()
        val cleared = runCatching {
            for (backup in backups) {
                changed.add(backup)
                // 先移除方块实体，避免容器的 onStateReplaced 把已存入快照的物品再次散落。
                world.removeBlockEntity(backup.pos)
                check(world.setBlock(backup.pos, Blocks.AIR.defaultBlockState(), flags)) { "无法清除 ${backup.pos.toShortString()}" }
            }
        }
        if (cleared.isFailure) {
            model.discard()
            var restoreFailure: Throwable? = null
            for (backup in changed) {
                runCatching {
                    world.setBlock(backup.pos, backup.state, flags)
                    check(world.getBlockState(backup.pos) == backup.state) { "方块状态恢复失败" }
                    backup.data?.let {
                        val restored = checkNotNull(BlockEntity.loadStatic(backup.pos, backup.state, it, world.registryAccess())) {
                            "方块实体恢复失败"
                        }
                        world.setBlockEntity(restored)
                    }
                    world.getBlockEntity(backup.pos)?.setChanged()
                }.onFailure {
                    restoreFailure = it
                    CooParticlesConstants.logger.error("结构转换回滚失败：{}；可从模板 {} 恢复", backup.pos, name, it)
                }
            }
            check(restoreFailure == null) { "部分方块无法恢复，请使用已保存模板 $name 恢复选区" }
            cleared.getOrThrow()
        }
        // 整批清空后补回原版的逻辑与形状通知；仅 neighborUpdate 不会刷新边界栅栏的连接状态。
        for (backup in backups) runCatching {
            world.updateNeighborsAt(backup.pos, backup.state.block)
            backup.state.updateIndirectNeighbourShapes(world, backup.pos, Block.UPDATE_CLIENTS)
            val current = world.getBlockState(backup.pos)
            current.updateNeighbourShapes(world, backup.pos, Block.UPDATE_CLIENTS)
            current.updateIndirectNeighbourShapes(world, backup.pos, Block.UPDATE_CLIENTS)
        }.onFailure { CooParticlesConstants.logger.error("结构转换已完成，但邻居更新失败：{}", backup.pos, it) }
    }

    /** 将独立的小型状态标签发送给当前玩家。 */
    private fun send(player: ServerPlayer, data: CompoundTag) {
        CooServerPacketManager.sendTo(player, StructureSelectionPayload(data))
    }
}
