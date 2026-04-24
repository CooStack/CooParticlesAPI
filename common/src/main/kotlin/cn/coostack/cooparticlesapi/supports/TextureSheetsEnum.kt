package cn.coostack.cooparticlesapi.supports


/**
 * 防止在服务器环境需要提供 RenderType/TextureSheet 名字 然后懒得手打字符串的
 *
 * 因为傻逼OJNG非要设计那个傻逼注解强制区分客服环境:)
 *
 * @constructor Create empty Texture sheets enum
 */
enum class TextureSheetsEnum {
    // CooParticlesAPI 提供
    ADDITION_BLEND_TRANSLUCENT,
    ADDITION_BLEND,

    // ParticleRenderType提供
    PARTICLE_SHEET_OPAQUE,
    PARTICLE_SHEET_TRANSLUCENT,
    PARTICLE_SHEET_LIT,
    CUSTOM,
    TERRAIN_SHEET,
    NO_RENDER
}