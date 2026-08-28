package cn.coostack.cooparticlesapi.compat

/**
 * Iris 合成链中的颜色纹理及其渲染尺寸。
 *
 * @property textureId OpenGL 场景颜色纹理对象名
 * @property width 颜色纹理宽度，单位为像素
 * @property height 颜色纹理高度，单位为像素
 */
internal data class IrisFinalPassColorTexture(
    val textureId: Int,
    val width: Int,
    val height: Int,
)

/**
 * 把 Iris/OptiFine 的 render target sampler 名称解析为 attachment 编号。
 */
internal fun irisFinalPassColorAttachment(uniformName: String): Int? {
    val name = uniformName.substringBefore('[')
    if (name.startsWith("colortex")) {
        return name.removePrefix("colortex").toIntOrNull()?.takeIf { it in 0..15 }
    }
    return when (name) {
        "gcolor" -> 0
        "gdepth" -> 1
        "gnormal" -> 2
        "composite" -> 3
        "gaux1" -> 4
        "gaux2" -> 5
        "gaux3" -> 6
        "gaux4" -> 7
        else -> null
    }
}

/**
 * 从 final program 的活跃 sampler 中选择承载场景颜色的 attachment。
 *
 * 单一 render target 可以精确选择。多个 target 时只接受标准主颜色 attachment 0；
 * 多个非零 target 无法安全区分时返回 null，避免把 Terrain Mapping 写进辅助数据。
 */
internal fun selectIrisFinalPassColorAttachment(activeUniformNames: Iterable<String>): Int? {
    val attachments = activeUniformNames.mapNotNull(::irisFinalPassColorAttachment).toSet()
    if (0 in attachments) return 0
    return attachments.singleOrNull()
}

/**
 * 从首个 fragment composite 的活跃 sampler 中选择进入后处理链的主场景颜色。
 *
 * 标准 shaderpack 以 `colortex0` 承载 composite 前的主场景。没有明确采样
 * `colortex0` 时不写入 composite attachment，交给 final pass 兜底，避免污染辅助缓冲。
 */
internal fun selectIrisCompositeSceneColorAttachment(
    activeUniformNames: Iterable<String>,
): Int? {
    val attachments = activeUniformNames.mapNotNull(::irisFinalPassColorAttachment).toSet()
    if (0 in attachments) return 0
    return null
}

/**
 * 按 Iris final pass 的实际采样方向选择指定颜色 attachment。
 *
 * Iris 的 baseline framebuffer 使用 final stage 读集合的反向 attachment；因此它只适用于
 * 没有 final shader、由 Iris 直接复制 baseline 的路径。
 */
internal fun selectIrisFinalPassColorTextureId(
    hasFinalPass: Boolean,
    finalPassReadsFromAlt: Boolean,
    baselineTextureId: Int,
    mainTextureId: Int,
    altTextureId: Int,
): Int {
    if (!hasFinalPass) return baselineTextureId
    return if (finalPassReadsFromAlt) altTextureId else mainTextureId
}

/**
 * 选择 Iris 最终 composite 链开始前读取的颜色纹理。
 *
 * fragment pass 保存的读取集合就是 composite 执行前的 ping-pong 状态。
 */
internal fun selectIrisCompositeInputColorTextureId(
    firstFragmentPassReadsFromAlt: Boolean,
    mainTextureId: Int,
    altTextureId: Int,
): Int {
    return if (firstFragmentPassReadsFromAlt) altTextureId else mainTextureId
}
