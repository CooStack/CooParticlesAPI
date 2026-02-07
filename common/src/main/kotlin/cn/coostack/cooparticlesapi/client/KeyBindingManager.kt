package cn.coostack.cooparticlesapi.client

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.key.KeyActionEvent
import cn.coostack.cooparticlesapi.event.events.key.KeyActionType
import cn.coostack.cooparticlesapi.network.packet.PacketKeyActionC2S
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

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
object KeyBindingManager {
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
        val mapping = KeyMapping(keyId.toString(), keyType, defaultKey, category)
        val state = KeyState(keyId, mapping)
        keyStates[keyId] = state
        registerIfPossible(state)
        return mapping
    }

    fun getMapping(keyId: ResourceLocation): KeyMapping? {
        return keyStates[keyId]?.mapping
    }

    fun tick() {
        if (keyStates.isEmpty()) return
        tickCounter++
        val doubleInterval = doubleClickWindowTicks.coerceAtLeast(0).toLong()
        val states = keyStates.values.toList()
        states.forEach { state ->
            val down = state.mapping.isDown
            if (down) {
                if (!state.wasDown) {
                    state.pressTick = 0
                }
                state.pressTick++
                sendAction(state.id, KeyActionType.LONG_PRESS, state.pressTick, false)
            } else if (state.wasDown) {
                sendAction(state.id, KeyActionType.LONG_PRESS, state.pressTick.coerceAtLeast(1), true)
                val isDouble =
                    state.lastClickTick >= 0 && tickCounter - state.lastClickTick <= doubleInterval
                val action = if (isDouble) KeyActionType.DOUBLE_CLICK else KeyActionType.SINGLE_CLICK
                sendAction(state.id, action, state.pressTick.coerceAtLeast(1), true)
                state.lastClickTick = tickCounter
                state.pressTick = 0
            }
            state.wasDown = down
        }
    }

    private fun registerIfPossible(state: KeyState) {
        val registerer = registrar ?: return
        if (registeredIds.add(state.id)) {
            registerer(state.mapping)
        }
    }

    private fun sendAction(
        keyId: ResourceLocation,
        action: KeyActionType,
        pressTick: Int,
        isRelease: Boolean
    ) {
        val client = Minecraft.getInstance()
        val player = client.player
        if (player == null || client.level == null) {
            return
        }
        CooEventBus.call(
            KeyActionEvent(player, keyId, action, pressTick, isRelease, false)
        )
        CooParticlesServices.CLIENT_NETWORK.send(PacketKeyActionC2S(keyId, action, pressTick, isRelease))
    }
}
