package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.mixin.MutableSpriteSetAccessor
import cn.coostack.cooparticlesapi.renderer.backend.glTextureId
import cn.coostack.cooparticlesapi.renderer.backend.glSamplerId
import net.minecraft.client.renderer.texture.AbstractTexture
import cn.coostack.cooparticlesapi.mixin.ParticleEngineAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.util.RandomSource
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap

/**
 * 客户端纹理来源解析器。
 *
 * 它只在 spawn、DYNAMIC 来源变化或资源重载时解析模型，不参与普通 age 推进。
 * 示例： [CParticleSystem] 在写入 STATIC 槽位前调用 [resolve] 一次。
 * 禁止： 服务端逻辑不能加载此对象。
 */
object CParticleTextureResolver {
    private data class AtlasSpriteKey(
        val bindingKey: CParticleTextureBindingKey,
        val sprite: Identifier,
    )

    private data class AtlasAnimationKey(
        val bindingKey: CParticleTextureBindingKey,
        val sprites: List<Identifier>,
    )

    private data class EffectKey(val typeId: Identifier, val animation: Boolean)

    private data class BlockKey(val state: net.minecraft.world.level.block.state.BlockState)

    private data class CustomKey(val bindingKey: CParticleTextureBindingKey, val uv: CParticleUv)

    /**
     * 按 ItemStack 内容和 model seed 复用的模型描述符键。
     *
     * 键只在共享 descriptor 表中保存一份 stack 快照，资源重载后可再次调用 ItemRenderer。
     * 示例： 十万份相同钻石 stack 只注册一个模型描述符。
     * 禁止： 不能用 ItemStack 引用相等，否则会把 descriptor 数量放大到粒子数量。
     *
     * @param sourceStack 创建纹理来源时保存的 stack 快照
     * @property modelSeed 传给 ItemRenderer 的模型随机种子
     * @property bindingKey 首次解析模型时得到的图集绑定
     */
    internal class ItemDescriptorKey(
        sourceStack: ItemStack,
        val modelSeed: Int,
        val bindingKey: CParticleTextureBindingKey,
    ) {
        /** 供资源重载重新解析模型使用的共享快照。 */
        val stack: ItemStack = sourceStack.copy()

        /** 按 item、components、count 和 model seed 判断模型输入是否相同。 */
        override fun equals(other: Any?): Boolean {
            return other is ItemDescriptorKey &&
                    modelSeed == other.modelSeed &&
                    bindingKey == other.bindingKey &&
                    stack.count == other.stack.count &&
                    ItemStack.isSameItemSameComponents(stack, other.stack)
        }

        /** 返回与 [equals] 相同字段组成的稳定哈希。 */
        override fun hashCode(): Int {
            var result = ItemStack.hashItemAndComponents(stack)
            result = 31 * result + stack.count
            result = 31 * result + modelSeed
            return 31 * result + bindingKey.hashCode()
        }
    }

    private data class ItemSprite(
        val bindingKey: CParticleTextureBindingKey,
        val sprite: TextureAtlasSprite,
    )

    private val effectSetCache = ConcurrentHashMap<Identifier, SpriteSet>()
    private val customTextureAvailability = HashMap<Identifier, Boolean>()
    private val warnedMissingTextures = HashSet<Identifier>()
    private val warnedMissingAtlases = HashSet<Identifier>()

    /**
     * 当前解析缓存代数。
     *
     * 示例： DYNAMIC 槽位发现代数变化时会重新解析同一来源。
     * 禁止： 普通 tick 不应递增此值。
     */
    @JvmStatic
    var generation: Int = 0
        private set

    /**
     * 把纹理来源解析为 binding、descriptor、基础 UV 和颜色倍率。
     *
     * 示例： Item 来源会调用当前世界的 `ItemRenderer.getModel`。
     * 禁止： 返回值不能保存原 CParticle 或每粒子的 ItemStack。
     *
     * @param source 不可变纹理来源
     * @param position 粒子世界坐标，用于 BlockColors
     * @return 可直接写入实例数据的统一描述
     */
    @JvmStatic
    fun resolve(source: CParticleTextureSource, position: net.minecraft.world.phys.Vec3): CParticleResolvedTexture {
        val minecraft = Minecraft.getInstance()
        val blockPos = BlockPos.containing(position)
        return when (source) {
            is CParticleTextureSource.ParticleEffect -> resolveEffect(source)
            is CParticleTextureSource.AtlasSprite -> resolveAtlasSprite(source)
            is CParticleTextureSource.AtlasAnimation -> resolveAtlasAnimation(source)
            is CParticleTextureSource.Block -> resolveBlock(source, minecraft.level, blockPos)
            is CParticleTextureSource.Item -> resolveItem(source)
            is CParticleTextureSource.Custom -> resolveCustom(source)
        }
    }

