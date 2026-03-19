package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 基于 `@CooAutoRegister` 的 RenderEntity codec 自动注册器。
 *
 * 它只负责把 RenderEntity 的 codec 放进客户端注册表，
 * renderer 仍然需要由开发者显式注册或在别处补充。
 */
object RenderEntityAutoRegistry {
    /**
     * 扫描带 `@CooAutoRegister` 的类型，并尝试自动注册其中的 RenderEntity。
     */
    fun registerScanner() {
        CooParticlesConstants.logger.info("正在自动注册 RenderEntity")
        CooAPIScanner.getWithAnnotation(CooAutoRegister::class.java)
            .iterator()
            .forEach { candidate ->
                registerClass(candidate.toClass())
            }
    }

    /**
     * 尝试把一个 class 注册为 RenderEntity codec 条目。
     *
     * 不是 `RenderEntity` 子类时会直接跳过；
     * 若该 id 已经注册过，也会保持幂等不重复注册。
     */
    internal fun registerClass(clazz: Class<*>) {
        if (!RenderEntity::class.java.isAssignableFrom(clazz)) {
            return
        }
        val instance = createInstance(clazz as Class<out RenderEntity>)
        val id = instance.getRenderID()
        if (ClientRenderEntityRegistry.get(id) != null) {
            return
        }
        ClientRenderEntityRegistry.register(id, instance.getCodec())
    }

    /**
     * 反射创建一个 RenderEntity 临时实例。
     *
     * 支持两种公开构造器：
     * - 无参构造
     * - `(Level, Vec3)` 构造
     *
     * 这是自动注册阶段读取 `getRenderID()` / `getCodec()` 所需的最小实例化约束。
     */
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
