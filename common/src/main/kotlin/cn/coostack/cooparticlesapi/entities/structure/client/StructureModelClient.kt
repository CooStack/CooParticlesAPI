package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.enums.DistType
import cn.coostack.cooparticlesapi.entities.structure.ModelEditorPayload
import cn.coostack.cooparticlesapi.event.events.packet.CooPacketReceiveEvent
import cn.coostack.cooparticlesapi.event.events.world.client.ClientWorldChangeEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientStoppingEvent
import cn.coostack.cooparticlesapi.event.events.client.ClientResourceReloadEvent
import net.minecraft.client.Minecraft

/** 两个平台共用的客户端窗口与展示缓存入口，只在客户端扫描注册。 */
@EventListener(dist = DistType.CLIENT)
object StructureModelClient {
    /** 物品渲染的独占缓存，由统一物品渲染钩子调用。 */
    val itemRenderer = HeldStructureModelRenderer()

    /** 只消费服务端响应；预览版本号在窗口中再次校验。 */
    @EventHandler
    fun receive(event: CooPacketReceiveEvent) {
        if (event.side != CooPacketReceiveEvent.Side.CLIENT) return
        val packet = event.packet as? ModelEditorPayload ?: return
        val client = Minecraft.getInstance()
        if (packet.data.contains("PreviewRequest")) {
            (client.gui.screen() as? ModelEditorScreen)?.preview(packet.data)
        } else if (packet.data.contains("Result")) {
            (client.gui.screen() as? ModelEditorScreen)?.result(
                packet.data.getBooleanOr("Result", false), packet.data.getStringOr("Message", "")
            )
        } else client.setScreenAndShow(ModelEditorScreen(packet.data))
    }

    /** 切换世界时释放预览实体。 */
    @EventHandler
    fun change(event: ClientWorldChangeEvent) { itemRenderer.clear() }

    /** 资源重载时重新解析物品展示变换并释放旧结构展示状态。 */
    @EventHandler
    fun reload(event: ClientResourceReloadEvent) { itemRenderer.clear() }

    /** 退出客户端时释放预览实体。 */
    @EventHandler
    fun stop(event: ClientStoppingEvent) { itemRenderer.clear() }
}
