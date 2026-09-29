package cn.coostack.cooparticlesapi.entities.structure

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import cn.coostack.cooparticlesapi.entities.collision.EntityTransform
import org.joml.Quaterniond
import org.joml.Vector3d
import kotlin.math.PI

/**
 * 模型的可编辑属性；旋转单位为度，偏移单位为格，缩放为正倍率。
 * @property health 最大生命值；应用编辑时同时恢复到此数值
 * @property rotation 三个局部轴的旋转角，按 X、Y、Z 顺序组合
 * @property offset 渲染与碰撞共同使用的偏移
 * @property scale 三轴独立缩放
 * @property deathAnimation 是否播放死亡侧翻与红色覆盖
 * @property pivot 结构局部坐标中的变换中心，单位为格；与实体身体定位点独立
 * @property pivotCompensation 更换中心时保持模型原位的累计位移补偿，不占用手动偏移范围
 * @property pickable 是否允许潜行右键收起，不要求编辑器，旧存档默认关闭
 * @property itemName 收起后的物品名称；可拾取时必填，最多 64 字符，不接受控制字符
 * @property edible 收起后的物品是否可以食用，默认关闭；具体效果交给完成事件
 * @property placeable 收起后的物品是否允许潜行右键方块重新放回实体，默认关闭
 * @property itemProperties 事件标识、绑定组件及食物属性
 */
