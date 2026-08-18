package cn.coostack.cooparticlesapi.coofx

import net.minecraft.resources.ResourceLocation

/**
 * 按 CooFX 资产约定创建入口资源 ID。
 *
 * 例如 `coofxAsset("examplemod", "people")` 会得到
 * `examplemod:coofx/people/people.coofx.json`。`assetID` 必须是单段、
 * 小写的资源文件名，不能包含 `/`、反斜杠、空格或 `..` 路径；导出器和
 * 资源包必须使用同名目录、同名入口文件，Blender 项目名不会自动参与解析。
 */
fun coofxAsset(modid: String, assetID: String): ResourceLocation {
    val assetIdPattern = Regex("[a-z0-9][a-z0-9._-]*")
    require(assetIdPattern.matches(assetID)) {
        "CooFX assetID 必须是小写单段资源名，只能包含 a-z、0-9、点、下划线和短横线"
    }
    require(".." !in assetID) { "CooFX assetID 不能包含 .. 路径片段" }
    return ResourceLocation.fromNamespaceAndPath(modid, "coofx/$assetID/$assetID.coofx.json")
}
