package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.network.particle.emitters.command.curve.BezierFloatKeyframe
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
 * 共享逐粒子生命周期外观，并通过 RGBA32F texture buffer 提供给 vertex shader。
 *
 * 每个 descriptor 固定占 73 个 texel：25 个锚点 texel、32 个标量控制柄 texel，
 * 以及 16 个颜色控制柄 texel。
 * STATIC 粒子只保存 descriptor ID，不保留 CParticle 或曲线对象。
 * 示例：同一 binding 中的不同曲线仍可留在一次实例化 draw 中。
 * 禁止：age 推进不得重新注册 descriptor 或上传实例外观。
 */
object CParticleAppearanceDescriptors {
    /**
     * 每个外观描述符在 RGBA32F 查找表中占用的 texel 数。
     *
     * 示例：descriptor `2` 从 texel `146` 开始。
     * 禁止：修改布局时不能只改此常量，着色器中的布局必须同步。
     */
    const val TEXELS_PER_DESCRIPTOR = 73
    const val IDENTITY_DESCRIPTOR_ID = 0

    /** 保证 `descriptorId * TEXELS_PER_DESCRIPTOR` 仍处于 float 精确整数范围。 */
    const val MAX_DESCRIPTOR_COUNT = CParticleInstanceFlags.FLOAT_EXACT_INTEGER_LIMIT / TEXELS_PER_DESCRIPTOR
    const val MAX_DESCRIPTOR_ID = MAX_DESCRIPTOR_COUNT - 1

    private val descriptorIds = LinkedHashMap<CParticleAppearanceDefinition, Int>()
    private val definitions = ArrayList<CParticleAppearanceDefinition>()

    @Volatile
    private var lookupDirty = true
    private var lookupBuffer = 0
    private var lookupTexture = 0

    init {
        check(register(null, null, null, null, null) == IDENTITY_DESCRIPTOR_ID)
    }

    /**
     * 注册或复用一组生命周期外观曲线。
     *
     * 示例：等值的曲线组合会返回同一个 descriptor ID。
     * 禁止：不要把会被外部修改的可变采样数组直接存入定义。
     *
     * @param alphaCurve 不透明度倍率曲线
     * @param scaleCurve X/Y 共用的等比缩放倍率曲线
     * @param scaleXCurve X 方向缩放倍率曲线
     * @param scaleYCurve Y 方向缩放倍率曲线
     * @param colorCurve RGB 颜色倍率曲线
     * @return 可写入粒子实例数据的稳定 descriptor ID
     */
    @Synchronized
    internal fun register(
        alphaCurve: CParticleCurve?,
        scaleCurve: CParticleCurve?,
        scaleXCurve: CParticleCurve?,
        scaleYCurve: CParticleCurve?,
        colorCurve: CParticleColorCurve?,
    ): Int {
        val definition = CParticleAppearanceDefinition(
            scalarDefinition(alphaCurve),
            scalarDefinition(scaleCurve),
            scalarDefinition(scaleXCurve),
            scalarDefinition(scaleYCurve),
            colorDefinition(colorCurve),
        )
        descriptorIds[definition]?.let { return it }
        check(definitions.size < MAX_DESCRIPTOR_COUNT) {
            "CParticle appearance descriptor limit exceeded: $MAX_DESCRIPTOR_COUNT"
        }
        val id = definitions.size
        descriptorIds[definition] = id
        definitions += definition
        lookupDirty = true
        return id
    }

    /** 校验写入实例 float 的 appearance descriptor ID。 */
    @JvmStatic
    fun requireValidDescriptorId(descriptorId: Int): Int {
        require(descriptorId in 0..MAX_DESCRIPTOR_ID) {
            "CParticle appearance descriptor id is outside range: $descriptorId"
        }
        return descriptorId
    }

    internal fun definition(descriptorId: Int): CParticleAppearanceDefinition {
        requireValidDescriptorId(descriptorId)
        return definitions.getOrNull(descriptorId)
            ?: error("Unknown CParticle appearance descriptor id: $descriptorId")
    }

    /**
     * 把槽位中的共享定义恢复到临时 [CParticle] 快照。
     *
     * 示例：CPU fallback 可以用它恢复 STATIC 粒子的全部生命周期曲线。
     * 禁止：不要传入尚未注册的 descriptor ID。
     *
     * @param particle 接收曲线的粒子快照
     * @param descriptorId 已注册的外观描述符 ID
     */
    internal fun applyTo(particle: CParticle, descriptorId: Int) {
        val definition = definition(descriptorId)
        particle.alphaCurve = definition.alpha?.let(::scalarCurve)
        particle.scaleCurve = definition.scale?.let(::scalarCurve)
        particle.scaleXCurve = definition.scaleX?.let(::scalarCurve)
        particle.scaleYCurve = definition.scaleY?.let(::scalarCurve)
        particle.colorCurve = definition.color?.let(::colorCurve)
    }

