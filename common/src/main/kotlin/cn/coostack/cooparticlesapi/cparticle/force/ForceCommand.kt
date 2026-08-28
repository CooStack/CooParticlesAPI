package cn.coostack.cooparticlesapi.cparticle.force

import net.minecraft.world.phys.Vec3

/**
 * GPU/CPU 共用的力命令。header 占 4 个 int，后面复用旧 Force 的 16-float packing 作为 p0..p3。
 * 这样旧类型的数值布局保持不变，同时允许按 sourceId、sign 和 commandMask 选择粒子。
 */
data class ForceCommand(
    val force: CParticleForce,
    val selector: CParticleSelector = CParticleSelector.All,
) {
    fun pack(out: FloatArray, base: Int, origin: Vec3) {
        packHeader(out, base)
        force.pack(out, base + 4, origin)
        require(force !is CParticleForce.Texture && force !is CParticleForce.FluidFlow) {
            "Resource-backed force ${force.javaClass.simpleName} must be packed with a resolved resource slot"
        }
    }

    internal fun pack(out: FloatArray, base: Int, origin: Vec3, resourceSlot: Int) {
        packHeader(out, base)
        force.pack(out, base + 4, origin)
        when (force) {
            is CParticleForce.Texture -> out[base + 7] = resourceSlot.toFloat()
            is CParticleForce.FluidFlow -> out[base + 6] = resourceSlot.toFloat()
            else -> require(resourceSlot < 0) {
                "Force ${force.javaClass.simpleName} does not use a resource slot"
            }
        }
    }

    private fun packHeader(out: FloatArray, base: Int) {
        out[base] = Float.fromBits(force.typeId)
        out[base + 1] = Float.fromBits(selector.mode)
        out[base + 2] = Float.fromBits(selector.value)
        out[base + 3] = Float.fromBits(selector.mask)
    }

    companion object {
        const val STRIDE = 20
        /**
         * 单个 system 可提交的 Command 数量。
         * 128 条记录占用 10,240 字节，低于 OpenGL 4.3 保证的最小 SSBO 块容量；
         * 只有使用选择器、混合新 Force 或超过 legacy 条件时，旧 1..9 Force 才会包装为 Command。
         */
        const val MAX_COMMANDS = 128
    }
}

typealias CParticleForceCommand = ForceCommand
