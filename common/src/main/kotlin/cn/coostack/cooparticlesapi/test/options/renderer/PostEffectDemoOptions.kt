package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.post.CooPostEffectTypes
import cn.coostack.cooparticlesapi.renderer.post.CooPostEffects
import cn.coostack.cooparticlesapi.renderer.post.PostEffectItemContext
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamValue
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player

object PostEffectDemoOptions {
    private val CUSTOM_CHAIN_ID: ResourceLocation = id("post/demo_custom_chain")

    fun grayscale(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "post/grayscale_screen",
            testingTick = 80,
            instanceFactory = {
                CooPostEffects.builtin.grayscale()
            },
            description = "Full-screen grayscale preset through CooPostEffects.builtin.grayscale()."
        )
    }

    fun serverShockwave(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "post/server_shockwave_world",
            testingTick = 60,
            useTrackingChunkSpawn = true,
            instanceFactory = { viewer ->
                val center = viewer.eyePosition.add(viewer.forward.scale(5.0))
                CooPostEffects.builtin.shockwave()
                    .bindWorld(center.x, center.y, center.z, viewer.level().dimension().location())
                    .params {
                        float("radius", 8.0f)
                        float("feather", 0.22f)
                        float("strength", 0.12f)
                    }
            },
            description = "Server-synchronized world-bound shockwave with radius and feather params."
        )
    }

    fun bloom(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "post/bloom_screen_space",
            testingTick = 100,
            instanceFactory = {
                CooPostEffects.builtin.bloom()
                    .params {
                        float("threshold", 0.75f)
                        float("softKnee", 0.45f)
                        float("intensity", 1.35f)
                        float("blurRadius", 4.0f)
                        int("iterations", 2)
                    }
            },
            description = "Screen-space bloom preset: bright extract, blur horizontal, blur vertical, composite."
        )
    }

    fun screenDistortion(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "post/screen_distortion",
            testingTick = 80,
            instanceFactory = {
                CooPostEffects.builtin.screenDistortion()
                    .params {
                        float("strength", 0.055f)
                    }
            },
            description = "Screen distortion preset that requires scene color and optionally reads depth."
        )
    }

    fun halo(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "post/halo_world_binding",
            testingTick = 100,
            instanceFactory = { viewer ->
                val center = viewer.eyePosition.add(viewer.forward.scale(4.0))
                CooPostEffects.builtin.halo()
                    .bindWorld(center.x, center.y, center.z, viewer.level().dimension().location())
                    .params {
                        color("color", 1.0f, 0.74f, 0.22f, 1.0f)
                        float("intensity", 1.6f)
                        float("radius", 1.8f)
                        float("feather", 0.35f)
                        float("depthFade", 0.8f)
                        bool("throughWalls", false)
                    }
            },
            description = "World-position halo preset, independent from bloom and point lights."
        )
    }

    fun blockBinding(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "post/block_bound_mask_debug",
            testingTick = 80,
            instanceFactory = { viewer ->
                CooPostEffects.builtin.maskDebug()
                    .bindBlock(
                        viewer.blockPosition(),
                        viewer.level().dimension().location(),
                        PostEffectParamValue.Vec3Value(0.5, 0.5, 0.5)
                    )
                    .params {
                        color("color", 0.0f, 1.0f, 0.25f, 0.55f)
                    }
            },
            description = "Block-bound mask debug effect using a block center offset."
        )
    }

    fun itemBinding(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "post/item_bound_gui_context",
            testingTick = 80,
            instanceFactory = { viewer ->
                val itemId = BuiltInRegistries.ITEM.getKey(viewer.mainHandItem.item)
                CooPostEffects.builtin.halo()
                    .bindItem(itemId, PostEffectItemContext.GUI)
                    .params {
                        color("color", 0.35f, 0.78f, 1.0f, 1.0f)
                        float("intensity", 1.2f)
                        float("radius", 1.0f)
                        float("feather", 0.22f)
                    }
            },
            description = "Item-bound halo sample for GUI/inventory context using the main-hand item id."
        )
    }

    fun customChain(player: Player): PostEffectDemoOption {
        return PostEffectDemoOption(
            player = player,
            displayName = "post/custom_chain_type",
            testingTick = 80,
            instanceFactory = {
                customChainType().create()
                    .bindScreen()
                    .params {
                        float("amount", 0.35f)
                    }
            },
            description = "Custom PostEffectType chain declared through the type builder API."
        )
    }

    private fun customChainType() =
        CooPostEffectTypes.get(CUSTOM_CHAIN_ID) ?: CooPostEffectTypes.register(CUSTOM_CHAIN_ID) {
            maskedScreen()
            require(RenderBackendCapability.FINAL_FRAME_POST)
            pass("demo_color_shift", shader("demo_color_shift")) {
                inputSceneColor("scene")
                outputToFinalScreen()
                uniform("amount") { it.params["amount"] ?: PostEffectParamValue.FloatValue(0.25f) }
                uniform("progress") { PostEffectParamValue.FloatValue(it.progress) }
            }
            outputToFinalScreen()
        }

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }

    private fun shader(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, "post/$path.fsh")
    }
}
