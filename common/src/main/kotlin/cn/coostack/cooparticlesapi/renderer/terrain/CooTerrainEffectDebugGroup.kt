package cn.coostack.cooparticlesapi.renderer.terrain

/**
 * 调试渲染读取的客户端效果组状态。
 *
 * [resolved] 为 `false` 表示快照已同步但 Pipeline 还没在客户端注册，因此该组不会改变地形外观，
 * 这是调试线框最需要区分的一种状态。
 *
 * @property snapshot 客户端当前持有的效果组快照
 * @property resolved 快照是否已解析出可渲染 Pipeline
 */
internal data class CooTerrainEffectDebugGroup(
    val snapshot: CooTerrainEffectGroupSnapshot,
    val resolved: Boolean
)
