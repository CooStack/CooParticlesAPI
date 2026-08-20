package cn.coostack.cooparticlesapi.renderer.terrain

/**
 * Terrain Mapping 内置 shader 的稳定 ABI 名称。
 *
 * 这些标识由 Pipeline 编译器、运行时 uniform 绑定和内置 shader 共同使用；修改名称会破坏已有
 * Pipeline 资源兼容性，因此调用方必须复用这里的值，不能在 Kotlin 代码中重复字符串。
 */
object CooTerrainMappingShaderAbi {
    const val REGION = "CooMappingRegion"
    const val REGION_SIZE = "CooMappingRegionSize"
    const val REGION_TYPE = "CooMappingRegionType"
    const val PROGRESS = "CooMappingProgress"
    const val CPARTICLE_COVERAGE_MASK = "CParticleCoverageMask"
    const val HAS_CPARTICLE_COVERAGE = "CooHasCParticleCoverage"
    const val BLACKNESS = "Blackness"
    const val FEATHER = "Feather"
}
