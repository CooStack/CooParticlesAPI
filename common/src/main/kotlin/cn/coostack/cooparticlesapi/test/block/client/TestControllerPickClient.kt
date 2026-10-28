package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S
import cn.coostack.cooparticlesapi.test.api.TestOptionParamCodec
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import net.minecraft.client.Minecraft
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

object TestControllerPickClient {
    private var request: TestControllerPickRequest? = null
    private var reopenParamPageOnNextController = false
    private var escWasDown = false
    private var useWasDown = false
    private var suppressUseUntilRelease = false

    /**
     * 开始一次世界取点请求，并显示与主控制器一致的操作提示。
     *
     * 示例：关键帧窗口传入 [onPicked] 后只回填本地输入框。
     * 禁止在关键帧编辑场景中依赖默认网络提交行为；应使用回调接收目标。
     *
     * @param screenPacket 控制器开屏快照
     * @param packet 控制器客户端草稿
     * @param kind 取点语义
     * @param precisionUnlocked 是否保留未限制的小数精度
     * @param paramOptionIndex 参数页选项索引
     * @param paramId 参数标识
     * @param paramComponentCount 参数向量分量数量
     * @param paramAbsolute 参数取点是否使用世界绝对坐标
     * @param onPicked 成功取点后的回调；参数依次为目标点、是否来自方块、玩家朝向
     * @param onCancelled 取消取点后的回调
     */
    /** 保留原有公共取点入口；高级本地回调入口仅供 BlockTest GUI 使用。 */
    fun begin(
        screenPacket: PacketOpenTestControllerScreenS2C,
        packet: PacketUpdateTestControllerC2S,
        kind: TestControllerPickKind,
        precisionUnlocked: Boolean,
        paramOptionIndex: Int = 0,
        paramId: String = "",
        paramComponentCount: Int = 3,
        paramAbsolute: Boolean = false,
    ) {
        begin(
            screenPacket,
            packet,
            kind,
            precisionUnlocked,
            paramOptionIndex,
            paramId,
            paramComponentCount,
            paramAbsolute,
            history = null,
            onPicked = null,
            onCancelled = null,
        )
    }

    internal fun begin(
        screenPacket: PacketOpenTestControllerScreenS2C,
        packet: PacketUpdateTestControllerC2S,
        kind: TestControllerPickKind,
        precisionUnlocked: Boolean,
        paramOptionIndex: Int = 0,
        paramId: String = "",
        paramComponentCount: Int = 3,
        paramAbsolute: Boolean = false,
        history: TestControllerUndoHistory<TestControllerPacketDrafts.TestControllerConfigSnapshot>? = null,
        onPicked: ((Vec3, Boolean, Vec3) -> Unit)? = null,
        onCancelled: (() -> Unit)? = null,
    ) {
        request = TestControllerPickRequest(
            screenPacket = screenPacket,
            packet = packet,
            kind = kind,
            precisionUnlocked = precisionUnlocked,
            paramOptionIndex = paramOptionIndex,
            paramId = paramId,
            paramComponentCount = paramComponentCount,
            paramAbsolute = paramAbsolute,
            history = history,
            onPicked = onPicked,
            onCancelled = onCancelled,
        )
        escWasDown = false
        useWasDown = false
        Minecraft.getInstance().player?.sendOverlayMessage(Component.literal(pickHint(kind)))
    }

    fun cancel() {
        request = null
        escWasDown = false
        useWasDown = false
        suppressUseUntilRelease = false
    }

    fun consumeOpenParamPage(): Boolean {
        return reopenParamPageOnNextController.also {
            reopenParamPageOnNextController = false
        }
    }

    @JvmStatic
    fun cancelAndReopenFromEsc(): Boolean {
        val current = request ?: return false
        val client = Minecraft.getInstance()
        cancelAndReopen(client, current)
        return true
    }

    /**
     * 在 Minecraft 分发方块使用逻辑前完成当前拾取。
     *
     * @return 本次 Shift + 右键是否已由拾取模式消费
     */
    @JvmStatic
    fun consumePickUseInput(): Boolean {
        if (suppressUseUntilRelease) {
            return true
        }
        val current = request ?: return false
        if (!Minecraft.getInstance().hasShiftDown()) {
            return false
        }
        val client = Minecraft.getInstance()
        if (client.gui.screen() != null) {
            return false
        }
        val level = client.level ?: return false
        val player = client.player ?: return false
        if (level.dimension().identifier().toString() != current.packet.dimension) {
            cancel()
            return false
        }
        val target = currentTarget(client, current) ?: return false
        completePick(current, target, player.lookAngle)
        return true
    }

    @JvmStatic
    fun releaseUseSuppression() {
        suppressUseUntilRelease = false
    }

    fun tick() {
        val client = Minecraft.getInstance()
        if (suppressUseUntilRelease && !isUseDown(client)) {
            suppressUseUntilRelease = false
        }
        val current = request ?: return
        val level = client.level ?: return cancel()
        val player = client.player ?: return cancel()
        if (level.dimension().identifier().toString() != current.packet.dimension) {
            cancel()
            return
        }
        if (client.gui.screen() != null) {
            if (client.gui.screen() is PauseScreen) {
                cancelAndReopen(client, current)
            } else {
                cancel()
            }
            return
        }
        val escDown = InputConstants.isKeyDown(InputConstants.KEY_ESCAPE)
        if (escDown && !escWasDown) {
            cancelAndReopen(client, current)
            return
        }
        escWasDown = escDown
        val useDown = isUseDown(client)
        val useClicked = client.options.keyUse.consumeClick() || (useDown && !useWasDown)
        useWasDown = useDown
        if (!client.hasShiftDown() || !useClicked) {
            return
        }
        val target = currentTarget(client, current) ?: return
        completePick(current, target, player.lookAngle)
    }

