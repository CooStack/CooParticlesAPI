package cn.coostack.cooparticlesapi.exceptions

import net.minecraft.resources.ResourceLocation

class RenderPipeOutputNotSetException(pipeID: ResourceLocation) :
    Exception("$pipeID 还没有设置一个输出管线!") {
}