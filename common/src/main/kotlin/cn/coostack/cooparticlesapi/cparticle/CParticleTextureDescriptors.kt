package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.CooParticlesConstants
import com.mojang.blaze3d.systems.RenderSystem
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.glBindTexture
import org.lwjgl.opengl.GL11.glDeleteTextures
import org.lwjgl.opengl.GL11.glGenTextures
import org.lwjgl.opengl.GL11.glGetInteger
import org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE
import org.lwjgl.opengl.GL13.GL_TEXTURE0
import org.lwjgl.opengl.GL13.glActiveTexture
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER
import org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING
import org.lwjgl.opengl.GL15.GL_STATIC_DRAW
import org.lwjgl.opengl.GL15.glBindBuffer
import org.lwjgl.opengl.GL15.glBufferData
import org.lwjgl.opengl.GL15.glDeleteBuffers
import org.lwjgl.opengl.GL15.glGenBuffers
import org.lwjgl.opengl.GL30.GL_RGBA32F
import org.lwjgl.opengl.GL31.GL_MAX_TEXTURE_BUFFER_SIZE
import org.lwjgl.opengl.GL31.GL_TEXTURE_BINDING_BUFFER
import org.lwjgl.opengl.GL31.GL_TEXTURE_BUFFER
import org.lwjgl.opengl.GL31.glTexBuffer

/**
 * 一个共享 GPU 纹理描述符的注册定义。
 *
 * Example: 十万粒子可共同引用一个 [id]，只在 TBO 中保存一次帧表。
 * Forbidden: [frames] 不得返回其他 [bindingKey] 的 UV。
 *
 * @property id 在 float 精确整数范围内的稳定 ID
 * @property bindingKey 所有帧所属的纹理绑定
 * @property frames 资源重载后可再次调用的帧解析函数
 */
internal data class CParticleTextureDescriptorDefinition(
    val id: Int,
    val bindingKey: CParticleTextureBindingKey,
    val frames: () -> List<CParticleUv>,
)

/**
 * 管理稳定 descriptor ID 与 RGBA32F texture-buffer 查找表。
 *
 * 注册表在资源重载时保留 ID，只丢弃已解析 UV 并重建 GL 表。
 * Example: STATIC 槽位重载前后的 `iAnimation.x` 无需改变。
 * Forbidden: 不要从非渲染线程调用 [bindLookup] 创建 GL 资源。
 */
object CParticleTextureDescriptors {
    /**
     * descriptor ID 使用 float 精确整数的 24 位范围。
     *
     * Example: ID `16_777_215` 仍可无损往返 float。
     * Forbidden: `16_777_216` 不能作为最大有效 ID 再继续递增。
     */
    const val DESCRIPTOR_ID_BITS = 24

    /**
     * 最大有效 descriptor ID。
     *
     * Example: 上限测试可直接检查 [requireValidDescriptorId]。
     * Forbidden: 真实 TBO 仍受 `GL_MAX_TEXTURE_BUFFER_SIZE` 的更小限制。
     */
    const val MAX_DESCRIPTOR_ID = (1 shl DESCRIPTOR_ID_BITS) - 1

    /**
     * 最大 descriptor 数量。
     *
     * Example: ID 从 `0` 开始，因此数量比 [MAX_DESCRIPTOR_ID] 大一。
     * Forbidden: 不要预分配此数量的数组。
     */
    const val MAX_DESCRIPTOR_COUNT = MAX_DESCRIPTOR_ID + 1

    private val descriptorIds = LinkedHashMap<Any, Int>()
    private val definitions = ArrayList<CParticleTextureDescriptorDefinition>()
    private val resolvedFrames = HashMap<Int, List<CParticleUv>>()
    private val warnedDescriptors = HashSet<Int>()

    @Volatile
    private var lookupDirty = true
    private var lookupBuffer = 0
    private var lookupTexture = 0

    /**
     * 注册或复用一个共享描述符。
     *
     * Example: 相同 BlockState 和 binding 会返回同一 ID。
     * Forbidden: 相同 [key] 不能在不同 binding 下注册；调用方应把 binding 纳入 key。
     *
     * @param key 可稳定比较的描述符键
     * @param bindingKey 所有帧所属绑定
     * @param frames 资源重载时重新解析帧的函数
     * @return 稳定 descriptor ID
     * @throws IllegalStateException 超过 24 位 descriptor 上限时抛出
     */
    @Synchronized
    internal fun register(
        key: Any,
        bindingKey: CParticleTextureBindingKey,
        frames: () -> List<CParticleUv>,
    ): Int {
        descriptorIds[key]?.let { id ->
            check(definitions[id].bindingKey == bindingKey) {
                "CParticle texture descriptor key was reused across bindings: $key"
            }
            return id
        }
        check(definitions.size < MAX_DESCRIPTOR_COUNT) {
            "CParticle texture descriptor limit exceeded: $MAX_DESCRIPTOR_COUNT"
        }
        val id = definitions.size
        descriptorIds[key] = id
        definitions += CParticleTextureDescriptorDefinition(id, bindingKey, frames)
        lookupDirty = true
        return id
    }

