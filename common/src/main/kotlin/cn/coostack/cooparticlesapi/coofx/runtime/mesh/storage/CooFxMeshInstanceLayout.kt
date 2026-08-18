package cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage

/**
 * runtime 兼容别名。
 *
 * CooFX 网格实例 ABI 的唯一实现位于 render.instance；此别名只保留旧内部包的源码兼容性，
 * 不再声明第二套 attribute、offset 或编码逻辑。
 */
typealias CooFxMeshInstanceLayout = cn.coostack.cooparticlesapi.coofx.render.instance.CooFxMeshInstanceLayout