    fun render(event: ClientWorldRenderEvent) {
        val current = request ?: return
        val client = Minecraft.getInstance()
        if (event.world.dimension().identifier().toString() != current.packet.dimension) {
            return
        }
        val target = currentTarget(client, current) ?: return
        val camera = event.camera.pos
        MinecraftRendererUtil.submitLineBox(
            event.collector, event.poseStack, target.box, camera, 0.2F, 1F, 0.2F, 1F
        )
        val origin = origin(current.packet)
        if (origin.distanceTo(target.point) <= 96.0) {
            MinecraftRendererUtil.submitLine(
                event.collector, event.poseStack, origin, target.point, camera,
                60F / 255F, 1F, 60F / 255F, 1F,
            )
        }
    }

    private fun applyTarget(current: TestControllerPickRequest, target: TestControllerPickTarget, lookAngle: Vec3) {
        val packet = current.packet
        when (current.kind) {
            TestControllerPickKind.OFFSET -> {
                val offset = target.point.subtract(origin(packet))
                packet.offsetX = current.format(offset.x)
                packet.offsetY = current.format(offset.y)
                packet.offsetZ = current.format(offset.z)
            }
            TestControllerPickKind.FORWARD -> {
                val forward = if (target.fromBlock) {
                    target.point.subtract(origin(packet)).normal()
                } else {
                    lookAngle.normal()
                }
                packet.forwardX = current.format(forward.x)
                packet.forwardY = current.format(forward.y)
                packet.forwardZ = current.format(forward.z)
            }
            TestControllerPickKind.PARAM_POSITION -> {
                if (current.paramId.isBlank()) return
                val picked = if (current.paramAbsolute) {
                    target.point
                } else {
                    target.point.subtract(origin(packet))
                }
                val mode = if (current.paramAbsolute) "absolute" else "relative"
                val values = LinkedHashMap(TestOptionParamCodec.decodeOptionValues(packet.optionParamValues))
                values[current.paramId] = "$mode:${formatPickedVector(current, picked)}"
                packet.optionParamIndex = current.paramOptionIndex.coerceAtLeast(0)
                packet.optionParamValues = TestOptionParamCodec.encodeOptionValues(values)
            }
        }
    }

    private fun completePick(current: TestControllerPickRequest, target: TestControllerPickTarget, lookAngle: Vec3) {
        val callback = current.onPicked
        if (callback != null) {
            cancel()
            suppressUseUntilRelease = true
            callback(target.point, target.fromBlock, lookAngle)
            return
        }
        applyTarget(current, target, lookAngle)
        current.history?.record(TestControllerPacketDrafts.snapshotFrom(current.screenPacket, current.packet))
        reopenParamPageOnNextController = current.kind == TestControllerPickKind.PARAM_POSITION
        current.packet.reopen = true
        CooClientPacketManager.sendTo(current.packet)
        cancel()
        suppressUseUntilRelease = true
    }

    private fun isUseDown(client: Minecraft): Boolean {
        return client.mouseHandler.isRightPressed || client.options.keyUse.isDown
    }

    private fun currentTarget(client: Minecraft, current: TestControllerPickRequest): TestControllerPickTarget? {
        val player = client.player ?: return null
        val hit = client.hitResult
        if (hit is BlockHitResult && hit.type == HitResult.Type.BLOCK) {
            val point = Vec3.atCenterOf(hit.blockPos)
            return TestControllerPickTarget(
                point = point,
                box = AABB(hit.blockPos).inflate(0.02),
                fromBlock = true
            )
        }
        val point = when (current.kind) {
            TestControllerPickKind.OFFSET -> player.position()
            TestControllerPickKind.FORWARD -> origin(current.packet).add(player.lookAngle.normal().scale(4.0))
            TestControllerPickKind.PARAM_POSITION -> player.position()
        }
        return TestControllerPickTarget(point, AABB.ofSize(point, 0.35, 0.35, 0.35), false)
    }

    private fun origin(packet: PacketUpdateTestControllerC2S): Vec3 {
        return Vec3.atCenterOf(packet.blockPos)
    }

    private fun cancelAndReopen(client: Minecraft, current: TestControllerPickRequest) {
        val onCancelled = current.onCancelled
        val packet = reopenPacket(current)
        cancel()
        client.gui.setScreen(null)
        client.player?.sendOverlayMessage(Component.literal("已取消拾取"))
        if (onCancelled != null) {
            onCancelled()
            return
        }
        client.setScreenAndShow(TestControllerScreen(packet, current.kind == TestControllerPickKind.PARAM_POSITION))
    }

    private fun reopenPacket(current: TestControllerPickRequest): PacketOpenTestControllerScreenS2C {
        return TestControllerPacketDrafts.reopenPacket(current.screenPacket, current.packet)
    }

    private fun Vec3.normal(): Vec3 {
        val length = sqrt(x * x + y * y + z * z)
        return if (length <= 1.0E-7) Vec3(0.0, 0.0, 1.0) else scale(1.0 / length)
    }

    private fun formatPickedVector(current: TestControllerPickRequest, value: Vec3): String {
        return if (current.paramComponentCount == 2) {
            "${current.format(value.x)},${current.format(value.z)}"
        } else {
            "${current.format(value.x)},${current.format(value.y)},${current.format(value.z)}"
        }
    }

    private fun pickHint(kind: TestControllerPickKind): String {
        return when (kind) {
            TestControllerPickKind.PARAM_POSITION -> "参数取点: Shift+右键拾取, Esc取消"
            TestControllerPickKind.OFFSET -> "偏移取点: Shift+右键拾取, Esc取消"
            TestControllerPickKind.FORWARD -> "方向取点: Shift+右键拾取, Esc取消"
        }
    }

}