    /**
     * 返回 binding 当前应绑定的 GL texture id。
     *
     * 示例：atlas key 通过当前资源管理器获取重载后的新纹理对象。
     * 禁止：不要缓存跨资源重载的 GL id。
     *
     * @param bindingKey 系统的基础或蒙版纹理绑定
     * @return 可绑定到 OpenGL 二维纹理单元的对象名
     */
    internal fun textureId(bindingKey: CParticleTextureBindingKey): Int {
        return bindingTexture(bindingKey).texture.glTextureId
    }

    /**
     * 取得与当前资源纹理匹配的原版 sampler，保留图集 mip 和过滤设置。
     * 示例：`glBindSampler(0, CParticleTextureResolver.samplerId(binding))`。
     * @param bindingKey 与纹理解析相同的基础或蒙版绑定
     * @return 原版 sampler 的 GL 对象名，调用方不得删除
     */
    internal fun samplerId(bindingKey: CParticleTextureBindingKey): Int {
        return bindingTexture(bindingKey).sampler.glSamplerId
    }

    /** 同时供纹理和 sampler 解析使用，避免重载后的资源身份不一致。 */
    private fun bindingTexture(bindingKey: CParticleTextureBindingKey): AbstractTexture {
        val minecraft = Minecraft.getInstance()
        if (bindingKey == CParticleTextureBindingKey.MISSING) {
            return minecraft.textureManager.getTexture(MissingTextureAtlasSprite.getLocation())
        }
        return when (bindingKey.kind) {
            CParticleTextureBindingKind.ATLAS -> textureAtlas(bindingKey.location) ?: run {
                if (warnedMissingAtlases.add(bindingKey.location)) {
                    CooParticlesConstants.logger.warn("Missing CParticle texture atlas {}", bindingKey.location)
                }
                minecraft.textureManager.getTexture(MissingTextureAtlasSprite.getLocation())
            }

            CParticleTextureBindingKind.TEXTURE ->
                minecraft.textureManager.getTexture(bindingKey.location)
        }
    }

    /**
     * 返回与 binding 兼容的 missing UV。
     *
     * 示例： 已存在 atlas 中缺失的 sprite 使用该 atlas 的 missing sprite UV。
     * 禁止： atlas missing UV 不能和另一张 atlas 的绑定混用。
     *
     * @param bindingKey 目标纹理绑定
     * @return atlas missing sprite UV 或独立纹理完整 UV
     */
    internal fun missingUv(bindingKey: CParticleTextureBindingKey): CParticleUv {
        if (bindingKey.kind == CParticleTextureBindingKind.TEXTURE) return CParticleUv.FULL
        val sprite = atlasSprite(bindingKey.location, MissingTextureAtlasSprite.getLocation())
            ?: return CParticleUv.FULL
        return uv(sprite)
    }

    /**
     * 清理模型、图集和 SpriteSet 缓存，并让描述符表重建。
     *
     * 示例： atlas stitch 完成后的 full reload 调用一次。
     * 禁止： 不要清空稳定 descriptor ID。
     */
    @JvmStatic
    @Synchronized
    fun invalidate() {
        effectSetCache.clear()
        customTextureAvailability.clear()
        CParticleBlockAppearanceResolver.clearCache()
        generation++
        CParticleTextureDescriptors.invalidate()
    }

    private fun resolveEffect(source: CParticleTextureSource.ParticleEffect): CParticleResolvedTexture {
        val typeId = BuiltInRegistries.PARTICLE_TYPE.getKey(source.effect.type)
            ?: return missingTexture()
        if (!hasEffectFrames(typeId)) return missingTexture()
        val binding = CParticleTextureBindingKey.PARTICLE_ATLAS
        val animationId = if (source.animateByAge) {
            CParticleTextureDescriptors.register(EffectKey(typeId, true), binding) {
                effectFrames(typeId)
            }
        } else {
            null
        }
        val descriptorId = CParticleTextureDescriptors.register(EffectKey(typeId, false), binding) {
            listOf(effectFrames(typeId).first())
        }
        return CParticleResolvedTexture(
            binding,
            descriptorId,
            CParticleTextureDescriptors.firstFrame(descriptorId),
            animationId,
            Vector3f(1F),
        )
    }

