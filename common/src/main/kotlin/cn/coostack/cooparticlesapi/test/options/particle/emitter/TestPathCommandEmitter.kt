package cn.coostack.cooparticlesapi.test.options.particle.emitter

import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathEndMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathOffsetMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathEvaluator
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathForwardAxis
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathGeometry
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathLibrary
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathPlayMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathProgressMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathSlot
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticleCommandQueue
import cn.coostack.cooparticlesapi.network.particle.emitters.command.ParticlePathCommand
import cn.coostack.cooparticlesapi.particles.ParticleCameraOption
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import cn.coostack.cooparticlesapi.supports.TextureSheetsEnum
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.random.Random

/**
 * # 传统 ParticleCommand 路径位置约束测试发射器
 *
 * 与 [TestPathGPUCParticleEmitter] 使用同一几何形状与同一组播放参数，但粒子走传统
 * [cn.coostack.cooparticlesapi.particles.ControlableParticle] 路径，运动由 [ParticlePathCommand] 约束。
 * 用途：
 *
 * - 与 GPU 用例切换对比，检查两条路线的位置、环绕与朝向是否一致。
 * - 验证传统粒子的路径命令不会在发射器运动之后再叠加一次速度积分。
 *
 * ## 与 GPU 用例的预期差异
 * - 规模：传统粒子是逐粒子对象，数量必须远小于 GPU 用例，否则会拖慢客户端。
 * - 这里只做行为对照，不用于性能对比。
 *
 * ## 路径由本发射器自己持有
 * 原因与 [TestPathGPUCParticleEmitter] 相同：路径句柄不能跨网络传输，因此按 [pathKind]
 * 在每一侧各自建立一条本地路径，懒创建、取消时释放。
 *
 * @param pos 发射器世界坐标；路径几何相对该点解释
 * @param world 发射器生效世界
 */
@CooAutoRegister
class TestPathCommandEmitter(pos: Vec3, world: Level?) : AutoParticleEmitters(pos, world) {

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
     * 刻意不使用 `ControlableCParticleData`：只有非 GPU 数据才会落到传统
     * [cn.coostack.cooparticlesapi.particles.ControlableParticle] 路径，从而真正走到 [ParticlePathCommand]。
     */
    @CodecField
    var template = ControlableParticleData().apply {
        setTextureSheet(TextureSheetsEnum.ADDITION_BLEND_TRANSLUCENT)
        uniformSize = false
        weightSize = 0.06F
        heightSize = 0.40F
        alpha = 0.9F
        maxAge = 120
        light = 15
        speedLimit = 4.0
        visibleRange = 192F
        cameraOption = ParticleCameraOption.ROTATION
    }

    /** 每 tick 生成的粒子数；传统粒子逐对象存在，默认较低。 */
    @CodecField
    var spawnPerTick = 6

    /**
     * 一个完整播放循环的时长（tick）。
     *
     * 与 GPU 用例口径一致：周期是"绕路径走完一圈"的时长，短周期才能看出沿路径运动。
     */
    @CodecField
    var playPeriodTicks = 60.0

    /** 环绕半径。 */
    @CodecField
    var orbitRadius = 0.65

    /** **一个播放周期内**绕路径环绕的圈数。 */
    @CodecField
    var orbitTurns = 3.0

    /** 显式选择出生偏移；默认仍使用严格路径。 */
    @CodecField
    var offsetMode = CParticlePathOffsetMode.NONE

    /** 出生球体半径，只在生成时随机一次。 */
    @CodecField
    var spawnSpreadRadius = 1.0

    /** 播放模式。 */
    var playMode = CParticlePathPlayMode.LOOP

    /** 进度映射方式。 */
    var progressMode = CParticlePathProgressMode.ARC_LENGTH

    /** 终点处理方式。 */
    var endMode = CParticlePathEndMode.HOLD

    /** 模型前方轴约定。 */
    var forwardAxis = CParticlePathForwardAxis.MODEL_POSITIVE_Y

    /**
     * 全发射器共享的一条命令队列。
     *
     * 命令实例本来就是无状态的、可被同一发射器所有粒子共享；这里缓存一份队列，
     * 避免在每个粒子的 `singleParticleAction` 里重复创建对象。
     */
    private val queue = ParticleCommandQueue()

    private val random = Random(System.nanoTime())

    /** 本发射器自己持有的路径；首次需要时创建，取消时释放。 */
    private var ownedPath: CParticlePathSlot? = null

    /** 当前 [ownedPath] 是用哪一份几何建立的；几何变化时据此重建。 */
    private var builtFrom: CParticlePathGeometry? = null

    /** 上次构建队列时的配置指纹；参数变化时才重建，避免每 tick 重建。 */
    private var configuredFingerprint: String = ""

    /**
     * 取本发射器的本地路径，必要时按当前几何创建。
     *
     * 懒创建：BlockTest 会为了读取元数据而构造发射器，此时不应分配任何路径资源。
     * 几何被换成另一份（例如从网络同步进来）时，旧槽位会被释放并按新几何重建。
     */
    private fun path(): CParticlePathSlot {
        val current = ownedPath?.takeIf { !it.released && builtFrom === pathGeometry }
        if (current != null) return current
        ownedPath?.let { stale ->
            if (!stale.released) CParticlePathLibrary.release(stale)
        }
        return CParticlePathLibrary.create(pathGeometry).also {
            ownedPath = it
            builtFrom = pathGeometry
        }
    }

    /**
     * 按当前参数与路径重建队列。
     *
     * 路径命令会把 `data.velocity` 归零以消除发射器的重复位移，所以它是队列中的**最后一个**命令；
     * 粒子的 `maxAge` 由模板数据提供，路径系统不会改写它，无需再补一条命令。
     */
    private fun rebuildQueue(slot: CParticlePathSlot) {
        queue.commands.clear()
        // 环绕角速度按**播放周期**换算，与 GPU 用例口径一致。
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
        queue.add(
            ParticlePathCommand(
                path = slot,
                playMode = playMode,
                progressMode = progressMode,
                endMode = endMode,
                playPeriodTicks = playPeriodTicks,
                offsetRadius = orbitRadius,
                phaseRadians = 0.0,
                angularVelocityRadiansPerTick = orbitAngularVelocity,
                forwardAxis = forwardAxis,
                faceMotion = true,
                offsetMode = offsetMode,
            ),
        )
        configuredFingerprint = fingerprint(slot)
    }

    /** 把会影响命令内容的参数压成一个字符串，用于判断是否需要重建队列。 */
    private fun fingerprint(slot: CParticlePathSlot): String =
        "${slot.slot}|$playMode|$progressMode|$endMode|$playPeriodTicks|$orbitRadius|" +
            "$orbitTurns|$forwardAxis|${template.maxAge}|$offsetMode"

    override fun doTick() {
        val slot = path()
        if (configuredFingerprint != fingerprint(slot)) rebuildQueue(slot)
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        val count = spawnPerTick
        if (count <= 0) return emptyList()
        val slot = path()
        if (configuredFingerprint != fingerprint(slot)) rebuildQueue(slot)
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

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float,
    ) {
        controler.addPreTickAction {
            queue.applyVelocity(data, this)
        }
    }

    override fun stop() {
        super.stop()
        // 队列持有路径引用，先清空再释放，避免停止后仍指向已释放的槽位。
        queue.commands.clear()
        configuredFingerprint = ""
        ownedPath?.let { current ->
            if (!current.released) CParticlePathLibrary.release(current)
        }
        ownedPath = null
        builtFrom = null
    }
}
