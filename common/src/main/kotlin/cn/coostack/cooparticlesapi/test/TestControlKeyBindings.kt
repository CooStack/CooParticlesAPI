package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.event.events.key.KeyActionEvent
import cn.coostack.cooparticlesapi.key.CooKeyBindingManager
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.resources.Identifier

/** 注册测试项目切换按键，并将释放动作解释为单击、双击或长按。 */
object TestControlKeyBindings {
    /** 两个测试切换按键共用的控制设置分类翻译键。 */
    private const val CATEGORY = "key.category.cooparticlesapi.test"

    /** 前进动作的注册标识，客户端注册与服务端监听共用。 */
    val NEXT_KEY: Identifier =
        Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_next")
    /** 后退动作的注册标识，客户端注册与服务端监听共用。 */
    val PREVIOUS_KEY: Identifier =
        Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_previous")

    /** 防止加载器初始化重复注册两个动作。 */
    private var registered = false

    fun register() {
        if (registered) {
            return
        }
        registered = true
        CooKeyBindingManager.register(
            NEXT_KEY,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_PAGEDOWN,
            CATEGORY
        )
        CooKeyBindingManager.register(
            PREVIOUS_KEY,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_PAGEUP,
            CATEGORY
        )
    }

    fun isLongPressRelease(event: KeyActionEvent, keyId: Identifier): Boolean {
        return event.serverSide && event.isReleased(keyId) && event.getPressTick(keyId) >= 10
    }

    fun isReleasedDoubleClick(event: KeyActionEvent, keyId: Identifier): Boolean {
        return event.serverSide && event.isReleased(keyId) && event.isDoubleClick(keyId)
    }

    fun isReleasedSingleClick(event: KeyActionEvent, keyId: Identifier): Boolean {
        return event.serverSide && event.isReleased(keyId) && event.isSingleClick(keyId)
    }
}

@EventListener(CooParticlesConstants.MOD_ID)
/** 在服务端把测试按键释放动作分发给当前玩家的测试会话。 */
object TestControlKeyListener {
    @EventHandler
    fun onKeyAction(event: KeyActionEvent) {
        if (!event.serverSide) {
            return
        }
        val player = event.player
        when {
            TestControlKeyBindings.isLongPressRelease(event, TestControlKeyBindings.NEXT_KEY) -> {
                TestManager.jumpToLast(player)
            }

            TestControlKeyBindings.isLongPressRelease(event, TestControlKeyBindings.PREVIOUS_KEY) -> {
                TestManager.jumpToFirst(player)
            }

            TestControlKeyBindings.isReleasedDoubleClick(event, TestControlKeyBindings.NEXT_KEY) -> {
                TestManager.jumpRelative(player, 5)
            }

            TestControlKeyBindings.isReleasedDoubleClick(event, TestControlKeyBindings.PREVIOUS_KEY) -> {
                TestManager.jumpRelative(player, -5)
            }

            TestControlKeyBindings.isReleasedSingleClick(event, TestControlKeyBindings.NEXT_KEY) -> {
                TestManager.completeCurrent(player)
            }

            TestControlKeyBindings.isReleasedSingleClick(event, TestControlKeyBindings.PREVIOUS_KEY) -> {
                TestManager.jumpRelative(player, -1)
            }
        }
    }
}
