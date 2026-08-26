package cn.coostack.cooparticlesapi.compat

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderPass
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.ShaderInstance
import org.joml.Matrix4f
import org.lwjgl.opengl.GL20.GL_ACTIVE_UNIFORMS
import org.lwjgl.opengl.GL20.GL_SAMPLER_2D
import org.lwjgl.opengl.GL20.glGetActiveUniform
import org.lwjgl.opengl.GL20.glGetProgrami
import org.lwjgl.system.MemoryStack
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

    @Volatile
    private var terrainDepthMethodsResolved = false

    @Volatile
    private var terrainDepthMethods: IrisTerrainDepthMethods? = null

    @Volatile
    private var finalPassMethodsResolved = false

    @Volatile
    private var finalPassMethods: IrisFinalPassMethods? = null

    @Volatile
    private var compositeMethodsResolved = false

    @Volatile
    private var compositeMethods: IrisCompositeMethods? = null

    private var finalPassColorProgram: Any? = null
    private var finalPassColorAttachment = 0

    private var compositeColorProgram: Any? = null
    private var compositeColorAttachment = 0

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
        val methods = resolveTerrainDepthMethods() ?: return null
        return try {
            val manager = methods.getPipelineManager.invoke(null)
            val pipeline = (methods.getPipeline.invoke(manager) as Optional<*>).orElse(null) ?: return null
            val renderTargets = methods.renderTargetsField.get(pipeline)
            val depthTexture = methods.getDepthTextureNoTranslucents.invoke(renderTargets) ?: return null
            val textureId = methods.getDepthTextureId.invoke(depthTexture) as Int
            if (textureId <= 0) return null
            IrisTerrainDepthTexture(
                textureId,
                methods.getCurrentWidth.invoke(renderTargets) as Int,
                methods.getCurrentHeight.invoke(renderTargets) as Int,
            )
        } catch (t: Throwable) {
            LOGGER.error("Failed to resolve Iris terrain depth texture", t)
            null
        }
    }

    /** 返回 Iris 当前场景使用的深度纹理，供 final pass 后的屏幕合成读取。 */
    internal fun currentSceneDepthTexture(): IrisTerrainDepthTexture? {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return null
        val methods = resolveTerrainDepthMethods() ?: return null
        return try {
            val manager = methods.getPipelineManager.invoke(null)
            val pipeline = (methods.getPipeline.invoke(manager) as Optional<*>).orElse(null) ?: return null
            val renderTargets = methods.renderTargetsField.get(pipeline)
            val textureId = methods.getDepthTexture.invoke(renderTargets) as Int
            if (textureId <= 0) return null
            IrisTerrainDepthTexture(
                textureId,
                methods.getCurrentWidth.invoke(renderTargets) as Int,
                methods.getCurrentHeight.invoke(renderTargets) as Int,
            )
        } catch (t: Throwable) {
            LOGGER.error("Failed to resolve Iris scene depth texture", t)
            null
        }
    }

    /** 返回 Iris 在 hand 绘制前保存的深度纹理，用于排除手部改变的屏幕像素。 */
    internal fun currentSceneDepthNoHandTexture(): IrisTerrainDepthTexture? {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return null
        val methods = resolveTerrainDepthMethods() ?: return null
        return try {
            val manager = methods.getPipelineManager.invoke(null)
            val pipeline = (methods.getPipeline.invoke(manager) as Optional<*>).orElse(null) ?: return null
            val renderTargets = methods.renderTargetsField.get(pipeline)
            val getDepthTextureNoHand = methods.getDepthTextureNoHand ?: return null
            val depthTexture = getDepthTextureNoHand.invoke(renderTargets) ?: return null
            val textureId = methods.getDepthTextureId.invoke(depthTexture) as Int
            if (textureId <= 0) return null
            IrisTerrainDepthTexture(
                textureId,
                methods.getCurrentWidth.invoke(renderTargets) as Int,
                methods.getCurrentHeight.invoke(renderTargets) as Int,
            )
        } catch (t: Throwable) {
            LOGGER.error("Failed to resolve Iris no-hand depth texture", t)
            null
        }
    }

    /** 返回 Iris final pass 当前读取的场景颜色纹理。 */
    internal fun currentFinalPassColorTexture(): IrisFinalPassColorTexture? {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return null
        val methods = resolveFinalPassMethods() ?: return null
        return try {
            val state = resolveIrisFinalColorState(methods) ?: return null
            val textureId = selectIrisFinalPassColorTextureId(
                hasFinalPass = state.finalPass != null,
                finalPassReadsFromAlt = state.finalPassReadsFromAlt,
                baselineTextureId = state.baselineTextureId,
                mainTextureId = state.mainTextureId,
                altTextureId = state.altTextureId,
            )
            if (textureId <= 0) return null
            IrisFinalPassColorTexture(
                textureId,
                state.width,
                state.height,
            )
        } catch (t: Throwable) {
            LOGGER.error("Failed to resolve Iris final pass color texture", t)
            null
        }
    }

    /** 返回 Iris 最终 composite 链开始前读取的场景颜色纹理。 */
    internal fun currentCompositeInputColorTexture(): IrisFinalPassColorTexture? {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return null
        val methods = resolveCompositeMethods() ?: return null
        return try {
            val manager = methods.getPipelineManager.invoke(null)
            val pipeline = (methods.getPipeline.invoke(manager) as Optional<*>).orElse(null) ?: return null
            val renderTargets = methods.renderTargetsField.get(pipeline)
            val compositeRenderer = methods.compositeRendererField.get(pipeline)
            @Suppress("UNCHECKED_CAST")
            val passes = methods.compositePassesField.get(compositeRenderer) as List<Any>
            val firstFragmentPass = passes.firstOrNull { pass ->
                methods.compositeProgramField.get(pass) != null
            }
            if (firstFragmentPass == null) return null
            val compositeProgram = methods.compositeProgramField.get(firstFragmentPass)
            val attachment = resolveCompositeSceneColorAttachment(
                compositeProgram,
                methods,
            ) ?: return null
            val renderTarget = methods.getRenderTarget.invoke(renderTargets, attachment)
            val mainTextureId = methods.getMainTexture.invoke(renderTarget) as Int
            val altTextureId = methods.getAltTexture.invoke(renderTarget) as Int
            @Suppress("UNCHECKED_CAST")
            val firstFragmentPassReadsFromAlt =
                (methods.compositeStageReadsFromAltField.get(firstFragmentPass) as Set<Int>).contains(attachment)
            val textureId = selectIrisCompositeInputColorTextureId(
                firstFragmentPassReadsFromAlt = firstFragmentPassReadsFromAlt,
                mainTextureId = mainTextureId,
                altTextureId = altTextureId,
            )
            if (textureId <= 0) return null
            IrisFinalPassColorTexture(
                textureId,
                methods.getCurrentWidth.invoke(renderTargets) as Int,
                methods.getCurrentHeight.invoke(renderTargets) as Int,
            )
        } catch (t: Throwable) {
            LOGGER.error("Failed to resolve Iris composite input color texture", t)
            null
        }
    }

    private fun resolveCompositeSceneColorAttachment(
        program: Any,
        methods: IrisCompositeMethods,
    ): Int? {
        if (compositeColorProgram === program) {
            return compositeColorAttachment.takeIf { it >= 0 }
        }
        val activeUniformNames = activeSampler2DUniformNames(program, methods.getProgramId)
        val attachment = selectIrisCompositeSceneColorAttachment(activeUniformNames)
        compositeColorProgram = program
        compositeColorAttachment = attachment ?: -1
        LOGGER.info(
            "Resolved Iris composite scene color attachment {} from active sampler2D uniforms {}",
            attachment?.let { "colortex$it" } ?: "ambiguous",
            activeUniformNames.filter { irisFinalPassColorAttachment(it) != null },
        )
        return attachment
    }

    private fun resolveIrisFinalColorState(methods: IrisFinalPassMethods): IrisFinalColorState? {
        val manager = methods.getPipelineManager.invoke(null)
        val pipeline = (methods.getPipeline.invoke(manager) as Optional<*>).orElse(null) ?: return null
        val finalPassRenderer = methods.finalPassRendererField.get(pipeline)
        val finalPass = methods.finalPassField.get(finalPassRenderer)
        val renderTargets = methods.renderTargetsField.get(pipeline)
        val attachment = if (finalPass == null) {
            0
        } else {
            val program = methods.programField.get(finalPass)
            resolveFinalPassColorAttachment(program, methods) ?: return null
        }
        val renderTarget = methods.getRenderTarget.invoke(renderTargets, attachment)
        val baseline = methods.baselineField.get(finalPassRenderer)
        val finalPassReadsFromAlt = finalPass?.let { pass ->
            @Suppress("UNCHECKED_CAST")
            (methods.stageReadsFromAltField.get(pass) as Set<Int>).contains(attachment)
        } ?: false
        return IrisFinalColorState(
            pipeline = pipeline,
            finalPass = finalPass,
            attachment = attachment,
            finalPassReadsFromAlt = finalPassReadsFromAlt,
            baselineTextureId = methods.getColorAttachment.invoke(baseline, 0) as Int,
            mainTextureId = methods.getMainTexture.invoke(renderTarget) as Int,
            altTextureId = methods.getAltTexture.invoke(renderTarget) as Int,
            width = methods.getCurrentWidth.invoke(renderTargets) as Int,
            height = methods.getCurrentHeight.invoke(renderTargets) as Int,
        )
    }

    private fun resolveFinalPassColorAttachment(program: Any, methods: IrisFinalPassMethods): Int? {
        if (finalPassColorProgram === program) return finalPassColorAttachment.takeIf { it >= 0 }

        val activeUniformNames = activeSampler2DUniformNames(program, methods.getProgramId)
        val attachment = selectIrisFinalPassColorAttachment(activeUniformNames)
        finalPassColorProgram = program
        finalPassColorAttachment = attachment ?: -1
        LOGGER.info(
            "Resolved Iris final scene color attachment {} from active sampler2D uniforms {}",
            attachment?.let { "colortex$it" } ?: "ambiguous",
            activeUniformNames.filter { irisFinalPassColorAttachment(it) != null },
        )
        return attachment
    }

    private fun activeSampler2DUniformNames(program: Any, getProgramId: Method): List<String> {
        val programId = getProgramId.invoke(program) as Int
        return MemoryStack.stackPush().use { stack ->
            val size = stack.mallocInt(1)
            val type = stack.mallocInt(1)
            buildList {
                repeat(glGetProgrami(programId, GL_ACTIVE_UNIFORMS).coerceAtLeast(0)) { index ->
                    size.clear()
                    type.clear()
                    val uniformName = glGetActiveUniform(programId, index, size, type)
                    if (type[0] == GL_SAMPLER_2D) {
                        add(uniformName)
                    }
                }
            }
        }
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

    /** 解析 Iris 深度纹理访问所需的反射成员。 */
    private fun resolveTerrainDepthMethods(): IrisTerrainDepthMethods? {
        if (terrainDepthMethodsResolved) return terrainDepthMethods
        synchronized(this) {
            if (terrainDepthMethodsResolved) return terrainDepthMethods
            terrainDepthMethodsResolved = true
            terrainDepthMethods = try {
                val particleMethods = resolveParticleRenderingMethods()
                if (particleMethods == null) {
                    null
                } else {
                    val pipelineClass = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline")
                    val renderTargetsField = pipelineClass.getDeclaredField("renderTargets")
                    check(renderTargetsField.trySetAccessible()) { "Iris renderTargets field is not accessible" }
                    val renderTargetsClass = Class.forName("net.irisshaders.iris.targets.RenderTargets")
                    val getDepthTexture = renderTargetsClass.getMethod("getDepthTexture")
                    val getDepthTextureNoTranslucents = renderTargetsClass.getMethod("getDepthTextureNoTranslucents")
                    val getDepthTextureNoHand = try {
                        renderTargetsClass.getMethod("getDepthTextureNoHand")
                    } catch (_: NoSuchMethodException) {
                        null
                    }
                    IrisTerrainDepthMethods(
                        particleMethods.getPipelineManager,
                        particleMethods.getPipeline,
                        renderTargetsField,
                        getDepthTexture,
                        getDepthTextureNoTranslucents,
                        getDepthTextureNoHand,
                        getDepthTextureNoTranslucents.returnType.getMethod("getTextureId"),
                        renderTargetsClass.getMethod("getCurrentWidth"),
                        renderTargetsClass.getMethod("getCurrentHeight"),
                    )
                }
            } catch (_: ClassNotFoundException) {
                null
            } catch (_: NoSuchFieldException) {
                null
            } catch (_: NoSuchMethodException) {
                null
            } catch (t: Throwable) {
                LOGGER.warn("Unexpected failure resolving Iris terrain depth access", t)
                null
            }
            return terrainDepthMethods
        }
    }

    /** 解析 Iris final pass 当前输入颜色纹理所需的反射成员。 */
    private fun resolveFinalPassMethods(): IrisFinalPassMethods? {
        if (finalPassMethodsResolved) return finalPassMethods
        return synchronized(this) {
            if (finalPassMethodsResolved) return@synchronized finalPassMethods
            finalPassMethodsResolved = true
            finalPassMethods = try {
                val particleMethods = resolveParticleRenderingMethods()
                if (particleMethods == null) {
                    null
                } else {
                    val pipelineClass = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline")
                    val renderTargetsField = pipelineClass.getDeclaredField("renderTargets")
                    check(renderTargetsField.trySetAccessible()) { "Iris renderTargets field is not accessible" }
                    val finalPassRendererField = pipelineClass.getDeclaredField("finalPassRenderer")
                    check(finalPassRendererField.trySetAccessible()) {
                        "Iris finalPassRenderer field is not accessible"
                    }
                    val finalPassRendererClass = Class.forName("net.irisshaders.iris.pipeline.FinalPassRenderer")
                    val finalPassField = finalPassRendererClass.getDeclaredField("finalPass")
                    check(finalPassField.trySetAccessible()) { "Iris final pass field is not accessible" }
                    val programField = finalPassField.type.getDeclaredField("program")
                    check(programField.trySetAccessible()) { "Iris final pass program field is not accessible" }
                    val programClass = Class.forName("net.irisshaders.iris.gl.program.Program")
                    val stageReadsFromAltField = finalPassField.type.getDeclaredField("stageReadsFromAlt")
                    check(stageReadsFromAltField.trySetAccessible()) {
                        "Iris final pass stageReadsFromAlt field is not accessible"
                    }
                    val baselineField = finalPassRendererClass.getDeclaredField("baseline")
                    check(baselineField.trySetAccessible()) { "Iris final pass baseline field is not accessible" }
                    val glFramebufferClass = Class.forName("net.irisshaders.iris.gl.framebuffer.GlFramebuffer")
                    val renderTargetsClass = Class.forName("net.irisshaders.iris.targets.RenderTargets")
                    val renderTargetClass = Class.forName("net.irisshaders.iris.targets.RenderTarget")
                    IrisFinalPassMethods(
                        particleMethods.getPipelineManager,
                        particleMethods.getPipeline,
                        renderTargetsField,
                        finalPassRendererField,
                        finalPassField,
                        programField,
                        programClass.getMethod("getProgramId"),
                        stageReadsFromAltField,
                        baselineField,
                        glFramebufferClass.getMethod("getColorAttachment", Int::class.javaPrimitiveType),
                        renderTargetsClass.getMethod("get", Int::class.javaPrimitiveType),
                        renderTargetClass.getMethod("getMainTexture"),
                        renderTargetClass.getMethod("getAltTexture"),
                        renderTargetsClass.getMethod("getCurrentWidth"),
                        renderTargetsClass.getMethod("getCurrentHeight"),
                    )
                }
            } catch (_: ClassNotFoundException) {
                null
            } catch (_: NoSuchFieldException) {
                null
            } catch (_: NoSuchMethodException) {
                null
            } catch (t: Throwable) {
                LOGGER.warn("Unexpected failure resolving Iris final pass color access", t)
                null
            }
            finalPassMethods
        }
    }

    /** 解析 Iris composite 链当前输入颜色纹理所需的反射成员。 */
    private fun resolveCompositeMethods(): IrisCompositeMethods? {
        if (compositeMethodsResolved) return compositeMethods
        return synchronized(this) {
            if (compositeMethodsResolved) return@synchronized compositeMethods
            compositeMethodsResolved = true
            compositeMethods = try {
                val particleMethods = resolveParticleRenderingMethods()
                if (particleMethods == null) {
                    null
                } else {
                    val pipelineClass = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline")
                    val renderTargetsField = pipelineClass.getDeclaredField("renderTargets")
                    check(renderTargetsField.trySetAccessible()) { "Iris renderTargets field is not accessible" }
                    val compositeRendererField = pipelineClass.getDeclaredField("compositeRenderer")
                    check(compositeRendererField.trySetAccessible()) {
                        "Iris compositeRenderer field is not accessible"
                    }
                    val compositeRendererClass = Class.forName("net.irisshaders.iris.pipeline.CompositeRenderer")
                    val compositePassesField = compositeRendererClass.getDeclaredField("passes")
                    check(compositePassesField.trySetAccessible()) { "Iris composite passes field is not accessible" }
                    val compositePassClass = Class.forName("net.irisshaders.iris.pipeline.CompositeRenderer\$Pass")
                    val compositeProgramField = compositePassClass.getDeclaredField("program")
                    check(compositeProgramField.trySetAccessible()) {
                        "Iris composite pass program field is not accessible"
                    }
                    val compositeStageReadsFromAltField = compositePassClass.getDeclaredField("stageReadsFromAlt")
                    check(compositeStageReadsFromAltField.trySetAccessible()) {
                        "Iris composite pass stageReadsFromAlt field is not accessible"
                    }
                    val programClass = Class.forName("net.irisshaders.iris.gl.program.Program")
                    val renderTargetsClass = Class.forName("net.irisshaders.iris.targets.RenderTargets")
                    val renderTargetClass = Class.forName("net.irisshaders.iris.targets.RenderTarget")
                    IrisCompositeMethods(
                        particleMethods.getPipelineManager,
                        particleMethods.getPipeline,
                        renderTargetsField,
                        compositeRendererField,
                        compositePassesField,
                        compositeProgramField,
                        compositeStageReadsFromAltField,
                        programClass.getMethod("getProgramId"),
                        renderTargetsClass.getMethod("get", Int::class.javaPrimitiveType),
                        renderTargetClass.getMethod("getMainTexture"),
                        renderTargetClass.getMethod("getAltTexture"),
                        renderTargetsClass.getMethod("getCurrentWidth"),
                        renderTargetsClass.getMethod("getCurrentHeight"),
                    )
                }
            } catch (_: ClassNotFoundException) {
                null
            } catch (_: NoSuchFieldException) {
                null
            } catch (_: NoSuchMethodException) {
                null
            } catch (t: Throwable) {
                LOGGER.warn("Unexpected failure resolving Iris composite color access", t)
                null
            }
            compositeMethods
        }
    }

}

private data class IrisFinalColorState(
    val pipeline: Any,
    val finalPass: Any?,
    val attachment: Int,
    val finalPassReadsFromAlt: Boolean,
    val baselineTextureId: Int,
    val mainTextureId: Int,
    val altTextureId: Int,
    val width: Int,
    val height: Int,
)
