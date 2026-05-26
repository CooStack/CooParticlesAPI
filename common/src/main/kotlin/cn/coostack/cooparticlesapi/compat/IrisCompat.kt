package cn.coostack.cooparticlesapi.compat

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

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
}
