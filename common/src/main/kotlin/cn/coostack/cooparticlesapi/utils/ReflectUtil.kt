package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.network.particle.emitters.ClassParticleEmitters
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object ReflectUtil {
    @JvmStatic
    fun getVec3Class(): Class<Vec3> = Vec3::class.java

    @JvmStatic
    fun getLevelClass(): Class<Level> = Level::class.java

    @JvmStatic
    fun getStreamCodecClass(): Class<StreamCodec<*, *>> = StreamCodec::class.java


    @JvmStatic
    fun infoTimeWith(name: String = "", invoker: Runnable) {
        val start = System.currentTimeMillis()
        invoker.run()
        val end = System.currentTimeMillis()
        CooParticlesConstants.logger.info("执行${name}完毕 : 耗时${end - start}ms")
    }

    @JvmStatic
    fun <T> infoTimeCallable(name: String = "", invoker: () -> T): T {
        val start = System.currentTimeMillis()
        val res = invoker()
        val end = System.currentTimeMillis()
        CooParticlesConstants.logger.info("执行并返回${name}完毕 : 耗时${end - start}ms")
        return res
    }

}