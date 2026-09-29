package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.CooParticlesConstants
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceProvider

internal object CooShaderSourceLoader {
    /**
     * 从指定来源读取并解析 `load` 数据；输入必须符合 `CooShaderSourceLoader` 使用的资源或网络格式。
     *
     * 示例：`load(resources = resources, source = source)`。
     *
     * @param resources 要批量处理的元素集合；集合内容会直接影响本次构建、绑定或渲染结果
     *
     * @param source 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    fun load(resources: ResourceProvider, source: ResourceLocation): String {
        return preprocess(source) { location ->
            resources.getResource(location)
                .orElseThrow { IllegalArgumentException("Coo shader source does not exist: $location") }
                .open()
                .use { stream -> stream.readBytes().decodeToString() }
        }
    }

    /**
     * 直接从 classpath 读取并展开 `#coo_import`，不依赖资源管理器。
     *
     * GPU 程序编译路径（`IdentifierShader`）在资源管理器尚未就绪时也会被调用，例如单元测试或
     * 独立工具环境。此时仍必须展开 include，否则驱动会收到未知的 `#coo_import` 指令并直接编译失败。
     *
     * @param source 程序自身的资源位置；path 不含 `shaders/` 前缀
     * @return 展开后的源码
     * @throws IllegalArgumentException 程序自身或任一 include 在 classpath 上不存在时抛出
     */
    internal fun loadFromClasspath(source: ResourceLocation): String =
        preprocess(source) { location -> readClasspathSource(location) }

    /**
     * include 目录前缀（相对 `assets/<namespace>/`）。
     *
     * 注意是单数 `shader/`：它与程序源码目录 `shaders/` 不同，`includeLocation` 生成的位置也以此开头。
     */
    private const val INCLUDE_DIRECTORY_PREFIX = "shader/"

    private fun readClasspathSource(location: ResourceLocation): String {
        // include 目录在仓库里是**单数** `assets/<namespace>/shader/include/...`，
        // 与程序源码的 `assets/<namespace>/shaders/...` 是两个不同的目录；
        // classpath 读取必须复现这个差异，否则 include 永远找不到。
        val classpath = if (location.path.startsWith(INCLUDE_DIRECTORY_PREFIX)) {
            "assets/${location.namespace}/${location.path}"
        } else {
            "assets/${location.namespace}/shaders/${location.path}"
        }
        val stream = CooShaderSourceLoader::class.java.classLoader.getResourceAsStream(classpath)
            ?: throw IllegalArgumentException("Coo shader source does not exist on classpath: $classpath")
        return stream.use { it.readAllBytes().decodeToString() }
    }

    /**
     * 执行 `CooShaderSourceLoader` 定义的 `preprocess` 操作；输入和返回值用于该组件当前的渲染职责。
     *
     * 示例：`preprocess(source = source, read = read)`。
     *
     * @param source 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @param read 当前操作需要的输入值；其语义由方法名和所属组件共同限定
     *
     * @return 当前操作计算、更新或查询得到的结果
     */
    internal fun preprocess(
        source: ResourceLocation,
        read: (ResourceLocation) -> String
    ): String {
        return preprocess(source, linkedSetOf(), read)
    }

    private fun preprocess(
        source: ResourceLocation,
        imports: MutableSet<ResourceLocation>,
        read: (ResourceLocation) -> String
    ): String {
        check(imports.add(source)) { "Coo shader import cycle: $source" }
        return try {
            read(source).lineSequence().joinToString("\n") { line ->
                importPath(line)?.let { path ->
                    preprocess(includeLocation(path), imports, read)
                } ?: line
            }
        } finally {
            imports.remove(source)
        }
    }

    private fun importPath(line: String): String? {
        val import = line.trim().removePrefix("#coo_import").trim().takeIf { line.trim().startsWith("#coo_import") }
            ?: return null
        require(import.startsWith('<') && import.endsWith('>')) {
            "Coo shader import must use <path>: $line"
        }
        return import.substring(1, import.lastIndex).trim().also { path ->
            require(path.isNotEmpty()) { "Coo shader import path must not be empty" }
        }
    }

    private fun includeLocation(path: String): ResourceLocation {
        if (':' in path) {
            return requireNotNull(ResourceLocation.tryParse(path)) {
                "Invalid Coo shader import: $path"
            }.let { include ->
                ResourceLocation.fromNamespaceAndPath(include.namespace, "shader/${include.path}")
            }
        }
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "shader/include/$path")
    }
}
