package cn.coostack.cooparticlesapi.cparticle.path

import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.ForceCommand
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

/**
 * # 路径位置约束的命令打包
 *
 * 把一条 [CParticleForce.Path] 解析成 GPU compute 与 CPU 模拟器都能读取的 16-float payload。
 * 这里是“路径槽位 → 图层基址 → 命令参数”的唯一换算点，因此两侧不会出现布局分歧。
 *
 * ## 打包时机
 * 每个 system 每次 tick 打包一次命令，打包发生在 [CParticlePathLibrary.syncLayer] 之后。
 * 路径槽位尚未分配图层区间时本命令会被**跳过**（返回 `false`），不会写出读不到数据的参数。
 *
 * ## 精度保护
 * 图层重建版本写入 payload 的 [CooPathCommandAbi.P_LAYER_VERSION]。版本被限制在
 * [MAX_EXACT_LAYER_VERSION] 以内以保证 float 能精确表示；运行时会校验版本，
 * 因此引用了旧基址的陈旧命令会整条跳过，而不是读到别的路径。
 */
object CParticlePathCommandPacker {
    /**
     * 图层版本的精确表示上限。
     *
     * float 只有 24 bit 尾数，超过本值的整数无法精确往返；达到上限时图层会整体重建版本计数，
     * 因此这里只需要保证日常运行区间可精确表示。
     */
    const val MAX_EXACT_LAYER_VERSION = 0x00FF_FFFF

    /**
     * 把路径命令写入 Command 槽位。
     *
     * 必须同时写入 **header** 与 payload：header 的类型字段是 shader 与 CPU 模拟器识别
     * 「这是一条路径约束」的唯一依据。只写 payload 会让类型字段保持为 0，
     * 命令被当成未知类型静默跳过——粒子既不沿路径运动也不报错，表现为全部堆在出生点。
     *
     * @param out 目标 float 数组
     * @param base [out] 中命令起始下标（`ForceCommand` header 的位置）
     * @param command 路径命令；其 `force` 必须是 [CParticleForce.Path]
     * @param layerVersion 当前图层重建版本
     * @return 打包成功返回 `true`；路径槽位已释放或尚未分配图层区间时返回 `false`
     */
    fun pack(out: FloatArray, base: Int, command: ForceCommand, layerVersion: Int): Boolean {
        val force = command.force
        require(force is CParticleForce.Path) {
            "CParticlePathCommandPacker only packs CParticleForce.Path, got ${force.javaClass.simpleName}"
        }
        val slot = force.path
        require(!force.faceMotion || force.forwardAxis != CParticlePathForwardAxis.CUSTOM) {
            "GPU path commands require a coordinate forward axis; custom axes are supported by ParticlePathCommand"
        }
        // 先完成全部校验再写：失败时保持整条命令为零，运行时会按“未知类型”安全跳过，
        // 而不是留下一个类型正确但参数残缺的命令。
        if (slot.released) return false
        if (slot.base <= 0) return false
        if (CParticlePathLibrary.slotAt(slot.slot) !== slot) return false

        val payload = base + FORCE_PAYLOAD_OFFSET
        // payload 预算在编译期无法表达，因此在运行时守住：越界写会踩到下一条命令的 header
        // （类型字段），让后续命令被解释成另一种力，而症状会远离真正的出错点。
        check(payload + CooPathCommandAbi.USED_FLOATS <= base + ForceCommand.STRIDE) {
            "Path command payload overflows ForceCommand stride: needs " +
                "${CooPathCommandAbi.USED_FLOATS} floats after header, budget is " +
                "${ForceCommand.STRIDE - CooPathCommandAbi.FORCE_PAYLOAD_OFFSET}"
        }
        command.packHeader(out, base)
        out[payload + CooPathCommandAbi.P_SLOT] = slot.slot.toFloat()
        out[payload + CooPathCommandAbi.P_MODE_PACK] = CooPathCommandAbi.packModes(
            force.playMode,
            force.progressMode,
            force.endMode,
            force.forwardAxis.wireValue,
            force.offsetMode,
        ).toFloat()
        out[payload + CooPathCommandAbi.P_OFFSET_RADIUS] =
            CParticlePathEvaluator.usableRadius(force.offsetRadius).toFloat()
        out[payload + CooPathCommandAbi.P_LAYER_VERSION] =
            layerVersion.coerceIn(0, MAX_EXACT_LAYER_VERSION).toFloat()
        // 未显式给出周期时写入哨兵值 0：运行时会按粒子寿命换算，不会把它当成"周期为零"。
        val period = force.playPeriodTicks
        out[payload + CooPathCommandAbi.P_PLAY_PERIOD] =
            if (period.isFinite() && period > 0.0) period.toFloat() else 0F
        out[payload + CooPathCommandAbi.P_PHASE] = force.phaseRadians.toFloat()
        out[payload + CooPathCommandAbi.P_ANGULAR_VELOCITY] = force.angularVelocityRadiansPerTick.toFloat()
        out[payload + CooPathCommandAbi.P_ANGULAR_SCALE] = 1F
        out[payload + CooPathCommandAbi.P_FACE_MOTION] = if (force.faceMotion) 1F else 0F
        writeBinding(out, payload, force)
        return true
    }

