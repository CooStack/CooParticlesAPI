package cn.coostack.cooparticlesapi.renderer.shader.texture

import cn.coostack.cooparticlesapi.renderer.shader.api.CooProgramUniformAccess
import cn.coostack.cooparticlesapi.renderer.shader.api.texture.GlTexture
import kotlin.math.floor

/**
 * 为现有 `GlTexture` 提供 sprite-sheet 帧选择能力的包装器。
 *
 * 这个类不改变原有纹理绑定流程，只负责：
 * 1. 按时间推导当前播放帧
 * 2. 计算该帧对应的 UV rect
 * 3. 把 rect 写入 shader uniform，供 fragment shader 做二次 UV 映射
 */
class SpriteSheetTexture(
    private val delegate: GlTexture,
    val columns: Int,
    val rows: Int,
    val frameCount: Int = columns * rows,
    val framesPerSecond: Float = 20.0f,
    val firstFrame: Int = 0,
    val looping: Boolean = true,
    val rowsStartFromTop: Boolean = true
) : GlTexture by delegate {
    companion object {
        const val DEFAULT_ENABLED_UNIFORM = "useSpriteUv"
        const val DEFAULT_UV_RECT_UNIFORM = "spriteUvRect"
        const val DEFAULT_FRAME_INDEX_UNIFORM = "spriteFrame"
    }

    private val sheetFrameCapacity = columns * rows

    init {
        require(columns > 0) { "columns 必须大于 0" }
        require(rows > 0) { "rows 必须大于 0" }
        require(frameCount > 0) { "frameCount 必须大于 0" }
        require(frameCount <= sheetFrameCapacity) {
            "frameCount($frameCount) 不能超过 sprite-sheet 容量 $sheetFrameCapacity"
        }
        require(firstFrame >= 0) { "firstFrame 不能小于 0" }
        require(firstFrame + frameCount <= sheetFrameCapacity) {
            "firstFrame + frameCount 不能超过 sprite-sheet 容量 $sheetFrameCapacity"
        }
        require(framesPerSecond > 0.0f) { "framesPerSecond 必须大于 0" }
    }

    fun frameAt(timeSeconds: Float): SpriteFrameRegion {
        val absoluteFrameIndex = resolveAbsoluteFrameIndex(timeSeconds)
        val column = absoluteFrameIndex % columns
        val rowInSheet = absoluteFrameIndex / columns
        val sampledRow = if (rowsStartFromTop) rows - 1 - rowInSheet else rowInSheet
        val scaleU = 1.0f / columns.toFloat()
        val scaleV = 1.0f / rows.toFloat()
        return SpriteFrameRegion(
            frameIndex = absoluteFrameIndex,
            column = column,
            row = sampledRow,
            offsetU = column * scaleU,
            offsetV = sampledRow * scaleV,
            scaleU = scaleU,
            scaleV = scaleV
        )
    }

    fun uploadSpriteUniforms(
        program: CooProgramUniformAccess,
        timeSeconds: Float,
        enabledUniform: String = DEFAULT_ENABLED_UNIFORM,
        uvRectUniform: String = DEFAULT_UV_RECT_UNIFORM,
        frameIndexUniform: String? = DEFAULT_FRAME_INDEX_UNIFORM
    ) {
        val region = frameAt(timeSeconds)
        program.setBoolean(enabledUniform, true)
        program.setFloat4(uvRectUniform, region.uvRect())
        if (frameIndexUniform != null) {
            program.setInt(frameIndexUniform, region.frameIndex)
        }
    }

    fun disableSpriteUniforms(
        program: CooProgramUniformAccess,
        enabledUniform: String = DEFAULT_ENABLED_UNIFORM
    ) {
        program.setBoolean(enabledUniform, false)
    }

    private fun resolveAbsoluteFrameIndex(timeSeconds: Float): Int {
        if (frameCount == 1) {
            return firstFrame
        }
        val nonNegativeTime = timeSeconds.coerceAtLeast(0.0f)
        val advancedFrames = floor(nonNegativeTime * framesPerSecond).toInt()
        val localFrameIndex = if (looping) {
            advancedFrames % frameCount
        } else {
            advancedFrames.coerceAtMost(frameCount - 1)
        }
        return firstFrame + localFrameIndex
    }
}
