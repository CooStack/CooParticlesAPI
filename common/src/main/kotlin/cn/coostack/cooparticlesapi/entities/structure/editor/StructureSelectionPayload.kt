package cn.coostack.cooparticlesapi.entities.structure.editor

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation

/** 双向选区协议，由监听器按接收逻辑侧分离意图与权威回执。 */
@CooAutoRegister
class StructureSelectionPayload() : CooPacket() {
    /** 选区意图或状态，保存操作必须携带服务器版本号。 */
    @field:CodecField
    var data = CompoundTag()

    /** 复制意图或响应。 */
    constructor(data: CompoundTag) : this() { this.data = data.copy() }

    override fun id(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "structure_selection")
}
