package cn.coostack.cooparticlesapi.entities.structure.editor.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.event.events.packet.CooPacketReceiveEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientPostTickEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientInteractionInputEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientBlockOutlineEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import cn.coostack.cooparticlesapi.key.CooKeyBindingManager
import cn.coostack.cooparticlesapi.key.CooKeyBindingTriggerScope
import cn.coostack.cooparticlesapi.key.isPhysicallyDown
import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import net.minecraft.resources.ResourceLocation
import cn.coostack.cooparticlesapi.entities.structure.editor.StructureEditor
import cn.coostack.cooparticlesapi.entities.structure.editor.StructureSelection
import cn.coostack.cooparticlesapi.entities.structure.editor.StructureSelectionPayload
import net.minecraft.world.level.block.Blocks
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.LevelRenderer
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW

/** 客户端选点输入与世界线框入口；逻辑角点完全由服务器回执驱动，常驻框不依赖是否继续手持工具。 */
@EventListener(dist = DistType.CLIENT)
object StructureEditorClient {
    /** 工具已有平滑目标框，避免再叠加原版黑色选框。 */
    @EventHandler
    fun outline(event: ClientBlockOutlineEvent) {
        if (Minecraft.getInstance().player?.let(StructureEditor::isHolding) == true) event.isCancelled = true
    }

    /** 输入事件只发操作意图；长按挖掘期间只拦截，不重复选点。 */
    @EventHandler
    fun input(event: ClientInteractionInputEvent) {
        if (event.isCancelled) return
        event.isCancelled = when (event.action) {
            ClientInteractionInputEvent.Action.ATTACK -> click(true)
            ClientInteractionInputEvent.Action.USE -> click(false)
            ClientInteractionInputEvent.Action.CONTINUE_ATTACK -> {
                val client = Minecraft.getInstance()
                val held = client.player?.let(StructureEditor::isHolding) == true
                if (held) client.gameMode?.stopDestroyBlock()
                held
            }
        }
    }
    /** 可改绑的保存界面入口，与坐骑 G 键通过物理状态和持有工具条件隔离。 */
    private lateinit var openKey: KeyMapping

    /**
     * 由加载器的按键注册阶段调用，不随 CooEvent 监听器扫描延迟注册。
     * Fabric 在客户端初始化入口调用，NeoForge 在 RegisterKeyMappingsEvent 中调用。
     * 重复调用不重复注册；不得从专用服务器调用。
     */
    fun registerKeys() {
        if (::openKey.isInitialized) return
        openKey = CooKeyBindingManager.register(
            ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "structure_editor"),
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.cooparticlesapi", CooKeyBindingTriggerScope.BOTH)
    }
    /** 仅对应当前连接世界的服务端状态。 */
    private var state = CompoundTag()
    /** 上次更新的世界对象，维度切换与重进世界均清空旧动画。 */
    private var world: ClientLevel? = null
    /** G 键边沿状态，菜单中也更新，防止关闭菜单后补触发。 */
    private var openHeld = false
    /** 右键保持状态，避免原版持续使用每四 tick 重复改点。 */
    private var rightHeld = false
    /** 范围线框的显示动画。 */
    private val rangeAnimation = SmoothSelectionBox()
    /** 第一角点的显示动画。 */
    private val firstAnimation = SmoothSelectionBox()
    /** 第二角点的显示动画。 */
    private val secondAnimation = SmoothSelectionBox()
    /** 准星目标的显示动画，未落点前也连续移动。 */
    private val hoverAnimation = SmoothSelectionBox()
    /** 单调时钟，仅用于计算渲染帧间隔。 */
    private var lastFrame = 0L
    /** 不可见方块标记缓存，避免每帧遍历选区；键为方块坐标，值为是否属于结构空位或屏障。 */
    private var invisible = emptyMap<BlockPos, Boolean>()
    /** 最近构建不可见方块标记的选区。 */
    private var invisibleSelection: StructureSelection? = null
    /** 标记刷新计时，单位为客户端 tick。 */
    private var invisibleTicks = 0

