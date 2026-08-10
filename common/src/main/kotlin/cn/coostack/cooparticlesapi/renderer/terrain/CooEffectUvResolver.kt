package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooEffectUvMode
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import kotlin.math.abs
import kotlin.math.floor

/**
 * 计算并编码地形效果顶点使用的 EffectUV 与生效 tick。
 *
 * 该解析器同时供原版区块构建路径和 Sodium 兼容路径调用。两条路径必须使用同一坐标换算和
 * 16-bit 打包规则，否则相同 Pipeline 在不同渲染器下会出现纹理漂移或生效时间不一致。
 */
internal object CooEffectUvResolver {
    /**
     * 按 Pipeline 的 UV 模式计算一个方块顶点的效果坐标。
     *
     * 方块局部模式使用 section 内顶点坐标还原到当前方块；世界模式使用绝对方块坐标，
     * 最终结果取小数部分并落在 `0.0F..<1.0F`。
     *
     * @param mode 效果 UV 的坐标来源
     * @param blockPos 当前方块的世界坐标
     * @param x 顶点在 section 构建缓冲中的 X 坐标
     * @param y 顶点在 section 构建缓冲中的 Y 坐标
     * @param z 顶点在 section 构建缓冲中的 Z 坐标
     * @param baseU 原方块纹理 U 坐标
     * @param baseV 原方块纹理 V 坐标
     * @param normalX 顶点法线 X 分量
     * @param normalY 顶点法线 Y 分量
     * @param normalZ 顶点法线 Z 分量
     * @return 可写入扩展地形顶点格式的效果 UV
     */
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

    /**
     * 把归一化效果坐标编码为 `VertexConsumer.setUv1` 接受的有符号 16-bit 分量。
     *
     * @param value 效果坐标，超出 `0.0F..1.0F` 的部分会被截断
     * @return 保留完整 16-bit 位模式的有符号整数
     */
    fun pack(value: Float): Int {
        return (value.coerceIn(0F, 1F) * 65535F).toInt() - 32768
    }

    /**
     * 把世界坐标折回固定周期，降低远离原点时转换为 `Float` 的精度损失。
     *
     * @param value 原始世界坐标
     * @return 位于 `0.0F..<1024.0F` 的周期坐标
     */
    fun periodicWorldCoordinate(value: Double): Float {
        val period = 1024.0
        return (value - floor(value / period) * period).toFloat()
    }

    /**
     * 保留两个 lightmap 分量的低八位，并在空闲高位写入 16-bit 生效 tick。
     *
     * shader 以模 `65536` 的方式比较该值与当前游戏 tick，因此这里只保存绝对 tick 的低 16 位。
     *
     * @param packedLight 原版打包后的 sky light 和 block light
     * @param activatedAt 该顶点所属效果位置的绝对生效 tick
     * @return 同时携带原版光照低八位和生效 tick 的打包整数
     */
    fun packLightWithActivation(packedLight: Int, activatedAt: Long): Int {
        val activationTick = (activatedAt and 0xFFFFL).toInt()
        val lightU = packedLight and 0xFFFF
        val lightV = (packedLight ushr 16) and 0xFFFF
        val packedU = (lightU and 0xFF) or ((activationTick and 0xFF) shl 8)
        val packedV = (lightV and 0xFF) or (((activationTick ushr 8) and 0xFF) shl 8)
        return packedU or (packedV shl 16)
    }

    private fun fractional(value: Float): Float = value - floor(value)
}
