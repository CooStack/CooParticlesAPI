package cn.coostack.cooparticlesapi.key

import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.events.key.KeyActionBatch
import cn.coostack.cooparticlesapi.event.events.key.KeyActionData
import cn.coostack.cooparticlesapi.event.events.key.KeyActionEvent
import cn.coostack.cooparticlesapi.event.events.key.KeyActionType
import cn.coostack.cooparticlesapi.network.packet.client.PacketKeyActionC2S
import cn.coostack.cooparticlesapi.network.packet.server.PacketKeyBindingCountdownS2C
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.CooParticlesConstants
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import org.lwjgl.sdl.SDLMouse

/**
 * 管理客户端按键注册、界面触发范围、长按/双击状态与服务端动作通知。
 * 加载器先在注册阶段调用 [setRegistrar]，业务通过 [register] 添加绑定，客户端每 tick 调用 [tick]。
 * 服务端仅使用 [sendCountdown]；不得在专用服务器初始化客户端注册表。
 *
 * 示例：`CooKeyBindingManager.register(keyId, InputConstants.Type.KEYBOARD, InputConstants.KEY_G, "key.categories.cooparticlesapi")`。
 */
object CooKeyBindingManager {
    /** 单个绑定的边沿、长按和防止跨界面补触发的状态。 */
    private data class KeyState(
        val id: Identifier,
        val mapping: KeyMapping,
        val triggerScope: CooKeyBindingTriggerScope,
        var wasDown: Boolean = false,
        var blockedUntilRelease: Boolean = false,
        var pressTick: Int = 0,
        var lastClickTick: Long = -1
    )

    /** 已注册绑定及其客户端状态，键为独立动作标识。 */
    private val keyStates = LinkedHashMap<Identifier, KeyState>()
    /** 已经提交到加载器的动作标识，保证初始化回调幂等。 */
    private val registeredIds = HashSet<Identifier>()
    /** 字符串分类解析后的原版注册单例，键为规范化分类标识。 */
    private val categories = mutableMapOf<Identifier, KeyMapping.Category>()
    /** 加载器提供的注册回调，仅在客户端注册阶段配置。 */
    private var registrar: ((KeyMapping) -> Unit)? = null
    /** 客户端动作采样 tick，用于双击间隔。 */
    private var tickCounter = 0L

    /** 客户端禁止动作的剩余 tick，键为动作标识。 */
    private val keyCountDowns = mutableMapOf<Identifier, Int>()
    /** 两次释放允许组成双击的最大 tick 间隔，负值按零处理。 */
    var doubleClickWindowTicks = 6

    /**
     * 配置加载器注册回调并提交此前登记的绑定；必须处于加载器允许的客户端注册阶段。
     * 示例：`CooKeyBindingManager.setRegistrar { mapping -> registerMapping(mapping) }`。
     * @param registerer 接收每个原版绑定的加载器回调，每个动作只提交一次
     */
    fun setRegistrar(registerer: (KeyMapping) -> Unit) {
        registrar = registerer
        keyStates.values.forEach { registerIfPossible(it) }
    }

    /**
     * 复用原版分类注册单例，兼容既有字符串配置。
     * 示例：`CooKeyBindingManager.category("key.categories.cooparticlesapi")`。
     * @param key 原分类翻译键或命名空间标识；旧翻译键转换为命名空间和分类路径
     * @return 同一规范化标识共享的原版分类，必须在客户端按键注册阶段首次调用
     */
    fun category(key: String): KeyMapping.Category {
        val id = if (':' in key) {
            Identifier.parse(key)
        } else {
            val legacy = key.removePrefix("key.categories.").removePrefix("key.category.")
            val namespace = legacy.substringBefore('.')
            val path = legacy.substringAfter('.', "general")
            Identifier.fromNamespaceAndPath(namespace.ifBlank { CooParticlesConstants.MOD_ID }, path)
        }
        return categories.getOrPut(id) { KeyMapping.Category.register(id) }
    }

