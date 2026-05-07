package cn.coostack.cooparticlesapi.renderer.utils

import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModel
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelPipe
import cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelVertex
import net.minecraft.resources.ResourceLocation
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f
import java.io.InputStream

/**
 * OBJ 模型读取工具。
 *
 * 基本用法：
 * 1. 把 `.obj` 文件放到 `assets/<modid>/<path>` 下，例如
 *    `assets/cooparticlesapi/models/obj/my_model.obj`
 * 2. 用 [load] 读取资源，或直接用 [parse] 读取字符串/输入流
 * 3. 用 [buildModel] 直接得到可渲染的 [RenderEntityModel]
 *
 * 支持的 OBJ 语句：
 * - `v` 顶点坐标
 * - `vt` 纹理坐标
 * - `vn` 法线
 * - `f` 面
 *
 * `f` 会自动扇形三角化，所以三角面、四边面和多边形都会被拆成三角形批次。
 * 这里不解析 `.mtl` 材质文件，材质、颜色、shader 等由 [RenderEntityModelPipe] 和渲染器负责。
 */
object ObjModelLoader {
    /**
     * 从资源路径读取 OBJ 模型。
     *
     * @param id 资源位置，路径会自动映射到 `assets/<namespace>/<path>`
     * @param color 默认颜色，OBJ 顶点没有携带颜色时使用
     * @param flipV 是否翻转 V 轴纹理坐标
     * @param classLoader 用于读取资源的 classloader，测试时可以自定义
     */
    @JvmStatic
    fun load(
        id: ResourceLocation,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false,
        classLoader: ClassLoader = ObjModelLoader::class.java.classLoader
    ): RenderVertexBuilder {
        return load(id.namespace, id.path, color, flipV, classLoader)
    }

    /**
     * 从指定命名空间和路径读取 OBJ 模型。
     *
     * @param namespace 资源命名空间，例如 `cooparticlesapi`
     * @param path 资源路径，例如 `models/obj/my_model.obj`
     */
    @JvmStatic
    fun load(
        namespace: String,
        path: String,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false,
        classLoader: ClassLoader = ObjModelLoader::class.java.classLoader
    ): RenderVertexBuilder {
        val resourcePath = "assets/$namespace/$path"
        val stream = classLoader.getResourceAsStream(resourcePath)
            ?: error("OBJ resource not found: $namespace:$path ($resourcePath)")
        return parse(stream, color, flipV)
    }

    /**
     * 直接从字符串解析 OBJ 内容。
     */
    @JvmStatic
    fun parse(
        source: String,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false
    ): RenderVertexBuilder {
        return parseLines(source.lineSequence().toList(), color, flipV)
    }

    /**
     * 直接从输入流解析 OBJ 内容。
     */
    @JvmStatic
    fun parse(
        input: InputStream,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false
    ): RenderVertexBuilder {
        return input.bufferedReader(Charsets.UTF_8).use { reader ->
            parseLines(reader.readLines(), color, flipV)
        }
    }

