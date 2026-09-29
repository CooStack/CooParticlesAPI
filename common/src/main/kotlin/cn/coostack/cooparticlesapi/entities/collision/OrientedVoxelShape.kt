package cn.coostack.cooparticlesapi.entities.collision

import it.unimi.dsi.fastutil.doubles.DoubleArrayList
import it.unimi.dsi.fastutil.doubles.DoubleList
import net.minecraft.core.Direction
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * 原版逐轴运动的适配形状，体素数据只表达粗筛范围，实际运动由有向框计算。
 * 不能将本类型交给体素布尔运算后继续期待精确有向框语义。
 * @property box 不可变的世界空间碰撞框
 */
internal class OrientedVoxelShape(private val box: OrientedBox) :
    VoxelShape(BitSetDiscreteVoxelShape(1, 1, 1).apply { fill(0, 0, 0) }) {
    override fun getCoords(axis: Direction.Axis): DoubleList =
        DoubleArrayList(doubleArrayOf(box.bounds.min(axis), box.bounds.max(axis)))

    override fun collide(axis: Direction.Axis, moving: AABB, distance: Double): Double =
        box.clip(axis, moving, distance)
}
