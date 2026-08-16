package cn.coostack.cooparticlesapi.coofx.render.gpu

import cn.coostack.cooparticlesapi.coofx.render.compiled.CooFxCompiledRenderPackage
import net.minecraft.resources.ResourceLocation

/**
 * CooFX GPU generation 的常驻状态。
 *
 * [ACTIVE] 表示当前资源 ID 的新 lease 都会取得该 generation；[RETIRED] 表示它已被新 generation
 * 替换，但仍等待已有 lease 结束；[RELEASED] 表示上传器拥有的资源已经释放，不能再次取得或使用。
 * 状态只在渲染线程推进，不代表 CPU prepare 阶段。
 */
enum class CooFxGpuGenerationState {
    /** 当前可取得 lease 的 generation。 */
    ACTIVE,

    /** 已被替换但可能仍被帧 lease 引用的 generation。 */
    RETIRED,

    /** 已完成幂等释放的 generation。 */
    RELEASED
}

data class CooFxGpuPackageKey(
    val resourceId: ResourceLocation,
    val contentDigest: String,
    val backendCapabilitySignature: String
) {
    init {
        require(contentDigest.matches(Regex("[0-9a-f]{64}"))) {
            "Content digest must be a lowercase SHA-256 value"
        }
        require(backendCapabilitySignature.isNotBlank()) { "Backend capability signature must not be blank" }
    }
}

/**
 * 已由具体 backend 创建的 CooFX GPU 常驻包。
 *
 * 实现可以拥有 VAO、VBO、EBO、实例缓冲、纹理引用和 program binding，但必须把这些细节封装在
 * client 渲染路径中。[release] 只允许在渲染线程调用，必须可重复调用且不得释放其他 generation。
 */
interface CooFxGpuPackage {
    val key: CooFxGpuPackageKey
    val generation: Long
    fun release()
}

/**
 * CooFX 编译包到 GPU 常驻包的 client backend 边界。
 *
 * 调用方已保证在渲染线程执行。实现必须使用现有 Coo shader、Pipeline 和状态管理路径；上传中途失败时，
 * 实现负责释放本次调用已创建的资源后再抛出异常，不能留下半成品 handle。
 */
fun interface CooFxGpuPackageUploader {
    fun upload(
        compiledPackage: CooFxCompiledRenderPackage,
        key: CooFxGpuPackageKey,
        generation: Long
    ): CooFxGpuPackage
}

/**
 * 渲染线程断言边界。
 *
 * 平台 client adapter 注入真实 RenderSystem 断言；纯单元测试可以注入记录调用的实现。CPU prepare
 * 不依赖该接口，也不得通过该接口间接调度 GL。
 */
fun interface CooFxRenderThreadGuard {
    fun assertRenderThread()
}

class CooFxGpuGenerationLease internal constructor(
    val gpuPackage: CooFxGpuPackage,
    private val closeAction: () -> Unit
) : AutoCloseable {
    val generation: Long
        get() = gpuPackage.generation

    private var closed = false

    override fun close() {
        if (closed) {
            return
        }
        closed = true
        closeAction()
    }
}

class CooFxGpuPackageRegistry(
    private val renderThreadGuard: CooFxRenderThreadGuard,
    private val uploader: CooFxGpuPackageUploader
) {
    private val monitor = Any()
    private val active = mutableMapOf<ResourceLocation, GenerationEntry>()
    private val retired = mutableSetOf<GenerationEntry>()
    private var nextGeneration = 1L

    fun uploadAndReplace(
        compiledPackage: CooFxCompiledRenderPackage,
        backendCapabilitySignature: String
    ): Result<Long> {
        renderThreadGuard.assertRenderThread()
        val key = CooFxGpuPackageKey(
            resourceId = compiledPackage.id,
            contentDigest = compiledPackage.contentDigest,
            backendCapabilitySignature = backendCapabilitySignature
        )
        return runCatching {
            synchronized(monitor) {
                val generation = nextGeneration++
                val uploaded = uploader.upload(compiledPackage, key, generation)
                if (uploaded.key != key || uploaded.generation != generation) {
                    uploaded.release()
                    error("Uploader returned a package with mismatched key or generation")
                }
                val replacement = GenerationEntry(uploaded)
                val previous = active.put(compiledPackage.id, replacement)
                if (previous != null) {
                    previous.state = CooFxGpuGenerationState.RETIRED
                    retired += previous
                    releaseIfUnused(previous)
                }
                generation
            }
        }
    }

    fun acquire(resourceId: ResourceLocation): CooFxGpuGenerationLease? {
        renderThreadGuard.assertRenderThread()
        return synchronized(monitor) {
            val entry = active[resourceId] ?: return@synchronized null
            check(entry.state == CooFxGpuGenerationState.ACTIVE) { "Only active generation can be leased" }
            entry.leaseCount++
            CooFxGpuGenerationLease(entry.gpuPackage) {
                releaseLease(entry)
            }
        }
    }

    fun activeGeneration(resourceId: ResourceLocation): Long? = synchronized(monitor) {
        active[resourceId]?.gpuPackage?.generation
    }

    fun disposeAll() {
        renderThreadGuard.assertRenderThread()
        synchronized(monitor) {
            val entries = active.values.toSet() + retired
            check(entries.none { it.leaseCount > 0 }) { "Cannot dispose GPU packages while leases are active" }
            active.clear()
            retired.clear()
            entries.forEach { entry ->
                entry.state = CooFxGpuGenerationState.RETIRED
                releaseIfUnused(entry)
            }
        }
    }

    internal fun stateOf(generation: Long): CooFxGpuGenerationState? = synchronized(monitor) {
        (active.values.asSequence() + retired.asSequence())
            .firstOrNull { it.gpuPackage.generation == generation }
            ?.state
    }

    private fun releaseLease(entry: GenerationEntry) {
        renderThreadGuard.assertRenderThread()
        synchronized(monitor) {
            check(entry.leaseCount > 0) { "GPU generation lease count underflow" }
            entry.leaseCount--
            releaseIfUnused(entry)
        }
    }

    private fun releaseIfUnused(entry: GenerationEntry) {
        if (entry.state != CooFxGpuGenerationState.RETIRED || entry.leaseCount != 0) {
            return
        }
        entry.gpuPackage.release()
        entry.state = CooFxGpuGenerationState.RELEASED
        retired -= entry
    }

    private class GenerationEntry(val gpuPackage: CooFxGpuPackage) {
        var state = CooFxGpuGenerationState.ACTIVE
        var leaseCount = 0
    }
}
