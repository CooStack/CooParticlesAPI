package cn.coostack.cooparticlesapi.exceptions

import net.minecraft.resources.ResourceLocation

class RenderPipeLinkerNotSetException(var renderID: ResourceLocation) :
    Exception("Render $renderID's pipe linker not set, use manager.setLinkerFunc to set pipes link") {
}