package cn.coostack.cooparticlesapi.renderer.terrain

import net.minecraft.client.renderer.LevelRenderer
import net.minecraft.client.renderer.oit.OitStage

/**
 * 把单 RGBA 输出的地形片元程序接入原版 OIT 算法，保留自定义 main、uniform、采样与 discard。
 * 多输出和自定义片元深度不属于该适配契约；调用方须保留原始效果的晚期合成回退。
 */
internal object CooTerrainOitSource {
    /** 只识别独立的单个 vec4 颜色输出，复杂接口声明由兼容路线处理。 */
    fun supports(source: String): Boolean {
        val clean = withoutComments(source)
        return outputs(clean).size == 1 &&
            Regex("""\bout\b""").findAll(clean).count() == 1 &&
            !Regex("""\bgl_FragDepth\b|\bgl_FragData\b|\bgl_FragColor\b|\bimageStore\b|\bimageAtomic\w*\b|\batomic\w*\b|\bbuffer\b""")
                .containsMatchIn(clean)
    }

    /**
     * 生成当前阶段的独立片元源码。原版 OIT include 已由资源加载器完整展开。
     * @param source 资源包提供并展开 include 的自定义地形片元源码
     * @param oitInclude 当前版本原版 oit.glsl 展开结果
     * @param stage 当前原版 OIT 阶段
     * @param additive 当前效果是否只贡献加法颜色
     * @param zeroToOne 原版设备是否使用零至一 NDC
     * @return 保留原 shader 计算并写入正确 OIT 目标的 GLSL 330 源码
     */
    fun build(source: String, oitInclude: String, stage: OitStage, additive: Boolean, zeroToOne: Boolean): String {
        require(supports(source)) { "原版 OIT 适配需要单个 vec4 输出，且不支持自定义 gl_FragDepth" }
        val clean = withoutComments(source)
        val output = outputs(clean).single()
        val outputName = output.groupValues[1]
        val body = clean.replaceRange(output.range, "vec4 $outputName;")
            .replace(Regex("""(?m)^\s*#version[^\n]*"""), "")
        val extensions = Regex("""(?m)^\s*#extension[^\n]*""").findAll(body).map { it.value.trim() }.toList()
        val withoutExtensions = body.replace(Regex("""(?m)^\s*#extension[^\n]*"""), "")
        return buildString {
            appendLine("#version 330 core")
            extensions.forEach(::appendLine)
            appendLine("#define main cooOriginalTerrainMain")
            appendLine("#define $outputName cooOriginalTerrainColor")
            appendLine(withoutExtensions)
            appendLine("#undef main")
            appendLine("#undef $outputName")
            appendLine("uniform mat4 CooOitProjection;")
            appendLine("#define MINECRAFT_PROJECTION_GLSL")
            appendLine("#define ProjMat CooOitProjection")
            appendLine("#define OIT")
            appendLine("#define OIT_WAVELET_RANK ${Integer.numberOfTrailingZeros(LevelRenderer.OIT_COEFFICIENT_COUNT) - 1}")
            appendLine("#define OIT_COEFF_COUNT ${LevelRenderer.OIT_COEFFICIENT_COUNT}")
            appendLine("#define OIT_COEFF_ATTACHMENT_COUNT ${LevelRenderer.OIT_TRANSMITTANCE_TARGET_COUNT}")
            if (zeroToOne) appendLine("#define RENDERPEARL_DEPTH_IS_ZERO_TO_ONE")
            if (additive) appendLine("#define OIT_ADDITIVE")
            appendLine("#define OIT_${stage.name}")
            appendLine(oitInclude)
            appendLine("#undef ProjMat")
            if (stage == OitStage.ACCUMULATE) appendLine("layout(location = 0) out vec4 cooOitColor;")
            appendLine("void main() {")
            appendLine("    cooOriginalTerrainMain();")
            if (stage == OitStage.ACCUMULATE) {
                appendLine("    cooOitColor = sampleColorForAccumulation(cooOriginalTerrainColor);")
            } else {
                appendLine("    executeAlphaOnlyPhase(gl_FragCoord.z, cooOriginalTerrainColor.a);")
            }
            appendLine("}")
        }
    }

    private fun outputs(source: String) =
        Regex("""(?m)^\s*(?:layout\s*\(\s*location\s*=\s*0\s*\)\s*)?out\s+vec4\s+([A-Za-z_]\w*)\s*;""")
            .findAll(source).toList()

    private fun withoutComments(source: String): String =
        source.replace(Regex("""/\*[\s\S]*?\*/|//[^\n]*""")) { match ->
            match.value.map { if (it == '\n') '\n' else ' ' }.joinToString("")
        }
}
