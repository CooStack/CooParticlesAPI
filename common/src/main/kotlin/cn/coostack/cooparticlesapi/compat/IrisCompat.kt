package cn.coostack.cooparticlesapi.compat

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderPass
import cn.coostack.cooparticlesapi.compat.iris.CooIrisRenderState
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import org.joml.Matrix4f
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Field
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
    private var particleRenderingMethods: IrisParticleRenderingMethods? = null

    @Volatile
    private var particleTranslucentShaderMethodResolved = false

    @Volatile
    private var particleTranslucentShaderMethod: Method? = null

    @Volatile
    private var shadowActiveFieldResolved = false

    @Volatile
    private var shadowActiveField: Field? = null

    @Volatile
    private var shadowPassStateFailureLogged = false

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

    /** 当前是否正在执行 Iris shadow terrain pass。 */
    @JvmStatic
    fun isShadowPassActive(): Boolean {
        return shadowPassState() == IrisShadowPassState.ACTIVE
    }

    /** 返回是否应跳过当前可能属于 Iris 阴影 pass 的地形深度捕获。 */
    @JvmStatic
    fun shouldSkipShadowPass(): Boolean {
        return when (shadowPassState()) {
            IrisShadowPassState.ACTIVE,
            IrisShadowPassState.UNKNOWN -> true
            IrisShadowPassState.INACTIVE -> false
        }
    }

    internal fun shadowPassState(): IrisShadowPassState {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return IrisShadowPassState.INACTIVE
        val field = resolveShadowActiveField()
        if (field == null) {
            logShadowPassStateFailure(null)
            return IrisShadowPassState.UNKNOWN
        }
        return try {
            if (field.getBoolean(null)) IrisShadowPassState.ACTIVE else IrisShadowPassState.INACTIVE
        } catch (t: Throwable) {
            logShadowPassStateFailure(t)
            IrisShadowPassState.UNKNOWN
        }
    }

    private fun logShadowPassStateFailure(error: Throwable?) {
        if (shadowPassStateFailureLogged) return
        synchronized(this) {
            if (shadowPassStateFailureLogged) return
            shadowPassStateFailureLogged = true
            if (error == null) {
                LOGGER.error("Iris shadow pass state is unavailable; terrain overlays will use the vanilla fallback")
            } else {
                LOGGER.error("Failed to query Iris shadow pass state; terrain overlays will use the vanilla fallback", error)
            }
        }
    }

    /** 返回 Iris 在半透明阶段开始前保存的深度纹理，避免玻璃等材质完全裁掉后绘制特效。 */
    internal fun currentTerrainDepthTexture(): IrisTerrainDepthTexture? {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return null
        val state = CooIrisRenderState.snapshot()
        return IrisTerrainDepthTexture(
            state.terrainDepthTextureId(),
            state.terrainDepthWidth(),
            state.terrainDepthHeight(),
        ).takeIf { it.textureId > 0 }
    }

    /** 返回 Iris 当前场景使用的深度纹理，供 final pass 后的屏幕合成读取。 */
    internal fun currentSceneDepthTexture(): IrisTerrainDepthTexture? {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return null
        val state = CooIrisRenderState.snapshot()
        return IrisTerrainDepthTexture(
            state.sceneDepthTextureId(),
            state.sceneDepthWidth(),
            state.sceneDepthHeight(),
        ).takeIf { it.textureId > 0 }
    }

    /** 返回 Iris 在 hand 绘制前保存的深度纹理，用于排除手部改变的屏幕像素。 */
    internal fun currentSceneDepthNoHandTexture(): IrisTerrainDepthTexture? {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return null
        val state = CooIrisRenderState.snapshot()
        return IrisTerrainDepthTexture(
            state.noHandDepthTextureId(),
            state.noHandDepthWidth(),
            state.noHandDepthHeight(),
        ).takeIf { it.textureId > 0 }
    }

    /** 返回 Iris final pass 当前读取的场景颜色纹理。 */
    internal fun currentFinalPassColorTexture(): IrisFinalPassColorTexture? {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return null
        val state = CooIrisRenderState.snapshot()
        return IrisFinalPassColorTexture(
            state.finalColorTextureId(),
            state.finalColorWidth(),
            state.finalColorHeight(),
        ).takeIf { it.textureId > 0 }
    }

    /** 返回 Iris 最终 composite 链开始前读取的场景颜色纹理。 */
    internal fun currentCompositeInputColorTexture(): IrisFinalPassColorTexture? {
        return currentFinalPassColorTexture()
    }

    /**
     * 使用 Iris 当前粒子 program 绘制已经展开为原版 PARTICLE 格式的 GPU 顶点。
     *
     * 示例：常规层通过此入口执行 `drawExpanded(...)`；需要完整纹理 Alpha 的有界 Screen 层
     * 可在回调内临时切换 program，但只能写主颜色附件，并且返回前必须恢复 Iris program。
     *
     * @param pass 当前 Iris 粒子分流 pass
     * @param view 原版粒子顶点使用的 model-view 矩阵
     * @param projection 当前世界投影矩阵
     * @param draw 在 Iris 粒子 framebuffer 中提交绘制的回调
     */
    @JvmStatic
    fun runWithParticleShader(
        pass: CParticleRenderPass,
        view: Matrix4f,
        projection: Matrix4f,
        draw: () -> Unit,
    ) {
        val particleShader = when (pass) {
            CParticleRenderPass.TRANSLUCENT -> getParticleTranslucentShader()
            CParticleRenderPass.ALL, CParticleRenderPass.OPAQUE -> GameRenderer.getParticleShader()
            CParticleRenderPass.NONE -> null
        }
        if (particleShader == null) return

        particleShader.setDefaultUniforms(
            VertexFormat.Mode.TRIANGLES,
            view,
            projection,
            Minecraft.getInstance().window,
        )
        particleShader.apply()
        try {
            draw()
        } finally {
            particleShader.clear()
        }
    }

    /** 在 Iris 的半透明 entity framebuffer 中执行 RenderEntity world pass。 */
    internal fun runWithRenderEntityShader(
        view: Matrix4f,
        projection: Matrix4f,
        shaderKind: IrisEntityShaderKind = IrisEntityShaderKind.TRANSLUCENT,
        draw: () -> Unit,
    ) {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) {
            draw()
            return
        }

        val entityShader = when (shaderKind) {
            IrisEntityShaderKind.TRANSLUCENT -> GameRenderer.getRendertypeEntityTranslucentShader()
            IrisEntityShaderKind.SOLID -> GameRenderer.getRendertypeEntitySolidShader()
            IrisEntityShaderKind.CUTOUT -> GameRenderer.getRendertypeEntityCutoutNoCullShader()
        }
        if (entityShader == null) {
            draw()
            return
        }

        resolveEntityRenderTypeWrapper()
        val stateShard = entityRenderStateShard
        stateShard?.setupRenderState()
        try {
            entityShader.setDefaultUniforms(
                VertexFormat.Mode.TRIANGLES,
                view,
                projection,
                Minecraft.getInstance().window,
            )
            entityShader.apply()
            try {
                draw()
            } finally {
                entityShader.clear()
            }
        } finally {
            stateShard?.clearRenderState()
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

    private fun resolveParticleRenderingMethods(): IrisParticleRenderingMethods? {
        if (particleRenderingMethodsResolved) return particleRenderingMethods
        synchronized(this) {
            if (particleRenderingMethodsResolved) return particleRenderingMethods
            particleRenderingMethodsResolved = true
            particleRenderingMethods = try {
                val irisClass = Class.forName("net.irisshaders.iris.Iris")
                val pipelineManagerClass = Class.forName("net.irisshaders.iris.pipeline.PipelineManager")
                val worldPipelineClass = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPipeline")
                IrisParticleRenderingMethods(
                    irisClass.getMethod("getPipelineManager"),
                    pipelineManagerClass.getMethod("getPipeline"),
                    worldPipelineClass.getMethod("getParticleRenderingSettings"),
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
        val method = resolveParticleTranslucentShaderMethod() ?: return GameRenderer.getParticleShader()
        return try {
            method.invoke(null) as? ShaderInstance ?: GameRenderer.getParticleShader()
        } catch (t: Throwable) {
            LOGGER.warn("Failed to get Iris translucent particle shader", t)
            GameRenderer.getParticleShader()
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

    private fun resolveShadowActiveField(): Field? {
        if (shadowActiveFieldResolved) return shadowActiveField
        synchronized(this) {
            if (shadowActiveFieldResolved) return shadowActiveField
            shadowActiveFieldResolved = true
            shadowActiveField = try {
                Class.forName("net.irisshaders.iris.shadows.ShadowRenderer").getField("ACTIVE")
            } catch (_: ClassNotFoundException) {
                null
            } catch (_: NoSuchFieldException) {
                null
            } catch (t: Throwable) {
                LOGGER.warn("Unexpected failure resolving Iris shadow pass state", t)
                null
            }
            return shadowActiveField
        }
    }

}
