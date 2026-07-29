package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.test.block.BlockTestForward
import net.minecraft.world.phys.Vec3

/**
 * 标识动态窗口正在编辑的位置或 forward 轨道。
 *
 * 示例：`TestControllerTrackKind.POSITION` 使用相对控制器中心的偏移。
 * 禁止把 forward 当作位置轨道写入同一字段。
 */
enum class TestControllerTrackKind(val displayName: String) {
    /** 位置偏移轨道。 */
    POSITION("位置"),

    /** forward 方向轨道。 */
    FORWARD("forward");

    /**
     * 按轨道语义规范化关键帧值。
     *
     * 示例：forward 的 `(0.5, 0.5, 0)` 会转为单位向量，位置值保持原样。
     * 禁止绕过此方法向 forward 轨道插入非单位向量。
     *
     * @param value 待写入轨道的三维值
     * @return 位置原值或规范化后的 forward
     */
    fun normalizeValue(value: Vec3): Vec3 {
        return if (this == FORWARD) BlockTestForward.normalizeOrDefault(value) else value
    }
}
