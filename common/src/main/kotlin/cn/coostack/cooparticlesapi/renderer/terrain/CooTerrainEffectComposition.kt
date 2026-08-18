package cn.coostack.cooparticlesapi.renderer.terrain

/**
 * 定义多个地形效果组在同一方块位置上的合成方式。
 *
 * `REPLACE` 会丢弃此前累计的层并成为新的基底，是默认值并保持旧行为；
 * `ALPHA_OVER` 按排序顺序在此前结果上叠加透明层；`ADDITIVE` 按排序顺序
 * 追加加色层。该枚举随地形组快照同步，未知网络值必须降级为 `REPLACE`。
 */
enum class CooTerrainEffectComposition {
    /** 重置此前累计结果并绘制当前层。 */
    REPLACE,

    /** 在此前累计结果上进行 alpha-over 合成。 */
    ALPHA_OVER,

    /** 在此前累计结果上进行加色合成。 */
    ADDITIVE;

    companion object {
        /** 将不受信任的网络 ordinal 映射为稳定的合成方式。 */
        fun fromWire(ordinal: Int): CooTerrainEffectComposition {
            return entries.getOrNull(ordinal) ?: REPLACE
        }
    }
}