    /** 在渲染线程把查找表绑定到指定纹理单元，并恢复 active texture。 */
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

    /** 释放 GL 表；稳定 ID 和 CPU 定义继续保留。 */
    @JvmStatic
    fun release() {
        lookupDirty = true
        releaseLookup()
    }

    @Synchronized
    private fun ensureLookup() {
        if (!lookupDirty && lookupBuffer != 0 && lookupTexture != 0) return
        val texelCount = Math.multiplyExact(definitions.size, TEXELS_PER_DESCRIPTOR)
        check(texelCount < CParticleInstanceFlags.FLOAT_EXACT_INTEGER_LIMIT) {
            "CParticle appearance table exceeds exact float offsets: $texelCount"
        }
        val glTexelLimit = glGetInteger(GL_MAX_TEXTURE_BUFFER_SIZE)
        check(glTexelLimit <= 0 || texelCount <= glTexelLimit) {
            "CParticle appearance table requires $texelCount texels, GL limit is $glTexelLimit"
        }

        val packed = BufferUtils.createFloatBuffer(texelCount * 4)
        for (definition in definitions) packDefinition(packed, definition)
        packed.flip()

        val previousBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING)
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_BUFFER)
        val newBuffer = glGenBuffers()
        val newTexture = glGenTextures()
        glBindBuffer(GL_ARRAY_BUFFER, newBuffer)
        glBufferData(GL_ARRAY_BUFFER, packed, GL_STATIC_DRAW)
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

    /**
     * 按顶点着色器约定的固定布局写入一个描述符。
     *
     * 示例：缺失曲线写入计数 `0`，采样时返回倍率 `1`。
     * 禁止：不要改变字段顺序而不同时更新 `cparticle.vsh`。
     *
     * @param target 目标浮点缓冲区
     * @param definition 要写入的标准化定义
     */
    private fun packDefinition(
        target: java.nio.FloatBuffer,
        definition: CParticleAppearanceDefinition,
    ) {
        target.put(encodedKeyCount(definition.alpha).toFloat())
            .put(encodedKeyCount(definition.scale).toFloat())
            .put(encodedKeyCount(definition.scaleX).toFloat())
            .put(
                (encodedKeyCount(definition.scaleY) +
                        encodedKeyCount(definition.color) * PACKED_CURVE_RADIX).toFloat()
            )
        for (i in 0 until CParticleCurve.MAX_KEYS) {
            target.put(definition.alpha?.times?.getOrNull(i) ?: 0f)
                .put(definition.alpha?.values?.getOrNull(i) ?: 0f)
                .put(definition.scale?.times?.getOrNull(i) ?: 0f)
                .put(definition.scale?.values?.getOrNull(i) ?: 0f)
        }
        for (i in 0 until CParticleCurve.MAX_KEYS) {
            target.put(definition.scaleX?.times?.getOrNull(i) ?: 0f)
                .put(definition.scaleX?.values?.getOrNull(i) ?: 0f)
                .put(definition.scaleY?.times?.getOrNull(i) ?: 0f)
                .put(definition.scaleY?.values?.getOrNull(i) ?: 0f)
        }
        for (i in 0 until CParticleColorCurve.MAX_KEYS) {
            val offset = i * CParticleColorCurveDefinition.ENTRY_SIZE
            target.put(definition.color?.entries?.getOrNull(offset) ?: 0f)
                .put(definition.color?.entries?.getOrNull(offset + 1) ?: 0f)
                .put(definition.color?.entries?.getOrNull(offset + 2) ?: 0f)
                .put(definition.color?.entries?.getOrNull(offset + 3) ?: 0f)
        }
        listOf(definition.alpha, definition.scale, definition.scaleX, definition.scaleY).forEach { curve ->
            for (i in 0 until CParticleCurve.MAX_KEYS * CParticleBezierMath.SCALAR_HANDLE_COMPONENTS) {
                target.put(curve?.handles?.getOrNull(i) ?: 0f)
            }
        }
        for (i in 0 until CParticleColorCurve.MAX_KEYS * CParticleColorCurveDefinition.ENTRY_SIZE) {
            target.put(definition.color?.outHandles?.getOrNull(i) ?: 0f)
        }
        for (i in 0 until CParticleColorCurve.MAX_KEYS * CParticleColorCurveDefinition.ENTRY_SIZE) {
            target.put(definition.color?.inHandles?.getOrNull(i) ?: 0f)
        }
    }

    private fun scalarDefinition(curve: CParticleCurve?): CParticleScalarCurveDefinition? {
        if (curve == null) return null
        return CParticleScalarCurveDefinition(
            curve.interpolation,
            List(curve.keyCount) { curve.packedData[it] },
            List(curve.keyCount) { curve.packedData[CParticleCurve.MAX_KEYS + it] },
            List(curve.keyCount * CParticleBezierMath.SCALAR_HANDLE_COMPONENTS) { curve.packedHandleData[it] },
        )
    }

    private fun colorDefinition(curve: CParticleColorCurve?): CParticleColorCurveDefinition? {
        if (curve == null) return null
        return CParticleColorCurveDefinition(
            curve.interpolation,
            buildList(curve.keyCount * CParticleColorCurveDefinition.ENTRY_SIZE) {
                for (i in 0 until curve.keyCount) {
                    val colorOffset = i * 3
                    add(curve.packedTimeData[i])
                    add(curve.packedColorData[colorOffset])
                    add(curve.packedColorData[colorOffset + 1])
                    add(curve.packedColorData[colorOffset + 2])
                }
            },
            List(curve.keyCount * CParticleColorCurveDefinition.ENTRY_SIZE) { curve.packedOutHandleData[it] },
            List(curve.keyCount * CParticleColorCurveDefinition.ENTRY_SIZE) { curve.packedInHandleData[it] },
        )
    }

    private fun scalarCurve(definition: CParticleScalarCurveDefinition): CParticleCurve =
        when (definition.interpolation) {
            CParticleCurveInterpolation.LINEAR -> CParticleCurve.of(*Array(definition.keyCount) { index ->
                definition.times[index] to definition.values[index]
            })

            CParticleCurveInterpolation.CUBIC_BEZIER -> CParticleCurve.bezier(
                *Array(definition.keyCount) { index ->
                    val offset = index * CParticleBezierMath.SCALAR_HANDLE_COMPONENTS
                    BezierFloatKeyframe(
                        definition.times[index].toDouble(),
                        definition.values[index].toDouble(),
                        definition.handles[offset].toDouble(),
                        definition.handles[offset + 1].toDouble(),
                        definition.handles[offset + 2].toDouble(),
                        definition.handles[offset + 3].toDouble(),
                    )
                },
            )
        }

    private fun colorCurve(definition: CParticleColorCurveDefinition): CParticleColorCurve =
        when (definition.interpolation) {
            CParticleCurveInterpolation.LINEAR -> CParticleColorCurve.of(*Array(definition.keyCount) { index ->
                val offset = index * CParticleColorCurveDefinition.ENTRY_SIZE
                definition.entries[offset] to org.joml.Vector3f(
                    definition.entries[offset + 1],
                    definition.entries[offset + 2],
                    definition.entries[offset + 3],
                )
            })

            CParticleCurveInterpolation.CUBIC_BEZIER -> CParticleColorCurve.bezier(
                *Array(definition.keyCount) { index ->
                    val offset = index * CParticleColorCurveDefinition.ENTRY_SIZE
                    CParticleBezierColorKeyframe(
                        definition.entries[offset].toDouble(),
                        org.joml.Vector3f(
                            definition.entries[offset + 1],
                            definition.entries[offset + 2],
                            definition.entries[offset + 3],
                        ),
                        definition.outHandles[offset].toDouble(),
                        org.joml.Vector3f(
                            definition.outHandles[offset + 1],
                            definition.outHandles[offset + 2],
                            definition.outHandles[offset + 3],
                        ),
                        definition.inHandles[offset].toDouble(),
                        org.joml.Vector3f(
                            definition.inHandles[offset + 1],
                            definition.inHandles[offset + 2],
                            definition.inHandles[offset + 3],
                        ),
                    )
                },
            )
        }

    private fun encodedKeyCount(definition: CParticleScalarCurveDefinition?): Int =
        definition?.let { it.keyCount + it.interpolation.wireId * CURVE_TYPE_RADIX } ?: 0

    private fun encodedKeyCount(definition: CParticleColorCurveDefinition?): Int =
        definition?.let { it.keyCount + it.interpolation.wireId * CURVE_TYPE_RADIX } ?: 0

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

    /**
     * 在一个精确整数 float 中分隔 Y 尺寸与颜色曲线元数据的进位基数。
     *
     * 示例：`3 + 2 * PACKED_CURVE_RADIX` 表示两个独立编码的曲线字段。
     * 禁止：此值必须大于类型和 key 数组合后的最大值，并与顶点着色器一致。
     */
    private const val PACKED_CURVE_RADIX = 32

    private const val CURVE_TYPE_RADIX = 16
}