data class StructureModelSettings(
    val health: Float = 20F,
    val rotation: List<Double> = listOf(0.0, 0.0, 0.0),
    val offset: List<Double> = listOf(0.0, 0.0, 0.0),
    val scale: List<Double> = listOf(1.0, 1.0, 1.0),
    val deathAnimation: Boolean = true,
    val pivot: List<Double> = listOf(0.0, 0.0, 0.0),
    val pivotCompensation: List<Double> = listOf(0.0, 0.0, 0.0),
    val pickable: Boolean = false,
    val itemName: String = "",
    val edible: Boolean = false,
    val placeable: Boolean = false,
    val itemProperties: ModelItemProperties = ModelItemProperties()
) {
    /** 兼容碰撞 API 的构造入口，编辑器仍使用三个分量列表。 */
    constructor(transform: EntityTransform, health: Float = 20F, deathAnimation: Boolean = true) : this(
        health, listOf(transform.rotation.x, transform.rotation.y, transform.rotation.z),
        listOf(transform.offset.x, transform.offset.y, transform.offset.z),
        listOf(transform.scale.x, transform.scale.y, transform.scale.z), deathAnimation,
        listOf(transform.pivot.x, transform.pivot.y, transform.pivot.z),
        listOf(transform.pivotCompensation.x, transform.pivotCompensation.y, transform.pivotCompensation.z)
    )

    /** 碰撞和表面采样复用通用变换，避免再维护一套旋转几何实现。 */
    val transform: EntityTransform get() = EntityTransform(
        Vec3(rotation[0], rotation[1], rotation[2]), Vec3(offset[0], offset[1], offset[2]),
        Vec3(scale[0], scale[1], scale[2]), Vec3(pivot[0], pivot[1], pivot[2]),
        Vec3(pivotCompensation[0], pivotCompensation[1], pivotCompensation[2]))

    /** 中心相对于放置锚点的最终位置，渲染、碰撞和死亡动画使用同一结果。 */
    val pivotPosition: Vec3
        get() = Vec3(
            offset[0] + pivotCompensation[0] + pivot[0],
            offset[1] + pivotCompensation[1] + pivot[1],
            offset[2] + pivotCompensation[2] + pivot[2]
        )

    /** 检查网络输入和存档数据；无穷值及零缩放不得进入碰撞计算。 */
    fun valid(): Boolean = health.isFinite() && health in 0.1F..1024F &&
        rotation.size == 3 && rotation.all { it.isFinite() && it in -360.0..360.0 } &&
        offset.size == 3 && offset.all { it.isFinite() && it in -16.0..16.0 } &&
        scale.size == 3 && scale.all { it.isFinite() && it in 0.05..16.0 } &&
        pivot.size == 3 && pivot.all { it.isFinite() && it in -48.0..48.0 } &&
        pivotCompensation.size == 3 && pivotCompensation.all { it.isFinite() } &&
        pivotPosition.let { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() } &&
        itemName.length <= 64 && itemName.none { it.isISOControl() || it == '\u00a7' } &&
        (!pickable || itemName.isNotBlank()) && itemProperties.valid()

    /**
     * 更换局部变换中心并保持当前模型姿态；后续旋转、缩放和倒地才围绕新中心进行。
     * 示例：`settings.withPivotKeepingPosition(listOf(4.0, 0.0, 4.0))`。
     * @param newPivot 三个有限的局部坐标，范围为 -48 到 48 格
     * @return 携带新中心和位移补偿的独立设置，手动偏移不变
     * @throws IllegalArgumentException 当前设置或新中心无效
     */
    fun withPivotKeepingPosition(newPivot: List<Double>): StructureModelSettings {
        require(valid())
        require(newPivot.size == 3 && newPivot.all { it.isFinite() && it in -48.0..48.0 })
        val delta = newPivot.indices.map { newPivot[it] - pivot[it] }
        val transformed = quaternion().transform(Vector3d(delta[0] * scale[0], delta[1] * scale[1], delta[2] * scale[2]))
        // 对 T(offset + compensation + pivot) * R * S * T(-pivot) 求差，补偿量为 (R*S - I) * delta。
        val correction = listOf(transformed.x, transformed.y, transformed.z)
        return copy(
            pivot = newPivot.toList(),
            pivotCompensation = delta.indices.map { pivotCompensation[it] + correction[it] - delta[it] }
        )
    }

    /** 返回渲染与碰撞共用的旋转，调用方可独占修改返回值。 */
    fun quaternion(): Quaterniond = Quaterniond().rotationXYZ(
        rotation[0] * PI / 180.0, rotation[1] * PI / 180.0, rotation[2] * PI / 180.0
    )

    /** 返回独立的设置标签，不包含结构快照和物品。 */
    fun toNbt(): CompoundTag = CompoundTag().apply {
        putFloat("Health", health)
        putBoolean("DeathAnimation", deathAnimation)
        putBoolean("Pickable", pickable)
        putString("ItemName", itemName)
        putBoolean("Edible", edible)
        putBoolean("Placeable", placeable)
        put("ItemProperties", itemProperties.toNbt())
        for (axis in 0..2) {
            putDouble("Rotation$axis", rotation[axis])
            putDouble("Offset$axis", offset[axis])
            putDouble("Scale$axis", scale[axis])
            putDouble("Pivot$axis", pivot[axis])
            putDouble("PivotCompensation$axis", pivotCompensation[axis])
        }
    }

    companion object {
        /**
         * 有结构尺寸时读取设置；旧数据未记录中心才采用默认支点，并补偿已有旋转和缩放。
         * 示例：`fromNbt(tag, snapshot.defaultPivot)`。
         * @param tag 保存或同步的设置标签，不会被修改
         * @param defaultPivot 结构底部水平中心的局部坐标
         * @return 保留显式中心的设置，或保持原姿态并迁移到默认中心的设置
         * @throws IllegalArgumentException 需要迁移时原设置或默认中心无效
         */
        fun fromNbt(tag: CompoundTag, defaultPivot: Vec3): StructureModelSettings {
            val settings = fromNbt(tag)
            val transform = if (tag.contains("Transform")) tag.getCompound("Transform") else tag
            if ((0..2).any { transform.contains("Pivot$it") }) return settings
            return settings.withPivotKeepingPosition(listOf(defaultPivot.x, defaultPivot.y, defaultPivot.z))
        }

        /** 从完整设置标签读取；返回值仍须通过 valid 校验后才能应用。 */
        fun fromNbt(tag: CompoundTag): StructureModelSettings {
            if (tag.contains("Transform")) return StructureModelSettings(
                EntityTransform.fromNbt(tag.getCompound("Transform")),
                if (tag.contains("Health")) tag.getFloat("Health") else 20F,
                !tag.contains("DeathAnimation") || tag.getBoolean("DeathAnimation"))
            return StructureModelSettings(
            tag.getFloat("Health"), (0..2).map { tag.getDouble("Rotation$it") },
            (0..2).map { tag.getDouble("Offset$it") }, (0..2).map { tag.getDouble("Scale$it") },
            tag.getBoolean("DeathAnimation"),
            (0..2).map { tag.getDouble("Pivot$it") },
            (0..2).map { tag.getDouble("PivotCompensation$it") },
            tag.getBoolean("Pickable"), tag.getString("ItemName").trim(),
            tag.getBoolean("Edible"), tag.getBoolean("Placeable"),
            ModelItemProperties.fromNbt(tag.getCompound("ItemProperties"))
            )
        }
    }
}
