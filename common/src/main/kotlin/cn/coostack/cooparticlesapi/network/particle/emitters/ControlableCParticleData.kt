package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.cparticle.CParticleColorCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleCurve
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureSource
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import org.joml.Vector3f

/**
 * 单个 CParticle GPU 粒子的完整生成数据。
 *
 * 纹理、生命周期曲线、旋转和 GPU 更新方式都属于每份 data，且本类型只让当前 data
 * 进入 CParticle 路径。同一次
 * `genParticles()` 可以混合本类型与普通 [ControlableParticleData]：前者走 GPU，后者继续执行
 * `singleParticleAction` 和 [sign] 对应的 CPU 行为。未设置 [textureSource] 时仍使用父类 [effect]。
 *
 * Example:
 * ```kotlin
 * val block = ControlableCParticleData().apply {
 *     textureSource = textureOfBlock(state)
 * }
 * val item = ControlableCParticleData().apply {
 *     textureSource = textureOfItem(stack)
 * }
 * ```
 * Forbidden: 不要把一次 emitter 调用中的公共纹理写回 emitter 单例。
 */
open class ControlableCParticleData : ControlableParticleData() {
    /**
     * # STATIC
     * 生成后不保留本 data；
     *
     * # DYNAMIC
     * 允许同步可变外观
     *
     * 不建议使用DYNAMIC，因为GPU粒子不支持singleAction，
     * 这会导致你必须得额外存储每一个粒子的Data，并且按照对应的方式自己修改
     * 非常浪费性能的同时，也不是这个API希望的做法，
     * 一般 颜色渐变，生命周期的 不透明度，大小等曲线都有做兼容 改为STATIC即可
     *
     * 按照GPU粒子的设定一般使用STATIC
     * */
    var updateMode: CParticleUpdateMode = CParticleUpdateMode.STATIC

    /**
     * 当前粒子数据的显式纹理来源；`null` 表示沿用 [effect]。
     *
     * Example: 同一 emitter 的另一份 CParticle data 可以设置完全不同的 atlas binding。
     * Forbidden: 生成后的 STATIC 粒子不会继续引用或观察此对象。
     */
    var textureSource: CParticleTextureSource? = null

    /** 按 `age / maxAge` 在 GPU 中采样的不透明度乘数曲线。 */
    var alphaCurve: CParticleCurve? = null

    /** 按 `age / maxAge` 同时缩放 X/Y 尺寸的 GPU 曲线。 */
    var sizeCurve: CParticleCurve? = null

    /** 按 `age / maxAge` 与基础颜色相乘的 GPU RGB 曲线。 */
    var colorCurve: CParticleColorCurve? = null

    /** ROTATION 模式的指向向量；`null` 时使用父类 yaw/pitch。 */
    var rotationDirection: Vector3f? = null

    /** 欧拉角速度，单位 rad/tick；x=pitch、y=yaw、z=roll。 */
    var angularVelocity: Vector3f = Vector3f()

    /** 每个整数 tick 使用稳定随机种子重新选择纹理动画帧。 */
    var randomAgePreTick: Boolean = false

    /** GPU 随机帧和随机 UV 的种子；`null` 表示生成时自动分配。 */
    var randomSeed: Int? = null

