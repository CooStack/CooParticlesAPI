package cn.coostack.cooparticlesapi.cparticle

import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f

/**
 * 旧 sprite/effect API 的兼容入口。
 *
 * 新代码应使用 [CParticleTextureSource]；本对象把旧调用转换为相同的通用描述符表。
 * Example: 既有 `CParticleSprites.animationId(sprite, effect)` 仍保持 sprite 优先。
 * Forbidden: 不要在这里重新建立只支持粒子图集的第二套 TBO。
 */
object CParticleSprites {
    private data class LegacySpriteKey(val sprite: ResourceLocation)
    private data class LegacyEffectKey(val typeId: ResourceLocation)
    private data class RawUvKey(val uv: CParticleUv)
    private data class ExternalKey(val id: ResourceLocation, val bindingKey: CParticleTextureBindingKey)

    /**
     * 默认固定 sprite 标识，保留原有 API 值。
     *
     * Example: 未设置 sprite/effect 时使用该来源的首帧。
     * Forbidden: 不要把它当作 ParticleType 的数值 ID。
     */
    @JvmStatic
    val DEFAULT: ResourceLocation = ResourceLocation.fromNamespaceAndPath("minecraft", "end_rod")

    /**
     * 旧低层 API 使用的 UV 数据类型。
     *
     * Example: `UvRect(0f, 0f, 1f, 1f)` 覆盖完整纹理。
     * Forbidden: atlas sprite 应优先通过 [CParticleTextureSource.AtlasSprite] 解析。
     *
     * @property u0 第一个水平坐标
     * @property v0 第一个垂直坐标
     * @property u1 第二个水平坐标
     * @property v1 第二个垂直坐标
     */
    data class UvRect(val u0: Float, val v0: Float, val u1: Float, val v1: Float) {
        /**
         * 转成通用 UV。
         *
         * Example: legacy fixed UV 注册前调用。
         * Forbidden: 转换不会执行 atlas stitch 查询。
         *
         * @return 值相同的 [CParticleUv]
         */
        internal fun toUv(): CParticleUv = CParticleUv(u0, v0, u1, v1)
    }

    /**
     * 解析粒子图集中的固定 sprite。
     *
     * Example: `resolve(minecraft:glitter_0)` 返回当前 stitch 周期的 UV。
     * Forbidden: 不要用它解析方块图集。
     *
     * @param sprite 粒子图集 sprite；空值使用默认首帧
     * @return 当前资源周期的 UV
     */
    @JvmStatic
    fun resolve(sprite: ResourceLocation?): UvRect {
        if (sprite == null || sprite == DEFAULT) return resolveEffect(ParticleTypes.END_ROD, 0, 1)
        val resolved = CParticleTextureResolver.resolve(textureOfParticleSprite(sprite), net.minecraft.world.phys.Vec3.ZERO)
        return resolved.uv.toLegacy()
    }

    /**
     * 保留旧 CPU effect 选帧入口，公式与 GPU 描述符一致。
     *
     * Example: 调试代码可按 `age=5, lifetime=20` 获取当前帧。
     * Forbidden: 正常 GPU age 推进不应逐粒子调用。
     *
     * @param effect 原版粒子参数
     * @param age 当前年龄
     * @param lifetime 最大年龄
     * @return 对应 SpriteSet 帧 UV
     */
    @JvmStatic
    @JvmOverloads
    fun resolveEffect(effect: ParticleOptions, age: Int = 0, lifetime: Int = 1): UvRect {
        val resolved = CParticleTextureResolver.resolve(textureOfEffect(effect), net.minecraft.world.phys.Vec3.ZERO)
        val id = resolved.animationId ?: resolved.descriptorId
        return CParticleTextureDescriptors.frame(id, age, lifetime).toLegacy()
    }

    /**
     * 按 CParticle 当前纹理来源解析 CPU UV。
     *
     * Example: legacy 调试路径可读取新 Block/Item 来源的基础 UV。
     * Forbidden: 随机 1/4 裁剪由 shader 完成，此处返回基础 sprite UV。
     *
     * @param particle 粒子生成描述
     * @param age 当前年龄
     * @param lifetime 最大年龄
     * @return 选中的基础 UV
     */
    @JvmStatic
    fun resolve(particle: CParticle, age: Int, lifetime: Int): UvRect {
        val resolved = CParticleTextureResolver.resolve(particle.effectiveTextureSource(), particle.pos)
        val id = resolved.animationId ?: resolved.descriptorId
        return CParticleTextureDescriptors.frame(id, age, lifetime).toLegacy()
    }

    /**
     * 注册 CParticle 当前来源并返回实例实际使用的 descriptor ID。
     *
     * Example: effect 返回动画 ID，固定 sprite 返回静态 descriptor ID。
     * Forbidden: 返回值不是 ParticleType 注册表 ID。
     *
     * @param particle 粒子生成描述
     * @return 写入实例 `textureDescriptorId` 的稳定 ID
     */
    @JvmStatic
    fun animationId(particle: CParticle): Int {
        val resolved = CParticleTextureResolver.resolve(particle.effectiveTextureSource(), particle.pos)
        return resolved.animationId ?: resolved.descriptorId
    }

