@file:JvmName("CParticleTextures")

package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block as MinecraftBlock
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector3f

/**
 * CParticle 的不可变纹理来源。
 *
 * 来源对象只在生成或 DYNAMIC 配置变化时解析；STATIC 槽位仅保存描述符 ID、flags 和最终颜色。
 * 赋给 [CParticle.textureSource] 时会作为额外蒙版；effect provider 也可用它声明基础纹理。
 * Example: `particle.textureSource = textureOfBlock(state)` 会在基础粒子上叠加方块纹理。
 * Forbidden: 不要在服务端类加载路径中主动解析这些客户端纹理来源。
 */
sealed interface CParticleTextureSource {
    /**
     * 提供纹理来源的网络编解码。
     *
     * Example: `ControlableCParticleData.PACKET_CODEC` 用它同步当前 data 的纹理来源。
     * Forbidden: descriptor ID 和解析后的 UV 不属于网络数据，不能写入这里。
     */
    companion object {
        /**
         * 单个 atlas 动画允许同步的最大帧数。
         *
         * Example: 常规材质动画通常只有几帧到几十帧，远低于 `4096`。
         * Forbidden: 超出此上限的序列不能构造、编码或从网络解码。
         */
        const val MAX_ATLAS_ANIMATION_FRAMES: Int = 4096

        /**
         * 支持所有公共纹理来源的 registry-aware stream codec。
         *
         * Example: 可注册到 `CodecHelper` 后用于 `@CodecField`。
         * Forbidden: 不要用普通 `FriendlyByteBuf` 编解码 ItemStack 或 ParticleOptions。
         */
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CParticleTextureSource> =
            StreamCodec.of(::encodeSource, ::decodeSource)

        /**
         * 写入来源类型和该类型的原始配置。
         *
         * Example: Item 来源写入构造时保存的 stack 快照，而不是调用方原对象。
         * Forbidden: 编码端的类型编号必须与 [decodeSource] 保持一致。
         *
         * @param buf 目标网络缓冲区
         * @param source 要编码的纹理来源
         */
        private fun encodeSource(
            buf: RegistryFriendlyByteBuf,
            source: CParticleTextureSource,
        ) {
            when (source) {
                is ParticleEffect -> {
                    buf.writeByte(0)
                    ParticleTypes.STREAM_CODEC.encode(buf, source.effect)
                    buf.writeBoolean(source.animateByAge)
                }
                is AtlasSprite -> {
                    buf.writeByte(1)
                    buf.writeResourceLocation(source.atlasLocation)
                    buf.writeResourceLocation(source.spriteLocation)
                }
                is AtlasAnimation -> {
                    require(source.spriteLocations.size <= MAX_ATLAS_ANIMATION_FRAMES) {
                        "CParticle atlas animation exceeds $MAX_ATLAS_ANIMATION_FRAMES frames"
                    }
                    buf.writeByte(2)
                    buf.writeResourceLocation(source.atlasLocation)
                    buf.writeVarInt(source.spriteLocations.size)
                    source.spriteLocations.forEach(buf::writeResourceLocation)
                }
                is Block -> {
                    buf.writeByte(3)
                    ByteBufCodecs.idMapper<BlockState>(MinecraftBlock.BLOCK_STATE_REGISTRY)
                        .encode(buf, source.state)
                    buf.writeBoolean(source.randomCrop)
                    buf.writeBoolean(source.applyTint)
                    buf.writeBoolean(source.applyBrightness)
                }
                is Item -> {
                    buf.writeByte(4)
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, source.resolveStack())
                    buf.writeInt(source.modelSeed)
                    buf.writeBoolean(source.applyTint)
                    buf.writeInt(source.tintIndex)
                }
                is Custom -> {
                    buf.writeByte(5)
                    buf.writeResourceLocation(source.textureLocation)
                    writeUv(buf, source.uv)
                }
            }
        }

