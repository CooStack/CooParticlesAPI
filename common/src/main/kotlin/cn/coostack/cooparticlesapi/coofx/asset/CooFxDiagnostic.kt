package cn.coostack.cooparticlesapi.coofx.asset

import net.minecraft.resources.ResourceLocation

/**
 * 描述导入诊断对资产加载结果的影响程度。
 *
 * [INFO] 仅记录兼容性或能力信息，不影响资产可用性。
 * [WARNING] 表示资产仍可导入，但包含当前运行阶段不会执行的可选元数据。
 * [ERROR] 表示输入违反协议或受限 glTF 能力边界，导入结果不得被下游使用。
 */
enum class CooFxDiagnosticSeverity {
    /** 资产仍完全可用的说明信息。 */
    INFO,

    /** 资产可用但需要调用方关注的兼容性信息。 */
    WARNING,

    /** 阻止当前资产进入后续编译阶段的错误。 */
    ERROR,
}

/**
 * 结构化描述 CooFX 或 glTF 导入阶段发现的问题。
 *
 * @property severity 问题严重度。
 * @property code 稳定的机器可读诊断代码。
 * @property assetResource 顶层 CooFX 资源标识。
 * @property pointer 对应输入文档中的 JSON Pointer。
 * @property message 面向开发者的简体中文说明。
 * @property gltfResource 发生 glTF 问题时的模型资源标识。
 * @property objectIndex 发生问题的 glTF 对象索引。
 * @property accessorIndex 发生问题的 accessor 索引。
 */
data class CooFxDiagnostic(
    val severity: CooFxDiagnosticSeverity,
    val code: String,
    val assetResource: ResourceLocation,
    val pointer: String,
    val message: String,
    val gltfResource: ResourceLocation? = null,
    val objectIndex: Int? = null,
    val accessorIndex: Int? = null,
)

/**
 * 保存一次导入的规范化结果和全部诊断。
 *
 * @property asset 无错误时生成的不可变源资产；存在错误时固定为 null。
 * @property diagnostics 按发现顺序保存的结构化诊断。
 */
data class CooFxImportResult(
    val asset: CooFxSourceAsset?,
    val diagnostics: List<CooFxDiagnostic>,
) {
    val isSuccess: Boolean
        get() = asset != null && diagnostics.none { it.severity == CooFxDiagnosticSeverity.ERROR }
}
