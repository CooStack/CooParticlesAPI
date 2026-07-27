package cn.coostack.cooparticlesapi.cparticle

/**
 * 描述主纹理中的矩形 UV 区域。
 *
 * Example: `CParticleUv(0f, 0f, 0.5f, 0.5f)` 选择左上四分之一区域。
 * Forbidden: 不要传入 `NaN` 或无穷值，这些值会让顶点着色器输出无效坐标。
 *
 * @property u0 第一个水平坐标，不强制小于 [u1]
 * @property v0 第一个垂直坐标，不强制小于 [v1]
 * @property u1 第二个水平坐标
 * @property v1 第二个垂直坐标
 * @throws IllegalArgumentException 任一坐标不是有限值时抛出
 */
data class CParticleUv(
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
) {
    init {
        require(u0.isFinite() && v0.isFinite() && u1.isFinite() && v1.isFinite()) {
            "CParticle UV coordinates must be finite"
        }
    }

    /**
     * 提供覆盖整张纹理的常用 UV。
     *
     * Example: `textureOf(id, CParticleUv.FULL)` 使用整张独立纹理。
     * Forbidden: 不要修改这里保存的值；[CParticleUv] 是不可变对象。
     */
    companion object {
        /**
         * 完整的 `0, 0, 1, 1` UV 区域。
         *
         * Example: `CParticleTextureSource.Custom(id, FULL)`。
         * Forbidden: 图集 sprite 应使用图集解析结果，不要假设它占满整张图集。
         */
        @JvmField
        val FULL = CParticleUv(0f, 0f, 1f, 1f)
    }
}
