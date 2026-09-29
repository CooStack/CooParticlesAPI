package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.entities.structure.StructureSnapshot
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.material.FluidState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3
import net.minecraft.world.level.BlockAndTintGetter
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.ColorResolver
import net.minecraft.world.level.lighting.LevelLightEngine
import net.minecraft.client.renderer.LightTexture
import org.joml.Matrix4f
import org.joml.Vector3f
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap
import java.util.IdentityHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.SectionPos

/** 将结构内邻接查询与变换后位置的真实光照、染色组合，供方块、流体和方块实体显示使用。
 * @property snapshot 提供局部坐标方块与流体
 * @property world 提供展示位置的光照和生物群系
 * @property origin 插值后的结构原点，保留小数和大世界坐标精度
 * @property transform 结构局部变换，不包含相机或世界原点，构造后调用方不得修改
 * @property displayLight 物品和 GUI 展示的指定亮度；世界实体使用空值逐格采样
 * @property displays 键为结构局部坐标，值为本次渲染的只读展示方块实体
 */
class StructureRenderView(private val snapshot: StructureSnapshot, private val world: Level, private val origin: Vec3,
                      private val transform: Matrix4f, private val displayLight: Int?,
                      private val displays: Map<BlockPos, BlockEntity> = emptyMap()) : BlockAndTintGetter {
    /** 仅存活于一次模型绘制，键为局部格坐标，值为天空光及方块光的打包值。 */
    private val lightSamples = Long2IntOpenHashMap().apply { defaultReturnValue(-1) }
    /** 按染色器身份隔离，每个局部格在本次绘制只查询一次生物群系颜色。 */
    private val colors = IdentityHashMap<ColorResolver, Long2IntOpenHashMap>()
    /** 当前绘制实际依赖的世界区段，供持久网格精确响应光照失效，不跨帧复用。 */
    internal val sampledSections = LongOpenHashSet()
    override fun getBlockState(pos: BlockPos): BlockState = snapshot.getBlockState(pos)
    override fun getFluidState(pos: BlockPos): FluidState = snapshot.getFluidState(pos)
    override fun getBlockEntity(pos: BlockPos): BlockEntity? = displays[pos]
    override fun getHeight(): Int = snapshot.height
    override fun getMinBuildHeight(): Int = 0
    override fun getShade(direction: Direction, shaded: Boolean): Float = world.getShade(direction, shaded)
    override fun getLightEngine(): LevelLightEngine = world.lightEngine
    override fun getBlockTint(pos: BlockPos, resolver: ColorResolver): Int {
        val samples = colors.getOrPut(resolver) { Long2IntOpenHashMap() }
        val key = pos.asLong()
        if (samples.containsKey(key)) return samples.get(key)
        val sample = samplePosition(pos)
        sampledSections.add(SectionPos.blockToSection(sample.asLong()))
        return world.getBlockTint(sample, resolver).also { samples.put(key, it) }
    }

    /** 保留展示入口指定的天空光和方块光；世界中的结构按变换后位置采样，不复用实体身体亮度。 */
    override fun getBrightness(type: LightLayer, pos: BlockPos): Int {
        displayLight?.let {
            return if (type == LightLayer.SKY) LightTexture.sky(it)
                else LightTexture.block(it)
        }
        val key = pos.asLong()
        var packed = lightSamples.get(key)
        if (packed == -1) {
            val sample = samplePosition(pos)
            sampledSections.add(SectionPos.blockToSection(sample.asLong()))
            packed = LightTexture.pack(
                world.getBrightness(LightLayer.BLOCK, sample), world.getBrightness(LightLayer.SKY, sample)
            )
            lightSamples.put(key, packed)
        }
        return if (type == LightLayer.SKY) LightTexture.sky(packed)
            else LightTexture.block(packed)
    }

    /**
     * 将局部格中心映射到实际世界格，用于光照和生物群系查询。
     * @param pos 结构局部格坐标，也允许流体查询相邻格
     * @return 经过缩放、旋转、支点补偿和位移后的采样格
     */
    fun samplePosition(pos: BlockPos): BlockPos {
        val point = transform.transformPosition(Vector3f(pos.x + 0.5F, pos.y + 0.5F, pos.z + 0.5F))
        return BlockPos.containing(origin.x + point.x, origin.y + point.y, origin.z + point.z)
    }
}