    private fun resolveAtlasSprite(source: CParticleTextureSource.AtlasSprite): CParticleResolvedTexture {
        val atlasLocation = source.atlasLocation
        val spriteLocation = source.spriteLocation
        val binding = CParticleTextureBindingKey(CParticleTextureBindingKind.ATLAS, atlasLocation)
        val descriptorId = CParticleTextureDescriptors.register(
            AtlasSpriteKey(binding, spriteLocation),
            binding,
        ) {
            listOf(atlasUv(atlasLocation, spriteLocation))
        }
        return CParticleResolvedTexture(
            binding,
            descriptorId,
            CParticleTextureDescriptors.firstFrame(descriptorId),
            null,
            Vector3f(1F),
        )
    }

    private fun resolveAtlasAnimation(source: CParticleTextureSource.AtlasAnimation): CParticleResolvedTexture {
        val atlasLocation = source.atlasLocation
        val binding = CParticleTextureBindingKey(CParticleTextureBindingKind.ATLAS, atlasLocation)
        val frames = source.spriteLocations.toList()
        val animationId = CParticleTextureDescriptors.register(AtlasAnimationKey(binding, frames), binding) {
            frames.map { atlasUv(atlasLocation, it) }
        }
        val descriptorId = CParticleTextureDescriptors.register(
            AtlasSpriteKey(binding, frames.first()),
            binding,
        ) {
            listOf(atlasUv(source.atlasLocation, frames.first()))
        }
        return CParticleResolvedTexture(
            binding,
            descriptorId,
            CParticleTextureDescriptors.firstFrame(descriptorId),
            animationId,
            Vector3f(1F),
        )
    }

    private fun resolveBlock(
        source: CParticleTextureSource.Block,
        level: ClientLevel?,
        pos: BlockPos,
    ): CParticleResolvedTexture {
        val state = source.state
        val appearance = CParticleBlockAppearanceResolver.resolve(
            state,
            level,
            pos,
            source.applyTint,
            source.applyBrightness,
        ) ?: return missingTexture()
        val binding = CParticleTextureBindingKey.BLOCK_ATLAS
        val descriptorId = CParticleTextureDescriptors.register(BlockKey(state), binding) {
            val refreshed = CParticleBlockAppearanceResolver.resolve(
                state,
                Minecraft.getInstance().level,
                BlockPos.ZERO,
                applyTint = false,
                applyBrightness = false,
            )
            listOf(refreshed?.sprite?.let(::uv) ?: missingUv(binding))
        }
        return CParticleResolvedTexture(
            binding,
            descriptorId,
            uv(appearance.sprite),
            null,
            appearance.colorMultiplier,
        )
    }

    @Synchronized
    private fun resolveItem(source: CParticleTextureSource.Item): CParticleResolvedTexture {
        val stack = source.resolveStack()
        val itemSprite = resolveItemSprite(stack, source.modelSeed) ?: return missingTexture()
        val binding = itemSprite.bindingKey
        val descriptorKey = ItemDescriptorKey(stack, source.modelSeed, binding)
        val descriptorId = CParticleTextureDescriptors.register(
            descriptorKey,
            binding,
        ) {
            val refreshed = resolveItemSprite(descriptorKey.stack, descriptorKey.modelSeed)
            listOf(
                if (refreshed?.bindingKey == descriptorKey.bindingKey) uv(refreshed.sprite)
                else missingUv(descriptorKey.bindingKey)
            )
        }
        val color = if (source.applyTint) {
            CParticleBlockAppearanceResolver.colorMultiplier(
                itemTintColor(stack, source.tintIndex)
            )
        } else {
            Vector3f(1F)
        }
        return CParticleResolvedTexture(
            binding,
            descriptorId,
            uv(itemSprite.sprite),
            null,
            color,
        )
    }

    private fun resolveCustom(source: CParticleTextureSource.Custom): CParticleResolvedTexture {
        val textureLocation = source.textureLocation
        val resolvedUv = source.uv
        val binding = CParticleTextureBindingKey(CParticleTextureBindingKind.TEXTURE, textureLocation)
        val minecraft = Minecraft.getInstance()
        val exists = customTextureAvailability.getOrPut(textureLocation) {
            minecraft.resourceManager.getResource(textureLocation).isPresent
        }
        if (!exists &&
            warnedMissingTextures.add(textureLocation)
        ) {
            CooParticlesConstants.logger.warn(
                "Missing CParticle texture {}, using the missing texture",
                textureLocation,
            )
        }
        val descriptorId = CParticleTextureDescriptors.register(CustomKey(binding, resolvedUv), binding) {
            listOf(resolvedUv)
        }
        return CParticleResolvedTexture(binding, descriptorId, resolvedUv, null, Vector3f(1F))
    }

