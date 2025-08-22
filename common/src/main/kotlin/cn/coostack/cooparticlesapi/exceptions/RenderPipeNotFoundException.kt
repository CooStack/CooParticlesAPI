package cn.coostack.cooparticlesapi.exceptions

import net.minecraft.resources.ResourceLocation

class RenderPipeNotFoundException(var renderID: ResourceLocation) :
    Exception("Render $renderID's bound pipe manager not fount, you need to use ClientRenderEntityManager.bindEntityRenderPipe(YourRenderEntity.ID, YourRegisteredPipeManager.pipeID to bind a output pipe manager") {
}