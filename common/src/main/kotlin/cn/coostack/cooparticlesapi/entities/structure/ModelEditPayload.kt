package cn.coostack.cooparticlesapi.entities.structure

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation

/** 客户端模型编辑意图，由服务端 CooEvent 监听器校验并处理。 */
@CooAutoRegister
class ModelEditPayload() : CooPacket() {
    /** 用户草稿，不接受客户端提供的结构快照。 */
    @field:CodecField
    var data = CompoundTag()

    /** 发送前复制草稿。 */
    constructor(data: CompoundTag) : this() { this.data = data.copy() }

    override fun id(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "structure_model_edit")
}
