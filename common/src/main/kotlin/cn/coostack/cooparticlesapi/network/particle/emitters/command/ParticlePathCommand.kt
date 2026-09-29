package cn.coostack.cooparticlesapi.network.particle.emitters.command

import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathCpuEvaluator
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathEndMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathForwardAxis
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathLibrary
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathPlayMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathProgressMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathSlot
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathEvaluator
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.ControlableParticle
import cn.coostack.cooparticlesapi.particles.control.CParticlePathRequest
import net.minecraft.world.phys.Vec3
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathOrientation
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathOffsetMode
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathBirth
import org.joml.Quaternionf
import org.joml.Vector3f
import org.joml.Matrix4f

/**
 * # 传统粒子的路径位置约束命令
 *
 * 让使用 `ParticleCommand` 的 `ControlableParticle` 沿同一条路径运动。它复用 GPU 路径约束的
 * **同一套**路径定义、播放规则、稳定随机规则与位置约束数学：
 * 求值走 [CParticlePathCpuEvaluator]，数据来自 CPU 侧同版本图层，不从 GPU 回读。
 *
 * ## 与 GPU 路径约束的区别
 * GPU 路径约束在 compute 中直接覆盖位置，因此天然不会重复位移。传统粒子的运动由发射器统一完成，
 * 命令又在运动**之前**执行，所以本命令把结果放进 [cn.coostack.cooparticlesapi.particles.control.ParticleControler]，
 * 由发射器在位移与碰撞之后应用。这样两条路线都不会出现“路径写入后再叠加一次速度积分”。
 *
 * ## 顺序语义
 * 路径约束是**位置权威**：为消除重复位移，命令会同时把 `data.velocity` 归零。因此建议把本命令
 * 放在命令队列的最后一个；同一粒子存在多条路径约束时，后执行的一条覆盖前一条，与 GPU 侧一致。
 *
 * ## 终点与寿命
 * 传统粒子继续使用用户设置的 `lifetime`/`maxAge`，本命令不会覆盖、延长或重置它。
 * [CParticlePathEndMode.DISAPPEAR] 到达终点时会请求结束粒子，这是显式的提前结束模式。
 *
 * @property path 要跟随的路径槽位；生命周期由 [CParticlePathLibrary] 管理
 * @property playMode 播放模式
 * @property progressMode 播放进度到曲线长度的映射方式
 * @property endMode 终点处理方式
 * @property playPeriodTicks 一个完整播放循环的时长（tick）；`<= 0` 表示沿用粒子寿命
 * @property offsetRadius 环绕半径；`<= 0` 表示不启用环绕偏移
 * @property phaseRadians 环绕初始相位（弧度）
 * @property angularVelocityRadiansPerTick 环绕角速度（弧度 / tick）
 * @property forwardAxis 模型前方轴约定
 * @property customForwardAxis [CParticlePathForwardAxis.CUSTOM] 时使用的显式前方轴
 * @property faceMotion 显式开启按前方轴对齐运动方向；默认关闭，仅约束位置
 * @property binding 路径本地空间到世界空间的变换；`null` 表示恒等
 * @property offsetMode 出生偏移方式，默认 NONE；出生参考由发射器记录在各自的控制器中
 */
