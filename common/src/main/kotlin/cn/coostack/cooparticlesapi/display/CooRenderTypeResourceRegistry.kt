package cn.coostack.cooparticlesapi.display

import cn.coostack.cooparticlesapi.CooParticlesConstants
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import java.io.InputStream
import java.io.InputStreamReader

object CooRenderTypeResourceRegistry {
    private const val INDEX_RESOURCE = "assets/cooparticlesapi/rendertypes/index.json"

    private val renderTypes = LinkedHashMap<ResourceLocation, CooRenderTypeDescriptor>()
    private val layeredRenderTypes = LinkedHashMap<ResourceLocation, CooLayeredRenderTypeDescriptor>()
    private var loaded = false

    fun ensureLoaded() {
        if (!loaded) {
            reloadFromClasspath()
        }
    }

    fun reload(resourceManager: ResourceManager) {
        renderTypes.clear()
        layeredRenderTypes.clear()
        loaded = true

        val layeredJsons = mutableListOf<JsonObject>()
        resourceManager.listResources("rendertypes") { location ->
            location.namespace == CooParticlesConstants.MOD_ID &&
                location.path.endsWith(".json") &&
                !location.path.endsWith("index.json")
        }
            .toSortedMap(compareBy { it.toString() })
            .values
            .forEach { resource ->
                resource.openAsReader().use { reader ->
                    val json = JsonParser.parseReader(reader).asJsonObject
                    if (json.has("layers")) {
                        layeredJsons += json
                    } else {
                        loadRenderType(json)
                    }
                }
            }
        layeredJsons.forEach(::loadLayeredRenderType)
    }

    fun reloadFromClasspath() {
        renderTypes.clear()
        layeredRenderTypes.clear()
        loaded = true

        val loader = this::class.java.classLoader
        val indexStream = loader.getResourceAsStream(INDEX_RESOURCE) ?: return
        indexStream.use { stream ->
            val root = JsonParser.parseReader(InputStreamReader(stream)).asJsonObject
            root.getAsJsonArray("render_types")?.forEach { element ->
                loader.getResourceAsStream(element.asString)?.use { loadRenderType(it) }
            }
            root.getAsJsonArray("layered_render_types")?.forEach { element ->
                loader.getResourceAsStream(element.asString)?.use { loadLayeredRenderType(it) }
            }
        }
    }

    fun get(id: ResourceLocation): CooRenderTypeDescriptor? {
        ensureLoaded()
        return renderTypes[id]
    }

    fun getLayered(id: ResourceLocation): CooLayeredRenderTypeDescriptor? {
        ensureLoaded()
        return layeredRenderTypes[id]
    }

    fun all(): Collection<CooRenderTypeDescriptor> {
        ensureLoaded()
        return renderTypes.values
    }

    fun allLayered(): Collection<CooLayeredRenderTypeDescriptor> {
        ensureLoaded()
        return layeredRenderTypes.values
    }

    private fun loadRenderType(stream: InputStream) {
        val json = JsonParser.parseReader(InputStreamReader(stream)).asJsonObject
        loadRenderType(json)
    }

    private fun loadRenderType(json: JsonObject) {
        val id = resourceLocation(json.requireString("id"))
        renderTypes[id] = CooRenderTypeDescriptor.builder(
            name = json.requireString("name"),
            vertexFormat = vertexFormat(json.optionalString("vertex_format") ?: "position_color"),
            mode = drawMode(json.optionalString("mode") ?: "quads")
        )
            .bufferSize(json.optionalInt("buffer_size") ?: 256)
            .affectsCrumbling(json.optionalBoolean("affects_crumbling") ?: false)
            .sortOnUpload(json.optionalBoolean("sort_on_upload") ?: false)
            .shaderPreset(shaderPreset(json.optionalString("shader_preset") ?: "position_color"))
            .transparencyMode(transparencyMode(json.optionalString("transparency_mode") ?: "none"))
            .cullMode(cullMode(json.optionalString("cull_mode") ?: "disabled"))
            .lightmapMode(lightmapMode(json.optionalString("lightmap_mode") ?: "disabled"))
            .depthTestMode(depthTestMode(json.optionalString("depth_test_mode") ?: "lequal"))
            .build()
    }