    /**
     * 注册默认允许世界与界面触发的客户端动作。
     * 示例：`register(keyId, InputConstants.Type.KEYBOARD, InputConstants.KEY_G, "key.categories.cooparticlesapi")`。
     * @param keyId 独立动作标识，重复注册会失败
     * @param keyType 原版键盘或鼠标输入类型
     * @param defaultKey 对应输入类型的 SDL 默认键编号
     * @param category 分类翻译键或命名空间标识
     * @return 加载器注册和物理状态检测共用的原版绑定
     */
    fun register(
        keyId: Identifier,
        keyType: InputConstants.Type,
        defaultKey: Int,
        category: String
    ): KeyMapping {
        return register(keyId, keyType, defaultKey, category, CooKeyBindingTriggerScope.BOTH)
    }

    /**
     * 注册指定界面触发范围的客户端动作，鼠标绑定默认仅监听物理状态。
     * 示例：`register(keyId, InputConstants.Type.KEYBOARD, InputConstants.KEY_G, "key.categories.cooparticlesapi", CooKeyBindingTriggerScope.NON_GUI)`。
     * @param keyId 独立动作标识，重复注册会失败
     * @param keyType 原版键盘或鼠标输入类型
     * @param defaultKey 对应输入类型的 SDL 默认键编号
     * @param category 分类翻译键或命名空间标识
     * @param triggerScope 开始与延续动作允许的界面状态
     * @return 加载器注册和物理状态检测共用的原版绑定
     */
    fun register(
        keyId: Identifier,
        keyType: InputConstants.Type,
        defaultKey: Int,
        category: String,
        triggerScope: CooKeyBindingTriggerScope
    ): KeyMapping {
        return register(keyId, keyType, defaultKey, category, keyType == InputConstants.Type.MOUSE, triggerScope)
    }

    /**
     * 注册默认界面范围的动作，并显式选择只监听绑定。
     * 示例：`register(keyId, InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_RIGHT, "key.categories.cooparticlesapi", true)`。
     * @param keyId 独立动作标识，重复注册会失败
     * @param keyType 原版键盘或鼠标输入类型
     * @param defaultKey 对应输入类型的 SDL 默认键编号
     * @param category 分类翻译键或命名空间标识
     * @param listenOnly 是否使用只监听绑定类型
     * @return 已登记的原版绑定
     */
    fun register(
        keyId: Identifier,
        keyType: InputConstants.Type,
        defaultKey: Int,
        category: String,
        listenOnly: Boolean
    ): KeyMapping {
        return register(keyId, keyType, defaultKey, category, listenOnly, CooKeyBindingTriggerScope.BOTH)
    }

    /**
     * 完整注册入口，保存触发范围并在加载器回调可用时立即提交。
     * 示例：`register(keyId, InputConstants.Type.KEYBOARD, InputConstants.KEY_G, "key.categories.cooparticlesapi", false, CooKeyBindingTriggerScope.BOTH)`。
     * @param keyId 独立动作标识，重复注册会失败
     * @param keyType 原版键盘或鼠标输入类型
     * @param defaultKey 对应输入类型的 SDL 默认键编号
     * @param category 分类翻译键或命名空间标识
     * @param listenOnly 是否使用只监听绑定类型
     * @param triggerScope 开始与延续动作允许的界面状态
     * @return 后续重绑与动作采样使用的原版绑定
     */
    fun register(
        keyId: Identifier,
        keyType: InputConstants.Type,
        defaultKey: Int,
        category: String,
        listenOnly: Boolean,
        triggerScope: CooKeyBindingTriggerScope
    ): KeyMapping {
        require(keyId !in keyStates) { "按键标识已注册：$keyId" }
        val mappingName = "key.${keyId.namespace}.${keyId.path}"
        val keyCategory = category(category)
        val mapping = if (listenOnly) {
            CooListeningKeyMapping(mappingName, keyType, defaultKey, keyCategory)
        } else {
            KeyMapping(mappingName, keyType, defaultKey, keyCategory)
        }
        val state = KeyState(keyId, mapping, triggerScope)
        keyStates[keyId] = state
        registerIfPossible(state)
        return mapping
    }

