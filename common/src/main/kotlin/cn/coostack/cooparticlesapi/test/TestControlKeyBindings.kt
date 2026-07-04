package cn.coostack.cooparticlesapi.test

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.event.events.key.KeyActionEvent
import cn.coostack.cooparticlesapi.key.CooKeyBindingManager
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.resources.ResourceLocation
import org.lwjgl.glfw.GLFW

object TestControlKeyBindings {
    private const val CATEGORY = "key.category.cooparticlesapi.test"
    private const val LONG_PRESS_THRESHOLD = 10

    val NEXT_KEY: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_next")
    val PREVIOUS_KEY: ResourceLocation =
        ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_previous")

    private var registered = false

    fun register() {
        if (registered) {
            return
        }
        registered = true
        CooKeyBindingManager.register(
            NEXT_KEY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_DOWN,
            CATEGORY
        )
        CooKeyBindingManager.register(
            PREVIOUS_KEY,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_PAGE_UP,
            CATEGORY
        )
    }

    fun isLongPressRelease(event: KeyActionEvent, keyId: ResourceLocation): Boolean {
        return event.serverSide && event.isReleased(keyId) && event.getPressTick(keyId) >= LONG_PRESS_THRESHOLD
    }

    fun isReleasedDoubleClick(event: KeyActionEvent, keyId: ResourceLocation): Boolean {
        return event.serverSide && event.isReleased(keyId) && event.isDoubleClick(keyId)
    }

    fun isReleasedSingleClick(event: KeyActionEvent, keyId: ResourceLocation): Boolean {
        return event.serverSide && event.isReleased(keyId) && event.isSingleClick(keyId)
    }
}

@EventListener(CooParticlesConstants.MOD_ID)
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
