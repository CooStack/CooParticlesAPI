package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathGeometry
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathPoint
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathSegmentType
import net.minecraft.world.phys.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import cn.coostack.cooparticlesapi.utils.RelativeLocation

/**
 * # 路径约束测试用例使用的几何
 *
 * 这里提供的是**可序列化几何数据**（[CParticlePathGeometry]），不是「几何种类」的枚举：
 * 路径形状是任意数据，发射器把几何本身作为自己的字段同步，两侧各自建立本地路径。
 *
 * 每个构建器返回新对象，因此调用方修改自己的几何不会影响别人。
 */
object PathConstraintTestPaths {
    /** 在路径起点附近的球体内均匀出生；只在生成粒子时调用，后续移动不重新抽样。 */
    fun randomBirth(geometry: CParticlePathGeometry, radius: Double, random: Random): RelativeLocation {
        val start = geometry.points.first().position
        if (!radius.isFinite() || radius <= 0.0) return RelativeLocation(start.x, start.y, start.z)
        var offset: Vec3
        do {
            offset = Vec3(random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0))
        } while (offset.lengthSqr() > 1.0)
        val position = start.add(offset.scale(radius))
        return RelativeLocation(position.x, position.y, position.z)
    }
    /** 圆环半径；环绕偏移必须明显小于它，否则相邻段的环绕会互相穿透。 */
    private const val RING_RADIUS = 2.2

    /** 水平圆环的段数。 */
    private const val RING_SEGMENTS = 12

    /**
     * 抬高的平缓弧线，作为偏移环绕示例的中心路径。
     *
     * 示例：交给 [TestPathGPUCParticleEmitter] 并设置非零 orbitRadius，可观察粒子绕中心线前进。
     * 两端距基准点 3 格高，避免环绕轨迹被地面遮住；中间控制柄保持切线连续。
     */
    fun orbitArc(): CParticlePathGeometry = CParticlePathGeometry(
        points = listOf(
            CParticlePathPoint(Vec3(-6.0, 3.0, 0.0), outHandle = Vec3(2.0, 0.0, 0.0)),
            CParticlePathPoint(
                Vec3(0.0, 5.0, 0.0),
                inHandle = Vec3(-2.0, 0.0, 0.0),
                outHandle = Vec3(2.0, 0.0, 0.0),
            ),
            CParticlePathPoint(Vec3(6.0, 3.0, 0.0), inHandle = Vec3(-2.0, 0.0, 0.0)),
        ),
        segmentType = CParticlePathSegmentType.BEZIER,
        closed = false,
    )

    /**
     * 开放的空间贝塞尔曲线：先上抛、经两个拐角再落下。
     *
     * 便于观察朝向是否跟随运动方向：拐角处方向变化明显。
     */
    fun bezierArc(): CParticlePathGeometry = CParticlePathGeometry(
        points = listOf(
            CParticlePathPoint(Vec3(-2.4, 0.0, -2.4), outHandle = Vec3(0.0, 0.0, -1.6)),
            CParticlePathPoint(
                Vec3(0.0, 2.6, -1.2),
                inHandle = Vec3(-1.2, -0.6, 0.0),
                outHandle = Vec3(1.2, -0.6, 0.0),
            ),
            CParticlePathPoint(
                Vec3(2.4, 0.6, 0.0),
                inHandle = Vec3(0.0, 0.0, -1.2),
                outHandle = Vec3(0.0, 0.0, 1.2),
            ),
            CParticlePathPoint(
                Vec3(0.0, 2.2, 2.4),
                inHandle = Vec3(1.2, -0.6, 0.0),
                outHandle = Vec3(-1.2, -0.6, 0.0),
            ),
            CParticlePathPoint(Vec3(-2.2, 0.4, 1.4), inHandle = Vec3(0.0, 0.0, 1.4)),
        ),
        segmentType = CParticlePathSegmentType.BEZIER,
        closed = false,
    )

    /** 折线：两个急拐角，用于观察拐角处横截面参考基是否突变。 */
    fun linearZigzag(): CParticlePathGeometry = CParticlePathGeometry.polyline(
        listOf(
            Vec3(-2.6, 0.4, -1.2),
            Vec3(-0.6, 2.0, 1.4),
            Vec3(1.4, 0.4, -1.4),
            Vec3(2.8, 2.2, 1.0),
        ),
        closed = false,
    )

    /** 闭合的水平圆环：用于观察接缝处是否连续推进。 */
    fun closedRing(): CParticlePathGeometry = CParticlePathGeometry.polyline(
        (0 until RING_SEGMENTS).map { index ->
            val angle = 2.0 * PI * index / RING_SEGMENTS
            Vec3(cos(angle) * RING_RADIUS, 0.6, sin(angle) * RING_RADIUS)
        },
        closed = true,
    )

    /** 闭合的空间贝塞尔环：段类型与闭合属性都为非默认值。 */
    fun closedBezierRing(): CParticlePathGeometry = CParticlePathGeometry(
        points = listOf(
            CParticlePathPoint(
                Vec3(-1.8, 0.8, -1.8),
                inHandle = Vec3(0.0, 0.0, 1.2),
                outHandle = Vec3(0.0, 0.0, -1.2),
            ),
            CParticlePathPoint(
                Vec3(1.8, 1.6, -1.8),
                inHandle = Vec3(-1.2, 0.0, 0.0),
                outHandle = Vec3(1.2, 0.0, 0.0),
            ),
            CParticlePathPoint(
                Vec3(1.8, 0.8, 1.8),
                inHandle = Vec3(0.0, 0.0, -1.2),
                outHandle = Vec3(0.0, 0.0, 1.2),
            ),
            CParticlePathPoint(
                Vec3(-1.8, 1.6, 1.8),
                inHandle = Vec3(1.2, 0.0, 0.0),
                outHandle = Vec3(-1.2, 0.0, 0.0),
            ),
        ),
        segmentType = CParticlePathSegmentType.BEZIER,
        closed = true,
    )
}
