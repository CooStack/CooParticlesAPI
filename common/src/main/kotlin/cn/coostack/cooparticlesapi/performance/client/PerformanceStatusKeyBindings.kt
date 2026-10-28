package cn.coostack.cooparticlesapi.performance.client

import cn.coostack.cooparticlesapi.key.isPhysicallyDown
import cn.coostack.cooparticlesapi.key.CooKeyBindingManager
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft

/** 注册并检测打开 Status GUI 的两个可独立重绑按键。 */
object PerformanceStatusKeyBindings {
    /** 默认修饰键 F3，可在原版控制设置中单独修改。 */
    private val modifier = KeyMapping(
        "key.cooparticlesapi.status_modifier",
        InputConstants.Type.KEYBOARD,
        InputConstants.KEY_F3,
        CooKeyBindingManager.category("key.categories.cooparticlesapi"),
    )

    /** 默认触发键 Grave/波浪号，可在原版控制设置中单独修改。 */
    private val trigger = KeyMapping(
        "key.cooparticlesapi.status_open",
        InputConstants.Type.KEYBOARD,
        InputConstants.KEY_GRAVE,
        CooKeyBindingManager.category("key.categories.cooparticlesapi"),
    )

    /** 上一客户端 tick 是否已经按住完整组合，避免长按重复打开。 */
    private var chordWasDown = false

    /** 返回需要由 Fabric 或 NeoForge 注册的两个原版 KeyMapping。 */
    fun mappings(): List<KeyMapping> = listOf(modifier, trigger)

    /** 检测组合键上升沿，并只在没有其他界面时打开 Status GUI。 */
    fun tick(client: Minecraft) {
        val chordDown = modifier.isPhysicallyDown(client) && trigger.isPhysicallyDown(client)
        if (chordDown && !chordWasDown && client.gui.screen() == null) {
            PerformanceStatusClientController.openGui()
        }
        chordWasDown = chordDown
    }
}
