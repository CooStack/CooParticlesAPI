package cn.coostack.cooparticlesapi.renderer.light

import org.joml.Vector3f

/**
 * 一次世界光照提交的基础描述。
 */
data class WorldLight(
    /** 光源中心位置。 */
    val position: Vector3f,
    /** 光源颜色。 */
    val color: Vector3f,
    /** 光源影响半径。 */
    val radius: Float,
    /** 光源强度。 */
    val intensity: Float,
    /** 光源法线；对定向盘形光照尤其重要。 */
    val normal: Vector3f = Vector3f(0f, 1f, 0f),
    /** 光源几何形态。 */
    val shape: WorldLightShape = WorldLightShape.POINT,
    /** 边缘柔化程度。 */
    val softness: Float = 0.42f
)

/**
 * world light 的几何形态枚举。
 */
enum class WorldLightShape {
    /**
     * 点光源。
     *
     * 能量从中心向周围均匀扩散，适合球形光团、火焰、核心光球等效果。
     */
    POINT,

    /**
     * 盘形光源。
     *
     * 更适合法阵、光盘、定向面光之类具有明显平面朝向的效果。
     */
    DISK
}
