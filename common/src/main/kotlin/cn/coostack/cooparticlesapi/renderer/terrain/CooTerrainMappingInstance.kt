package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.resources.ResourceLocation

/** 已创建的 mapping 实例不可变描述；区域和参数由客户端在渲染阶段直接求值。 */
data class CooTerrainMappingInstance(
    val instanceId: ResourceLocation,
    val mappingId: ResourceLocation,
    val dimension: ResourceLocation,
    val region: CooTerrainMappingRegion,
    val uniforms: Map<String, CooUniformValue>,
    val priority: Int,
    val composition: CooTerrainEffectComposition,
    val startedAt: Long,
    val expiresAt: Long?,
    val sequence: Long,
    val revision: Long,
    val pausedAt: Long? = null
) {
    /** 便于调用方声明实例所属的逻辑维度类型。 */
    fun dimensionKey(): ResourceLocation = dimension

    /** 判断实例是否处于暂停状态。 */
    fun isPaused(): Boolean = pausedAt != null
}

/** 创建实例时覆盖模板默认值的配置块。 */
class CooTerrainMappingInstanceBuilder internal constructor(
    private val defaults: CooTerrainMappingDefaults
) {
    private var uniformsValue = defaults.uniforms.toMap()
    private var priorityValue = defaults.priority
    private var compositionValue = defaults.composition
    private var durationValue = defaults.durationTicks

    /** 完整替换实例 uniforms。 */
    fun uniforms(values: Map<String, CooUniformValue>) = apply {
        require(values.keys.all(String::isNotBlank)) { "Terrain mapping uniform name must not be blank" }
        uniformsValue = values.toMap()
    }

    /** 设置实例排序优先级。 */
    fun priority(value: Int) = apply { priorityValue = value }

    /** 设置实例合成方式。 */
    fun composition(value: CooTerrainEffectComposition) = apply { compositionValue = value }

    /** 设置实例持续时间；空值表示持久。 */
    fun duration(ticks: Long?) = apply {
        require(ticks == null || ticks > 0L) { "Terrain mapping duration must be greater than zero" }
        durationValue = ticks
    }

    internal fun build(): CooTerrainMappingInstanceOptions = CooTerrainMappingInstanceOptions(
        uniformsValue.toMap(),
        priorityValue,
        compositionValue,
        durationValue
    )
}

internal data class CooTerrainMappingInstanceOptions(
    val uniforms: Map<String, CooUniformValue>,
    val priority: Int,
    val composition: CooTerrainEffectComposition,
    val durationTicks: Long?
)
