package cn.coostack.cooparticlesapi.key

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.key.KeyActionBatch
import cn.coostack.cooparticlesapi.event.events.key.KeyActionData
import cn.coostack.cooparticlesapi.event.events.key.KeyActionEvent
import cn.coostack.cooparticlesapi.event.events.key.KeyActionType
import cn.coostack.cooparticlesapi.network.packet.client.PacketKeyActionC2S
import cn.coostack.cooparticlesapi.network.packet.server.PacketKeyBindingCountdownS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import org.lwjgl.glfw.GLFW

/**
 * keyName 默认使用 keyId
 *
 * 简单示例:
 * ```
 * val keyId = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_action")
 * KeyBindingManager.register(
 *     keyId,
 *     InputConstants.Type.KEYSYM,
 *     GLFW.GLFW_KEY_G,
 *     "key.category.cooparticlesapi"
 * )
 * ```
 */
object CooKeyBindingManager {
    private data class KeyState(
        val id: ResourceLocation,
        val mapping: KeyMapping,
        val triggerScope: CooKeyBindingTriggerScope,
        var wasDown: Boolean = false,
        var blockedUntilRelease: Boolean = false,
        var pressTick: Int = 0,
        var lastClickTick: Long = -1
    )

    private val keyStates = LinkedHashMap<ResourceLocation, KeyState>()
    private val registeredIds = HashSet<ResourceLocation>()
    private var registrar: ((KeyMapping) -> Unit)? = null
    private var tickCounter = 0L

    // 服务器调用或者客户端调用， 设置countdown (在这期间不处理这个按键功能）
    private val keyCountDowns = mutableMapOf<ResourceLocation, Int>()
    var doubleClickWindowTicks = 6

    fun setRegistrar(registerer: (KeyMapping) -> Unit) {
        registrar = registerer
        keyStates.values.forEach { registerIfPossible(it) }
    }

    fun register(
        keyId: ResourceLocation,
        keyType: InputConstants.Type,
        defaultKey: Int,
        category: String
    ): KeyMapping {
        return register(keyId, keyType, defaultKey, category, CooKeyBindingTriggerScope.BOTH)
    }

    fun register(
        keyId: ResourceLocation,
        keyType: InputConstants.Type,
        defaultKey: Int,
        category: String,
        triggerScope: CooKeyBindingTriggerScope
    ): KeyMapping {
        return register(keyId, keyType, defaultKey, category, keyType == InputConstants.Type.MOUSE, triggerScope)
    }

    fun register(
        keyId: ResourceLocation,
        keyType: InputConstants.Type,
        defaultKey: Int,
        category: String,
        listenOnly: Boolean
    ): KeyMapping {
        return register(keyId, keyType, defaultKey, category, listenOnly, CooKeyBindingTriggerScope.BOTH)
    }

    fun register(
        keyId: ResourceLocation,
        keyType: InputConstants.Type,
        defaultKey: Int,
        category: String,
        listenOnly: Boolean,
        triggerScope: CooKeyBindingTriggerScope
    ): KeyMapping {
        require(keyId !in keyStates) { "key id already registered: $keyId" }
        val mappingName = "key.${keyId.namespace}.${keyId.path}"
        val mapping = if (listenOnly) {
            CooListeningKeyMapping(mappingName, keyType, defaultKey, category)
        } else {
            KeyMapping(mappingName, keyType, defaultKey, category)
        }
        val state = KeyState(keyId, mapping, triggerScope)
        keyStates[keyId] = state
        registerIfPossible(state)
        return mapping
    }

    fun getMapping(keyId: ResourceLocation): KeyMapping? {
        return keyStates[keyId]?.mapping
    }

    fun setCountdown(key: ResourceLocation, cd: Int) {
        keyCountDowns[key] = cd
    }


    fun sendCountdown(to: ServerPlayer, key: ResourceLocation, cd: Int) {
        val packet = PacketKeyBindingCountdownS2C(key, cd)
        CooParticlesServices.SERVER_NETWORK.send(packet, to)
    }

