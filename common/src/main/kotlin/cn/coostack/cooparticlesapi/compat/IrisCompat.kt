package cn.coostack.cooparticlesapi.compat

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderPass
import cn.coostack.cooparticlesapi.renderer.runtime.IrisWorldPassMode
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Method
import java.util.Optional

/**
 * 与 IRIS 互操作的反射桥接，不依赖 IRIS 类型也不需要 mixin。
 *
 * IRIS 1.8.x 的两个关键事实：
 *
 * 1. `MixinShaderInstance` 在 shader pack 启用时，会让所有 *未被 IRIS 注册* 的
 *    ShaderInstance 在绘制时直接 `return`，导致 `RenderType.create` 自定义出来的
 *    渲染管线整段不渲染。
 * 2. 这套 mixin 同时提供了 `setShouldSkip(MethodHandle)` 公共入口：
 *    传入一个返回 `false` 的 MethodHandle，就能把该 ShaderInstance 标记为
 *    "永远不要跳过"。我们用反射调用即可，不需要编译期依赖 IRIS。
 *
 * IRIS 不存在时所有方法都是 no-op，所以宿主代码可以无脑调用。
 */
object IrisCompat {
    private val LOGGER = LoggerFactory.getLogger("CooParticlesAPI/IrisCompat")

    /** 反射缓存：ShaderInstance.setShouldSkip(MethodHandle) → 由 IRIS mixin 注入。 */
    @Volatile
    private var setShouldSkipResolved = false

    @Volatile
    private var setShouldSkipHandle: MethodHandle? = null

    /** 反射缓存：OuterWrappedRenderType.wrapExactlyOnce(String, RenderType, RenderStateShard)。 */
    @Volatile
    private var entityRenderTypeWrapperResolved = false

    @Volatile
    private var entityRenderTypeWrapperHandle: MethodHandle? = null

    @Volatile
    private var entityRenderStateShard: RenderStateShard? = null

    @Volatile
    private var particleRenderingMethodsResolved = false

    @Volatile
    private var particleRenderingMethods: ParticleRenderingMethods? = null

    @Volatile
    private var particleTranslucentShaderMethodResolved = false

    @Volatile
    private var particleTranslucentShaderMethod: Method? = null

    /** 单例 MethodHandle：永远返回 false (= "请不要跳过我")。 */
    private val NEVER_SKIP: MethodHandle by lazy {
        MethodHandles.constant(Boolean::class.javaPrimitiveType, false)
            .asType(MethodType.methodType(Boolean::class.javaPrimitiveType))
    }

    /**
     * 在 IRIS 光影下把一个 ShaderInstance 标记为不可跳过。
     * 没装 IRIS / IRIS 版本不支持此 hook 时静默 no-op。
     */
    @JvmStatic
    fun markUnskippable(shader: ShaderInstance) {
        if (!CooParticlesAPIClient.irisLoaded) return
        val handle = resolveSetShouldSkip(shader.javaClass) ?: return
        try {
            handle.invokeWithArguments(shader, NEVER_SKIP)
        } catch (t: Throwable) {
            LOGGER.warn("Failed to mark shader '${shader.name}' as unskippable under Iris", t)
        }
    }

    /**
     * 保留调用方传入的自定义 RenderType，并在 IRIS 光影启用时套上 IRIS 的 entity pass 标记。
     *
     * 这不是切回原版 RenderType；IRIS 的 wrapper 会通过 `unwrap()` 指向原始 RenderType。
     */
    @JvmStatic
    fun wrapEntityRenderType(renderType: RenderType): RenderType {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return renderType

        val handle = resolveEntityRenderTypeWrapper() ?: return renderType
        val stateShard = entityRenderStateShard ?: return renderType
        return try {
            handle.invokeWithArguments("iris:entity", renderType, stateShard) as RenderType
        } catch (t: Throwable) {
            LOGGER.warn("Failed to wrap custom RenderType for Iris entity rendering: $renderType", t)
            renderType
        }
    }

    /** Iris 的 MIXED 设置会把粒子分成 opaque/translucent 两次调用。 */
    @JvmStatic
    fun usesMixedParticleRendering(): Boolean {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return false
        val methods = resolveParticleRenderingMethods() ?: return false
        return try {
            val manager = methods.getPipelineManager.invoke(null)
            val pipeline = (methods.getPipeline.invoke(manager) as Optional<*>).orElse(null) ?: return false
            val setting = methods.getParticleRenderingSettings.invoke(pipeline) as? Enum<*>
            setting?.name == "MIXED"
        } catch (t: Throwable) {
            LOGGER.warn("Failed to query Iris particle rendering mode", t)
            false
        }
    }

    /**
     * 在 Iris 粒子 shader 的 writing framebuffer 中执行 GPU 粒子绘制。
     *
     * 原版粒子批次结束后 Iris 已通过 ShaderInstance.clear() 切回主目标，不能依赖
     * RenderSystem 中残留的 shader。TRANSLUCENT pass 使用 Iris 的公开 ShaderAccess 入口。
     */
    @JvmStatic
    fun runWithParticleShader(pass: CParticleRenderPass, draw: () -> Unit) {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) {
            draw()
            return
        }

        val particleShader = when (pass) {
            CParticleRenderPass.TRANSLUCENT -> getParticleTranslucentShader()
            CParticleRenderPass.ALL, CParticleRenderPass.OPAQUE -> GameRenderer.getParticleShader()
            CParticleRenderPass.NONE -> null
        }
        if (particleShader == null) {
            draw()
            return
        }