    /**
     * 把绑定变换写成「四元数 + 缩放」。
     *
     * 平移不参与：命令与粒子位置都在 system 原点相对坐标中，路径自然随 system 一起移动。
     * 自定义前方轴由 [CParticlePathForwardAxis.CUSTOM] 在求值侧解释，这里只写模式编号。
     */
    private fun writeBinding(out: FloatArray, payload: Int, force: CParticleForce.Path) {
        val binding = force.binding
        if (binding == null) {
            out[payload + CooPathCommandAbi.P_BINDING_QUAT] = 0F
            out[payload + CooPathCommandAbi.P_BINDING_QUAT + 1] = 0F
            out[payload + CooPathCommandAbi.P_BINDING_QUAT + 2] = 0F
            out[payload + CooPathCommandAbi.P_BINDING_QUAT + 3] = 1F
            out[payload + CooPathCommandAbi.P_BINDING_SCALE] = 1F
            out[payload + CooPathCommandAbi.P_BINDING_SCALE + 1] = 1F
            out[payload + CooPathCommandAbi.P_BINDING_SCALE + 2] = 1F
            return
        }
        val quaternion = CParticlePathEvaluator.quaternionOf(Matrix4f(binding))
        CParticlePathEvaluator.writeQuaternion(out, payload + CooPathCommandAbi.P_BINDING_QUAT, quaternion)
        out[payload + CooPathCommandAbi.P_BINDING_SCALE] = bindingScaleX(binding)
        out[payload + CooPathCommandAbi.P_BINDING_SCALE + 1] = bindingScaleY(binding)
        out[payload + CooPathCommandAbi.P_BINDING_SCALE + 2] = bindingScaleZ(binding)
    }

    private fun bindingScaleX(matrix: org.joml.Matrix4fc): Float =
        Vec3(matrix.m00().toDouble(), matrix.m10().toDouble(), matrix.m20().toDouble()).length().toFloat()

    private fun bindingScaleY(matrix: org.joml.Matrix4fc): Float =
        Vec3(matrix.m01().toDouble(), matrix.m11().toDouble(), matrix.m21().toDouble()).length().toFloat()

    private fun bindingScaleZ(matrix: org.joml.Matrix4fc): Float =
        Vec3(matrix.m02().toDouble(), matrix.m12().toDouble(), matrix.m22().toDouble()).length().toFloat()

    /**
     * 从 payload 还原一份求值参数。
     *
     * 供 CPU 模拟器与测试对照使用；GPU 侧在 shader 内按同一布局读取。
     *
     * @param commands 命令数组
     * @param base 命令起始下标
     * @return 求值参数
     */
    fun readEvaluation(commands: FloatArray, base: Int): CParticlePathEvaluation {
        val payload = base + FORCE_PAYLOAD_OFFSET
        val packed = commands[payload + CooPathCommandAbi.P_MODE_PACK].toInt()
        val forwardMode = CooPathCommandAbi.forwardAxisModeOf(packed)
        return CParticlePathEvaluation(
            progressMode = CParticlePathProgressMode.entries.getOrElse(
                CooPathCommandAbi.progressModeOf(packed),
            ) { CParticlePathProgressMode.ARC_LENGTH },
            offsetRadius = commands[payload + CooPathCommandAbi.P_OFFSET_RADIUS].toDouble(),
            playPeriodTicks = commands[payload + CooPathCommandAbi.P_PLAY_PERIOD].toDouble(),
            phaseRadians = commands[payload + CooPathCommandAbi.P_PHASE].toDouble(),
            angularVelocityRadiansPerTick =
                commands[payload + CooPathCommandAbi.P_ANGULAR_VELOCITY].toDouble(),
            angularVelocityScale = commands[payload + CooPathCommandAbi.P_ANGULAR_SCALE].toDouble(),
            forwardAxis = CParticlePathForwardAxis.entries.getOrElse(forwardMode) {
                CParticlePathForwardAxis.MODEL_POSITIVE_X
            },
            bindingQuaternion = CParticlePathEvaluator.readQuaternion(
                commands,
                payload + CooPathCommandAbi.P_BINDING_QUAT,
            ),
            bindingScale = Vec3(
                commands[payload + CooPathCommandAbi.P_BINDING_SCALE].toDouble(),
                commands[payload + CooPathCommandAbi.P_BINDING_SCALE + 1].toDouble(),
                commands[payload + CooPathCommandAbi.P_BINDING_SCALE + 2].toDouble(),
            ),
            deltaTicks = CParticlePathEvaluator.DEFAULT_DELTA_TICKS,
        )
    }

    /** 读出 payload 中的路径槽位号。 */
    fun readSlot(commands: FloatArray, base: Int): Int =
        commands[base + FORCE_PAYLOAD_OFFSET + CooPathCommandAbi.P_SLOT].toInt()

    /** 读出 payload 中打包的图层重建版本。 */
    fun readLayerVersion(commands: FloatArray, base: Int): Int =
        commands[base + FORCE_PAYLOAD_OFFSET + CooPathCommandAbi.P_LAYER_VERSION].toInt()

    /** 读出播放模式。 */
    fun readPlayMode(commands: FloatArray, base: Int): CParticlePathPlayMode {
        val packed = commands[base + FORCE_PAYLOAD_OFFSET + CooPathCommandAbi.P_MODE_PACK].toInt()
        return CParticlePathPlayMode.entries.getOrElse(CooPathCommandAbi.playModeOf(packed)) {
            CParticlePathPlayMode.ONCE
        }
    }

    /** 读出终点处理模式。 */
    fun readEndMode(commands: FloatArray, base: Int): CParticlePathEndMode {
        val packed = commands[base + FORCE_PAYLOAD_OFFSET + CooPathCommandAbi.P_MODE_PACK].toInt()
        return CParticlePathEndMode.entries.getOrElse(CooPathCommandAbi.endModeOf(packed)) {
            CParticlePathEndMode.HOLD
        }
    }

    /** `ForceCommand` header 之后就是 16-float 的 Force payload。 */
    private const val FORCE_PAYLOAD_OFFSET = CooPathCommandAbi.FORCE_PAYLOAD_OFFSET
}
