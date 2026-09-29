package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceSink
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathEndMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathOffsetMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathEvaluator
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathForwardAxis
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathGeometry
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathLibrary
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathPlayMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathPoint
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathProgressMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathSlot
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.supports.TextureSheetsEnum
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * # GPU 路径位置约束测试发射器
 *
 * 粒子沿一条路径前进并绕路径环绕，同时让「模型前方」对齐**实际运动方向**。覆盖：
 *
 * - 位置由进度直接求值（不累加、不是吸引力或弹簧追踪）。
 * - 环绕偏移随年龄旋转；半径为零时退化成纯沿程。
 * - 朝向来自位置差分，因此拐角与环绕处都跟着真实运动方向变化。
 * - `maxAge` 仍由本发射器控制，路径系统不覆盖寿命。
 * - 可选地**动态修改控制点**，用来确认位置立即跟随新几何（不应有平滑追赶）。
 *
 * ## 路径由本发射器自己持有
 * BlockTest 的测试组只在服务端构建，客户端发射器由网络同步创建；而 [CParticlePathSlot] 是
 * 进程内的本地句柄，不能跨网络传输。因此这里按 [pathGeometry] 在**每一侧各自建立**一条本地路径，
 * 在首次提交路径命令时懒创建，并在发射器取消时释放。
 *
 * [pathGeometry] 参与网络同步，两侧因此得到形状一致的路径。
 *
 * ## 观察要点
 * 粒子外观刻意做成「薄而长」的不等比尺寸：显式选择 +Y 后长边沿运动方向。在 ROTATION 模式下它看起来
 * 像一支朝向运动方向的箭头，能直接判断朝向是否正确；如果模型前方轴设错，就会明显看到箭头**侧着**滑行。
 *
 * @param pos 发射器世界坐标；路径几何相对该点解释
 * @param world 发射器生效世界
 */
@CooAutoRegister
class TestPathGPUCParticleEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {

    /**
     * 本发射器使用的路径几何；**随发射器一起同步**。
     *
     * 这就是路径的传递方式：几何是任意数据（任意数量的控制点与空间贝塞尔控制柄），
     * 因此不能用枚举或编号代表「用哪条路径」；而 [CParticlePathSlot] 是进程内句柄、不能跨网络传输。
     * 每一侧拿到同一份几何后各自建立自己的本地路径槽位，形状因此一致。
     */
    @CodecField
    var pathGeometry: CParticlePathGeometry = PathConstraintTestPaths.bezierArc()

    /**
     * 粒子外观与寿命模板。
     *
     * 朝向模式固定为 ROTATION：显式开启跟随后写入前后姿态，渲染时做最短弧插值。
     * 尺寸刻意不等比，形成「箭头」外观。
     */
    @CodecField
    var template = ControlableCParticleData().apply {
        setTextureSheet(TextureSheetsEnum.ADDITION_BLEND_TRANSLUCENT)
        uniformSize = false
        weightSize = 0.06F
        heightSize = 0.40F
        alpha = 0.9F
        maxAge = 240
        light = 15
        speedLimit = 4.0
        visibleRange = 192F
        updateMode = CParticleUpdateMode.STATIC
        cameraOption = ParticleCameraOption.ROTATION
        alphaCurve = CParticleCurve.fadeInOut(fadeIn = 0.08F, fadeOut = 0.85F)
        scaleCurve = CParticleCurve.of(
            0F to 0.3F,
            0.10F to 1F,
            0.85F to 1F,
            1F to 0.15F,
        )
    }

    /** 每 tick 生成的粒子数；默认较小，便于看清单个粒子的运动。 */
    @CodecField
    var spawnPerTick = 24

    /**
     * 一个完整播放循环的时长（tick）。
     *
     * 路径总长只有十格左右，若沿用粒子寿命（几百 tick）会慢到看不出运动，因此默认 60。
     * 设为 `<= 0` 表示沿用 [ControlableCParticleData.maxAge]。
     */
    @CodecField
    var playPeriodTicks = 60.0

    /** 环绕半径；`0` 表示只在路径上走，不做环绕。 */
    @CodecField
    var orbitRadius = 0.65

    /** **一个播放周期内**绕路径环绕的圈数。 */
    @CodecField
    var orbitTurns = 3.0

    /** 显式选择出生偏移；默认仍使用严格路径。 */
    @CodecField
    var offsetMode = CParticlePathOffsetMode.NONE

    /** 出生球体半径，只影响出生位置，不在移动过程中重复随机。 */
    @CodecField
    var spawnSpreadRadius = 1.0

    /** 播放模式。 */
    var playMode = CParticlePathPlayMode.LOOP

    /** 进度映射方式；弧长均匀时沿程速度恒定。 */
    var progressMode = CParticlePathProgressMode.ARC_LENGTH

    /** 终点处理方式；`DISAPPEAR` 会在到达终点时提前结束粒子。 */
    var endMode = CParticlePathEndMode.HOLD

    /** 模型前方轴约定；设成与实际模型不符的值可以观察「侧着前进」。 */
    var forwardAxis = CParticlePathForwardAxis.MODEL_POSITIVE_Y

    /**
     * 是否让控制点随时间摆动。
     *
     * 用于验证「动态改点后位置立即跟随，不存在平滑追赶」这一契约。
     */
    var dynamicPoints = false

    /** 动态点摆动的最大幅度（格）。 */
    var dynamicAmplitude = 1.2

