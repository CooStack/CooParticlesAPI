package cn.coostack.cooparticlesapi.utils

import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object ReflectUtil {
    @JvmStatic
    fun getVec3Class(): Class<Vec3> = Vec3::class.java

    @JvmStatic
    fun getLevelClass(): Class<Level> = Level::class.java

    @JvmStatic
    fun getStreamCodecClass(): Class<StreamCodec<*,*>> = StreamCodec::class.java
}