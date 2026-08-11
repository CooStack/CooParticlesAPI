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
