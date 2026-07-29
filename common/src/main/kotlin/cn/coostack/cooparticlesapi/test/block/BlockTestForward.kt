package cn.coostack.cooparticlesapi.test.block

import net.minecraft.world.phys.Vec3

/**
 * 提供模拟玩家朝向的默认值和纯向量规范化。
 *
 * 示例：曲线编辑器可在 Minecraft 注册表启动前调用 [normalizeOrDefault]。
 * 禁止在此引用 [BlockTestPlayer] 或其他实体类，否则纯单元测试会触发游戏注册表初始化。
 */
object BlockTestForward {
    /**
     * 无有效朝向时使用的正 Z 单位向量。
     *
     * 示例：零向量会回退到 [DEFAULT]。
     * 禁止修改向量分量；[Vec3] 是不可变值。
     */
    val DEFAULT: Vec3 = Vec3(0.0, 0.0, 1.0)

    /**
     * 把有限的非零向量转为单位向量，其他输入使用 [DEFAULT]。
     *
     * 示例：`Vec3(2.0, 0.0, 0.0)` 会得到正 X 单位向量。
     * 禁止假定 `NaN` 或极短向量可以保留原值。
     *
     * @param value 待规范化朝向
     * @return 单位向量或 [DEFAULT]
     */
    fun normalizeOrDefault(value: Vec3): Vec3 {
        if (!value.x.isFinite() || !value.y.isFinite() || !value.z.isFinite() || value.lengthSqr() < 1.0E-8) {
            return DEFAULT
        }
        return value.normalize()
    }
}
