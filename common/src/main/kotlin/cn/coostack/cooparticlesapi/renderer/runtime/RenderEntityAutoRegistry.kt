package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

object RenderEntityAutoRegistry {
    fun registerScanner() {
        CooParticlesConstants.logger.info("正在自动注册 RenderEntity")
        CooAPIScanner.getWithAnnotation(CooAutoRegister::class.java)
            .iterator()
            .forEach { candidate ->
                registerClass(candidate.toClass())
            }
    }

    internal fun registerClass(clazz: Class<*>) {
        if (!RenderEntity::class.java.isAssignableFrom(clazz)) {
            return
        }
        val instance = createInstance(clazz as Class<out RenderEntity>)
        val id = instance.getRenderID()
        if (ClientRenderEntityRegistry.get(id) != null) {
            return
        }
        ClientRenderEntityRegistry.register(id, instance.getCodec()) {
            throw IllegalStateException("RenderEntity renderer not registered: $id")
        }
    }

    private fun createInstance(type: Class<out RenderEntity>): RenderEntity {
        val noArgCtor = runCatching { type.getConstructor() }.getOrNull()
        if (noArgCtor != null) {
            return noArgCtor.newInstance()
        }
        val levelVecCtor = runCatching { type.getConstructor(Level::class.java, Vec3::class.java) }.getOrNull()
            ?: throw IllegalStateException(
                "RenderEntity requires public no-arg or (Level, Vec3) constructor: ${type.name}"
            )
        return levelVecCtor.newInstance(null, Vec3.ZERO)
    }
}
