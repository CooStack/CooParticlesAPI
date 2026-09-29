package cn.coostack.cooparticlesapi.entities.structure

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.entities.structure.editor.StructureEditor
import cn.coostack.cooparticlesapi.entities.CooModEntityTypes
import cn.coostack.cooparticlesapi.entities.collision.EntityTransform
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.platform.registry.CommonDeferredRegistry
import cn.coostack.cooparticlesapi.network.packet.api.CooServerPacketManager
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.event.events.packet.CooPacketReceiveEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerUseBlockEvent
import cn.coostack.cooparticlesapi.event.events.entity.player.PlayerUseEntityEvent
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.StringTag
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Registry
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionHand
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

/** 结构模型的注册与服务端交互入口，由公共初始化调用 init；所有修改在服务器线程执行。 */
@EventListener
object StructureModels {
    /** 方块、物品与实体注册共用的模型 ID。 */
    const val MODEL_ID = "structure_model"
    /** 右键后选择结构的占位方块。 */
    private val blockEntry = CooParticlesServices.COO_REGISTRY.register(CommonDeferredRegistry(
        BuiltInRegistries.BLOCK, ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, MODEL_ID)
    ) { ModelBlock(BlockBehaviour.Properties.of().strength(1F).noOcclusion()) })
    /** 模型占位方块，在平台注册完成后读取。 */
    val BLOCK: Block get() = blockEntry.get()
    /** 模型方块的持久化存储类型，与方块共享注册标识。 */
    private val blockEntityEntry = CooParticlesServices.COO_REGISTRY.register(CommonDeferredRegistry(
        BuiltInRegistries.BLOCK_ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, MODEL_ID)
    ) { BlockEntityType.Builder.of(::ModelBlockEntity, BLOCK).build(null) })
    /** 模型方块存储类型，由两个加载器共用。 */
    @Suppress("UNCHECKED_CAST")
    val BLOCK_ENTITY: BlockEntityType<ModelBlockEntity> get() = blockEntityEntry.get() as BlockEntityType<ModelBlockEntity>
    /** 占位方块对应的物品。 */
    private val blockItemEntry = CooParticlesServices.COO_REGISTRY.register(CommonDeferredRegistry(
        BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, MODEL_ID)
    ) { BlockItem(BLOCK, Item.Properties()) })
    /** 模型方块物品的平台注册实例。 */
    val BLOCK_ITEM: Item get() = blockItemEntry.get()
    /** 唯一允许打开实体属性与掉落库存的工具。 */
    private val editorEntry = CooParticlesServices.COO_REGISTRY.register(CommonDeferredRegistry(
        BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "structure_model_editor")
    ) { Item(Item.Properties().stacksTo(1)) })
    /** 编辑已有模型的工具。 */
    val EDITOR: Item get() = editorEntry.get()
    /** 收起后的结构载体，不提供空白创造物品，名称由编辑器设置。 */
    private val heldEntry = CooParticlesServices.COO_REGISTRY.register(CommonDeferredRegistry(
        BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "held_structure_model")
    ) { HeldStructureModelItem(Item.Properties().stacksTo(1)) })
    /** 带完整快照的模型物品，不注册空白创造栏物品。 */
    val HELD_MODEL: HeldStructureModelItem get() = heldEntry.get() as HeldStructureModelItem
    /** 单一结构模型实体类型；几何来自每个实例的快照。 */
    val ENTITY: EntityType<StructureModelEntity> get() = CooModEntityTypes.STRUCTURE_MODEL.get()

    /** 注册属性与双向协议，仅从公共初始化入口调用一次。 */
    fun init() {
        StructureEditor.init()
    }

    /** 在服务器线程接收编辑意图，客户端不能上传快照或替换玩家身份。 */
    @EventHandler
    fun receive(event: CooPacketReceiveEvent) {
        val packet = event.packet as? ModelEditPayload ?: return
        if (event.side == CooPacketReceiveEvent.Side.SERVER) event.sender?.let { applyEdit(it, packet.data) }
    }

    /** 接管模型物品的方块交互，防止容器优先吞掉模型转移操作。 */
    @EventHandler
    fun useBlock(event: PlayerUseBlockEvent) {
        if (event.result != InteractionResult.PASS) return
        val player = event.player
        val world = player.level()
        val hand = event.hand
        val hit = event.hit
        // 仅接管模型物品，防止箱子等原版交互吞掉右键，或放置分支回落到进食而重复发事件。
        event.result = run {
            val stack = player.getItemInHand(hand)
            if (world.getBlockState(hit.blockPos).`is`(BLOCK) &&
                (stack.`is`(HELD_MODEL) || ModelItemBehavior.isSkinned(stack))) {
                if (player is ServerPlayer) transferToBlock(player, hit.blockPos, hand)
                else InteractionResult.SUCCESS
            } else if (stack.`is`(HELD_MODEL) || (player.isShiftKeyDown && ModelItemBehavior.canPlace(stack, player.level()))) {
                HELD_MODEL.useOn(UseOnContext(player, hand, hit))
            } else InteractionResult.PASS
        }
    }

    /** 模型收起优先于原生实体交互，只影响携带模型的物品。 */
    @EventHandler
    fun useEntity(event: PlayerUseEntityEvent) {
        if (event.result != InteractionResult.PASS) return
        val player = event.player
        val world = player.level()
        val hand = event.hand
        val target = event.target
        event.result = run {
            if (target is StructureModelEntity && player.isShiftKeyDown && target.settings.pickable) {
                HELD_MODEL.capture(target, player, hand)
            } else if (player.getItemInHand(hand).`is`(HELD_MODEL)) {
                HELD_MODEL.use(world, player, hand).result
            } else InteractionResult.PASS
        }
    }

    /** 在服务器线程加载模板的独立静态快照；例如 `load(level, id)`，模板不存在时抛出异常。 */
    fun load(level: ServerLevel, id: ResourceLocation): StructureSnapshot =
        StructureSnapshot.fromTemplate(level.registryAccess(), level.structureManager.get(id)
            .orElseThrow { IllegalArgumentException("找不到结构模板：$id") })

    /**
     * 在服务器线程生成结构实体，失败不留下实体；例如 `spawn(level, origin, snapshot)`。
     * @param level 目标世界
     * @param origin 世界空间结构原点
     * @param snapshot 已验证快照
     * @param settings 模型设置，默认采用底部中心
     * @param name 原结构追溯名称
     * @return 已加入世界的实体
     */
    fun spawn(level: ServerLevel, origin: Vec3, snapshot: StructureSnapshot,
              settings: StructureModelSettings = StructureModelSettings(EntityTransform(pivot = snapshot.defaultPivot)),
              name: String = ""): StructureModelEntity {
        require(listOf(origin.x, origin.y, origin.z).all { it.isFinite() })
        return StructureModelEntity(ENTITY, level).also {
            it.setPos(origin)
            it.configure(name, snapshot, settings)
            check(level.addFreshEntity(it)) { "结构实体生成失败" }
        }
    }

    /**
     * 打开初次选择或实体编辑窗口，目录由服务器的原版模板管理器提供。
     * 示例：`openEditor(player, model.blockPos, model)`。
     * @param player 接收窗口的服务端玩家
     * @param pos 初次转换的模型方块坐标；实体编辑时仅供界面保存定位
     * @param model 已有模型；空值表示首次选择结构，不授予后续编辑权限
     */
    fun openEditor(player: ServerPlayer, pos: BlockPos, model: StructureModelEntity?) {
        if (model != null && !model.canEdit(player)) return
        val stored = if (model == null) blockStorage(player, pos) else null
        val data = CompoundTag()
        data.putLong("Pos", pos.asLong())
        if (model != null) data.putUUID("Entity", model.uuid)
        data.putString("Name", model?.structureName ?: stored?.structureName ?: "")
        data.put("Settings", (model?.settings ?: stored?.settings ?: StructureModelSettings()).toNbt())
        data.putBoolean("SavedBlock", stored?.hasSelectedStructure == true)
        stored?.draft()?.let { data.put("EditorDraft", it) }
        val names = ListTag()
        player.server.structureManager.listTemplates().use { stream ->
            stream.limit(4096).sorted().forEach { names.add(StringTag.valueOf(it.toString())) }
        }
        data.put("Names", names)
        CooServerPacketManager.sendTo(player, ModelEditorPayload(data))
    }

    /** 兼容升级前没有方块实体的空模型方块，仅在服务端为仍存在的方块补建存储。 */
    private fun blockStorage(player: ServerPlayer, pos: BlockPos): ModelBlockEntity? {
        val world = player.serverLevel()
        if (!world.hasChunkAt(pos) || !world.getBlockState(pos).`is`(BLOCK)) return null
        return world.getBlockEntity(pos) as? ModelBlockEntity ?: ModelBlockEntity(pos, world.getBlockState(pos)).also {
            world.setBlockEntity(it)
            it.setChanged()
        }
    }

    /** 原子转移模型载荷；目标、权限和数据全部有效后才清空手持物品，包括创造模式。 */
    private fun transferToBlock(player: ServerPlayer, pos: BlockPos, hand: InteractionHand): InteractionResult {
        val stack = player.getItemInHand(hand)
        val result = runCatching {
            require(player.isAlive && !player.isSpectator && player.abilities.mayBuild &&
                player.canInteractWithBlock(pos, 1.0) && player.serverLevel().mayInteract(player, pos)) { "当前无法转移模型" }
            require(stack.count == 1 && (stack.`is`(HELD_MODEL) || ModelItemBehavior.isSkinned(stack))) { "请手持单件模型物品" }
            val stored = requireNotNull(blockStorage(player, pos)) { "模型方块已不存在" }
            val data = ModelItemBehavior.currentForPlacement(stack, player.serverLevel())
            stored.receive(data, player.registryAccess())
            stack.shrink(1)
            player.setItemInHand(hand, ItemStack.EMPTY)
        }
        return result.fold({
            openEditor(player, pos, null)
            InteractionResult.CONSUME
        }, {
            player.displayClientMessage(Component.literal(it.message ?: "模型转移失败"), true)
            InteractionResult.FAIL
        })
    }

    /** 验证目标、工具、距离及数值，完成加载后才替换方块，失败保持原状态。 */
    private fun applyEdit(player: ServerPlayer, data: CompoundTag) {
        if (!player.isAlive || player.isSpectator || !player.abilities.mayBuild) {
            rejectEdit(player, data, "当前无法编辑模型")
            return
        }
        val world = player.serverLevel()
        val pos = BlockPos.of(data.getLong("Pos"))
        val existing = if (data.hasUUID("Entity")) world.getEntity(data.getUUID("Entity")) as? StructureModelEntity else null
        if (data.contains("Entity") && existing == null) { rejectEdit(player, data, "模型已不存在"); return }
        if (existing != null) {
            if (!existing.canEdit(player)) { rejectEdit(player, data, "请手持结构模型编辑器并靠近模型"); return }
        } else if (!world.hasChunkAt(pos) || !world.getBlockState(pos).`is`(BLOCK) ||
            !player.canInteractWithBlock(pos, 1.0) || !world.mayInteract(player, pos)) {
            rejectEdit(player, data, "模型方块已改变、距离过远或此处禁止修改")
            return
        }
        val stored = if (existing == null) blockStorage(player, pos) else null
        if (data.getString("Action") == "import") {
            if (existing != null) { reply(player, false, "只能向空模型方块转移"); return }
            val hand = InteractionHand.entries.firstOrNull {
                val stack = player.getItemInHand(it)
                stack.`is`(HELD_MODEL) || ModelItemBehavior.isSkinned(stack)
            }
            if (hand == null) reply(player, false, "请先手持模型物品")
            else if (transferToBlock(player, pos, hand) == InteractionResult.FAIL) reply(player, false, "转移失败，请检查模型方块是否为空")
            return
        }
        val preview = data.getString("Action") == "preview"
        if (!preview && player.cooldowns.isOnCooldown(EDITOR)) { reply(player, false, "请稍后重试"); return }
        if (!preview) player.cooldowns.addCooldown(EDITOR, 5)
        if (data.getString("Action") == "drops") {
            val inventory = existing?.drops ?: stored?.drops ?: return
            player.openMenu(SimpleMenuProvider(
                { syncId, playerInventory, _ -> ChestMenu.threeRows(syncId, playerInventory, inventory) },
                Component.literal("模型死亡掉落物（放入真实物品）")
            ))
            return
        }
        val saveBlock = data.getString("Action") == "save"
        if (data.getString("Action") != "apply" && !preview && !saveBlock) return
        if (saveBlock && existing != null) { reply(player, false, "实体不能保存为模型方块"); return }
        if (saveBlock) {
            runCatching {
                val state = data.getCompound("EditorDraft")
                require(state.sizeInBytes() <= 524288L) { "编辑草稿超过 512 KiB" }
                val name = ResourceLocation.tryParse(state.getString("Name").trim())
                val snapshot = name?.let { runCatching { resolveSnapshot(player, it, null, stored) }.getOrNull() }
                requireNotNull(stored).saveDraft(state, snapshot)
            }.onSuccess { reply(player, true, "配置草稿已保存") }
                .onFailure { reply(player, false, it.message ?: "无法保存配置草稿") }
            return
        }
        if (preview && !data.hasUUID("PreviewRequest")) return
        val options = runCatching { StructureModelSettings.fromNbt(data.getCompound("Settings")) }.getOrNull()
        if (options == null || !options.valid()) {
            rejectEdit(player, data, "参数无效；可拾取模型必须填写物品名（最多 64 字符）")
            return
        }
        val inputName = data.getString("Name").trim()
        val name = ResourceLocation.tryParse(inputName.take(256))
        if (name == null) { rejectEdit(player, data, "请输入有效的结构 ID"); return }
        val result = runCatching {
            val oldSettings = existing?.settings ?: stored?.settings
            if (!preview && options.itemProperties.simulatedStack.isNotEmpty() &&
                options.itemProperties.simulatedStack != oldSettings?.itemProperties?.simulatedStack) {
                val sample = options.itemProperties.simulation(world.registryAccess())
                require((0 until player.inventory.containerSize).any {
                    ItemStack.isSameItemSameComponents(sample, player.inventory.getItem(it))
                }) { "模拟物品已不在背包中，请重新选择" }
            }
            if (!preview && options.itemProperties.components != (oldSettings?.itemProperties?.components ?: "{}")) {
                require(player.hasPermissions(2)) { "修改组件 SNBT 需要管理员权限" }
            }
            if (!preview) options.itemProperties.createStack(world.registryAccess(), options.edible)
            val snapshot = resolveSnapshot(player, name, existing, stored)
            val configured = if (existing == null && !data.getBoolean("ExplicitPivot")) {
                val center = snapshot.defaultPivot
                options.copy(pivot = listOf(center.x, center.y, center.z), pivotCompensation = listOf(0.0, 0.0, 0.0))
            } else options
            if (preview) {
                val response = CompoundTag()
                response.putUUID("PreviewRequest", data.getUUID("PreviewRequest"))
                response.putString("Name", name.toString())
                response.put("Structure", snapshot.tag.copy())
                response.put("Settings", configured.toNbt())
                CooServerPacketManager.sendTo(player, ModelEditorPayload(response))
            } else if (existing != null) {
                existing.configure(name.toString(), snapshot, configured)
            } else {
                val entity = StructureModelEntity(ENTITY, world)
                entity.setPos(snapshot.placementOrigin(configured, Vec3.atBottomCenterOf(pos)))
                entity.configure(name.toString(), snapshot, configured)
                if (stored != null) {
                    if (stored.structureName.isNotEmpty()) entity.health = stored.storedHealth.coerceAtMost(configured.health)
                    repeat(27) { entity.drops.setItem(it, stored.drops.getItem(it).copy()) }
                }
                check(world.addFreshEntity(entity)) { "模型实体生成失败" }
                stored?.drops?.clearContent()
                if (!world.removeBlock(pos, false)) {
                    if (stored != null) repeat(27) { stored.drops.setItem(it, entity.drops.getItem(it).copy()) }
                    entity.drops.clearContent()
                    entity.discard()
                    error("无法替换模型方块")
                }
            }
        }
        result.onFailure { rejectEdit(player, data, it.message ?: "无法读取结构") }
            .onSuccess { if (!preview) reply(player, true, "模型已保存") }
    }

    /** 预览、草稿快照缓存和转换共用服务端结构加载规则，客户端不能上传结构或嵌入实体。 */
    private fun resolveSnapshot(player: ServerPlayer, name: ResourceLocation,
                                existing: StructureModelEntity?, stored: ModelBlockEntity?): StructureSnapshot {
        if (existing != null && existing.structureName == name.toString()) return requireNotNull(existing.snapshot)
        stored?.snapshotFor(name.toString(), player.registryAccess())?.let { return it }
        val template = player.server.structureManager.get(name).orElse(null) ?: error("找不到已保存结构：$name")
        return StructureSnapshot.fromTemplate(player.registryAccess(), template)
    }

    /** 预览失败必须携带请求标识，不能解锁另一个保存请求或向聊天持续输出输入过程中的错误。 */
    private fun rejectEdit(player: ServerPlayer, request: CompoundTag, message: String) {
        if (request.getString("Action") != "preview") {
            reply(player, false, message)
            return
        }
        if (!request.hasUUID("PreviewRequest")) return
        val response = CompoundTag()
        response.putUUID("PreviewRequest", request.getUUID("PreviewRequest"))
        response.putString("Name", ResourceLocation.tryParse(request.getString("Name"))?.toString() ?: request.getString("Name"))
        response.putString("Message", message)
        CooServerPacketManager.sendTo(player, ModelEditorPayload(response))
    }

    /** 发送可重试的结果，避免失败后客户端一直等待或丢失输入。 */
    private fun reply(player: ServerPlayer, success: Boolean, message: String) {
        val response = CompoundTag()
        response.putBoolean("Result", success)
        response.putString("Message", message)
        CooServerPacketManager.sendTo(player, ModelEditorPayload(response))
        player.displayClientMessage(Component.literal(message), success)
    }
}
