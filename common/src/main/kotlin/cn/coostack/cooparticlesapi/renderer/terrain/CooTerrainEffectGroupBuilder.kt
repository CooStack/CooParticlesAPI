package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue
import net.minecraft.core.BlockPos

/**
 * 配置批量方块效果组的位置、生效时间和共享 shader 参数。
 *
 * 该构建器由 [CooTerrainEffectGroup] 创建。位置延迟和持续时间都使用 Minecraft tick，
 * 同一个位置或 uniform 名称配置多次时以最后一次为准。
 *
 * 示例：
 * ```kotlin
 * CooTerrainEffectGroup(effectId, pipeline) {
 *     position(origin)
 *     positions(nextWave, delayTicks = 5L)
 *     uniform("Strength", 0.6F)
 *     duration(60L)
 * }
 * ```
 */
class CooTerrainEffectGroupBuilder internal constructor() {
    /** 键为不可变方块坐标，值为相对组开始时间的延迟 tick。 */
    private val activationOffsets = LinkedHashMap<BlockPos, Long>()

    /** 键为 shader uniform 名称，值为整组共享的数据。 */
    private val uniforms = LinkedHashMap<String, CooUniformValue>()

    /** 组持续 tick 数；`null` 表示不自动到期。 */
    private var durationTicks: Long? = null

    private var priorityValue: Int = 0

    /** 组与此前层的合成方式。 */
    private var compositionValue: CooTerrainEffectComposition = CooTerrainEffectComposition.REPLACE

    /**
     * 加入一个方块位置，并设置它相对组开始时间的生效延迟。
     *
     * 示例：`position(target, delayTicks = 10L)` 会让该位置在组开始 10 tick 后生效。
     *
     * @param position 要应用效果的方块坐标；构建器会保存其不可变副本
     * @param delayTicks 相对组开始时间的延迟 tick，必须大于或等于 `0`
     * @return 当前构建器，便于继续链式配置
     * @throws IllegalArgumentException 当 [delayTicks] 为负数时抛出
     */
    @JvmOverloads
    fun position(position: BlockPos, delayTicks: Long = 0L) = apply {
        require(delayTicks >= 0L) { "Terrain effect position delay must not be negative" }
        activationOffsets[position.immutable()] = delayTicks
    }

    /**
     * 批量加入使用同一生效延迟的方块位置。
     *
     * 示例：`positions(shell, delayTicks = 5L)`。
     *
     * @param positions 要应用效果的方块坐标
     * @param delayTicks 所有位置相对组开始时间的延迟 tick，必须大于或等于 `0`
     * @return 当前构建器，便于继续链式配置
     * @throws IllegalArgumentException 当 [delayTicks] 为负数时抛出
     */
    @JvmOverloads
    fun positions(positions: Iterable<BlockPos>, delayTicks: Long = 0L) = apply {
        positions.forEach { position(it, delayTicks) }
    }

    /**
     * 批量加入各自使用不同生效延迟的方块位置。
     *
     * 示例：`positions(mapOf(origin to 0L, target to 10L))`。
     *
     * @param positions 键为方块坐标，值为该位置相对组开始时间的延迟 tick；延迟不可为负数
     * @return 当前构建器，便于继续链式配置
     * @throws IllegalArgumentException 当任一延迟为负数时抛出
     */
    fun positions(positions: Map<BlockPos, Long>) = apply {
        positions.forEach(::position)
    }

    /**
     * 为整组设置一个单精度浮点 uniform。
     *
     * 示例：`uniform("Strength", 0.75F)`。
     *
     * @param name shader 中声明的 uniform 名称，不可为空白
     * @param value 要发送给 shader 的浮点值
     * @return 当前构建器，便于继续链式配置
     * @throws IllegalArgumentException 当 [name] 为空白时抛出
     */
    fun uniform(name: String, value: Float) = uniform(name, CooUniformValue.FloatValue(value))

    /**
     * 为整组设置一个受 [CooUniformValue] 支持的 uniform。
     *
     * 示例：`uniform("Tint", CooUniformValue.Vec3Value(1F, 0.4F, 0.2F))`。
     *
     * @param name shader 中声明的 uniform 名称，不可为空白
     * @param value 浮点数、整数或向量形式的 uniform 值
     * @return 当前构建器，便于继续链式配置
     * @throws IllegalArgumentException 当 [name] 为空白时抛出
     */
    fun uniform(name: String, value: CooUniformValue) = apply {
        require(name.isNotBlank()) { "Terrain effect uniform name must not be blank" }
        uniforms[name] = value
    }

    /**
     * 一次设置整组共享的多个 uniform。
     *
     * 示例：`uniforms(mapOf("Strength" to CooUniformValue.FloatValue(0.8F)))`。
     *
     * @param values 键为 shader uniform 名称，值为对应的 uniform 数据；名称不可为空白
     * @return 当前构建器，便于继续链式配置
     * @throws IllegalArgumentException 当任一 uniform 名称为空白时抛出
     */
    fun uniforms(values: Map<String, CooUniformValue>) = apply {
        values.forEach(::uniform)
    }

    /**
     * 设置组的绘制优先级，数值越小越早绘制，数值越大越晚绘制。
     *
     * @param value 有符号排序优先级
     * @return 当前构建器，便于继续链式配置
     */
    fun priority(value: Int) = apply {
        priorityValue = value
    }

    /**
     * 设置组与排序中此前结果的合成方式。
     *
     * @param value 当前组使用的合成方式
     * @return 当前构建器，便于继续链式配置
     */
    fun composition(value: CooTerrainEffectComposition) = apply {
        compositionValue = value
    }

    /**
     * 设置整组从开始时间起可持续的 tick 数。
     *
     * 示例：`duration(40L)` 会让效果持续 2 秒左右；未调用时该组不会自动到期。
     *
     * @param ticks 持续时间，必须大于 `0`
     * @return 当前构建器，便于继续链式配置
     * @throws IllegalArgumentException 当 [ticks] 小于或等于 `0` 时抛出
     */
    fun duration(ticks: Long) = apply {
        require(ticks > 0L) { "Terrain effect duration must be greater than zero" }
        durationTicks = ticks
    }

    /**
     * 将当前配置复制为供服务端保存和同步的不可变定义。
     *
     * @return 与当前构建状态对应的效果组定义
     */
    internal fun build(): CooTerrainEffectGroupDefinition {
        return CooTerrainEffectGroupDefinition(
            activationOffsets = activationOffsets.toMap(),
            uniforms = uniforms.toMap(),
            durationTicks = durationTicks,
            priority = priorityValue,
            composition = compositionValue
        )
    }
}