    /**
     * 返回描述符当前解析出的第一帧。
     *
     * Example: [CParticleResolvedTexture.uv] 使用此值提供 CPU 兼容信息。
     * Forbidden: age 动画不能只使用此方法选帧。
     *
     * @param descriptorId 已注册 ID
     * @return 第一帧，失败时返回与 binding 兼容的 missing UV
     */
    internal fun firstFrame(descriptorId: Int): CParticleUv = framesFor(descriptorId).first()

    /**
     * 按与 vertex shader 相同的 age/maxAge 公式选择一帧。
     *
     * Example: legacy CPU `resolveEffect` 可用它保持旧 API 行为。
     * Forbidden: GPU age 正常推进不能每粒子调用此方法。
     *
     * @param descriptorId 动画或单帧描述符
     * @param age 当前年龄
     * @param lifetime 最大年龄，非正值按 `1` 处理
     * @return 选中的 UV 帧
     */
    internal fun frame(descriptorId: Int, age: Int, lifetime: Int): CParticleUv {
        val frames = framesFor(descriptorId)
        if (frames.size == 1) return frames[0]
        val safeLifetime = lifetime.coerceAtLeast(1).toLong()
        val safeAge = age.toLong().coerceIn(0L, safeLifetime)
        val progress = safeAge * CParticleGpuMath.FRAME_PROGRESS_RESOLUTION / safeLifetime
        val frameIndex = (progress * (frames.size - 1) / CParticleGpuMath.FRAME_PROGRESS_RESOLUTION)
            .toInt()
        return frames[frameIndex]
    }

    /**
     * 校验 descriptor ID 的位宽。
     *
     * Example: `requireValidDescriptorId(MAX_DESCRIPTOR_ID)` 成功。
     * Forbidden: 负数和超过 24 位的值会抛出异常。
     *
     * @param descriptorId 要写入实例 float 的 ID
     * @return 原值，便于调用点内联使用
     * @throws IllegalArgumentException ID 超出范围时抛出
     */
    @JvmStatic
    fun requireValidDescriptorId(descriptorId: Int): Int {
        require(descriptorId in 0..MAX_DESCRIPTOR_ID) {
            "CParticle texture descriptor id is outside 24-bit range: $descriptorId"
        }
        return descriptorId
    }

    /**
     * 校验描述符已经注册并属于预期纹理绑定。
     *
     * Example: 方块图集 system 可接受由 `textureOfBlock` 注册的 descriptor。
     * Forbidden: 粒子图集 system 不能写入方块图集 descriptor。
     *
     * @param descriptorId 要写入实例数据的 descriptor ID
     * @param expectedBindingKey 当前 system 固定的基础或蒙版纹理绑定
     * @return 已验证的 descriptor ID
     * @throws IllegalArgumentException ID 未注册或 binding 不一致时抛出
     */
    @Synchronized
    internal fun requireBinding(
        descriptorId: Int,
        expectedBindingKey: CParticleTextureBindingKey,
    ): Int {
        requireValidDescriptorId(descriptorId)
        val definition = definitions.getOrNull(descriptorId)
        require(definition != null) {
            "Unknown CParticle texture descriptor id: $descriptorId"
        }
        require(definition.bindingKey == expectedBindingKey) {
            "CParticle texture descriptor $descriptorId belongs to ${definition.bindingKey}, " +
                    "not $expectedBindingKey"
        }
        return descriptorId
    }

