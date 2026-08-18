package cn.coostack.cooparticlesapi.renderer.terrain

import cn.coostack.cooparticlesapi.renderer.pipeline.CooUniformValue

/** 映射模板的默认运行参数；实例创建时复制为不可变快照。 */
data class CooTerrainMappingDefaults(
    val uniforms: Map<String, CooUniformValue> = emptyMap(),
    val priority: Int = 0,
    val composition: CooTerrainEffectComposition = CooTerrainEffectComposition.REPLACE,
    val durationTicks: Long? = null
) {
    init {
        require(uniforms.keys.all(String::isNotBlank)) { "Terrain mapping uniform name must not be blank" }
        require(durationTicks == null || durationTicks > 0L) {
            "Terrain mapping duration must be greater than zero"
        }
    }
}