    private fun resolveItemSprite(stack: ItemStack, modelSeed: Int): ItemSprite? {
        val minecraft = Minecraft.getInstance()
        if (stack.isEmpty) return null
        val modelState = ItemStackRenderState()
        minecraft.itemModelResolver.updateForTopItem(
            modelState,
            stack,
            ItemDisplayContext.NONE,
            minecraft.level,
            minecraft.player,
            modelSeed,
        )
        val material = modelState.pickParticleMaterial(RandomSource.create(modelSeed.toLong())) ?: return null
        val sprite = material.sprite()
        return ItemSprite(
            CParticleTextureBindingKey(CParticleTextureBindingKind.ATLAS, sprite.atlasLocation()),
            sprite,
        )
    }

    private fun itemTintColor(stack: ItemStack, tintIndex: Int): Int {
        val customColors = stack.get(DataComponents.CUSTOM_MODEL_DATA)?.colors()
        if (customColors != null && tintIndex in customColors.indices) {
            return customColors[tintIndex]
        }
        val dyedColor = stack.get(DataComponents.DYED_COLOR)
        if (tintIndex == 0 && dyedColor != null) {
            return dyedColor.rgb()
        }
        val potionContents = stack.get(DataComponents.POTION_CONTENTS)
        if (tintIndex == 0 && potionContents != null) {
            return potionContents.getColorOr(PotionContents.BASE_POTION_COLOR)
        }
        return 0xFFFFFF
    }

    internal fun effectFrames(typeId: Identifier): List<CParticleUv> {
        val set = effectSet(typeId) ?: return listOf(defaultEffectUv())
        val sprites = runCatching {
            (set as MutableSpriteSetAccessor).particleSprites
        }.getOrNull()
        return sprites?.takeIf { it.isNotEmpty() }?.map(::uv) ?: listOf(defaultEffectUv())
    }

    /**
     * 检查隐式 effect 是否有可用的 SpriteSet 帧。
     *
     * 示例： 已注册的 end rod effect 返回 `true`。
     * 禁止： 没有 SpriteSet 的 effect 不能继续注册 missing UV 描述符。
     *
     * @param typeId 粒子类型资源 ID
     * @return SpriteSet 存在且至少包含一帧时返回 `true`
     */
    private fun hasEffectFrames(typeId: Identifier): Boolean {
        val set = effectSet(typeId) ?: return false
        val sprites = runCatching {
            (set as MutableSpriteSetAccessor).particleSprites
        }.getOrNull()
        return !sprites.isNullOrEmpty()
    }

    private fun effectSet(typeId: Identifier): SpriteSet? {
        effectSetCache[typeId]?.let { return it }
        val engine = Minecraft.getInstance().particleEngine as? ParticleEngineAccessor ?: return null
        val loaded = engine.spriteSets[typeId] ?: return null
        return effectSetCache.putIfAbsent(typeId, loaded) ?: loaded
    }

    private fun defaultEffectUv(): CParticleUv {
        val endRodId = BuiltInRegistries.PARTICLE_TYPE.getKey(ParticleTypes.END_ROD)
        val set = endRodId?.let(::effectSet)
        val sprite = runCatching { set?.get(0, 1) }.getOrNull()
        return sprite?.let(::uv)
            ?: atlasUv(CParticleTextureBindingKey.PARTICLE_ATLAS.location, MissingTextureAtlasSprite.getLocation())
    }

    private fun atlasUv(atlas: Identifier, sprite: Identifier): CParticleUv {
        return atlasSprite(atlas, sprite)?.let(::uv)
            ?: CParticleUv.FULL
    }

    private fun atlasSprite(atlas: Identifier, sprite: Identifier): TextureAtlasSprite? {
        return textureAtlas(atlas)?.getSprite(sprite)
    }

    private fun textureAtlas(atlas: Identifier): TextureAtlas? {
        val minecraft = Minecraft.getInstance()
        val stitched = minecraft.textureManager.getTexture(atlas) as? TextureAtlas
        if (stitched != null) return stitched
        return runCatching { minecraft.atlasManager.getAtlasOrThrow(atlas) }.getOrNull()
    }

    private fun uv(sprite: TextureAtlasSprite): CParticleUv =
        CParticleUv(sprite.u0, sprite.v0, sprite.u1, sprite.v1)

    private fun missingTexture(): CParticleResolvedTexture {
        val binding = CParticleTextureBindingKey.MISSING
        val descriptorId = CParticleTextureDescriptors.register(CustomKey(binding, CParticleUv.FULL), binding) {
            listOf(CParticleUv.FULL)
        }
        return CParticleResolvedTexture(binding, descriptorId, CParticleUv.FULL, null, Vector3f(1F))
    }
}