class ParticlePathCommand(
    var path: CParticlePathSlot,
    var playMode: CParticlePathPlayMode = CParticlePathPlayMode.ONCE,
    var progressMode: CParticlePathProgressMode = CParticlePathProgressMode.ARC_LENGTH,
    var endMode: CParticlePathEndMode = CParticlePathEndMode.HOLD,
    var playPeriodTicks: Double = 0.0,
    var offsetRadius: Double = 0.0,
    var phaseRadians: Double = 0.0,
    var angularVelocityRadiansPerTick: Double = 0.0,
    var forwardAxis: CParticlePathForwardAxis = CParticlePathForwardAxis.MODEL_POSITIVE_X,
    var customForwardAxis: Vec3? = null,
    var faceMotion: Boolean = false,
    var binding: Matrix4f? = null,
    var offsetMode: CParticlePathOffsetMode = CParticlePathOffsetMode.NONE,
) : ParticleCommand, CParticlePathRequest {

    private var active: Boolean = false
    private var requestPosition: Vec3 = Vec3.ZERO
    private var requestDirection: Vec3 = Vec3.ZERO
    private var requestWrapped: Boolean = false
    private var requestDisappear: Boolean = false

    override fun execute(data: ControlableParticleData, particle: ControlableParticle) {
        active = false
        val slot = path
        // 路径槽位已释放时整条命令跳过，不改变粒子位置，也不影响其他命令的副作用。
        if (slot.released || CParticlePathLibrary.slotAt(slot.slot) !== slot) return
        val layer = CParticlePathLibrary.currentLayerData() ?: return
        // 命令在 age 递增之前执行，因此 currentAge 就是 GPU kernel 看到的 age；
        // 求值内部再加上 delta 得到“下一 tick 的位置”，与 GPU 路径完全同相位。
        val age = particle.currentAge
        val result = CParticlePathCpuEvaluator.evaluate(
            layer = layer,
            slot = slot.slot,
            ageTicks = age.toDouble(),
            maxAgeTicks = particle.lifetime.toDouble(),
            input = buildInput(particle.controler.pathBirth),
        ) ?: return
        active = true
        requestPosition = result.position
        requestDirection = result.direction
        requestWrapped = result.wrapped
        requestDisappear = result.reachedEnd && endMode == CParticlePathEndMode.DISAPPEAR
        // 位置权威：清空速度，避免发射器把 loc + velocity 再叠加一次。
        data.velocity = Vec3.ZERO
        particle.controler.setPendingPathRequest(this)
    }

    override fun isActive(): Boolean = active

    override fun pathPosition(): Vec3 = requestPosition

    override fun pathDirection(): Vec3 = requestDirection

    override fun pathWrapped(): Boolean = requestWrapped

    override fun pathReachedEndAndDisappears(): Boolean = requestDisappear

    override fun pathFacesMotion(): Boolean = faceMotion

    override fun pathRotation(): Vector3f? = if (faceMotion) {
        CParticlePathOrientation.angles(requestDirection, forwardAxis, customForwardAxis)
    } else {
        null
    }

    private fun buildInput(birth: CParticlePathBirth?): CParticlePathCpuEvaluator.Input {
        val matrix = binding
        val quaternion = if (matrix == null) {
            Quaternionf()
        } else {
            CParticlePathEvaluator.quaternionOf(Matrix4f(matrix))
        }
        val scale = if (matrix == null) {
            UNIT_SCALE
        } else {
            Vec3(
                Vec3(matrix.m00().toDouble(), matrix.m10().toDouble(), matrix.m20().toDouble()).length(),
                Vec3(matrix.m01().toDouble(), matrix.m11().toDouble(), matrix.m21().toDouble()).length(),
                Vec3(matrix.m02().toDouble(), matrix.m12().toDouble(), matrix.m22().toDouble()).length(),
            )
        }
        return CParticlePathCpuEvaluator.Input(
            progressMode = progressMode,
            playMode = playMode,
            offsetRadius = offsetRadius,
            playPeriodTicks = playPeriodTicks,
            phaseRadians = phaseRadians,
            angularVelocityRadiansPerTick = angularVelocityRadiansPerTick,
            angularVelocityScale = 1.0,
            forwardAxis = forwardAxis,
            customForwardAxis = customForwardAxis,
            bindingQuaternion = quaternion,
            bindingScale = scale,
            deltaTicks = CParticlePathEvaluator.DEFAULT_DELTA_TICKS,
            offsetMode = offsetMode,
            birth = birth,
        )
    }

    private companion object {
        val UNIT_SCALE = Vec3(1.0, 1.0, 1.0)
    }
}
