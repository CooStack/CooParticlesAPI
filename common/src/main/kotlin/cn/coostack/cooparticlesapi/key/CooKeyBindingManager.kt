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
        var wasDown: Boolean = false,
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
        require(keyId !in keyStates) { "key id already registered: $keyId" }
        val mapping = KeyMapping("key.${keyId.namespace}.${keyId.path}", keyType, defaultKey, category)
        val state = KeyState(keyId, mapping)
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
        states.forEach { state ->
            val down = isPhysicallyDown(state.mapping, client)
            syncMappingsWithSameKey(state.mapping, client, down)
            if (keyCountDowns.containsKey(state.id)) {
                val current = keyCountDowns[state.id]!!
                if (current > 0) {
                    keyCountDowns[state.id] = current - 1
                    state.wasDown = false
                    state.pressTick = 0
                    state.lastClickTick = tickCounter
                    return@forEach
                } else {
                    keyCountDowns.remove(state.id)
                }
            }
            if (down) {
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
            state.wasDown = down
        }
        if (pendingActions.isNotEmpty()) {
            sendActions(KeyActionBatch(pendingActions))
        }
    }

    private fun isPhysicallyDown(mapping: KeyMapping, client: Minecraft): Boolean {
        val window = client.window.window
        for (button in 0..GLFW.GLFW_MOUSE_BUTTON_LAST) {
            if (mapping.matchesMouse(button)) {
                return GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS
            }
        }
        for (keyCode in 0..GLFW.GLFW_KEY_LAST) {
            if (mapping.matches(keyCode, 0)) {
                return InputConstants.isKeyDown(window, keyCode)
            }
        }
        return mapping.isDown
    }

    private fun syncMappingsWithSameKey(source: KeyMapping, client: Minecraft, down: Boolean) {
        source.setDown(down)
        client.options.keyMappings.forEach { mapping ->
            if (mapping !== source && mapping.same(source)) {
                mapping.setDown(down)
            }
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
