package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooEffectUvMode
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import kotlin.math.abs
import kotlin.math.floor

internal data class CooEffectUv(
    val u: Float,
    val v: Float
)

internal object CooEffectUvResolver {
    fun resolve(
        mode: CooEffectUvMode,
        blockPos: BlockPos,
        x: Float,
        y: Float,
        z: Float,
        baseU: Float,
        baseV: Float,
        normalX: Float,
        normalY: Float,
        normalZ: Float
    ): CooEffectUv {
        val localX = x - SectionPos.sectionRelative(blockPos.x)
        val localY = y - SectionPos.sectionRelative(blockPos.y)
        val localZ = z - SectionPos.sectionRelative(blockPos.z)
        val worldX = blockPos.x + localX
        val worldY = blockPos.y + localY
        val worldZ = blockPos.z + localZ
        return when (mode) {
            CooEffectUvMode.BASE_UV -> CooEffectUv(fractional(baseU), fractional(baseV))
            CooEffectUvMode.FACE_LOCAL -> when {
                abs(normalX) >= abs(normalY) && abs(normalX) >= abs(normalZ) ->
                    CooEffectUv(fractional(localZ), fractional(localY))

                abs(normalY) >= abs(normalZ) -> CooEffectUv(fractional(localX), fractional(localZ))
                else -> CooEffectUv(fractional(localX), fractional(localY))
            }

            CooEffectUvMode.WORLD_XZ -> CooEffectUv(fractional(worldX), fractional(worldZ))
            CooEffectUvMode.WORLD_XY -> CooEffectUv(fractional(worldX), fractional(worldY))
            CooEffectUvMode.WORLD_YZ -> CooEffectUv(fractional(worldY), fractional(worldZ))
        }
    }

    fun pack(value: Float): Int {
        return (value.coerceIn(0F, 1F) * 65535F).toInt() - 32768
    }

    fun periodicWorldCoordinate(value: Double): Float {
        val period = 1024.0
        return (value - floor(value / period) * period).toFloat()
    }

    private fun fractional(value: Float): Float = value - floor(value)
}

/** 保留 UV0，在 UV1 写入 EffectUV，并把生效 tick 写入 light UV 的空闲高位。 */
internal class CooEffectUvVertexConsumer(
    private val delegate: VertexConsumer,
    private val mode: CooEffectUvMode,
    private val blockPos: BlockPos,
    activatedAt: Long
) : VertexConsumer {
    private val activationTick = (activatedAt and 0xFFFFL).toInt()
    private var x = 0F
    private var y = 0F
    private var z = 0F
    private var baseU = 0F
    private var baseV = 0F

    override fun addVertex(x: Float, y: Float, z: Float): VertexConsumer = apply {
        this.x = x
        this.y = y
        this.z = z
        delegate.addVertex(x, y, z)
    }

    override fun setColor(red: Int, green: Int, blue: Int, alpha: Int): VertexConsumer = apply {
        delegate.setColor(red, green, blue, alpha)
    }

    override fun setUv(u: Float, v: Float): VertexConsumer = apply {
        baseU = u
        baseV = v
        delegate.setUv(u, v)
    }

    override fun setUv1(u: Int, v: Int): VertexConsumer = this

    override fun setUv2(u: Int, v: Int): VertexConsumer = apply {
        val packedU = (u and 0xFF) or ((activationTick and 0xFF) shl 8)
        val packedV = (v and 0xFF) or (((activationTick ushr 8) and 0xFF) shl 8)
        delegate.setUv2(packedU, packedV)
    }

    override fun setNormal(x: Float, y: Float, z: Float): VertexConsumer = apply {
        val effectUv = CooEffectUvResolver.resolve(
            mode = mode,
            blockPos = blockPos,
            x = this.x,
            y = this.y,
            z = this.z,
            baseU = baseU,
            baseV = baseV,
            normalX = x,
            normalY = y,
            normalZ = z
        )
        delegate.setUv1(CooEffectUvResolver.pack(effectUv.u), CooEffectUvResolver.pack(effectUv.v))
        delegate.setNormal(x, y, z)
    }
}