        /**
         * 按来源类型恢复尚未经过客户端模型解析的配置。
         *
         * Example: 解码 Item 时构造函数会再次建立自己的 ItemStack 快照。
         * Forbidden: 未知类型编号必须报错，不能回退到 missing texture 掩盖协议不一致。
         *
         * @param buf 来源网络缓冲区
         * @return 解码得到的纹理来源
         * @throws IllegalArgumentException 类型编号未知或动画帧数量无效时抛出
         */
        private fun decodeSource(buf: RegistryFriendlyByteBuf): CParticleTextureSource {
            return when (val type = buf.readUnsignedByte().toInt()) {
                0 -> ParticleEffect(
                    ParticleTypes.STREAM_CODEC.decode(buf),
                    animateByAge = buf.readBoolean(),
                )
                1 -> AtlasSprite(
                    buf.readResourceLocation(),
                    buf.readResourceLocation(),
                )
                2 -> {
                    val atlas = buf.readResourceLocation()
                    val frameCount = buf.readVarInt()
                    require(frameCount in 1..MAX_ATLAS_ANIMATION_FRAMES) {
                        "CParticle atlas animation frame count is invalid: $frameCount"
                    }
                    AtlasAnimation(
                        atlas,
                        List(frameCount) { buf.readResourceLocation() },
                    )
                }
                3 -> Block(
                    ByteBufCodecs.idMapper<BlockState>(MinecraftBlock.BLOCK_STATE_REGISTRY).decode(buf),
                    randomCrop = buf.readBoolean(),
                    applyTint = buf.readBoolean(),
                    applyBrightness = buf.readBoolean(),
                )
                4 -> Item(
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    modelSeed = buf.readInt(),
                    applyTint = buf.readBoolean(),
                    tintIndex = buf.readInt(),
                )
                5 -> Custom(
                    buf.readResourceLocation(),
                    readUv(buf),
                )
                else -> throw IllegalArgumentException("Unknown CParticle texture source type: $type")
            }
        }

        /**
         * 按固定顺序写入四个 UV 分量。
         *
         * Example: `0, 0, 1, 1` 往返后仍表示整张纹理。
         * Forbidden: 不要在此处归一化或交换坐标，UV 允许翻转。
         *
         * @param buf 目标网络缓冲区
         * @param uv 要编码的 UV 区域
         */
        private fun writeUv(buf: RegistryFriendlyByteBuf, uv: CParticleUv) {
            buf.writeFloat(uv.u0)
            buf.writeFloat(uv.v0)
            buf.writeFloat(uv.u1)
            buf.writeFloat(uv.v1)
        }