    /** 注册客户端协议、G 键与原版线段层渲染，不直接改动 OpenGL 或 Iris 管线。 */
    @EventHandler
    fun receive(event: CooPacketReceiveEvent) {
            if (event.side != CooPacketReceiveEvent.Side.CLIENT) return
            val packet = event.packet as? StructureSelectionPayload ?: return
            val client = Minecraft.getInstance()
            if (world !== client.level) reset(client.level)
            val data = packet.data
            when (data.getString("Action")) {
                "state", "open" -> {
                    if (data.getString("Dimension") != client.level?.dimension()?.location().toString()) return
                    state = data.copy()
                    if (data.getString("Action") == "open" && client.screen == null &&
                        client.player?.let(StructureEditor::isHolding) == true && selection() != null) {
                        client.setScreen(StructureSaveScreen(data.copy()))
                    }
                }
                "result" -> (client.screen as? StructureSaveScreen)?.result(data)
            }
    }

    /** 客户端 tick 驱动输入边沿与不可见方块缓存。 */
    @EventHandler
    fun tick(event: ClientPostTickEvent) {
            val client = Minecraft.getInstance()
            if (world !== client.level) reset(client.level)
            val down = openKey.isPhysicallyDown(client)
            val pressed = down && !openHeld
            openHeld = down
            while (openKey.consumeClick()) { /* 丢弃单值映射队列，支持同键的场景化绑定。 */ }
            if (!client.options.keyUse.isPhysicallyDown(client)) rightHeld = false
            if (client.screen == null && client.isWindowActive &&
                client.player?.let { it.isAlive && !it.isSpectator && StructureEditor.isHolding(it) } == true &&
                pressed && selection() != null) send("open")
            refreshInvisible(client)
    }

    /**
     * 在客户端攻击入口拦截左右键，覆盖空气、方块、实体且不产生原版破坏或交互。
     * 示例：`StructureEditorClient.click(false)` 处理一次右键使用入口。
     * @param first 是否为左键；右键且玩家潜行时发送清空
     * @return 是否已接管该输入，包括冷却和不可操作时的拦截
     */
    @JvmStatic
    fun click(first: Boolean): Boolean {
        val client = Minecraft.getInstance()
        val player = client.player ?: return false
        if (!StructureEditor.isHolding(player)) return false
        if (!player.isAlive || player.isSpectator || client.screen != null || !client.isWindowActive) return true
        if (!first && rightHeld) return true
        if (!first) rightHeld = true
        send(if (first) "first" else if (player.isShiftKeyDown) "clear" else "second")
        player.swing(if (player.mainHandItem.`is`(StructureEditor.ITEM)) InteractionHand.MAIN_HAND else InteractionHand.OFF_HAND)
        return true
    }

    /** 读取当前服务器确认的两点，缺少任意一点时不允许打开窗口。 */
    private fun selection(): StructureSelection? =
        if (state.contains("First") && state.contains("Second")) StructureSelection(
            BlockPos.of(state.getLong("First")), BlockPos.of(state.getLong("Second"))
        ) else null

    /** 只发送动作，不上传客户端射线坐标，防止远距离伪造选区。 */
    private fun send(action: String) {
        if (Minecraft.getInstance().connection != null) {
            CooClientPacketManager.sendTo(StructureSelectionPayload(CompoundTag().apply {
                putString("Action", action)
                if (action == "first" || action == "second") Minecraft.getInstance().player?.let {
                    putFloat("Yaw", it.yRot)
                    putFloat("Pitch", it.xRot)
                }
            }))
        }
    }

    /** 清空跨世界状态，不影响已经保存的结构模板。 */
    private fun reset(nextWorld: ClientLevel?) {
        val client = Minecraft.getInstance()
        if (client.screen is StructureSaveScreen) client.setScreen(null)
        world = nextWorld
        state = CompoundTag()
        rightHeld = false
        invisible = emptyMap()
        invisibleSelection = null
        lastFrame = 0L
        listOf(rangeAnimation, firstAnimation, secondAnimation, hoverAnimation).forEach { it.advance(null, 0.0) }
    }

    /** 从窗口临时输入或服务器草稿取得准确范围；非法输入不传入渲染数学。 */
    private fun visibleSelection(client: Minecraft): StructureSelection? {
        val screen = client.screen as? StructureSaveScreen
        if (screen != null) return screen.previewSelection()
        val selected = selection() ?: return null
        val draft = state.getCompound("Draft")
        if (!draft.contains("SizeX")) return selected
        return runCatching { selected.adjusted(
            BlockPos(draft.getInt("OffsetX"), draft.getInt("OffsetY"), draft.getInt("OffsetZ")),
            BlockPos(draft.getInt("SizeX"), draft.getInt("SizeY"), draft.getInt("SizeZ"))
        ) }.getOrNull()
    }

