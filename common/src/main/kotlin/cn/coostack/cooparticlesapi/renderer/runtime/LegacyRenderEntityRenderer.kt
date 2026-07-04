package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameStage
import org.joml.Matrix4f
import org.joml.Matrix4fStack
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class LegacyRenderEntityRenderer :
    WorldPassRenderEntityRenderer<RenderEntity>,
    RenderEntityReleaseHook<RenderEntity> {

    override fun describeFeatures(entity: RenderEntity): RenderEntityFeatureSet {
        return RenderEntityFeatureSet(
            stages = setOf(RenderFrameStage.WORLD_PASS),
            localRendererEnabled = true,
            effectGraphEnabled = false
        )
    }

    override fun createVisualProfile(entity: RenderEntity): RenderEntityVisualProfile {
        return RenderEntityVisualProfile()
    }

    override fun initialize(instance: RenderEntityInstance<RenderEntity>) {
        findNoArgMethod(instance.entity.javaClass, "initialize")?.invoke(instance.entity)
    }

    override fun renderLocal(input: LocalRenderInput<RenderEntity>) {
        val entity = input.instance.entity
        findRenderMethod(entity.javaClass)?.invoke(
            entity,
            input.modelMatrix,
            input.viewMatrix,
            input.projMatrix,
            input.tickDelta
        )
    }

    override fun release(instance: RenderEntityInstance<RenderEntity>) {
        findNoArgMethod(instance.entity.javaClass, "release")?.invoke(instance.entity)
    }

    private companion object {
        private val noArgMethodCache = ConcurrentHashMap<Pair<Class<*>, String>, Method?>()
        private val renderMethodCache = ConcurrentHashMap<Class<*>, Method?>()

        fun findNoArgMethod(type: Class<*>, name: String): Method? {
            return noArgMethodCache.computeIfAbsent(type to name) {
                runCatching { type.getMethod(name) }.getOrNull()
            }
        }

        fun findRenderMethod(type: Class<*>): Method? {
            return renderMethodCache.computeIfAbsent(type) {
                runCatching {
                    type.getMethod(
                        "render",
                        Matrix4fStack::class.java,
                        Matrix4f::class.java,
                        Matrix4f::class.java,
                        java.lang.Float.TYPE
                    )
                }.getOrNull()
            }
        }
    }
}
