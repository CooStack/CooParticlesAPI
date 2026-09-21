package cn.coostack.cooparticlesapi.display

import cn.coostack.cooparticlesapi.annotations.display.handle.DisplayEntityRegistryHelper
import net.minecraft.network.PacketByteBuf
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

abstract class AutoDisplayEntity(pos: Vec3, world: Level?) : DisplayEntity(pos, world) {
    override fun getCodec(): ForgeStreamCodec<PacketByteBuf, DisplayEntity> {
        return DisplayEntityRegistryHelper.generateCodec(this)
    }
}
