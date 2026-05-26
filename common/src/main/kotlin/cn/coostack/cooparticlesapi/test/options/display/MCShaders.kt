package cn.coostack.cooparticlesapi.test.options.display

import cn.coostack.cooparticlesapi.compat.IrisCompat
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.minecraft.client.renderer.ShaderInstance
import net.minecraft.server.packs.resources.ResourceManager

/**
 * 自定义 ShaderInstance 注册中心。
 *
 * 历史结论：直接拿 [ShaderInstance] 配合 vanilla [net.minecraft.client.renderer.RenderType] 在
 * IRIS 光影下不会渲染 —— 因为 IRIS 的 MixinShaderInstance 把"未注册的 shader"集体跳过。
 *
 * 解法：每次 reload 后调用 [IrisCompat.markUnskippable] 把我们的 shader 标为
 * "永远不要 skip"，IRIS 不存在时该调用是 no-op。
 */
object MCShaders {

    lateinit var GLOW: ShaderInstance
        private set

    lateinit var ENTITY_CUTOUT_EMISSIVE: ShaderInstance
        private set

    fun init(resourceManager: ResourceManager) {
        release()
        GLOW = ShaderInstance(
            resourceManager,
            "coo_glow",
            DefaultVertexFormat.POSITION_COLOR
        )
        IrisCompat.markUnskippable(GLOW)
        ENTITY_CUTOUT_EMISSIVE = ShaderInstance(
            resourceManager,
            "coo_entity_cutout_emissive",
            DefaultVertexFormat.NEW_ENTITY
        )
        IrisCompat.markUnskippable(ENTITY_CUTOUT_EMISSIVE)
    }

    fun release() {
        if (::GLOW.isInitialized) {
            GLOW.close()
        }
        if (::ENTITY_CUTOUT_EMISSIVE.isInitialized) {
            ENTITY_CUTOUT_EMISSIVE.close()
        }
    }
}