    /**
     * 逐行解析 OBJ 内容，返回可继续追加到模型构建器中的顶点构建器。
     */
    @JvmStatic
    fun parseLines(
        lines: Iterable<String>,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false
    ): RenderVertexBuilder {
        val state = ObjParseState(defaultColor = Vector4f(color), flipV = flipV)
        lines.forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) {
                return@forEachIndexed
            }
            val parts = line.split(WHITESPACE)
            when (parts.first()) {
                "v" -> state.readPosition(parts, lineNumber)
                "vt" -> state.readUv(parts, lineNumber)
                "vn" -> state.readNormal(parts, lineNumber)
                "f" -> state.readFace(parts.drop(1), lineNumber)
                "o", "g", "s", "usemtl", "mtllib" -> Unit
                else -> Unit
            }
        }
        return state.builder
    }

    /**
     * 读取 OBJ 并直接追加到现有的 [RenderEntityModelBuilder]。
     */
    @JvmStatic
    fun addTo(
        id: ResourceLocation,
        model: cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false,
        classLoader: ClassLoader = ObjModelLoader::class.java.classLoader
    ): RenderVertexBuilder {
        return load(id, color, flipV, classLoader).addTo(model, pipe)
    }

    /**
     * 读取 OBJ 并直接追加到现有的 [RenderEntityModelBuilder]。
     */
    @JvmStatic
    fun addTo(
        namespace: String,
        path: String,
        model: cn.coostack.cooparticlesapi.renderer.model.RenderEntityModelBuilder,
        pipe: RenderEntityModelPipe,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false,
        classLoader: ClassLoader = ObjModelLoader::class.java.classLoader
    ): RenderVertexBuilder {
        return load(namespace, path, color, flipV, classLoader).addTo(model, pipe)
    }

    /**
     * 读取 OBJ 并直接构建出完整的 [RenderEntityModel]。
     */
    @JvmStatic
    fun buildModel(
        id: ResourceLocation,
        pipe: RenderEntityModelPipe,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false,
        classLoader: ClassLoader = ObjModelLoader::class.java.classLoader
    ): RenderEntityModel {
        return load(id, color, flipV, classLoader).buildModel(pipe)
    }

    /**
     * 读取 OBJ 并直接构建出完整的 [RenderEntityModel]。
     */
    @JvmStatic
    fun buildModel(
        namespace: String,
        path: String,
        pipe: RenderEntityModelPipe,
        color: Vector4f = Vector4f(1f, 1f, 1f, 1f),
        flipV: Boolean = false,
        classLoader: ClassLoader = ObjModelLoader::class.java.classLoader
    ): RenderEntityModel {
        return load(namespace, path, color, flipV, classLoader).buildModel(pipe)
    }

    private data class ObjPosition(
        val position: Vector3f,
        val color: Vector4f?
    )

    private data class ObjFaceIndex(
        val positionIndex: Int,
        val uvIndex: Int?,
        val normalIndex: Int?
    )

    private class ObjParseState(
        private val defaultColor: Vector4f,
        private val flipV: Boolean
    ) {
        val builder = RenderVertexBuilder()
        private val positions = ArrayList<ObjPosition>()
        private val uvs = ArrayList<Vector2f>()
        private val normals = ArrayList<Vector3f>()

        fun readPosition(parts: List<String>, lineNumber: Int) {
            // v x y z [r g b [a]]
            requireLine(parts.size >= 4, lineNumber, "v requires x y z")
            val position = Vector3f(
                parts[1].float(lineNumber, "x"),
                parts[2].float(lineNumber, "y"),
                parts[3].float(lineNumber, "z")
            )
            val vertexColor = if (parts.size >= 7) {
                Vector4f(
                    parts[4].float(lineNumber, "red"),
                    parts[5].float(lineNumber, "green"),
                    parts[6].float(lineNumber, "blue"),
                    if (parts.size >= 8) parts[7].float(lineNumber, "alpha") else defaultColor.w
                )
            } else {
                null
            }
            positions += ObjPosition(position, vertexColor)
        }

        fun readUv(parts: List<String>, lineNumber: Int) {
            // vt u [v]
            requireLine(parts.size >= 2, lineNumber, "vt requires u")
            val u = parts[1].float(lineNumber, "u")
            val rawV = parts.getOrNull(2)?.float(lineNumber, "v") ?: 0f
            val v = if (flipV) 1f - rawV else rawV
            uvs += Vector2f(u, v)
        }

        fun readNormal(parts: List<String>, lineNumber: Int) {
            // vn x y z
            requireLine(parts.size >= 4, lineNumber, "vn requires x y z")
            normals += safeNormal(
                Vector3f(
                    parts[1].float(lineNumber, "normal x"),
                    parts[2].float(lineNumber, "normal y"),
                    parts[3].float(lineNumber, "normal z")
                )
            )
        }

        fun readFace(faceParts: List<String>, lineNumber: Int) {
            // f v1/vt1/vn1 v2/vt2/vn2 ...
            requireLine(faceParts.size >= 3, lineNumber, "f requires at least 3 vertices")
            val face = faceParts.map { parseFaceIndex(it, lineNumber) }
            // 多边形使用扇形拆分成三角面
            for (index in 1 until face.lastIndex) {
                addTriangle(face[0], face[index], face[index + 1], lineNumber)
            }
        }

        private fun addTriangle(
            first: ObjFaceIndex,
            second: ObjFaceIndex,
            third: ObjFaceIndex,
            lineNumber: Int
        ) {
            val faceNormal = computeFaceNormal(first, second, third, lineNumber)
            builder.addTriangle(
                vertex(first, faceNormal, lineNumber),
                vertex(second, faceNormal, lineNumber),
                vertex(third, faceNormal, lineNumber)
            )
        }

        private fun vertex(index: ObjFaceIndex, faceNormal: Vector3f, lineNumber: Int): RenderEntityModelVertex {
            val source = position(index.positionIndex, lineNumber)
            val uv = index.uvIndex?.let { uvs.getOrNull(it) }
                ?: Vector2f(0f, 0f)
            val normal = index.normalIndex?.let { normals.getOrNull(it) }
                ?: faceNormal
            return RenderEntityModelVertex(
                position = Vector3f(source.position),
                color = Vector4f(source.color ?: defaultColor),
                uv = Vector2f(uv),
                normal = Vector3f(normal)
            )
        }

        private fun computeFaceNormal(
            first: ObjFaceIndex,
            second: ObjFaceIndex,
            third: ObjFaceIndex,
            lineNumber: Int
        ): Vector3f {
            val a = position(first.positionIndex, lineNumber).position
            val b = position(second.positionIndex, lineNumber).position
            val c = position(third.positionIndex, lineNumber).position
            val edgeA = Vector3f(b).sub(a)
            val edgeB = Vector3f(c).sub(a)
            return safeNormal(edgeA.cross(edgeB))
        }

        private fun position(index: Int, lineNumber: Int): ObjPosition {
            return positions.getOrNull(index)
                ?: lineError(lineNumber, "position index ${index + 1} is out of bounds")
        }

        private fun parseFaceIndex(token: String, lineNumber: Int): ObjFaceIndex {
            val parts = token.split('/')
            requireLine(parts.size in 1..3, lineNumber, "invalid face vertex '$token'")
            requireLine(parts[0].isNotEmpty(), lineNumber, "face vertex '$token' is missing position index")
            return ObjFaceIndex(
                positionIndex = resolveIndex(parts[0], positions.size, lineNumber, "position"),
                uvIndex = parts.getOrNull(1)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { resolveIndex(it, uvs.size, lineNumber, "uv") },
                normalIndex = parts.getOrNull(2)
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { resolveIndex(it, normals.size, lineNumber, "normal") }
            )
        }
    }

    private fun resolveIndex(token: String, size: Int, lineNumber: Int, label: String): Int {
        val value = token.toIntOrNull()
            ?: lineError(lineNumber, "invalid $label index '$token'")
        requireLine(value != 0, lineNumber, "$label index cannot be 0")
        val resolved = if (value > 0) value - 1 else size + value
        requireLine(resolved in 0 until size, lineNumber, "$label index $value is out of bounds")
        return resolved
    }

    private fun String.float(lineNumber: Int, label: String): Float {
        return toFloatOrNull() ?: lineError(lineNumber, "invalid $label value '$this'")
    }

    private fun safeNormal(value: Vector3f): Vector3f {
        return if (value.lengthSquared() > 1.0E-12f) {
            value.normalize()
        } else {
            Vector3f(0f, 1f, 0f)
        }
    }

    private fun requireLine(condition: Boolean, lineNumber: Int, message: String) {
        if (!condition) {
            lineError(lineNumber, message)
        }
    }

    private fun lineError(lineNumber: Int, message: String): Nothing {
        error("Invalid OBJ at line $lineNumber: $message")
    }

    private val WHITESPACE = Regex("\\s+")
}
