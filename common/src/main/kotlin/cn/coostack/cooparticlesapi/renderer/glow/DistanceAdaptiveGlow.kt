package cn.coostack.cooparticlesapi.renderer.glow

import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.max

data class DistanceAdaptiveGlowBlend(
    val directWeight: Float,
    val screenGlowWeight: Float,
    val projectedRadiusPx: Float
)

data class DistanceAdaptiveGlowCompensation(
    val persistence: Float,
    val radiusScale: Float,
    val intensityScale: Float
)

data class DistanceAdaptiveOrbGlowCompensation(
    val persistence: Float,
    val radiusScale: Float,
    val intensityScale: Float,
    val softness: Float
)

data class BrightSourceOrbProfile(
    val coreWhiteness: Float,
    val shellVisibility: Float,
    val haloSpread: Float
)

data class PersistentHaloProfile(
    val haloRadiusScale: Float,
    val brightnessNormalization: Float,
    val softOcclusionFloor: Float,
    val haloOpacity: Float,
    val blurSigma: Float,
    val blurRange: Float
)

data class PersistentDirectSphereProfile(
    val solidCoreFill: Float,
    val outerShellOpacity: Float,
    val distortionOpacity: Float
)

/**
 * 一组“按当前帧投影尺寸自适应”的 glow / bloom 采样工具。
 *
 * 核心思路不是只根据世界半径硬编码参数，而是先估算物体在屏幕上的像素尺寸，
 * 再决定近景是否保留本体、远景是否需要 halo 补偿，以及帧尾 bloom 应使用哪套 profile。
 */
object DistanceAdaptiveGlow {
    const val DEFAULT_DIRECT_FADE_START_PX = 18.0f
    const val DEFAULT_DIRECT_FADE_END_PX = 5.0f
    private const val CLIP_EPSILON = 1.0e-5f

    @JvmStatic
    fun fromProjectedRadiusPx(
        projectedRadiusPx: Float,
        directFadeStartPx: Float = DEFAULT_DIRECT_FADE_START_PX,
        directFadeEndPx: Float = DEFAULT_DIRECT_FADE_END_PX
    ): DistanceAdaptiveGlowBlend {
        val safeRadius = projectedRadiusPx.coerceAtLeast(0.0f)
        val directWeight = smoothstep(directFadeEndPx, directFadeStartPx, safeRadius)
        val screenGlowWeight =
            1.0f - smoothstep(directFadeEndPx * 1.15f, directFadeStartPx * 0.92f, safeRadius)

        return DistanceAdaptiveGlowBlend(
            directWeight = directWeight.coerceIn(0.0f, 1.0f),
            screenGlowWeight = screenGlowWeight.coerceIn(0.0f, 1.0f),
            projectedRadiusPx = safeRadius
        )
    }

    @JvmStatic
    fun orbFromProjectedRadiusPx(
        projectedRadiusPx: Float,
        directFadeStartPx: Float = 24.0f,
        directFadeEndPx: Float = 7.0f,
        screenGlowFadeStartPx: Float = 72.0f,
        screenGlowFadeEndPx: Float = 18.0f
    ): DistanceAdaptiveGlowBlend {
        val safeRadius = projectedRadiusPx.coerceAtLeast(0.0f)
        val directWeight = smoothstep(directFadeEndPx, directFadeStartPx, safeRadius)
        val screenGlowWeight = 1.0f - smoothstep(screenGlowFadeEndPx, screenGlowFadeStartPx, safeRadius)

        return DistanceAdaptiveGlowBlend(
            directWeight = directWeight.coerceIn(0.0f, 1.0f),
            screenGlowWeight = screenGlowWeight.coerceIn(0.0f, 1.0f),
            projectedRadiusPx = safeRadius
        )
    }

    @JvmStatic
    fun orbScreenHaloProfileFromProjectedRadiusPx(
        projectedRadiusPx: Float,
        haloFadeStartPx: Float = 20.0f,
        haloFadeEndPx: Float = 96.0f
    ): Float {
        val safeRadius = projectedRadiusPx.coerceAtLeast(0.0f)
        return smoothstep(haloFadeStartPx, haloFadeEndPx, safeRadius).coerceIn(0.0f, 1.0f)
    }

