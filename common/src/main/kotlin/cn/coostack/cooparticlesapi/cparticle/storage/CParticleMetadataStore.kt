package cn.coostack.cooparticlesapi.cparticle.storage

import java.util.Arrays

/**
 * 与 36-float 渲染粒子分离的 metadata 存储。
 * identity 四元组按 int bit pattern 保存，physical 四元组保存 charge、mass、radius 和保留值。
 */
class CParticleMetadataStore(capacity: Int) {
    companion object {
        const val STRIDE = 8
        const val BYTE_STRIDE = STRIDE * 4
        const val IDENTITY_SOURCE = 0
        const val IDENTITY_SIGN = 1
        const val IDENTITY_COMMAND_MASK = 2
        const val IDENTITY_FLAGS = 3
        const val PHYSICAL_CHARGE = 4
        const val PHYSICAL_MASS = 5
        const val PHYSICAL_RADIUS = 6
        const val PHYSICAL_RESERVED = 7
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
