package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap

/**
 * 方块 particle icon 与生成点颜色的解析结果。
 *
 * Example: FallingDust 和 CParticle resolver 共用同一结果。
 * Forbidden: 不要把 [colorMultiplier] 缓存为 BlockState 的全局颜色。
 *
 * @property sprite 方块模型的 particle icon
 * @property colorMultiplier 已包含可选 tint 和 `0.6` 亮度的 RGB
 */
internal data class CParticleBlockAppearance(
    val sprite: TextureAtlasSprite,
    val colorMultiplier: Vector3f,
)

/**
 * 复用 FallingDust 的方块 sprite、颜色与 1/4 UV 规则。
 *
 * Example: resolver 在粒子生成时传入当前 level 和 BlockPos。
 * Forbidden: 每帧渲染不能调用 [resolve]。
 */
internal object CParticleBlockAppearanceResolver {
    private val spriteCache = ConcurrentHashMap<BlockState, TextureAtlasSprite>()

    /**
     * 判断方块是否能提供可见 particle icon。
     *
     * Example: 普通石头返回 `true`。
     * Forbidden: 空气和 `RenderShape.INVISIBLE` 必须返回 `false`。
     *
     * @param state 要检查的方块状态
     * @return 状态可用于方块粒子时返回 `true`
     */
    fun isRenderable(state: BlockState): Boolean = !state.isAir && state.renderShape != RenderShape.INVISIBLE

    /**
     * 解析方块模型 sprite，并在当前生成点计算颜色。
     *
     * Example: biome tint 会读取 [level] 和 [pos]。
     * Forbidden: 不可见状态不会回退为任意方块模型，而是返回 `null`。
     *
     * @param state 方块状态
     * @param level 当前客户端世界，可为空
     * @param pos 粒子生成位置
     * @param applyTint 是否查询 BlockColors
     * @param applyBrightness 是否乘以 `0.6`
     * @return 可见方块的 appearance，否则 `null`
     */
    fun resolve(
        state: BlockState,
        level: ClientLevel?,
        pos: BlockPos,
        applyTint: Boolean,
        applyBrightness: Boolean,
    ): CParticleBlockAppearance? {
        if (!isRenderable(state)) return null
        val minecraft = Minecraft.getInstance()
        val sprite = spriteCache.getOrPut(state) {
            minecraft.blockRenderer.blockModelShaper.getParticleIcon(state)
        }
        return CParticleBlockAppearance(
            sprite,
            colorMultiplier(state, level, pos, applyTint, applyBrightness),
        )
    }

    /**
     * 计算 FallingDust 风格颜色倍率。
     *
     * 草方块保持原版 FallingDust 的不染色行为；其他状态按 tint index `0` 查询。
     * Example: `applyTint=false, applyBrightness=true` 返回 `(0.6, 0.6, 0.6)`。
     * Forbidden: 不要用此结果替代每个生成点的 biome 颜色查询。
     *
     * @param state 方块状态
     * @param level 当前客户端世界
     * @param pos 粒子生成位置
     * @param applyTint 是否应用 BlockColors
     * @param applyBrightness 是否应用 `0.6` 亮度
     * @return RGB 颜色倍率
     */
    fun colorMultiplier(
        state: BlockState,
        level: ClientLevel?,
        pos: BlockPos,
        applyTint: Boolean,
        applyBrightness: Boolean,
    ): Vector3f {
        val brightness = if (applyBrightness) 0.6f else 1f
        if (!applyTint || state.`is`(Blocks.GRASS_BLOCK)) return Vector3f(brightness)
        val packed = Minecraft.getInstance().blockColors.getColor(state, level, pos, 0)
        return colorMultiplier(packed, brightness)
    }

    /**
     * 把 ARGB 整数转换为 RGB 倍率。
     *
     * Example: `0x00FF0000` 和 `0.6` 得到 `(0.6, 0, 0)`。
     * Forbidden: alpha 通道不会写入粒子 alpha。
     *
     * @param packedColor BlockColors 或 ItemColors 返回的 ARGB 值
     * @param brightness 额外亮度倍率
     * @return RGB 浮点倍率
     */
    fun colorMultiplier(packedColor: Int, brightness: Float = 1f): Vector3f {
        return Vector3f(
            brightness * (packedColor shr 16 and 255) / 255f,
            brightness * (packedColor shr 8 and 255) / 255f,
            brightness * (packedColor and 255) / 255f,
        )
    }

    /**
     * 按现有 FallingDust 的连续偏移截取 sprite 的 1/4 区域。
     *
     * Example: `uOffset=0, vOffset=0` 选择左上 1/4，并保留原实现的 U 朝向。
     * Forbidden: offset 应在 `[0, 3]` 内，超出值会被夹紧。
     *
     * @param sprite 图集 sprite
     * @param uOffset 水平连续偏移
     * @param vOffset 垂直连续偏移
     * @return 裁剪后的图集 UV
     */
    fun quarterUv(sprite: TextureAtlasSprite, uOffset: Float, vOffset: Float): CParticleUv {
        val u = uOffset.coerceIn(0f, 3f)
        val v = vOffset.coerceIn(0f, 3f)
        return CParticleUv(
            sprite.getU((u + 1f) / 4f),
            sprite.getV(v / 4f),
            sprite.getU(u / 4f),
            sprite.getV((v + 1f) / 4f),
        )
    }

    /**
     * 清除只含模型 sprite 的缓存。
     *
     * Example: atlas stitch 完成后调用一次，下一次生成会取新 sprite。
     * Forbidden: 不要在普通帧中反复清理。
     */
    fun clearCache() {
        spriteCache.clear()
    }
}