    /**
     * 查询已注册的客户端绑定，不创建新绑定。
     * 示例：`CooKeyBindingManager.getMapping(keyId)?.isDown`。
     * @param keyId 动作标识
     * @return 已登记的绑定；未注册时为空
     */
    fun getMapping(keyId: Identifier): KeyMapping? {
        return keyStates[keyId]?.mapping
    }

    /**
     * 禁止客户端动作一段时间；按住的键须释放后才能重新触发。
     * 示例：`CooKeyBindingManager.setCountdown(keyId, 20)`。
     * @param key 动作标识
     * @param cd 剩余客户端 tick，非正数在下次采样时解除倒计时
     */
    fun setCountdown(key: Identifier, cd: Int) {
        keyCountDowns[key] = cd
    }


    /**
     * 服务端通知指定玩家对一个动作启用倒计时，不直接改变服务端动作校验。
     * 示例：`CooKeyBindingManager.sendCountdown(player, keyId, 20)`。
     * @param to 接收通知的服务端玩家
     * @param key 动作标识
     * @param cd 禁止动作的客户端 tick 数
     */
    fun sendCountdown(to: ServerPlayer, key: Identifier, cd: Int) {
        val packet = PacketKeyBindingCountdownS2C(key, cd)
        CooParticlesServices.SERVER_NETWORK.send(packet, to)
    }

    /**
     * 在客户端 tick 采样物理按键，发布本地事件并合并发送到服务器。
     * 示例：`CooKeyBindingManager.tick()`。
     * 界面切换不会把已按住的键误识别为新按下，服务端仍须校验业务权限。
     */
    fun tick() {
        if (keyStates.isEmpty()) return
        tickCounter++
        val doubleInterval = doubleClickWindowTicks.coerceAtLeast(0).toLong()
        val states = keyStates.values.toList()
        val pendingActions = ArrayList<KeyActionData<Identifier>>()
        val client = Minecraft.getInstance()
        val isGuiOpen = client.gui.screen() != null
        states.forEach { state ->
            val down = state.mapping.isPhysicallyDown(client)
            if (!down) {
                state.blockedUntilRelease = false
            }
            val canStart = state.triggerScope.canStart(isGuiOpen)
            val canContinue = state.triggerScope.canContinue(isGuiOpen)
            val activeDown = down && !state.blockedUntilRelease && (if (state.wasDown) canContinue else canStart)
            state.mapping.isDown = activeDown
            if (keyCountDowns.containsKey(state.id)) {
                val current = keyCountDowns[state.id]!!
                if (current > 0) {
                    keyCountDowns[state.id] = current - 1
                    state.wasDown = false
                    state.blockedUntilRelease = down
                    state.mapping.isDown = false
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

    private fun sendActions(keyActions: KeyActionBatch<Identifier>) {
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
 * 普通 Coo 按键都必须通过此入口保持相同判定。鼠标和键盘重绑均受支持，未绑定时返回 false。
 * 示例：`mapping.isPhysicallyDown(Minecraft.getInstance())`。
 * @receiver 已完成加载器注册的原版绑定
 * @param client 当前客户端，仅允许客户端主线程调用
 * @return 当前实际绑定的 SDL 输入是否按下，不消费点击队列
 */
internal fun KeyMapping.isPhysicallyDown(client: Minecraft): Boolean {
    if (isUnbound || !client.isWindowActive) return false
    val bound = InputConstants.getKey(saveString())
    return when (bound.type) {
        InputConstants.Type.KEYBOARD -> InputConstants.isKeyDown(bound.value)
        InputConstants.Type.MOUSE -> {
            // SDL 按键从 1 开始，位掩码从 0 开始；直接读状态可覆盖侧键和调试键组合。
            SDLMouse.SDL_GetMouseState(null, null) and (1 shl (bound.value - 1)) != 0
        }
    }
}