    /** 读取当前窗口或持久草稿中的可见性选项。 */
    private fun showBox(client: Minecraft): Boolean =
        (client.screen as? StructureSaveScreen)?.showBox
            ?: state.getCompound("Draft").let { !it.contains("ShowBox") || it.getBoolean("ShowBox") }

    /** 按需缓存不可见标记；单次最多 4096 个，防止大型空气选区制造几十万条线。 */
    private fun refreshInvisible(client: Minecraft) {
        val level = client.level ?: return
        val enabled = (client.screen as? StructureSaveScreen)?.showAir ?: state.getCompound("Draft").getBoolean("ShowAir")
        val selected = if (enabled && showBox(client)) visibleSelection(client)?.takeIf { it.valid() } else null
        if (selected == null) { invisible = emptyMap(); invisibleSelection = null; return }
        if (selected == invisibleSelection && ++invisibleTicks < 10) return
        invisibleTicks = 0
        invisibleSelection = selected
        val markers = linkedMapOf<BlockPos, Boolean>()
        for (pos in BlockPos.betweenClosed(selected.origin, selected.origin.offset(selected.size).offset(-1, -1, -1))) {
            if (!level.hasChunkAt(pos)) continue
            val block = level.getBlockState(pos)
            if (block.isAir || block.`is`(Blocks.STRUCTURE_VOID) || block.`is`(Blocks.BARRIER)) {
                markers[pos.immutable()] = !block.isAir
                if (markers.size == 4096) break
            }
        }
        invisible = markers
    }

    /** 使用原版批次的相机相对坐标绘制；插值只影响显示，窗口和保存始终读取整数角点。 */
    @EventHandler
    fun render(context: ClientWorldRenderEvent) {
        if (context.stage != ClientWorldRenderEvent.RenderStage.AFTER_ENTITY) return
        val client = Minecraft.getInstance()
        if (world !== client.level) reset(client.level)
        val player = client.player ?: return
        val matrices = context.poseStack
        val vertices = context.buffer.getBuffer(RenderType.lines())
        val now = System.nanoTime()
        val seconds = if (lastFrame == 0L) 0.0 else ((now - lastFrame) / 1.0e9).coerceIn(0.0, 0.1)
        lastFrame = now
        val camera = context.camera.position
        val held = StructureEditor.isHolding(player) && client.screen == null
        val target = if (held) {
            val tickDelta = context.delta.getGameTimeDeltaPartialTick(false)
            val hit = player.pick(StructureEditor.SELECTION_REACH, tickDelta, false)
            if (hit.type == HitResult.Type.BLOCK) (hit as BlockHitResult).blockPos
            else BlockPos.containing(player.getEyePosition(tickDelta).add(
                player.getViewVector(tickDelta).scale(StructureEditor.SELECTION_REACH)))
        } else null
        val first = if (state.contains("First")) BlockPos.of(state.getLong("First")) else null
        val second = if (state.contains("Second")) BlockPos.of(state.getLong("Second")) else null
        val visible = showBox(client)
        val bounds = if (visible) visibleSelection(client)?.box
            ?: if (first != null && target != null) StructureSelection(first, target).box else null else null
        matrices.pushPose()
        matrices.translate(-camera.x, -camera.y, -camera.z)
        rangeAnimation.advance(bounds, seconds)?.let { LevelRenderer.renderLineBox(matrices, vertices, it.inflate(0.004), 0.2F, 0.95F, 0.8F, 1F) }
        firstAnimation.advance(first?.takeIf { visible }?.let(::AABB), seconds)?.let {
            LevelRenderer.renderLineBox(matrices, vertices, it.inflate(0.007), 0.25F, 0.65F, 1F, 1F)
        }
        secondAnimation.advance(second?.takeIf { visible }?.let(::AABB), seconds)?.let {
            LevelRenderer.renderLineBox(matrices, vertices, it.inflate(0.009), 1F, 0.65F, 0.2F, 1F)
        }
        hoverAnimation.advance(target?.let(::AABB), seconds)?.let {
            LevelRenderer.renderLineBox(matrices, vertices, it.inflate(0.002), 1F, 1F, 1F, 0.7F)
        }
        if (visible) for ((pos, special) in invisible) {
            val box = AABB(Vec3.atCenterOf(pos).subtract(0.08, 0.08, 0.08), Vec3.atCenterOf(pos).add(0.08, 0.08, 0.08))
            LevelRenderer.renderLineBox(matrices, vertices, box, 1F, if (special) 0.2F else 0.75F, if (special) 0.2F else 0.9F, 0.6F)
        }
        matrices.popPose()
    }
}