        particleShader.apply()
        try {
            draw()
        } finally {
            particleShader.clear()
        }
    }

    /** 在 Iris 对应的 entity framebuffer 中执行 RenderEntity 的本地 world pass。 */
    @JvmStatic
    fun runWithEntityShader(mode: IrisWorldPassMode, draw: () -> Unit) {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) {
            draw()
            return
        }

        val entityShader = when (mode) {
            IrisWorldPassMode.ENTITY_SOLID -> GameRenderer.getRendertypeEntitySolidShader()
            IrisWorldPassMode.ENTITY_CUTOUT -> GameRenderer.getRendertypeEntityCutoutShader()
            IrisWorldPassMode.ENTITY_TRANSLUCENT -> GameRenderer.getRendertypeEntityTranslucentShader()
        }
        if (entityShader == null) {
            draw()
            return
        }

        entityShader.apply()
        try {
            draw()
        } finally {
            entityShader.clear()
        }
    }

    private fun resolveSetShouldSkip(cls: Class<*>): MethodHandle? {
        if (setShouldSkipResolved) return setShouldSkipHandle
        synchronized(this) {
            if (setShouldSkipResolved) return setShouldSkipHandle
            setShouldSkipResolved = true
            setShouldSkipHandle = try {
                MethodHandles.publicLookup().findVirtual(
                    cls,
                    "setShouldSkip",
                    MethodType.methodType(Void.TYPE, MethodHandle::class.java)
                )
            } catch (_: NoSuchMethodException) {
                null
            } catch (_: IllegalAccessException) {
                null
            } catch (t: Throwable) {
                LOGGER.warn("Unexpected failure resolving Iris setShouldSkip hook", t)
                null
            }
            return setShouldSkipHandle
        }
    }

    private fun resolveEntityRenderTypeWrapper(): MethodHandle? {
        if (entityRenderTypeWrapperResolved) return entityRenderTypeWrapperHandle
        synchronized(this) {
            if (entityRenderTypeWrapperResolved) return entityRenderTypeWrapperHandle
            entityRenderTypeWrapperResolved = true
            entityRenderTypeWrapperHandle = try {
                val wrapperClass = Class.forName("net.irisshaders.iris.layer.OuterWrappedRenderType")
                val stateShardClass = Class.forName("net.irisshaders.iris.layer.EntityRenderStateShard")
                entityRenderStateShard = stateShardClass.getField("INSTANCE").get(null) as RenderStateShard
                MethodHandles.publicLookup().findStatic(
                    wrapperClass,
                    "wrapExactlyOnce",
                    MethodType.methodType(
                        wrapperClass,
                        String::class.java,
                        RenderType::class.java,
                        RenderStateShard::class.java
                    )
                )
            } catch (_: ClassNotFoundException) {
                null
            } catch (_: NoSuchFieldException) {
                null
            } catch (_: NoSuchMethodException) {
                null
            } catch (_: IllegalAccessException) {
                null
            } catch (t: Throwable) {
                LOGGER.warn("Unexpected failure resolving Iris RenderType wrapper hook", t)
                null
            }
            return entityRenderTypeWrapperHandle
        }
    }

    private fun resolveParticleRenderingMethods(): ParticleRenderingMethods? {
        if (particleRenderingMethodsResolved) return particleRenderingMethods
        synchronized(this) {
            if (particleRenderingMethodsResolved) return particleRenderingMethods
            particleRenderingMethodsResolved = true
            particleRenderingMethods = try {
                val irisClass = Class.forName("net.irisshaders.iris.Iris")
                val pipelineManagerClass = Class.forName("net.irisshaders.iris.pipeline.PipelineManager")
                val worldPipelineClass = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline")
                ParticleRenderingMethods(
                    irisClass.getMethod("getPipelineManager"),
                    pipelineManagerClass.getMethod("getPipeline"),
                    worldPipelineClass.getMethod("getParticleRenderingSettings")
                )
            } catch (_: ClassNotFoundException) {
                null
            } catch (_: NoSuchMethodException) {
                null
            } catch (t: Throwable) {
                LOGGER.warn("Unexpected failure resolving Iris particle rendering mode", t)
                null
            }
            return particleRenderingMethods
        }
    }

    private fun getParticleTranslucentShader(): ShaderInstance? {
        val method = resolveParticleTranslucentShaderMethod() ?: return null
        return try {
            method.invoke(null) as? ShaderInstance
        } catch (t: Throwable) {
            LOGGER.warn("Failed to get Iris translucent particle shader", t)
            null
        }
    }

    private fun resolveParticleTranslucentShaderMethod(): Method? {
        if (particleTranslucentShaderMethodResolved) return particleTranslucentShaderMethod
        synchronized(this) {
            if (particleTranslucentShaderMethodResolved) return particleTranslucentShaderMethod
            particleTranslucentShaderMethodResolved = true
            particleTranslucentShaderMethod = try {
                Class.forName("net.irisshaders.iris.pipeline.programs.ShaderAccess")
                    .getMethod("getParticleTranslucentShader")
            } catch (_: ClassNotFoundException) {
                null
            } catch (_: NoSuchMethodException) {
                null
            } catch (t: Throwable) {
                LOGGER.warn("Unexpected failure resolving Iris translucent particle shader", t)
                null
            }
            return particleTranslucentShaderMethod
        }
    }

    private data class ParticleRenderingMethods(
        val getPipelineManager: Method,
        val getPipeline: Method,
        val getParticleRenderingSettings: Method,
    )

}
