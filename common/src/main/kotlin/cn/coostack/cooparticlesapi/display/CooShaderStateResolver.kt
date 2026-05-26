package cn.coostack.cooparticlesapi.display

import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.test.options.display.MCShaders
import net.minecraft.client.renderer.ShaderInstance

/**
 * 把 [CooRenderTypeDescriptor] 的 shader 配置翻译为 ShaderInstance 供应器。
 *
 * 跨平台：FabricRenderTypesProvider/NeoRenderTypesProvider 共用。
 *
 * 之所以不直接返回 vanilla `RenderStateShard.ShaderStateShard`：
 * `ShaderStateShard` 与 `POSITION_COLOR_SHADER` 在 vanilla 里是 `protected`，
 * 只有 Fabric AW / NeoForge AT 在各自平台模块里把它们公开。
 * common 模块编译时看不到这些保护成员，因此把封装责任下沉到各平台 provider，
 * common 只决定"用哪个 ShaderInstance 供应器"。
 *
 * @return 自定义 ShaderInstance 供应器；返回 null 表示该描述符要使用平台原生
 *         的 `POSITION_COLOR_SHADER`，由 provider 自行处理。
 */
object CooShaderStateResolver {
    fun customShaderSupplier(descriptor: CooRenderTypeDescriptor): (() -> ShaderInstance)? {
        val supplier = when (descriptor.shaderPreset) {
            CooRenderTypeShaderPreset.POSITION_COLOR -> null
            CooRenderTypeShaderPreset.COO_GLOW -> { { MCShaders.GLOW } }
            CooRenderTypeShaderPreset.CUSTOM -> requireNotNull(descriptor.customShader) {
                "CooRenderTypeDescriptor(name=${descriptor.name}) shaderPreset=CUSTOM but customShader is null"
            }
        }
        return supplier?.let { irisSafe(it) }
    }

    private fun irisSafe(supplier: () -> ShaderInstance): () -> ShaderInstance {
        return {
            supplier().also(IrisCompat::markUnskippable)
        }
    }
}
