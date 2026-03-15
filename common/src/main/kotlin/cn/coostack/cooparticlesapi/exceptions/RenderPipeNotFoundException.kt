package cn.coostack.cooparticlesapi.exceptions

import net.minecraft.resources.ResourceLocation

class RenderPipeNotFoundException(var renderID: ResourceLocation) :
    Exception("Render $renderID's bound pipe manager not found. Register a renderer/codec through the V2 registry and ensure the runtime config provides a pipe manager.") {
    }