    /**
     * 亮源本体 profile。
     *
     * 用来描述近景和远景下的核心白化程度、外壳可见度，以及 halo 扩散范围。
     */
    @JvmStatic
    fun brightSourceOrbProfileFromProjectedRadiusPx(
        projectedRadiusPx: Float,
        nearFadeStartPx: Float = 16.0f,
        nearFadeEndPx: Float = 96.0f
    ): BrightSourceOrbProfile {
        val safeRadius = projectedRadiusPx.coerceAtLeast(0.0f)
        val nearFactor = smoothstep(nearFadeStartPx, nearFadeEndPx, safeRadius)

        return BrightSourceOrbProfile(
            coreWhiteness = mix(0.98f, 0.76f, nearFactor).coerceIn(0.76f, 0.98f),
            shellVisibility = mix(0.04f, 0.26f, nearFactor).coerceIn(0.04f, 0.26f),
            haloSpread = mix(1.20f, 1.42f, nearFactor).coerceIn(1.20f, 1.42f)
        )
    }

    @JvmStatic
    fun farGlowCompensationFromProjectedRadiusPx(
        projectedRadiusPx: Float,
        compensationFadeStartPx: Float = 16.0f,
        maxCompensationPx: Float = 2.25f
    ): DistanceAdaptiveGlowCompensation {
        val safeRadius = projectedRadiusPx.coerceAtLeast(0.0f)
        val persistence = 1.0f - smoothstep(maxCompensationPx, compensationFadeStartPx, safeRadius)
        return DistanceAdaptiveGlowCompensation(
            persistence = persistence.coerceIn(0.0f, 1.0f),
            radiusScale = mix(1.0f, 3.8f, persistence),
            intensityScale = mix(1.0f, 2.6f, persistence)
        )
    }

    /**
     * 球体类亮源的远距补偿参数。
     *
     * 当投影尺寸很小时，通过适度放大 radius / intensity / softness
     * 维持远处的可读性，避免亮源缩成难以辨认的单点。
     */
    @JvmStatic
    fun farOrbCompensationFromProjectedRadiusPx(
        projectedRadiusPx: Float,
        compensationFadeStartPx: Float = 18.0f,
        maxCompensationPx: Float = 2.2f
    ): DistanceAdaptiveOrbGlowCompensation {
        val safeRadius = projectedRadiusPx.coerceAtLeast(0.0f)
        val persistence = 1.0f - smoothstep(maxCompensationPx, compensationFadeStartPx, safeRadius)

        return DistanceAdaptiveOrbGlowCompensation(
            persistence = persistence.coerceIn(0.0f, 1.0f),
            radiusScale = mix(1.0f, 1.32f, persistence).coerceIn(1.0f, 1.32f),
            intensityScale = mix(1.0f, 1.58f, persistence).coerceIn(1.0f, 1.58f),
            softness = mix(0.58f, 0.72f, persistence).coerceIn(0.58f, 0.72f)
        )
    }

    /**
     * 提供给 `PersistentBloom` 的 halo profile。
     *
     * 这些参数最终会进入帧尾 blur / composite 阶段，而不是直接喂给本体 shader。
     */
    @JvmStatic
    fun persistentHaloProfileFromProjectedRadiusPx(
        projectedRadiusPx: Float,
        nearFadeStartPx: Float = 6.0f,
        nearFadeEndPx: Float = 84.0f
    ): PersistentHaloProfile {
        val safeRadius = projectedRadiusPx.coerceAtLeast(0.0f)
        val nearFactor = smoothstep(nearFadeStartPx, nearFadeEndPx, safeRadius)

        return PersistentHaloProfile(
            haloRadiusScale = mix(2.25f, 1.45f, nearFactor).coerceIn(1.45f, 2.25f),
            brightnessNormalization = mix(1.10f, 0.98f, nearFactor).coerceIn(0.98f, 1.10f),
            softOcclusionFloor = mix(0.34f, 0.18f, nearFactor).coerceIn(0.18f, 0.34f),
            haloOpacity = mix(0.74f, 0.68f, nearFactor).coerceIn(0.68f, 0.74f),
            blurSigma = mix(4.20f, 3.20f, nearFactor).coerceIn(3.20f, 4.20f),
            blurRange = mix(4.00f, 3.00f, nearFactor).coerceIn(3.00f, 4.00f)
        )
    }

    /**
     * 提供给球体本体 shader 的直接绘制 profile。
     *
     * 主要决定核心填充、外层壳体透明度，以及额外 distortion 壳层的存在感。
     */
    @JvmStatic
    fun persistentDirectSphereProfileFromProjectedRadiusPx(
        projectedRadiusPx: Float,
        nearFadeStartPx: Float = 6.0f,
        nearFadeEndPx: Float = 84.0f
    ): PersistentDirectSphereProfile {
        val safeRadius = projectedRadiusPx.coerceAtLeast(0.0f)
        val nearFactor = smoothstep(nearFadeStartPx, nearFadeEndPx, safeRadius)

        return PersistentDirectSphereProfile(
            solidCoreFill = mix(0.84f, 0.94f, nearFactor).coerceIn(0.84f, 0.94f),
            outerShellOpacity = mix(0.05f, 0.0f, nearFactor).coerceIn(0.0f, 0.05f),
            distortionOpacity = mix(0.06f, 0.0f, nearFactor).coerceIn(0.0f, 0.06f)
        )
    }

