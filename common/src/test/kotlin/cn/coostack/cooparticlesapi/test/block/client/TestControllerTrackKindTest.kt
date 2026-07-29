package cn.coostack.cooparticlesapi.test.block.client

import cn.coostack.cooparticlesapi.test.block.BlockTestForward
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 覆盖曲线编辑器写入位置和 forward 关键帧时的值规范化规则。
 *
 * 示例：Ctrl 插入 forward 帧时，中间采样值会先转成单位向量。
 * 禁止用这些测试推断时间曲线形状；这里只校验轨道值语义。
 */
class TestControllerTrackKindTest {
    /**
     * 验证位置轨道不会改变用户输入的偏移长度。
     *
     * 示例：`(2, -3, 4)` 应原样保留。
     * 禁止把位置偏移误当作方向向量单位化。
     */
    @Test
    fun `position values remain unchanged`() {
        val value = Vec3(2.0, -3.0, 4.0)

        assertEquals(value, TestControllerTrackKind.POSITION.normalizeValue(value))
    }

    /**
     * 验证 forward 采样值会单位化，零向量会回退到默认朝向。
     *
     * 示例：`(0.5, 0.5, 0)` 的结果长度应为 `1`。
     * 禁止把零向量保存在 forward 关键帧中。
     */
    @Test
    fun `forward values are normalized with a safe fallback`() {
        val normalized = TestControllerTrackKind.FORWARD.normalizeValue(Vec3(0.5, 0.5, 0.0))

        assertEquals(1.0, normalized.length(), 1.0E-9)
        assertEquals(normalized.x, normalized.y, 1.0E-9)
        assertEquals(
            BlockTestForward.DEFAULT,
            TestControllerTrackKind.FORWARD.normalizeValue(Vec3.ZERO)
        )
    }
}
