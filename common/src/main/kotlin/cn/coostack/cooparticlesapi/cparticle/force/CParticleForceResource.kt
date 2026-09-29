package cn.coostack.cooparticlesapi.cparticle.force

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.math.floor

/**
 * 力场命令依赖的外部资源声明。
 *
 * 这里保存的是稳定的资源键，不是 OpenGL texture id、SSBO id 或其他瞬时句柄。
 * 运行时句柄由 [CParticleForceResourceRegistry] 的解析器提供，资源重载后可以重新解析。
 */
sealed interface CParticleForceResource {
    /** 资源注册表使用的稳定键。 */
    val key: ResourceLocation
}

/**
 * Texture 力使用的二维纹理资源。
 *
 * 采样坐标由 Texture 力定义为 simulation space 的 XZ 平面，解析器负责把资源键绑定到 GPU
 * sampler，并在调用方显式选择 CPU 模拟时提供相同的采样结果。
 *
 * @property location 纹理资源 ID，例如 `cooparticlesapi:textures/force/wind.png`
 */
data class CParticleTextureResource(
    val location: ResourceLocation,
) : CParticleForceResource {
    override val key: ResourceLocation
        get() = location
}

/**
 * FluidFlow 力使用的三维流场资源。
 *
 * 资源应提供 RGB 速度和可选的 A 密度通道；它与粒子渲染图集完全不同，不能使用
 * 粒子渲染纹理 binding 代替。
 *
 * @property location 流场资源 ID，由客户端资源绑定层解释
 */
data class CParticleFluidResource(
    val location: ResourceLocation,
) : CParticleForceResource {
    override val key: ResourceLocation
        get() = location
}

/**
 * 一个已解析的力场资源绑定。
 *
 * 实现负责隐藏 GL texture/SSBO 的瞬时 ID。GPU 路径调用 [bindCompute] 和 [resetCompute]，
 * CPU 路径调用对应的采样方法；这样同一个资源声明可以在两条模拟路径上保持一致。
 */
interface CParticleForceResourceBinding {
    /** 当前绑定对应的声明式资源。 */
    val resource: CParticleForceResource

    /**
     * 将资源绑定到指定的 compute sampler unit。
     *
     * 实现若抛出异常，必须先恢复已经修改的 GL 状态。
     */
    fun bindCompute(textureUnit: Int)

    /** 恢复 [bindCompute] 修改的 GL 状态。 */
    fun resetCompute()

    /**
     * 在 CPU 路径采样二维纹理。
     *
     * 默认实现直接报错，避免资源没有 CPU 采样器时静默产生零力。
     */
    fun sampleTexture(x: Double, y: Double, z: Double, output: FloatArray) {
        error("CParticle force resource ${resource.key} has no CPU texture sampler")
    }

    /**
     * 在 CPU 路径采样三维流场。
     *
     * 默认实现直接报错，避免资源没有 CPU 流场采样器时静默产生零力。
     */
    fun sampleFluid(x: Double, y: Double, z: Double, output: FloatArray) {
        error("CParticle force resource ${resource.key} has no CPU fluid sampler")
    }
}

/** 将声明式资源解析为当前客户端/模拟后端绑定。 */
fun interface CParticleForceResourceResolver {
    /**
     * 解析一个资源声明。
     *
     * @param resource 力命令携带的稳定资源声明
     * @return 可用于 GPU/CPU 的绑定；资源不存在时返回 `null`
     */
    fun resolve(resource: CParticleForceResource): CParticleForceResourceBinding?
}

/**
 * 当前客户端的力场资源解析入口。
 *
 * 资源键只负责描述需要什么，真正的 texture/SSBO 句柄由客户端初始化阶段注册的解析器提供。
 * 没有解析器或资源缺失时，提交资源型力场会抛出明确异常。
 */
object CParticleForceResourceRegistry {
    /** 客户端初始化阶段安装的资源解析器；服务端不应主动解析资源。 */
    @Volatile
    var resolver: CParticleForceResourceResolver? = null

    private val resolvedBindings = ConcurrentHashMap<CParticleForceResource, CParticleForceResourceBinding>()

    /**
     * 直接注册一个已经实现 GPU 绑定和 CPU 采样的资源。
     *
     * 适合三维 FluidFlow、自定义生成纹理或不来自资源包的运行时数据。注册键相同时，新绑定替换旧绑定。
     *
     * @param binding 要按 [CParticleForceResourceBinding.resource] 保存的绑定
     */
    @JvmStatic
    fun register(binding: CParticleForceResourceBinding) {
        resolvedBindings[binding.resource] = binding
    }

    /** 移除指定声明式资源的缓存或直接注册绑定。 */
    @JvmStatic
    fun unregister(resource: CParticleForceResource) {
        resolvedBindings.remove(resource)
    }

    /**
     * 清除已经解析的绑定。
     *
     * 资源重载后 GL 句柄可能变化，客户端重载流程会调用此方法；[resolver] 本身不会被移除。
     */
    @JvmStatic
    fun clearResolvedBindings() {
        resolvedBindings.clear()
    }

    /** 解析资源并在缺失时给出可定位的错误。 */
    internal fun requireBinding(resource: CParticleForceResource): CParticleForceResourceBinding {
        val binding = resolvedBindings.computeIfAbsent(resource) {
            resolver?.resolve(resource) ?: defaultBinding(resource)
            ?: error("No CParticle force resource resolver is installed for ${resource.key}")
        }
        require(binding.resource == resource) {
            "CParticle force resource resolver returned ${binding.resource.key} for ${resource.key}"
        }
        return binding
    }