    @JvmStatic
    fun computeBlend(
        worldPosition: Vector3f,
        worldRadius: Float,
        context: ScreenGlowRenderContext,
        directFadeStartPx: Float = DEFAULT_DIRECT_FADE_START_PX,
        directFadeEndPx: Float = DEFAULT_DIRECT_FADE_END_PX
    ): DistanceAdaptiveGlowBlend {
        return computeBlend(
            worldPosition = worldPosition,
            worldRadius = worldRadius,
            cameraWorldPos = context.cameraWorldPos,
            viewRotationMatrix = context.viewRotationMatrix,
            inverseViewRotationMatrix = context.inverseViewRotationMatrix,
            projMatrix = context.projMatrix,
            screenSize = context.screenSize,
            directFadeStartPx = directFadeStartPx,
            directFadeEndPx = directFadeEndPx
        )
    }

    @JvmStatic
    fun computeOrbBlend(
        worldPosition: Vector3f,
        worldRadius: Float,
        context: ScreenGlowRenderContext,
        directFadeStartPx: Float = 24.0f,
        directFadeEndPx: Float = 7.0f,
        screenGlowFadeStartPx: Float = 72.0f,
        screenGlowFadeEndPx: Float = 18.0f
    ): DistanceAdaptiveGlowBlend {
        return computeOrbBlend(
            worldPosition = worldPosition,
            worldRadius = worldRadius,
            cameraWorldPos = context.cameraWorldPos,
            viewRotationMatrix = context.viewRotationMatrix,
            inverseViewRotationMatrix = context.inverseViewRotationMatrix,
            projMatrix = context.projMatrix,
            screenSize = context.screenSize,
            directFadeStartPx = directFadeStartPx,
            directFadeEndPx = directFadeEndPx,
            screenGlowFadeStartPx = screenGlowFadeStartPx,
            screenGlowFadeEndPx = screenGlowFadeEndPx
        )
    }

    @JvmStatic
    fun computeBlend(
        worldPosition: Vector3f,
        worldRadius: Float,
        cameraWorldPos: Vector3f,
        viewRotationMatrix: Matrix3f,
        inverseViewRotationMatrix: Matrix3f,
        projMatrix: Matrix4f,
        screenSize: Vector2f,
        directFadeStartPx: Float = DEFAULT_DIRECT_FADE_START_PX,
        directFadeEndPx: Float = DEFAULT_DIRECT_FADE_END_PX
    ): DistanceAdaptiveGlowBlend {
        val centerUv = Vector2f()
        val centerDepth = FloatArray(1)
        val projected = projectWorldToScreen(
            worldPosition = worldPosition,
            cameraWorldPos = cameraWorldPos,
            viewRotationMatrix = viewRotationMatrix,
            projMatrix = projMatrix,
            uv = centerUv,
            depthOut = centerDepth
        )
        if (!projected) {
            return DistanceAdaptiveGlowBlend(0.0f, 0.0f, 0.0f)
        }

        val projectedRadiusPx = projectRadiusPixels(
            center = worldPosition,
            radius = worldRadius,
            centerUv = centerUv,
            cameraWorldPos = cameraWorldPos,
            viewRotationMatrix = viewRotationMatrix,
            inverseViewRotationMatrix = inverseViewRotationMatrix,
            projMatrix = projMatrix,
            screenSize = screenSize
        )
        return fromProjectedRadiusPx(
            projectedRadiusPx = max(projectedRadiusPx, 0.0f),
            directFadeStartPx = directFadeStartPx,
            directFadeEndPx = directFadeEndPx
        )
    }