    /**
     * 保存专用 data 的网络编解码器。
     *
     * Example: `@CodecField var template = ControlableCParticleData()` 会通过此 codec 同步。
     * Forbidden: 不要用父类 codec 解码本类型，否则 [textureSource] 会丢失。
     */
    companion object {
        /**
         * 先处理继承的基础字段，再处理可空纹理来源。
         *
         * Example: 编解码后 `sign`、velocity、effect 和 textureSource 都保持不变。
         * Forbidden: 字段顺序不能与 [decode] 分离修改。
         */
        @JvmField
        val PACKET_CODEC: StreamCodec<RegistryFriendlyByteBuf, ControlableCParticleData> =
            StreamCodec.of(::encode, ::decode)

        /**
         * 编码基础字段和当前 data 的可选纹理来源。
         *
         * Example: `null` 只写入一个 false，客户端继续使用 effect。
         * Forbidden: 不要在这里解析模型或 UV。
         *
         * @param buf 目标网络缓冲区
         * @param data 要编码的 CParticle data
         */
        private fun encode(buf: RegistryFriendlyByteBuf, data: ControlableCParticleData) {
            encodeBase(buf, data)
            val source = data.textureSource
            buf.writeBoolean(source != null)
            if (source != null) {
                CParticleTextureSource.STREAM_CODEC.encode(buf, source)
            }
            buf.writeByte(data.updateMode.ordinal)
            writeNullable(buf, data.alphaCurve, CParticleCurve.STREAM_CODEC)
            writeNullable(buf, data.sizeCurve, CParticleCurve.STREAM_CODEC)
            writeNullable(buf, data.colorCurve, CParticleColorCurve.STREAM_CODEC)
            val direction = data.rotationDirection
            buf.writeBoolean(direction != null)
            if (direction != null) buf.writeVector3f(direction)
            buf.writeVector3f(data.angularVelocity)
            buf.writeBoolean(data.randomAgePreTick)
            val seed = data.randomSeed
            buf.writeBoolean(seed != null)
            if (seed != null) buf.writeInt(seed)
        }

        /**
         * 解码基础字段，并在同一个子类实例上恢复纹理来源。
         *
         * Example: Item 来源解码后仍持有独立的 ItemStack 快照。
         * Forbidden: 不能先通过父类 codec 解码后再强制转换。
         *
         * @param buf 来源网络缓冲区
         * @return 完整的 CParticle data
         */
        private fun decode(buf: RegistryFriendlyByteBuf): ControlableCParticleData {
            val data = decodeBase(buf, ControlableCParticleData())
            if (buf.readBoolean()) {
                data.textureSource = CParticleTextureSource.STREAM_CODEC.decode(buf)
            }
            val updateModeOrdinal = buf.readUnsignedByte().toInt()
            require(updateModeOrdinal < CParticleUpdateMode.entries.size) {
                "unknown CParticle update mode: $updateModeOrdinal"
            }
            data.updateMode = CParticleUpdateMode.entries[updateModeOrdinal]
            data.alphaCurve = readNullable(buf, CParticleCurve.STREAM_CODEC)
            data.sizeCurve = readNullable(buf, CParticleCurve.STREAM_CODEC)
            data.colorCurve = readNullable(buf, CParticleColorCurve.STREAM_CODEC)
            if (buf.readBoolean()) data.rotationDirection = buf.readVector3f()
            data.angularVelocity = buf.readVector3f()
            data.randomAgePreTick = buf.readBoolean()
            if (buf.readBoolean()) data.randomSeed = buf.readInt()
            return data
        }

        private fun <T> writeNullable(
            buf: RegistryFriendlyByteBuf,
            value: T?,
            codec: StreamCodec<in RegistryFriendlyByteBuf, T>,
        ) {
            buf.writeBoolean(value != null)
            if (value != null) codec.encode(buf, value)
        }

        private fun <T> readNullable(
            buf: RegistryFriendlyByteBuf,
            codec: StreamCodec<in RegistryFriendlyByteBuf, T>,
        ): T? = if (buf.readBoolean()) codec.decode(buf) else null
    }

    /**
     * 返回包含纹理来源的专用 codec。
     *
     * Example: emitter 自动编码器可从实例查询此 codec。
     * Forbidden: 返回父类 codec 会截断扩展字段。
     *
     * @return [PACKET_CODEC]
     */
    override fun getCodec(): StreamCodec<RegistryFriendlyByteBuf, out ControlableCParticleData> = PACKET_CODEC

    /**
     * 复制基础字段和当前纹理来源，返回独立的 [ControlableCParticleData]。
     *
     * Example: emitter 模板可以为每个生成结果调用 `clone()`。
     * Forbidden: 派生类若有额外字段，必须覆盖本方法，不能依赖本实现保留派生类型。
     *
     * @return 带有新 UUID 的 CParticle data
     */
    override fun clone(): ControlableCParticleData {
        return ControlableCParticleData().also {
            copyTo(it)
            it.updateMode = updateMode
            it.textureSource = textureSource
            it.alphaCurve = alphaCurve
            it.sizeCurve = sizeCurve
            it.colorCurve = colorCurve
            it.rotationDirection = rotationDirection?.let(::Vector3f)
            it.angularVelocity = Vector3f(angularVelocity)
            it.randomAgePreTick = randomAgePreTick
            it.randomSeed = randomSeed
        }
    }
}
