package cn.coostack.cooparticlesapi.compat

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderPass
import cn.coostack.cooparticlesapi.compat.iris.CooIrisRenderState
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.RenderPipelines
import org.joml.Matrix4f
import org.slf4j.LoggerFactory
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Field
import java.util.IdentityHashMap
import java.util.Optional

/**
 * 对接 26.3 Iris 的公开管线、实体包装与深度快照入口。
 *
 * 反射只在安装 Iris 后解析其类型；普通实体仍经原版 collector 提交。
 * 既有直接 OpenGL 绘制通过 [IrisOpenGlProgramBridge] 使用 Iris program，
 * 不能把旧版 ShaderInstance 的跳过标记套用到 RenderPearl 管线。
 */
object IrisCompat {
    /** 可选 Iris 集成共用的诊断日志入口。 */
    private val LOGGER = LoggerFactory.getLogger("CooParticlesAPI/IrisCompat")

    /** 反射缓存：26.3 的 RenderType 包装入口，第三个参数是 RenderingWrapper。 */
    @Volatile
    private var entityRenderTypeWrapperResolved = false

    @Volatile
    private var entityRenderTypeWrapperHandle: MethodHandle? = null

    @Volatile
    private var entityRenderStateShard: Any? = null

    /** 普通粒子命令执行时使用的 Iris 粒子阶段包装器。 */
    private var particleRenderStateShard: Any? = null

    /** 以管线对象身份记录已完成的公开 API 映射，避免 Iris 拒绝重复注册。 */
    private val assignedPipelines = IdentityHashMap<RenderPipeline, String>()

    @Volatile
    private var particleRenderingMethodsResolved = false

    @Volatile
    private var particleRenderingMethods: IrisParticleRenderingMethods? = null

    @Volatile
    private var shadowActiveFieldResolved = false

    @Volatile
    private var shadowActiveField: Field? = null

    @Volatile
    private var shadowPassStateFailureLogged = false

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

    /**
     * 将自定义管线接入已安装 Iris 的公开 program 映射；光影关闭时也提前注册。
     *
     * 仅适用于允许 Iris 替换原始片元程序的管线；依赖自定义片元的图层不能调用此方法。
     *
     * @param pipeline 原版生命周期拥有的共享管线
     * @param programName `IrisProgram` 的精确枚举名称
     * @return 未安装 Iris 或映射失败时返回 false，已有相同映射返回 true
     */
    internal fun assignPipeline(pipeline: RenderPipeline, programName: String): Boolean {
        if (!CooParticlesServices.PLATFORM.isModLoaded("iris")) return false
        synchronized(assignedPipelines) {
            assignedPipelines[pipeline]?.let { return it == programName }
            return runCatching {
                val apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi")
                val programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram")
                val program = programClass.getField(programName).get(null)
                val api = apiClass.getMethod("getInstance").invoke(null)
                apiClass.getMethod("assignPipeline", RenderPipeline::class.java, programClass)
                    .invoke(api, pipeline, program)
                assignedPipelines[pipeline] = programName
                true
            }.getOrElse { failure ->
                LOGGER.error("Failed to assign custom pipeline to Iris program $programName", failure)
                false
            }
        }
    }