        /**
         * 读取四个 UV 分量并交给 [CParticleUv] 校验有限值。
         *
         * Example: 自定义纹理可以恢复调用方指定的子区域。
         * Forbidden: NaN 和无穷值不能绕过 [CParticleUv] 的构造校验。
         *
         * @param buf 来源网络缓冲区
         * @return 解码得到的 UV 区域
         */
        private fun readUv(buf: RegistryFriendlyByteBuf): CParticleUv = CParticleUv(
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
            buf.readFloat(),
        )
    }

    /**
     * 包装原版 [ParticleOptions]，使用其 SpriteSet 作为 GPU age 动画。
     *
     * Example: `textureOfEffect(ParticleTypes.END_ROD)`。
     * Forbidden: 不要把粒子类型的注册表数值 ID 当成 GPU 描述符 ID。
     *
     * @property effect 用于查找 SpriteSet 的原版粒子参数
     * @property animateByAge 是否由 shader 按 age/maxAge 选择 SpriteSet 帧
     */
    data class ParticleEffect(
        val effect: ParticleOptions,
        val animateByAge: Boolean = true,
    ) : CParticleTextureSource

    /**
     * 从任意已 stitch 图集中选择一个 sprite。
     *
     * Example: `textureOfAtlas(TextureAtlas.LOCATION_BLOCKS, blockTexture)`。
     * Forbidden: [atlasLocation] 必须是图集资源 ID，不能传 sprite 自身的图片路径。
     *
     * @property atlasLocation 已 stitch 图集的资源 ID
     * @property spriteLocation 图集中的 sprite ID
     */
    data class AtlasSprite(
        val atlasLocation: ResourceLocation,
        val spriteLocation: ResourceLocation,
    ) : CParticleTextureSource

    /**
     * 在同一图集中按 age/maxAge 播放一组 sprite。
     *
     * Example: `textureAnimationOfAtlas(atlas, listOf(frame0, frame1))`。
     * Forbidden: 不要把来自不同图集的帧放进同一来源。
     *
     * @property atlasLocation 所有帧所属的图集
     * @param spriteLocations 按播放顺序排列且不能为空的 sprite ID
     * @throws IllegalArgumentException 帧列表为空或超过 [MAX_ATLAS_ANIMATION_FRAMES] 时抛出
     */
    class AtlasAnimation(
        val atlasLocation: ResourceLocation,
        spriteLocations: List<ResourceLocation>,
    ) : CParticleTextureSource {
        /** 创建来源时复制的只读帧顺序。 */
        val spriteLocations: List<ResourceLocation> = spriteLocations.toList()

        init {
            require(this.spriteLocations.size in 1..MAX_ATLAS_ANIMATION_FRAMES) {
                "Atlas animation frame count must be in 1..$MAX_ATLAS_ANIMATION_FRAMES"
            }
        }

    }

    /**
     * 使用 BlockState 的模型 particle icon，并可应用 FallingDust 风格裁剪和颜色。
     *
     * Example: `textureOfBlock(state, randomCrop = true, applyTint = true)`。
     * Forbidden: 不要缓存依赖世界和位置的最终颜色；解析器会在生成时计算它。
     *
     * @property state 用于模型和颜色解析的方块状态
     * @property randomCrop 是否在 shader 中稳定选择 1/4 UV 区域
     * @property applyTint 是否查询 BlockColors
     * @property applyBrightness 是否乘以 FallingDust 使用的 `0.6` 亮度
     */
    data class Block(
        val state: BlockState,
        val randomCrop: Boolean = true,
        val applyTint: Boolean = true,
        val applyBrightness: Boolean = true,
    ) : CParticleTextureSource

    /**
     * 使用 ItemRenderer 解析后的 BakedModel particle icon。
     *
     * 构造时复制 ItemStack，后续调用方修改原 stack 不会改变来源。
     * Example: `textureOfItem(stack, modelSeed = 42, tintIndex = 0)`。
     * Forbidden: 不要期待它渲染完整三维模型；多层模型只取代表性的 particle icon。
     *
     * @param stack 要快照的物品堆
     * @property modelSeed 传给 `ItemRenderer.getModel` 的 override 随机种子
     * @property applyTint 是否使用 ItemColors
     * @property tintIndex ItemColors 的 tint 层，默认 `0`
     */
    class Item internal constructor(
        stack: ItemStack,
        val modelSeed: Int = 0,
        val applyTint: Boolean = true,
        val tintIndex: Int = 0,
    ) : CParticleTextureSource {
        private val snapshot = stack.copy()

        /**
         * 返回生成时 ItemStack 快照的副本。
         *
         * Example: 调试工具可以读取 `source.stackCopy()` 而不会改动解析输入。
         * Forbidden: 修改返回值不会更新已经创建的纹理来源。
         *
         * @return 与构造时 item/components 相同的新 ItemStack
         */
        fun stackCopy(): ItemStack = snapshot.copy()

        /**
         * 把只读快照交给客户端解析器，避免每次解析再复制一次。
         *
         * Example: 资源重载后描述符注册表用它重新调用 ItemRenderer。
         * Forbidden: 解析器不得修改返回的内部快照。
         *
         * @return 此来源私有持有的 ItemStack 快照
         */
        internal fun resolveStack(): ItemStack = snapshot
    }

    /**
     * 使用一张独立纹理和指定 UV 区域。
     *
     * Example: `textureOf(id, CParticleUv(0f, 0f, 0.5f, 1f))`。
     * Forbidden: atlas sprite 应使用 [AtlasSprite]，否则 UV 不会经过 stitch 解析。
     *
     * @property textureLocation 独立纹理资源 ID
     * @property uv 纹理内区域，默认整张纹理
     */
    data class Custom(
        val textureLocation: ResourceLocation,
        val uv: CParticleUv = CParticleUv.FULL,
    ) : CParticleTextureSource
}

