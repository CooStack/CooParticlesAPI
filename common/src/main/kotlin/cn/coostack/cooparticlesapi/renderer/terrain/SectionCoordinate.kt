package cn.coostack.cooparticlesapi.renderer.terrain

/**
 * 需要局部重建的区块 section 坐标。
 *
 * @property x section 的 X 坐标
 * @property y section 的 Y 坐标
 * @property z section 的 Z 坐标
 */
internal data class SectionCoordinate(
    val x: Int,
    val y: Int,
    val z: Int
)
