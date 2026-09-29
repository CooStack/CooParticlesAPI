package cn.coostack.cooparticlesapi.entities.collision

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import org.joml.Quaterniond
import org.joml.Vector3d
import kotlin.math.PI

/**
 * 不规则实体的不可变仿射变换，碰撞、采样和渲染共用同一坐标约定。
 * 示例：`EntityTransform(scale = Vec3(2.0, 1.0, 2.0))`。
 * @property rotation 按 XYZ 顺序组合的角度，单位为度
 * @property offset 相对于放置原点的平移，单位为格
 * @property scale 三轴正缩放倍率
 * @property pivot 模型局部坐标中的旋转和缩放支点
 * @property pivotCompensation 更换支点时保持模型姿态的额外平移
 */
data class EntityTransform(
    val rotation: Vec3 = Vec3.ZERO,
    val offset: Vec3 = Vec3.ZERO,
    val scale: Vec3 = Vec3(1.0, 1.0, 1.0),
    val pivot: Vec3 = Vec3.ZERO,
    val pivotCompensation: Vec3 = Vec3.ZERO
) {
    init {
        require(listOf(rotation, offset, scale, pivot, pivotCompensation).all {
            it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
        }) { "变换分量必须有限" }
        require(scale.x > 0.0 && scale.y > 0.0 && scale.z > 0.0) { "缩放必须为正数" }
        require(pivotPosition.let { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
    }

    /** 支点相对于放置原点的位置，已经包含显式平移和姿态补偿。 */
    val pivotPosition: Vec3 get() = offset.add(pivotCompensation).add(pivot)

    /**
     * 构造调用方独占的旋转四元数。
     * 示例：`val rotation = transform.quaternion()`。
     * @return 按 XYZ 欧拉角组合的单位四元数
     */
    fun quaternion(): Quaterniond = Quaterniond().rotationXYZ(
        rotation.x % 360.0 * PI / 180.0, rotation.y % 360.0 * PI / 180.0, rotation.z % 360.0 * PI / 180.0
    )

    /**
     * 把模型局部点转换到放置原点空间。
     * 示例：`transform.transformPoint(Vec3(1.0, 0.0, 0.0))`。
     * @param point 未变换的局部点
     * @return 包含缩放、旋转、平移和支点补偿的点
     */
    fun transformPoint(point: Vec3): Vec3 {
        val relative = point.subtract(pivot).multiply(scale)
        val rotated = quaternion().transform(Vector3d(relative.x, relative.y, relative.z))
        return pivotPosition.add(rotated.x, rotated.y, rotated.z)
    }

    /**
     * 更换支点但保持当前所有模型点的位置，后续缩放和旋转才使用新支点。
     * 示例：`transform.withPivotKeepingPosition(Vec3(4.0, 0.0, 2.0))`。
     * @param newPivot 新的有限局部支点
     * @return 带有新支点与独立补偿的变换
     * @throws IllegalArgumentException 新支点或计算结果不是有限值
     */
    fun withPivotKeepingPosition(newPivot: Vec3): EntityTransform {
        val delta = newPivot.subtract(pivot)
        val scaled = delta.multiply(scale)
        val rotated = quaternion().transform(Vector3d(scaled.x, scaled.y, scaled.z))
        // 保持 T(offset + compensation + pivot) * R * S * T(-pivot) 不变。
        return copy(pivot = newPivot, pivotCompensation = pivotCompensation
            .add(rotated.x, rotated.y, rotated.z).subtract(delta))
    }

    /**
     * 序列化为独立标签；不包含任何实体或世界引用。
     * 示例：`tag.put("Transform", transform.toNbt())`。
     * @return 调用方可修改的标签
     */
    fun toNbt(): CompoundTag = CompoundTag().apply {
        for ((name, value) in listOf("Rotation" to rotation, "Offset" to offset, "Scale" to scale,
            "Pivot" to pivot, "PivotCompensation" to pivotCompensation)) {
            putDouble("${name}0", value.x)
            putDouble("${name}1", value.y)
            putDouble("${name}2", value.z)
        }
    }

    companion object {
        /**
         * 读取变换，缺失字段采用单位变换；不接受非有限值或非正缩放。
         * 示例：`EntityTransform.fromNbt(tag.getCompound("Transform"))`。
         * @param tag 不会被修改的变换标签
         * @return 经校验的变换
         * @throws IllegalArgumentException 变换无效
         */
        fun fromNbt(tag: CompoundTag): EntityTransform {
            fun read(name: String, default: Double = 0.0): Vec3 {
                val values = (0..2).map { if (tag.contains("$name$it")) tag.getDouble("$name$it") else default }
                return Vec3(values[0], values[1], values[2])
            }
            return EntityTransform(read("Rotation"), read("Offset"), read("Scale", 1.0),
                read("Pivot"), read("PivotCompensation"))
        }
    }
}