    /**
     * 在 collector 执行普通粒子几何时设置 Iris 粒子阶段，不使用实体阶段代替。
     *
     * @param renderType 已经完成公开管线映射的粒子绘制类型
     * @return 光影关闭或包装不可用时返回原类型
     */
    internal fun wrapParticleRenderType(renderType: RenderType): RenderType {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) return renderType
        val handle = resolveEntityRenderTypeWrapper() ?: return renderType
        return runCatching {
            val shard = particleRenderStateShard ?: run {
                val phaseClass = Class.forName("net.irisshaders.iris.pipeline.WorldRenderingPhase")
                val phase = phaseClass.getField("PARTICLES").get(null)
                Class.forName("net.irisshaders.iris.layer.SetStateShard")
                    .getConstructor(String::class.java, phaseClass)
                    .newInstance("coo_particles", phase)
                    .also { particleRenderStateShard = it }
            }
            handle.invokeWithArguments("iris:particles", renderType, shard) as RenderType
        }.getOrElse { failure ->
            LOGGER.error("Failed to wrap custom particle RenderType for Iris", failure)
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
     * 可在回调内临时切换 program，但只能写主颜色附件；桥接退出后恢复入口 GL 状态。
     *
     * @param pass 当前 Iris 粒子分流 pass
     * @param view 原版粒子顶点使用的 model-view 矩阵
     * @param projection 当前世界投影矩阵
     * @param fallback Iris 使用细分程序时执行的自有程序回退，仍位于正确粒子 framebuffer 中
     * @param draw 在 Iris 粒子 framebuffer 中提交绘制的回调
     * @return 实际执行绘制或回退时返回 true；缺失目标程序时返回 false
     */
    @JvmStatic
    fun runWithParticleShader(
        pass: CParticleRenderPass,
        view: Matrix4f,
        projection: Matrix4f,
        fallback: (() -> Unit)? = null,
        draw: () -> Unit,
    ): Boolean {
        if (pass == CParticleRenderPass.NONE) return false
        val translucent = pass == CParticleRenderPass.TRANSLUCENT
        return IrisOpenGlProgramBridge.draw(
            if (translucent) "PARTICLES_TRANS" else "PARTICLES",
            if (translucent) RenderPipelines.TRANSLUCENT_PARTICLE else RenderPipelines.OPAQUE_PARTICLE,
            view,
            projection,
            fallback = fallback,
            block = draw,
        )
    }

    /**
     * 在 Iris entity framebuffer 中执行 RenderEntity world pass，失败时保留后续回放机会。
     *
     * @param view 当前视图矩阵
     * @param projection 当前投影矩阵
     * @param shaderKind 当前实体的混合与裁剪入口
     * @param fallback Iris 细分输入不兼容时保留自有片元的回调
     * @param draw 使用 Iris program 的同步绘制回调
     * @return 实际执行任一绘制回调时返回 true
     */
    internal fun runWithRenderEntityShader(
        view: Matrix4f,
        projection: Matrix4f,
        shaderKind: IrisEntityShaderKind = IrisEntityShaderKind.TRANSLUCENT,
        fallback: (() -> Unit)? = null,
        draw: () -> Unit,
    ): Boolean {
        if (!CooParticlesAPIClient.checkIrisShaderPackUsed()) {
            draw()
            return true
        }

        resolveEntityRenderTypeWrapper()
        val stateShard = entityRenderStateShard
        val wrapperClass = stateShard?.javaClass
        wrapperClass?.getMethod("setup")?.invoke(stateShard)
        try {
            return IrisOpenGlProgramBridge.draw(
                when (shaderKind) {
                    IrisEntityShaderKind.TRANSLUCENT -> "ENTITIES_TRANSLUCENT"
                    IrisEntityShaderKind.SOLID -> "ENTITIES_SOLID"
                    IrisEntityShaderKind.CUTOUT -> "ENTITIES_CUTOUT"
                },
                when (shaderKind) {
                    IrisEntityShaderKind.TRANSLUCENT -> RenderPipelines.ENTITY_TRANSLUCENT
                    IrisEntityShaderKind.SOLID -> RenderPipelines.ENTITY_SOLID
                    IrisEntityShaderKind.CUTOUT -> RenderPipelines.ENTITY_CUTOUT
                },
                view,
                projection,
                fallback = fallback,
                block = draw,
            )
        } finally {
            wrapperClass?.getMethod("clear")?.invoke(stateShard)
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
                val renderingWrapperClass = Class.forName("net.irisshaders.iris.layer.RenderingWrapper")
                entityRenderStateShard = stateShardClass.getField("INSTANCE").get(null)
                MethodHandles.publicLookup().findStatic(
                    wrapperClass,
                    "wrapExactlyOnce",
                    MethodType.methodType(
                        wrapperClass,
                        String::class.java,
                        RenderType::class.java,
                        renderingWrapperClass
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
