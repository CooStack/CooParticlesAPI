package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldRenderEvent
import cn.coostack.cooparticlesapi.network.packet.api.CooClientPacketManager
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketOpenTestControllerScreenS2C
import cn.coostack.cooparticlesapi.network.packet.testblock.PacketUpdateTestControllerC2S
import cn.coostack.cooparticlesapi.test.api.TestOptionParamCodec
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.RenderType
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import kotlin.math.sqrt

object TestControllerPickClient {
    private const val MAX_LINE_DISTANCE = 96.0
    private var request: TestControllerPickRequest? = null
    private var reopenParamPageOnNextController = false
    private var escWasDown = false
    private var useWasDown = false

    fun begin(
        screenPacket: PacketOpenTestControllerScreenS2C,
        packet: PacketUpdateTestControllerC2S,
        kind: TestControllerPickKind,
        precisionUnlocked: Boolean,
        paramOptionIndex: Int = 0,
        paramId: String = "",
        paramComponentCount: Int = 3,
        paramAbsolute: Boolean = false
    ) {
        request = TestControllerPickRequest(
            screenPacket = screenPacket,
            packet = packet,
            kind = kind,
            precisionUnlocked = precisionUnlocked,
            paramOptionIndex = paramOptionIndex,
            paramId = paramId,
            paramComponentCount = paramComponentCount,
            paramAbsolute = paramAbsolute
        )
        escWasDown = false
        useWasDown = false
        Minecraft.getInstance().player?.displayClientMessage(Component.literal(pickHint(kind)), true)
    }

    fun cancel() {
        request = null
        escWasDown = false
        useWasDown = false
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

    fun tick() {
        val current = request ?: return
        val client = Minecraft.getInstance()
        val level = client.level ?: return cancel()
        val player = client.player ?: return cancel()
        if (level.dimension().location().toString() != current.packet.dimension) {
            cancel()
            return
        }
        if (client.screen != null) {
            if (client.screen is PauseScreen) {
                cancelAndReopen(client, current)
            } else {
                cancel()
            }
            return
        }
        val escDown = GLFW.glfwGetKey(client.window.window, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS
        if (escDown && !escWasDown) {
            cancelAndReopen(client, current)
            return
        }
        escWasDown = escDown
        val useDown = GLFW.glfwGetMouseButton(client.window.window, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS ||
                client.options.keyUse.isDown
        val useClicked = client.options.keyUse.consumeClick() || (useDown && !useWasDown)
        useWasDown = useDown
        if (!Screen.hasShiftDown() || !useClicked) {
            return
        }
        val target = currentTarget(client, current) ?: return
        applyTarget(current, target, player.lookAngle)
        reopenParamPageOnNextController = current.kind == TestControllerPickKind.PARAM_POSITION
        current.packet.reopen = true
        CooClientPacketManager.sendTo(current.packet)
        cancel()
    }

    fun render(event: ClientWorldRenderEvent) {
        val current = request ?: return
        if (event.stage != ClientWorldRenderEvent.RenderStage.AFTER_ENTITY) {
            return
        }
        val client = Minecraft.getInstance()
        if (event.world.dimension().location().toString() != current.packet.dimension) {
            return
        }
        val target = currentTarget(client, current) ?: return
        val camera = event.camera.position
        val consumer = event.buffer.getBuffer(RenderType.lines())
        val box = target.box.move(-camera.x, -camera.y, -camera.z)
        LevelRenderer.renderLineBox(event.poseStack, consumer, box, 0.2f, 1.0f, 0.2f, 1.0f)
        val origin = origin(current.packet)
        if (origin.distanceTo(target.point) <= MAX_LINE_DISTANCE) {
            val start = origin.subtract(camera)
            val end = target.point.subtract(camera)
            val normal = end.subtract(start).normal()
            val pose = event.poseStack.last().pose()
            consumer.addVertex(pose, start.x.toFloat(), start.y.toFloat(), start.z.toFloat())
                .setColor(60, 255, 60, 255)
                .setNormal(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
            consumer.addVertex(pose, end.x.toFloat(), end.y.toFloat(), end.z.toFloat())
                .setColor(60, 255, 60, 255)
                .setNormal(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
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
        val packet = reopenPacket(current)
        cancel()
        client.setScreen(null)
        client.player?.displayClientMessage(Component.literal("已取消拾取"), true)
        client.setScreen(TestControllerScreen(packet, current.kind == TestControllerPickKind.PARAM_POSITION))
    }

    private fun reopenPacket(current: TestControllerPickRequest): PacketOpenTestControllerScreenS2C {
        val source = current.screenPacket
        val update = current.packet
        return PacketOpenTestControllerScreenS2C().also {
            it.blockPos = source.blockPos
            it.boxDepth = update.boxDepth
            it.boxHeight = update.boxHeight
            it.boxWidth = update.boxWidth
            it.currentIndex = source.currentIndex
            it.dimension = source.dimension
            it.forwardX = update.forwardX
            it.forwardY = update.forwardY
            it.forwardZ = update.forwardZ
            it.groupId = update.groupId
            it.mode = update.mode
            it.offsetX = update.offsetX
            it.offsetY = update.offsetY
            it.offsetZ = update.offsetZ
            it.optionCount = source.optionCount
            it.optionIds = ArrayList(source.optionIds)
            it.optionParamSpecs = ArrayList(source.optionParamSpecs)
            it.optionParamValues = ArrayList(source.optionParamValues).also { values ->
                while (values.size <= update.optionParamIndex) {
                    values.add("")
                }
                values[update.optionParamIndex] = update.optionParamValues
            }
            it.registeredIds = ArrayList(source.registeredIds)
            it.registeredOptionIds = ArrayList(source.registeredOptionIds)
            it.registeredOptionParamSpecs = ArrayList(source.registeredOptionParamSpecs)
            it.repeatDelayTicks = update.repeatDelayTicks
            it.repeatIndex = update.repeatIndex
            it.running = source.running
            it.selectedIndex = update.selectedIndex
            it.status = source.status
        }
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