    /**
     * 球体类亮源的投影自适应混合策略。
     *
     * 返回值中的：
     * - `directWeight` 控制是否继续直接绘制近景本体
     * - `screenGlowWeight` 控制远景附加辉光层占比
     * - `projectedRadiusPx` 表示当前帧估算出的屏幕像素半径
     */
    @JvmStatic
    fun computeOrbBlend(
        worldPosition: Vector3f,
        worldRadius: Float,
        cameraWorldPos: Vector3f,
        viewRotationMatrix: Matrix3f,
        inverseViewRotationMatrix: Matrix3f,
        projMatrix: Matrix4f,
        screenSize: Vector2f,
        directFadeStartPx: Float = 24.0f,
        directFadeEndPx: Float = 7.0f,
        screenGlowFadeStartPx: Float = 72.0f,
        screenGlowFadeEndPx: Float = 18.0f
    ): DistanceAdaptiveGlowBlend {
        val centerUv = Vector2f()
        val centerDepth = FloatArray(1)
        val projected = projectWorldToScreen(
            worldPosition = worldPosition,
            cameraWorldPos = cameraWorldPos,
            viewRotationMatrix = viewRotationMatrix,
            projMatrix = projMatrix,
            uv = centerUv,
            depthOut = centerDepth
        )
        if (!projected) {
            return DistanceAdaptiveGlowBlend(0.0f, 0.0f, 0.0f)
        }

        val projectedRadiusPx = projectRadiusPixels(
            center = worldPosition,
            radius = worldRadius,
            centerUv = centerUv,
            cameraWorldPos = cameraWorldPos,
            viewRotationMatrix = viewRotationMatrix,
            inverseViewRotationMatrix = inverseViewRotationMatrix,
            projMatrix = projMatrix,
            screenSize = screenSize
        )
        return orbFromProjectedRadiusPx(
            projectedRadiusPx = max(projectedRadiusPx, 0.0f),
            directFadeStartPx = directFadeStartPx,
            directFadeEndPx = directFadeEndPx,
            screenGlowFadeStartPx = screenGlowFadeStartPx,
            screenGlowFadeEndPx = screenGlowFadeEndPx
        )
    }

    private fun projectRadiusPixels(
        center: Vector3f,
        radius: Float,
        centerUv: Vector2f,
        cameraWorldPos: Vector3f,
        viewRotationMatrix: Matrix3f,
        inverseViewRotationMatrix: Matrix3f,
        projMatrix: Matrix4f,
        screenSize: Vector2f
    ): Float {
        val rightUv = Vector2f()
        val upUv = Vector2f()
        val rightDepth = FloatArray(1)
        val upDepth = FloatArray(1)
        val rightOffset = inverseViewRotationMatrix.transform(Vector3f(radius, 0.0f, 0.0f))
        val upOffset = inverseViewRotationMatrix.transform(Vector3f(0.0f, radius, 0.0f))
        val rightValid = projectWorldToScreen(
            worldPosition = Vector3f(center).add(rightOffset),
            cameraWorldPos = cameraWorldPos,
            viewRotationMatrix = viewRotationMatrix,
            projMatrix = projMatrix,
            uv = rightUv,
            depthOut = rightDepth
        )
        val upValid = projectWorldToScreen(
            worldPosition = Vector3f(center).add(upOffset),
            cameraWorldPos = cameraWorldPos,
            viewRotationMatrix = viewRotationMatrix,
            projMatrix = projMatrix,
            uv = upUv,
            depthOut = upDepth
        )

        var radiusPixels = 0.0f
        if (rightValid) {
            radiusPixels = max(radiusPixels, Vector2f(rightUv).sub(centerUv).mul(screenSize).length())
        }
        if (upValid) {
            radiusPixels = max(radiusPixels, Vector2f(upUv).sub(centerUv).mul(screenSize).length())
        }
        return radiusPixels
    }

    private fun projectWorldToScreen(
        worldPosition: Vector3f,
        cameraWorldPos: Vector3f,
        viewRotationMatrix: Matrix3f,
        projMatrix: Matrix4f,
        uv: Vector2f,
        depthOut: FloatArray
    ): Boolean {
        val cameraRelative = Vector3f(worldPosition).sub(cameraWorldPos)
        val viewPosition = viewRotationMatrix.transform(cameraRelative)
        val clip = projMatrix.transform(Vector4f(viewPosition, 1.0f))
        if (clip.w <= CLIP_EPSILON) {
            uv.set(-2.0f, -2.0f)
            depthOut[0] = 1.0f
            return false
        }

        val invW = 1.0f / clip.w
        val ndcX = clip.x * invW
        val ndcY = clip.y * invW
        val ndcZ = clip.z * invW
        uv.set(ndcX * 0.5f + 0.5f, ndcY * 0.5f + 0.5f)
        depthOut[0] = ndcZ * 0.5f + 0.5f
        return true
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge0 == edge1) {
            return if (value < edge0) 0.0f else 1.0f
        }
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
        return t * t * (3.0f - 2.0f * t)
    }

    private fun mix(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }
}
