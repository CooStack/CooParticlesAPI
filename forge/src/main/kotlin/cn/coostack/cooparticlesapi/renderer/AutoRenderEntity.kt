package cn.coostack.cooparticlesapi.renderer

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.annotations.renderer.handle.RenderEntityRegistryHelper
import net.minecraft.network.PacketByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

abstract class AutoRenderEntity(world: Level?, pos: Vec3 = Vec3.ZERO) : RenderEntity(world, pos) {
    override fun getCodec(): ForgeStreamCodec<PacketByteBuf, RenderEntity> {
        return RenderEntityRegistryHelper.generateCodec(this)
    }

    override fun loadProfileFromEntity(another: RenderEntity) {
        super.loadProfileFromEntity(another)
        CodecHelper.updateFields(this, another)
    }
}