    /** 动态点摆动一个来回所需 tick 数。 */
    var dynamicPeriodTicks = 160

    private val random = Random(System.nanoTime())

    /** 本发射器自己持有的路径；首次需要时创建，取消时释放。 */
    private var ownedPath: CParticlePathSlot? = null

    /** 当前 [ownedPath] 是用哪一份几何建立的；几何变化时据此重建。 */
    private var builtFrom: CParticlePathGeometry? = null

    /** 动态摆动的基准几何；避免在已改写的点上累积漂移。 */
    private var dynamicBase: List<CParticlePathPoint> = emptyList()

    /**
     * 取本发射器的本地路径，必要时按当前几何创建。
     *
     * 懒创建：BlockTest 会为了读取元数据而构造发射器，此时不应分配任何路径资源。
     * 几何被换成另一份（例如从网络同步进来）时，旧槽位会被释放并按新几何重建，
     * 因此不会出现「几何换了但路径还是旧的」这种不一致。
     */
    private fun path(): CParticlePathSlot {
        val current = ownedPath?.takeIf { !it.released && builtFrom === pathGeometry }
        if (current != null) return current
        // 几何变了：释放旧槽位，避免占着槽位不放。
        ownedPath?.let { stale ->
            if (!stale.released) CParticlePathLibrary.release(stale)
        }
        return CParticlePathLibrary.create(pathGeometry).also {
            ownedPath = it
            builtFrom = pathGeometry
            // 摆动基准取自同步过来的几何，因此两侧摆动的起始形状一致。
            dynamicBase = it.definition.points
        }
    }

    override fun doTick() {
        if (dynamicPoints) updateDynamicPoints()
    }

    /**
     * 让控制点按正弦摆动。
     *
     * 直接改写路径几何。进度仍从**既有年龄**求值，因此改点后位置应当立刻跳到新几何上，
     * 不应出现任何平滑追赶或吸附过渡。
     *
     * 摆动基准在第一次用到时自动记录，调用方不需要额外调用准备方法。
     */
    private fun updateDynamicPoints() {
        val slot = path()
        if (dynamicBase.isEmpty()) dynamicBase = slot.definition.points
        val base = dynamicBase
        if (base.size < 3) return
        val phase = 2.0 * PI * tick / dynamicPeriodTicks.coerceAtLeast(1)
        val offset = sin(phase) * dynamicAmplitude
        slot.definition.setPoints(
            base.mapIndexed { index, point ->
                // 只摆动中间点、端点固定，便于看出路径中段被"顶"起来的形状变化。
                if (index == 0 || index == base.lastIndex) {
                    point
                } else {
                    val weight = index.toDouble() / base.lastIndex.toDouble()
                    point.copy(position = point.position.add(0.0, offset * weight, offset * 0.35))
                }
            },
        )
    }

    override fun submitCParticleForces(sink: CParticleForceSink) {
        val slot = path()
        // 环绕角速度按**播放周期**换算：周期是"绕路径走完一圈"的时长，环绕圈数在该周期内完成。
        // 若按寿命换算，短周期下环绕会慢到几乎静止。
        val cycle = CParticlePathEvaluator.resolveCycle(
            playPeriodTicks,
            template.maxAge.toDouble(),
            CParticlePathEvaluator.DEFAULT_DELTA_TICKS,
        )
        val orbitAngularVelocity = if (orbitTurns == 0.0 || cycle <= 0.0) {
            0.0
        } else {
            2.0 * PI * orbitTurns / cycle
        }
        sink.submit(
            CParticleForce.Path(
                path = slot,
                playMode = playMode,
                progressMode = progressMode,
                endMode = endMode,
                playPeriodTicks = playPeriodTicks,
                offsetRadius = orbitRadius,
                // 初始相位固定为 0：所有粒子从横截面同一方向出发，便于判断环绕方向。
                phaseRadians = 0.0,
                angularVelocityRadiansPerTick = orbitAngularVelocity,
                forwardAxis = forwardAxis,
                faceMotion = true,
                offsetMode = offsetMode,
            ),
        )
    }

    /**
     * 严格模式在发射器位置生成；出生偏移模式在路径起点附近随机生成。
     * 不动模板的宽高，保留不等比「箭头」外观。
     */
    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        val count = spawnPerTick
        if (count <= 0) return emptyList()
        return ArrayList<Pair<ControlableParticleData, RelativeLocation>>(count).apply {
            repeat(count) {
                val data = template.clone().apply {
                    yaw = random.nextFloat() * (2F * PI.toFloat())
                    pitch = random.nextFloat() * (2F * PI.toFloat())
                    roll = random.nextFloat() * (2F * PI.toFloat())
                    color = Vector3f(1F)
                    velocity = Vec3.ZERO
                }
                val birth = if (offsetMode == CParticlePathOffsetMode.NONE) RelativeLocation(0.0, 0.0, 0.0)
                    else PathConstraintTestPaths.randomBirth(pathGeometry, spawnSpreadRadius, random)
                add(data to birth)
            }
        }
    }

    /** GPU 入池成功时不会调用；非 CParticle 数据仍走这里。 */
    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float,
    ) {
    }

    override fun stop() {
        super.stop()
        // 释放本发射器持有的路径：引用计数归零即回收图层存储。
        ownedPath?.let { current ->
            if (!current.released) CParticlePathLibrary.release(current)
        }
        ownedPath = null
        builtFrom = null
    }
}
