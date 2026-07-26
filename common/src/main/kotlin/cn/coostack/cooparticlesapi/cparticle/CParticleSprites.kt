package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.mixin.ParticleEngineAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.renderer.texture.TextureAtlas
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * 粒子图集 sprite -> UV 解析 (客户端).
 *
 * 所有 GPU 粒子共用原版粒子图集; 每个粒子在生成时把自己的 UV 矩形写入实例缓冲.
 * 资源重载后图集会重新 stitch, 需要调用 [clearCache].
 */
object CParticleSprites {
    private const val FRAME_PROGRESS_RESOLUTION = 4096

    /** 默认贴图: 末影烛 (与 ControlableParticleData 默认 effect 一致) */
    @JvmStatic
    val DEFAULT: ResourceLocation = ResourceLocation.fromNamespaceAndPath("minecraft", "end_rod")

    private val DEFAULT_ATLAS_SPRITE =
        ResourceLocation.fromNamespaceAndPath("minecraft", "glitter_7")

    data class UvRect(val u0: Float, val v0: Float, val u1: Float, val v1: Float)

    private val cache = ConcurrentHashMap<ResourceLocation, UvRect>()
    private val effectSetCache = ConcurrentHashMap<ResourceLocation, SpriteSet>()

    @Volatile
    private var defaultUv: UvRect? = null

    /**
     * 解析粒子图集 sprite id 为 UV 矩形.
     * 未找到时返回图集的 missingno sprite (紫黑格).
     */
    @JvmStatic
    fun resolve(sprite: ResourceLocation?): UvRect {
        if (sprite == null || sprite == DEFAULT) return resolveDefault()
        val id = sprite
        return cache.getOrPut(id) {
            val atlas = particleAtlas() ?: return@getOrPut UvRect(0f, 0f, 1f, 1f)
            val s = atlas.getSprite(id)
            UvRect(s.u0, s.v0, s.u1, s.v1)
        }
    }

    /**
     * 从 ControlableParticleEffect / 任意 ParticleOptions 的 SpriteSet 解析生命周期帧。
     */
    @JvmStatic
    @JvmOverloads
    fun resolveEffect(effect: ParticleOptions, age: Int = 0, lifetime: Int = 1): UvRect {
        return resolveEffectOrNull(effect, age, lifetime) ?: resolveDefault()
    }

    private fun resolveEffectOrNull(effect: ParticleOptions, age: Int, lifetime: Int): UvRect? {
        val typeId = BuiltInRegistries.PARTICLE_TYPE.getKey(effect.type)
            ?: return null
        val set = effectSetCache[typeId] ?: run {
            val engine = Minecraft.getInstance().particleEngine as? ParticleEngineAccessor
                ?: return null
            val loaded = engine.spriteSets[typeId] ?: return null
            effectSetCache.putIfAbsent(typeId, loaded) ?: loaded
        }
        val safeLifetime = lifetime.coerceAtLeast(1).toLong()
        val safeAge = age.toLong().coerceIn(0L, safeLifetime)
        val frameAge = ((safeAge * FRAME_PROGRESS_RESOLUTION) / safeLifetime).toInt()
        val frame = runCatching { set.get(frameAge, FRAME_PROGRESS_RESOLUTION) }.getOrNull()
            ?: return null
        return UvRect(frame.u0, frame.v0, frame.u1, frame.v1)
    }

    @JvmStatic
    fun resolve(particle: CParticle, age: Int, lifetime: Int): UvRect {
        particle.sprite?.let { return resolve(it) }
        particle.effect?.let { return resolveEffect(it, age, lifetime) }
        return resolveDefault()
    }

    /** 粒子图集纹理的 GL id (每帧获取, 抗资源重载) */
    @JvmStatic
    fun atlasGlId(): Int {
        return Minecraft.getInstance().textureManager.getTexture(TextureAtlas.LOCATION_PARTICLES).id
    }

    private fun particleAtlas(): TextureAtlas? {
        return Minecraft.getInstance().textureManager.getTexture(TextureAtlas.LOCATION_PARTICLES) as? TextureAtlas
    }

    private fun resolveDefault(): UvRect {
        defaultUv?.let { return it }
        val resolved = resolveEffectOrNull(ParticleTypes.END_ROD, 0, 1)
            ?: resolve(DEFAULT_ATLAS_SPRITE)
        defaultUv = resolved
        return resolved
    }

    /** 资源重载后必须清空 (图集 UV 会变化) */
    @JvmStatic
    fun clearCache() {
        cache.clear()
        effectSetCache.clear()
        defaultUv = null
    }
}