/**
 * 允许 [ParticleOptions] 或其他兼容对象声明自己的 CParticle 纹理来源。
 *
 * Example: 方块尘效果返回 [CParticleTextureSource.Block]，emitter 会自动进入方块图集批次。
 * Forbidden: 实现不得在这里访问客户端模型；模型查询由 [CParticleTextureResolver] 负责。
 */
fun interface CParticleTextureSourceProvider {
    /**
     * 返回尚未解析的纹理来源。
     *
     * Example: `CParticleTextureSource.Block(state)` 会在粒子生成时解析模型 particle icon。
     * Forbidden: 不要返回已经缓存的 UV 或 GL texture id。
     *
     * @return 可交给客户端统一解析器处理的纹理来源
     */
    fun cparticleTextureSource(): CParticleTextureSource
}

/**
 * 客户端统一纹理解析结果。
 *
 * [descriptorId] 表示静态 UV；[animationId] 非空时，实例实际使用动画描述符。
 * Example: effect 可返回首帧 descriptor 和独立的动画 descriptor。
 * Forbidden: 不要在这里保存 CParticle、BlockState 或 ItemStack。
 *
 * @property bindingKey 当前已解析来源使用的纹理绑定
 * @property descriptorId 静态 UV 描述符 ID
 * @property uv 静态描述符对应的基础 UV；随机裁剪在 shader 中完成
 * @property animationId 可选 GPU age 动画描述符 ID
 * @property colorMultiplier 生成时与粒子颜色相乘的 RGB
 */
data class CParticleResolvedTexture(
    val bindingKey: CParticleTextureBindingKey,
    val descriptorId: Int,
    val uv: CParticleUv,
    val animationId: Int?,
    val colorMultiplier: Vector3f,
) {
    /**
     * 此结果是否能进入粒子系统。
     *
     * Example: 空气方块和空 ItemStack 的解析结果为 `false`。
     * Forbidden: 显式缺失的 Custom 纹理仍有自己的 texture binding，不能当作无效来源丢弃。
     */
    val isValid: Boolean
        get() = bindingKey != CParticleTextureBindingKey.MISSING
}

/**
 * 创建 BlockState 纹理来源，赋给 [CParticle.textureSource] 时作为额外蒙版。
 *
 * Example: `particle.textureSource = textureOfBlock(state)` 会保留原有粒子纹理。
 * Forbidden: 世界相关 tint 不会在此函数中提前计算。
 *
 * @param state 方块状态
 * @param randomCrop 是否启用 FallingDust 风格 1/4 随机裁剪
 * @param applyTint 是否应用 BlockColors
 * @param applyBrightness 是否应用 `0.6` 亮度倍率
 * @return 不可变的方块纹理来源
 */
@JvmOverloads
fun textureOfBlock(
    state: BlockState,
    randomCrop: Boolean = true,
    applyTint: Boolean = true,
    applyBrightness: Boolean = true,
): CParticleTextureSource = CParticleTextureSource.Block(state, randomCrop, applyTint, applyBrightness)

/**
 * 创建 ItemStack 纹理来源，并立即复制 stack；赋给 [CParticle.textureSource] 时作为额外蒙版。
 *
 * 默认 [tintIndex] 是 `0`，对应多数单层染色物品的第一层；多层物品可显式选择其他层。
 * Example: `particle.textureSource = textureOfItem(stack, modelSeed = 7, tintIndex = 1)`。
 * Forbidden: 之后修改原 stack 不会改变已创建来源，需重新调用本函数。
 *
 * @param stack 要复制的物品堆
 * @param modelSeed ItemRenderer 模型 override 种子
 * @param applyTint 是否查询 ItemColors
 * @param tintIndex ItemColors 层索引，默认 `0`
 * @return 持有 stack 快照的物品纹理来源
 */