    /**
     * 把 texture-buffer 绑定到指定纹理单元，并恢复原 active texture。
     *
     * Example: renderer 在 draw 前调用 `bindLookup(2)`。
     * Forbidden: 非渲染线程不能调用此方法。
     *
     * @param textureUnit 从零开始的纹理单元
     */
    internal fun bindLookup(textureUnit: Int) {
        RenderSystem.assertOnRenderThread()
        val previousActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE)
        glActiveTexture(GL_TEXTURE0 + textureUnit)
        try {
            ensureLookup()
            glBindTexture(GL_TEXTURE_BUFFER, lookupTexture)
        } finally {
            glActiveTexture(previousActiveTexture)
        }
    }

    /**
     * 丢弃解析缓存和旧 TBO，但保留 descriptor ID。
     *
     * Example: atlas stitch 或完整资源重载后调用。
     * Forbidden: 不要清空 [definitions]，否则现存 STATIC 槽位会引用错误 ID。
     */
    @JvmStatic
    @Synchronized
    fun invalidate() {
        resolvedFrames.clear()
        lookupDirty = true
        releaseLookup()
    }

    /**
     * 释放 GL 查找表，保留 CPU 注册定义。
     *
     * Example: 客户端关闭时调用。
     * Forbidden: 不要把它当成 descriptor ID 重置操作。
     */
    @JvmStatic
    fun release() {
        lookupDirty = true
        releaseLookup()
    }

    @Synchronized
    private fun framesFor(descriptorId: Int): List<CParticleUv> {
        requireValidDescriptorId(descriptorId)
        val definition = definitions.getOrNull(descriptorId)
            ?: error("Unknown CParticle texture descriptor id: $descriptorId")
        resolvedFrames[descriptorId]?.let { return it }
        val frames = runCatching { definition.frames() }
            .getOrElse { error ->
                if (warnedDescriptors.add(descriptorId)) {
                    CooParticlesConstants.logger.warn(
                        "Failed to resolve CParticle texture descriptor {}",
                        descriptorId,
                        error,
                    )
                }
                emptyList()
            }
            .takeIf { it.isNotEmpty() }
            ?: listOf(CParticleTextureResolver.missingUv(definition.bindingKey))
        resolvedFrames[descriptorId] = frames
        return frames
    }

    @Synchronized
    private fun ensureLookup() {
        if (!lookupDirty && lookupBuffer != 0 && lookupTexture != 0) return
        val frames = ArrayList<List<CParticleUv>>(definitions.size)
        var frameCount = 0
        for (definition in definitions) {
            val resolved = framesFor(definition.id)
            frames += resolved
            frameCount = Math.addExact(frameCount, resolved.size)
        }
        val texelCount = Math.addExact(definitions.size, frameCount)
        check(texelCount <= CParticleInstanceFlags.FLOAT_EXACT_INTEGER_LIMIT) {
            "CParticle texture descriptor table exceeds exact float offsets: $texelCount"
        }
        val glTexelLimit = glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE)
        check(glTexelLimit <= 0 || texelCount <= glTexelLimit) {
            "CParticle texture descriptor table requires $texelCount texels, GL limit is $glTexelLimit"
        }

        val data = BufferUtils.createFloatBuffer(texelCount * 4)
        var frameOffset = definitions.size
        for (entry in frames) {
            data.put(frameOffset.toFloat()).put(entry.size.toFloat()).put(0f).put(0f)
            frameOffset += entry.size
        }
        for (entry in frames) for (uv in entry) {
            data.put(uv.u0).put(uv.v0).put(uv.u1).put(uv.v1)
        }
        data.flip()

        val previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_BUFFER)
        val newBuffer = glGenBuffers()
        val newTexture = glGenTextures()
        glBindBuffer(GL_ARRAY_BUFFER, newBuffer)
        glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW)
        glBindTexture(GL_TEXTURE_BUFFER, newTexture)
        glTexBuffer(GL_TEXTURE_BUFFER, GL_RGBA32F, newBuffer)

        val oldBuffer = lookupBuffer
        val oldTexture = lookupTexture
        glBindTexture(GL_TEXTURE_BUFFER, if (previousTexture == oldTexture) 0 else previousTexture)
        glBindBuffer(GL_ARRAY_BUFFER, if (previousBuffer == oldBuffer) 0 else previousBuffer)
        lookupBuffer = newBuffer
        lookupTexture = newTexture
        lookupDirty = false
        deleteLookup(oldBuffer, oldTexture)
    }

    private fun releaseLookup() {
        val oldBuffer = lookupBuffer
        val oldTexture = lookupTexture
        lookupBuffer = 0
        lookupTexture = 0
        deleteLookup(oldBuffer, oldTexture)
    }

    private fun deleteLookup(buffer: Int, texture: Int) {
        if (buffer == 0 && texture == 0) return
        if (RenderSystem.isOnRenderThread()) {
            if (texture != 0) glDeleteTextures(texture)
            if (buffer != 0) glDeleteBuffers(buffer)
        } else {
            RenderSystem.recordRenderCall {
                if (texture != 0) glDeleteTextures(texture)
                if (buffer != 0) glDeleteBuffers(buffer)
            }
        }
    }
}
