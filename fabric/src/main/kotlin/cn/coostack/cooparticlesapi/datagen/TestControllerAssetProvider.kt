package cn.coostack.cooparticlesapi.datagen

import cn.coostack.cooparticlesapi.CooParticlesConstants
import com.google.gson.JsonObject
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.resources.Identifier
import java.util.concurrent.CompletableFuture

/** 生成测试方块、绑定器及其方块状态资源。 */
class TestControllerAssetProvider(
    output: FabricPackOutput
) : DataProvider {
    private val blockStatePath = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates")
    private val blockModelPath = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/block")
    private val itemModelPath = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/item")
    /** 26.3 物品资产定义独立于烘焙模型，不能只生成旧 models/item 文件。 */
    private val itemAssetPath = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "items")

    override fun run(cachedOutput: CachedOutput): CompletableFuture<*> {
        val id = Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_controller")
        val binderId = Identifier.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_block_binder")
        return CompletableFuture.allOf(
            DataProvider.saveStable(cachedOutput, blockStateJson(), blockStatePath.json(id)),
            DataProvider.saveStable(cachedOutput, testControllerBlockModelJson(), blockModelPath.json(id)),
            DataProvider.saveStable(cachedOutput, testControllerItemModelJson(), itemModelPath.json(id)),
            DataProvider.saveStable(cachedOutput, testBlockBinderItemModelJson(), itemModelPath.json(binderId)),
            DataProvider.saveStable(cachedOutput, itemAssetJson(id), itemAssetPath.json(id)),
            DataProvider.saveStable(cachedOutput, itemAssetJson(binderId), itemAssetPath.json(binderId))
        )
    }

    override fun getName(): String {
        return "CooParticlesAPI/Test Controller Assets"
    }

    private fun blockStateJson(): JsonObject {
        val hiddenFalse = JsonObject().apply { addProperty("model", "cooparticlesapi:block/test_controller") }
        val hiddenTrue = JsonObject().apply { addProperty("model", "cooparticlesapi:block/test_controller") }
        val variants = JsonObject().apply {
            add("hidden=false", hiddenFalse)
            add("hidden=true", hiddenTrue)
        }
        return JsonObject().apply { add("variants", variants) }
    }

    private fun testControllerBlockModelJson(): JsonObject {
        val textures = JsonObject().apply {
            addProperty("all", "minecraft:block/command_block_side")
        }
        return JsonObject().apply {
            addProperty("parent", "minecraft:block/cube_all")
            add("textures", textures)
        }
    }

    private fun testControllerItemModelJson(): JsonObject {
        return JsonObject().apply {
            addProperty("parent", "cooparticlesapi:block/test_controller")
        }
    }

    private fun testBlockBinderItemModelJson(): JsonObject {
        val textures = JsonObject().apply {
            addProperty("layer0", "minecraft:item/ender_eye")
        }
        return JsonObject().apply {
            addProperty("parent", "minecraft:item/generated")
            add("textures", textures)
        }
    }

    /** 将客户端物品资产连接到本 provider 生成的独立烘焙模型。 */
    private fun itemAssetJson(id: Identifier): JsonObject {
        val model = JsonObject().apply {
            addProperty("type", "minecraft:model")
            addProperty("model", "${id.namespace}:item/${id.path}")
        }
        return JsonObject().apply { add("model", model) }
    }
}