    private fun loadLayeredRenderType(stream: InputStream) {
        val json = JsonParser.parseReader(InputStreamReader(stream)).asJsonObject
        loadLayeredRenderType(json)
    }

    private fun loadLayeredRenderType(json: JsonObject) {
        val id = resourceLocation(json.requireString("id"))
        val layers = json.getAsJsonArray("layers").map { layer ->
            get(resourceLocation(layer.asString))
                ?: error("Unknown render type layer ${layer.asString} referenced by $id")
        }
        layeredRenderTypes[id] = CooLayeredRenderTypeDescriptor(
            name = json.requireString("name"),
            layers = layers
        )
    }

    private fun resourceLocation(text: String): ResourceLocation {
        val split = text.split(':', limit = 2)
        require(split.size == 2) { "Invalid resource location: $text" }
        return ResourceLocation.fromNamespaceAndPath(split[0], split[1])
    }

    private fun vertexFormat(name: String): VertexFormat {
        return when (name.lowercase()) {
            "position_color" -> DefaultVertexFormat.POSITION_COLOR
            "position_tex_color" -> DefaultVertexFormat.POSITION_TEX_COLOR
            else -> error("Unsupported vertex format: $name")
        }
    }

    private fun drawMode(name: String): VertexFormat.Mode {
        return when (name.lowercase()) {
            "quads" -> VertexFormat.Mode.QUADS
            "lines" -> VertexFormat.Mode.LINES
            "line_strip" -> VertexFormat.Mode.LINE_STRIP
            "triangles" -> VertexFormat.Mode.TRIANGLES
            else -> error("Unsupported vertex mode: $name")
        }
    }

    private fun shaderPreset(name: String): CooRenderTypeShaderPreset {
        return when (name.lowercase()) {
            "position_color" -> CooRenderTypeShaderPreset.POSITION_COLOR
            "coo_glow" -> CooRenderTypeShaderPreset.COO_GLOW
            else -> error("Unsupported shader preset: $name")
        }
    }

    private fun transparencyMode(name: String): CooRenderTransparencyMode {
        return when (name.lowercase()) {
            "none" -> CooRenderTransparencyMode.NONE
            "additive" -> CooRenderTransparencyMode.ADDITIVE
            else -> error("Unsupported transparency mode: $name")
        }
    }

    private fun cullMode(name: String): CooRenderCullMode {
        return when (name.lowercase()) {
            "enabled" -> CooRenderCullMode.ENABLED
            "disabled" -> CooRenderCullMode.DISABLED
            else -> error("Unsupported cull mode: $name")
        }
    }

    private fun lightmapMode(name: String): CooRenderLightmapMode {
        return when (name.lowercase()) {
            "enabled" -> CooRenderLightmapMode.ENABLED
            "disabled" -> CooRenderLightmapMode.DISABLED
            else -> error("Unsupported lightmap mode: $name")
        }
    }

    private fun depthTestMode(name: String): CooRenderDepthTestMode {
        return when (name.lowercase()) {
            "lequal" -> CooRenderDepthTestMode.LEQUAL
            else -> error("Unsupported depth test mode: $name")
        }
    }

    private fun JsonObject.requireString(key: String): String {
        return getAsJsonPrimitive(key)?.asString ?: error("Missing required field: $key")
    }

    private fun JsonObject.optionalString(key: String): String? {
        return if (has(key)) getAsJsonPrimitive(key).asString else null
    }

    private fun JsonObject.optionalInt(key: String): Int? {
        return if (has(key)) getAsJsonPrimitive(key).asInt else null
    }

    private fun JsonObject.optionalBoolean(key: String): Boolean? {
        return if (has(key)) getAsJsonPrimitive(key).asBoolean else null
    }
}