    fun tick() {
        if (keyStates.isEmpty()) return
        tickCounter++
        val doubleInterval = doubleClickWindowTicks.coerceAtLeast(0).toLong()
        val states = keyStates.values.toList()
        val pendingActions = ArrayList<KeyActionData<ResourceLocation>>()
        val client = Minecraft.getInstance()
        val isGuiOpen = client.screen != null
        states.forEach { state ->
            val down = state.mapping.isPhysicallyDown(client)
            if (!down) {
                state.blockedUntilRelease = false
            }
            val canStart = state.triggerScope.canStart(isGuiOpen)
            val canContinue = state.triggerScope.canContinue(isGuiOpen)
            val activeDown = down && !state.blockedUntilRelease && (if (state.wasDown) canContinue else canStart)
            state.mapping.setDown(activeDown)
            if (keyCountDowns.containsKey(state.id)) {
                val current = keyCountDowns[state.id]!!
                if (current > 0) {
                    keyCountDowns[state.id] = current - 1
                    state.wasDown = false
                    state.blockedUntilRelease = down
                    state.mapping.setDown(false)
                    state.pressTick = 0
                    state.lastClickTick = tickCounter
                    return@forEach
                } else {
                    keyCountDowns.remove(state.id)
                }
            }
            if (activeDown) {
                if (!state.wasDown) {
                    state.pressTick = 0
                    pendingActions.add(
                        KeyActionData(state.id, listOf(KeyActionType.SINGLE_CLICK), 1, false)
                    )
                } else {
                    pendingActions.add(
                        KeyActionData(state.id, listOf(KeyActionType.LONG_PRESS), state.pressTick + 1, false)
                    )
                }
                state.pressTick++
            } else if (state.wasDown) {
                val capturedPressTick = state.pressTick.coerceAtLeast(1)
                pendingActions.add(
                    KeyActionData(state.id, listOf(KeyActionType.LONG_PRESS), capturedPressTick, true)
                )
                val isDouble =
                    state.lastClickTick >= 0 && tickCounter - state.lastClickTick <= doubleInterval
                val action = if (isDouble) KeyActionType.DOUBLE_CLICK else KeyActionType.SINGLE_CLICK
                pendingActions.add(
                    KeyActionData(state.id, listOf(action), capturedPressTick, true)
                )
                state.lastClickTick = tickCounter
                state.pressTick = 0
            }
            if (down && !activeDown) {
                state.blockedUntilRelease = true
            }
            state.wasDown = activeDown
        }
        if (pendingActions.isNotEmpty()) {
            sendActions(KeyActionBatch(pendingActions))
        }
    }

    private fun registerIfPossible(state: KeyState) {
        val registerer = registrar ?: return
        if (registeredIds.add(state.id)) {
            registerer(state.mapping)
        }
    }

    private fun sendActions(keyActions: KeyActionBatch<ResourceLocation>) {
        val client = Minecraft.getInstance()
        val player = client.player
        if (player == null || client.level == null) {
            return
        }
        CooEventBus.call(
            KeyActionEvent(player, keyActions, false)
        )
        CooParticlesServices.CLIENT_NETWORK.send(PacketKeyActionC2S(keyActions))
    }
}

/**
 * 读取 KeyMapping 当前绑定键的物理状态。
 *
 * Minecraft 会特殊处理 F3 等调试键，导致 KeyMapping.isDown 可能没有反映真实键盘状态；客户端组合键和
 * 普通 Coo 按键都必须通过此入口保持相同判定。鼠标和键盘重绑均受支持，无法解析时才回退到映射状态。
 */
internal fun KeyMapping.isPhysicallyDown(client: Minecraft): Boolean {
    val window = client.window.window
    for (button in 0..GLFW.GLFW_MOUSE_BUTTON_LAST) {
        if (matchesMouse(button)) {
            return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS
        }
    }
    for (keyCode in 0..GLFW.GLFW_KEY_LAST) {
        if (matches(keyCode, 0)) {
            return InputConstants.isKeyDown(window, keyCode)
        }
    }
    return isDown
}
