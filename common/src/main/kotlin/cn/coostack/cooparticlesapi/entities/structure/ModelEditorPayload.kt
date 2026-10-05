package cn.coostack.cooparticlesapi.entities.structure

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation

/** 服务端窗口、预览与编辑结果，只由客户端监听器消费。 */
@CooAutoRegister
class ModelEditorPayload() : CooPacket() {
    /** 独立响应标签，预览包含请求标识。 */
    @field:CodecField
    var data = CompoundTag()

    /** 复制服务端响应。 */
    constructor(data: CompoundTag) : this() { this.data = data.copy() }

    override fun id(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "structure_model_editor")
}