    /**
     * 保留旧 `sprite > effect > default` descriptor 注册规则。
     *
     * Example: sprite 非空时忽略 effect type。
     * Forbidden: effect 参数是资源 ID，不是数值 registry ID。
     *
     * @param sprite 旧固定 sprite
     * @param effectType 旧 effect 的粒子类型 ID
     * @return 稳定 descriptor ID
     */
    internal fun animationId(sprite: ResourceLocation?, effectType: ResourceLocation?): Int {
        if (sprite != null) {
            return CParticleTextureDescriptors.register(
                LegacySpriteKey(sprite),
                CParticleTextureBindingKey.PARTICLE_ATLAS,
            ) {
                listOf(resolve(sprite).toUv())
            }
        }
        if (effectType != null) {
            return CParticleTextureDescriptors.register(
                LegacyEffectKey(effectType),
                CParticleTextureBindingKey.PARTICLE_ATLAS,
            ) {
                CParticleTextureResolver.effectFrames(effectType)
            }
        }
        return CParticleTextureDescriptors.register(
            LegacySpriteKey(DEFAULT),
            CParticleTextureBindingKey.PARTICLE_ATLAS,
        ) {
            listOf(resolveEffect(ParticleTypes.END_ROD).toUv())
        }
    }

    /**
     * 把旧固定 UV 注册为粒子图集中的单帧描述符。
     *
     * Example: 自定义低层 spawn override 继续使用该入口。
     * Forbidden: 其他 binding 的 UV 应由对应 system 注册，不能隐式放入粒子图集。
     *
     * @param uv 旧固定 UV
     * @return 稳定 descriptor ID
     */
    internal fun animationId(uv: UvRect): Int {
        val commonUv = uv.toUv()
        return CParticleTextureDescriptors.register(
            RawUvKey(commonUv),
            CParticleTextureBindingKey.PARTICLE_ATLAS,
        ) {
            listOf(commonUv)
        }
    }

    /**
     * 返回 effect 的粒子类型资源 ID。
     *
     * Example: DYNAMIC legacy source 用它检测 effect type 变化。
     * Forbidden: 空 effect 返回 `null`，不要强制映射成默认值。
     *
     * @param effect 原版粒子参数
     * @return 粒子类型资源 ID 或 `null`
     */
    internal fun effectTypeId(effect: ParticleOptions?): ResourceLocation? =
        effect?.let { BuiltInRegistries.PARTICLE_TYPE.getKey(it.type) }

    /**
     * 保留旧外部 UV provider 注册入口。
     *
     * channel `0` 映射粒子图集，`1` 映射方块图集；新代码应直接使用 binding key 来源。
     * Example: 旧 Block provider 在重载后仍可返回新 UV。
     * Forbidden: 其他 channel 不会创建逐粒子 sampler。
     *
     * @param id 外部描述符稳定 ID
     * @param provider 可在资源重载后重新读取的帧 provider
     * @return 稳定 descriptor ID
     */
    @JvmStatic
    fun registerExternalAnimation(id: ResourceLocation, provider: CParticleUvProvider): Int {
        val binding = when (provider.textureChannel()) {
            CParticleUvProvider.RESERVED_BLOCK_ATLAS -> CParticleTextureBindingKey.BLOCK_ATLAS
            else -> CParticleTextureBindingKey.PARTICLE_ATLAS
        }
        return CParticleTextureDescriptors.register(ExternalKey(id, binding), binding) {
            provider.frames().map(UvRect::toUv)
        }
    }

    /**
     * 绑定通用 descriptor TBO。
     *
     * Example: renderer 使用纹理单元 `2`。
     * Forbidden: 非渲染线程不能调用。
     *
     * @param textureUnit 目标纹理单元
     */
    internal fun bindLookup(textureUnit: Int) {
        CParticleTextureDescriptors.bindLookup(textureUnit)
    }

    /**
     * 返回旧粒子图集的当前 GL id。
     *
     * Example: 兼容测试可读取当前资源周期 ID。
     * Forbidden: 通用 renderer 不应固定调用它绑定所有系统。
     *
     * @return 粒子图集 GL texture id
     */
    @JvmStatic
    fun atlasGlId(): Int = CParticleTextureResolver.textureId(CParticleTextureBindingKey.PARTICLE_ATLAS)

    /**
     * 使纹理解析和 descriptor TBO 失效，但保留稳定 ID。
     *
     * Example: 完整资源重载后调用。
     * Forbidden: 不要在每帧清缓存。
     */
    @JvmStatic
    fun clearCache() {
        CParticleTextureResolver.invalidate()
    }

    /**
     * 释放 descriptor GL 表。
     *
     * Example: 客户端关闭时调用。
     * Forbidden: 此方法不会清除 descriptor ID。
     */
    @JvmStatic
    fun release() {
        CParticleTextureDescriptors.release()
    }

    private fun CParticleUv.toLegacy(): UvRect = UvRect(u0, v0, u1, v1)
}