@JvmOverloads
fun textureOfItem(
    stack: ItemStack,
    modelSeed: Int = 0,
    applyTint: Boolean = true,
    tintIndex: Int = 0,
): CParticleTextureSource = CParticleTextureSource.Item(stack, modelSeed, applyTint, tintIndex)

/**
 * 创建独立纹理来源。
 *
 * Example: `textureOf(id)` 使用完整 UV。
 * Forbidden: 不要把 atlas sprite ID 直接传给此函数。
 *
 * @param textureLocation 独立纹理资源 ID
 * @param uv 自定义 UV，默认 [CParticleUv.FULL]
 * @return 独立纹理来源
 */
@JvmOverloads
fun textureOf(
    textureLocation: ResourceLocation,
    uv: CParticleUv = CParticleUv.FULL,
): CParticleTextureSource = CParticleTextureSource.Custom(textureLocation, uv)

/**
 * 创建单帧 atlas sprite 来源。
 *
 * Example: `textureOfAtlas(TextureAtlas.LOCATION_BLOCKS, id)`。
 * Forbidden: [atlasLocation] 必须对应已 stitch 图集。
 *
 * @param atlasLocation 图集资源 ID
 * @param spriteLocation 图集内 sprite ID
 * @return atlas sprite 来源
 */
fun textureOfAtlas(
    atlasLocation: ResourceLocation,
    spriteLocation: ResourceLocation,
): CParticleTextureSource = CParticleTextureSource.AtlasSprite(atlasLocation, spriteLocation)

/**
 * 创建同一图集内的 GPU age 动画来源。
 *
 * Example: `textureAnimationOfAtlas(atlas, listOf(frame0, frame1))`。
 * Forbidden: 列表不能为空，也不能混用其他图集的逻辑帧。
 *
 * @param atlasLocation 所有帧所属图集
 * @param spriteLocations 有序且非空的 sprite ID
 * @return atlas 动画来源
 */
fun textureAnimationOfAtlas(
    atlasLocation: ResourceLocation,
    spriteLocations: List<ResourceLocation>,
): CParticleTextureSource = CParticleTextureSource.AtlasAnimation(atlasLocation, spriteLocations.toList())

/**
 * 创建原版 ParticleEffect SpriteSet 来源。
 *
 * Example: `textureOfEffect(ParticleTypes.END_ROD)`。
 * Forbidden: 不要在服务端尝试解析返回来源的 SpriteSet。
 *
 * @param effect 用于查找粒子类型 SpriteSet 的参数
 * @return effect 纹理来源
 */
fun textureOfEffect(effect: ParticleOptions): CParticleTextureSource =
    (effect as? CParticleTextureSourceProvider)?.cparticleTextureSource()
        ?: CParticleTextureSource.ParticleEffect(effect)

/**
 * 创建粒子图集中的固定 sprite 来源，供 legacy `CParticle.sprite` 转换使用。
 *
 * Example: `textureOfParticleSprite(CParticleSprites.DEFAULT)` 保留旧的末影烛首帧语义。
 * Forbidden: 方块图集 sprite 应调用 [textureOfAtlas] 并传入方块图集。
 *
 * @param spriteLocation 粒子图集中的 sprite ID
 * @return 固定粒子 sprite 来源
 */
fun textureOfParticleSprite(spriteLocation: ResourceLocation): CParticleTextureSource =
    if (spriteLocation == CParticleSprites.DEFAULT) {
        CParticleTextureSource.ParticleEffect(ParticleTypes.END_ROD, animateByAge = false)
    } else {
        CParticleTextureSource.AtlasSprite(TextureAtlas.LOCATION_PARTICLES, spriteLocation)
    }