    private fun defaultBinding(resource: CParticleForceResource): CParticleForceResourceBinding? {
        return when (resource) {
            is CParticleTextureResource -> MinecraftTextureForceBinding(resource)
            is CParticleFluidResource -> null
        }
    }
}

/** 资源包二维纹理的默认绑定；调用方只提供 ResourceLocation，不接触 GL texture id。 */
private class MinecraftTextureForceBinding(
    override val resource: CParticleTextureResource,
) : CParticleForceResourceBinding {
    private val image: BufferedImage by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        val manager = Minecraft.getInstance().resourceManager
        val selected = manager.getResource(resource.location).orElseThrow {
            IllegalArgumentException("CParticle texture force resource does not exist: ${resource.location}")
        }
        selected.open().use { stream ->
            requireNotNull(ImageIO.read(stream)) {
                "CParticle texture force resource is not a supported image: ${resource.location}"
            }
        }
    }

    private var previousActiveTexture = GL13.GL_TEXTURE0
    private var previousTexture = 0

    override fun bindCompute(textureUnit: Int) {
        val capturedActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        var capturedTexture: Int? = null
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + textureUnit)
            val textureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
            capturedTexture = textureBinding
            val texture = Minecraft.getInstance().textureManager.getTexture(resource.location)
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.id)
            previousActiveTexture = capturedActiveTexture
            previousTexture = textureBinding
        } catch (error: RuntimeException) {
            var restoreFailure: RuntimeException? = null
            val textureToRestore = capturedTexture
            if (textureToRestore != null) {
                try {
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureToRestore)
                } catch (restoreError: RuntimeException) {
                    restoreFailure = restoreError
                }
            }
            try {
                GL13.glActiveTexture(capturedActiveTexture)
            } catch (restoreError: RuntimeException) {
                if (restoreFailure == null) restoreFailure = restoreError else restoreFailure.addSuppressed(restoreError)
            }
            if (restoreFailure != null) error.addSuppressed(restoreFailure)
            throw error
        }
    }

    override fun resetCompute() {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture)
        GL13.glActiveTexture(previousActiveTexture)
    }

    override fun sampleTexture(x: Double, y: Double, z: Double, output: FloatArray) {
        require(output.size >= 4) { "Texture force sample output must contain at least four floats" }
        val loaded = image
        val u = x - floor(x)
        val v = z - floor(z)
        val sampleX = (u * loaded.width).toInt().coerceIn(0, loaded.width - 1)
        val sampleY = (v * loaded.height).toInt().coerceIn(0, loaded.height - 1)
        val argb = loaded.getRGB(sampleX, sampleY)
        output[0] = ((argb ushr 16) and 0xff) / 255F
        output[1] = ((argb ushr 8) and 0xff) / 255F
        output[2] = (argb and 0xff) / 255F
        output[3] = ((argb ushr 24) and 0xff) / 255F
    }
}

/**
 * 单个 system 当前 tick 使用的资源槽位表。
 *
 * Command 只写入小整数槽位，实际资源绑定保存在此表中；槽位按本 tick 的提交顺序稳定分配。
 */
internal class CParticleForceResourceTable {
    private val textureBindings = ArrayList<CParticleForceResourceBinding>()
    private val textureSlots = LinkedHashMap<CParticleTextureResource, Int>()
    private val fluidBindings = ArrayList<CParticleForceResourceBinding>()
    private val fluidSlots = LinkedHashMap<CParticleFluidResource, Int>()

    fun clear() {
        textureBindings.clear()
        textureSlots.clear()
        fluidBindings.clear()
        fluidSlots.clear()
    }

    fun slotFor(resource: CParticleTextureResource): Int {
        textureSlots[resource]?.let { return it }
        val slot = textureBindings.size
        require(slot < MAX_TEXTURE_RESOURCES) {
            "CParticle texture force resource count exceeds $MAX_TEXTURE_RESOURCES"
        }
        val binding = CParticleForceResourceRegistry.requireBinding(resource)
        textureSlots[resource] = slot
        textureBindings += binding
        return slot
    }

    fun slotFor(resource: CParticleFluidResource): Int {
        fluidSlots[resource]?.let { return it }
        val slot = fluidBindings.size
        require(slot < MAX_FLUID_RESOURCES) {
            "CParticle fluid force resource count exceeds $MAX_FLUID_RESOURCES"
        }
        val binding = CParticleForceResourceRegistry.requireBinding(resource)
        fluidSlots[resource] = slot
        fluidBindings += binding
        return slot
    }

    fun textureBinding(slot: Int): CParticleForceResourceBinding {
        require(slot in textureBindings.indices) { "Unknown CParticle texture force resource slot: $slot" }
        return textureBindings[slot]
    }

    fun fluidBinding(slot: Int): CParticleForceResourceBinding {
        require(slot in fluidBindings.indices) { "Unknown CParticle fluid force resource slot: $slot" }
        return fluidBindings[slot]
    }

    fun textureBindings(): List<CParticleForceResourceBinding> = textureBindings

    fun fluidBindings(): List<CParticleForceResourceBinding> = fluidBindings

    companion object {
        /** OpenGL 4.3 保证 compute shader 至少有 16 个纹理单元，两类资源各保留一半。 */
        const val MAX_TEXTURE_RESOURCES = 8
        const val MAX_FLUID_RESOURCES = 8
    }
}
