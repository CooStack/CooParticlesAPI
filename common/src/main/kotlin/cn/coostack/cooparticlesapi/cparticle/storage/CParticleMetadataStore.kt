package cn.coostack.cooparticlesapi.cparticle.storage

import java.util.Arrays
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathBirth
import net.minecraft.world.phys.Vec3

/**
 * 与 36-float 渲染粒子分离的 metadata 存储。
 * identity 四元组按 int bit pattern 保存，physical 四元组保存 charge、mass、radius 和保留值；
 * 第三个四元组保存不可变出生位置及年龄，不随动态外观、路径命令或当前位置更新。
 */
class CParticleMetadataStore(capacity: Int) {
    companion object {
        const val STRIDE = 12
        const val BYTE_STRIDE = STRIDE * 4
        const val IDENTITY_SOURCE = 0
        const val IDENTITY_SIGN = 1
        const val IDENTITY_COMMAND_MASK = 2
        const val IDENTITY_FLAGS = 3
        const val PHYSICAL_CHARGE = 4
        const val PHYSICAL_MASS = 5
        const val PHYSICAL_RADIUS = 6
        const val PHYSICAL_RESERVED = 7
        /** 出生位置的 xyz，下标与 compute 的第三个 vec4 一致。 */
        const val BIRTH_POSITION = 8
        /** 出生时已有年龄，单位 tick。 */
        const val BIRTH_AGE = 11
    }

    var capacity: Int = capacity
        private set

    var data = FloatArray(capacity * STRIDE)
        private set

    /** 扩大 metadata 存储并保留已有槽位内容。 */
    internal fun growTo(newCapacity: Int) {
        require(newCapacity > capacity) {
            "newCapacity must be greater than capacity: $newCapacity <= $capacity"
        }
        data = data.copyOf(newCapacity * STRIDE)
        capacity = newCapacity
    }

    fun set(
        slot: Int,
        sourceId: Int,
        sign: Int,
        commandMask: Int,
        flags: Int,
        charge: Float,
        mass: Float,
        radius: Float,
    ) {
        require(slot in 0 until capacity)
        val base = slot * STRIDE
        data[base + IDENTITY_SOURCE] = Float.fromBits(sourceId)
        data[base + IDENTITY_SIGN] = Float.fromBits(sign)
        data[base + IDENTITY_COMMAND_MASK] = Float.fromBits(commandMask)
        data[base + IDENTITY_FLAGS] = Float.fromBits(flags)
        data[base + PHYSICAL_CHARGE] = when {
            charge.isNaN() -> Float.NaN
            charge.isFinite() -> charge
            else -> 0F
        }
        data[base + PHYSICAL_MASS] = if (mass.isFinite() && mass > 0F) mass else 1F
        data[base + PHYSICAL_RADIUS] = if (radius.isFinite() && radius >= 0F) radius else 0F
        data[base + PHYSICAL_RESERVED] = 0F
        // 出生位置和年龄由紧随其后的 setBirth 一次性完整覆盖。
    }

    /**
     * 入池时记录出生参考；只能在出生/槽位复用时写，不能由模拟器逐 tick 改写。
     *
     * 示例：`metadata.setBirth(slot, Vec3(rx, ry, rz), particle.age.toDouble())`。
     * @param slot 本次入池的槽位
     * @param position system 相对出生位置
     * @param age 出生时的已有年龄（tick）
     */
    internal fun setBirth(slot: Int, position: Vec3, age: Double) {
        setBirth(slot, position.x.toFloat(), position.y.toFloat(), position.z.toFloat(), age.toFloat())
    }

    /** 无临时 Vec3 的出生写入入口，供 GPU emitter 热路径使用。 */
    internal fun setBirth(slot: Int, x: Float, y: Float, z: Float, age: Float) {
        require(slot in 0 until capacity)
        val base = slot * STRIDE
        data[base + BIRTH_POSITION] = x
        data[base + BIRTH_POSITION + 1] = y
        data[base + BIRTH_POSITION + 2] = z
        data[base + BIRTH_AGE] = age
    }

    /** 读取逐粒子的不可变出生参考，用于 CPU 路径求值。 */
    internal fun birth(slot: Int): CParticlePathBirth {
        val base = slot * STRIDE
        return CParticlePathBirth(
            Vec3(
                data[base + BIRTH_POSITION].toDouble(),
                data[base + BIRTH_POSITION + 1].toDouble(),
                data[base + BIRTH_POSITION + 2].toDouble(),
            ),
            data[base + BIRTH_AGE].toDouble(),
        )
    }

    fun clear(slot: Int) {
        require(slot in 0 until capacity)
        Arrays.fill(data, slot * STRIDE, slot * STRIDE + STRIDE, 0F)
    }

    fun sourceId(slot: Int): Int = data[slot * STRIDE + IDENTITY_SOURCE].toRawBits()
    fun sign(slot: Int): Int = data[slot * STRIDE + IDENTITY_SIGN].toRawBits()
    fun commandMask(slot: Int): Int = data[slot * STRIDE + IDENTITY_COMMAND_MASK].toRawBits()
    fun flags(slot: Int): Int = data[slot * STRIDE + IDENTITY_FLAGS].toRawBits()
    fun charge(slot: Int): Float = data[slot * STRIDE + PHYSICAL_CHARGE]
    fun mass(slot: Int): Float = data[slot * STRIDE + PHYSICAL_MASS]
    fun radius(slot: Int): Float = data[slot * STRIDE + PHYSICAL_RADIUS]
}
