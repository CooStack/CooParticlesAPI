package cn.coostack.cooparticlesapi.datagen

import cn.coostack.cooparticlesapi.CooParticlesConstants
import com.google.gson.JsonObject
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput
import net.minecraft.data.CachedOutput
import net.minecraft.data.DataProvider
import net.minecraft.data.PackOutput
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.CompletableFuture

class TestControllerAssetProvider(
    private val output: FabricDataOutput
) : DataProvider {
    private val blockStatePath = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "blockstates")
    private val blockModelPath = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/block")
    private val itemModelPath = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "models/item")

    override fun run(cachedOutput: CachedOutput): CompletableFuture<*> {
        val id = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_controller")
        val binderId = ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "test_block_binder")
        return CompletableFuture.allOf(
            DataProvider.saveStable(cachedOutput, blockStateJson(), blockStatePath.json(id)),
            DataProvider.saveStable(cachedOutput, testControllerBlockModelJson(), blockModelPath.json(id)),
            DataProvider.saveStable(cachedOutput, testControllerItemModelJson(), itemModelPath.json(id)),
            DataProvider.saveStable(cachedOutput, testBlockBinderItemModelJson(), itemModelPath.json(binderId))
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
}
