package cn.coostack.cooparticlesapi.renderer.pipeline

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.renderer.backend.RenderBackendCapability
import cn.coostack.cooparticlesapi.renderer.post.CooPostEffects
import cn.coostack.cooparticlesapi.renderer.post.PostEffectInputSource
import cn.coostack.cooparticlesapi.renderer.post.PostEffectOutput
import cn.coostack.cooparticlesapi.renderer.post.PostEffectParamValue
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import net.minecraft.resources.ResourceLocation

class CooShaderEffectsTest {
    @AfterTest
    fun clearPlayback() {
        CooPostEffects.client.clear()
    }

    @Test
    fun `simple effect compiles to a screen quad post chain`() {
        val effect = CooShaderEffects.register(id("shader_effect/simple")) {
            fragment(id("post/heat_haze.fsh"))
            inputSceneColor("SceneColor")
            inputSceneDepth("SceneDepth", optional = true)
            outputToScreen()
        }

        val pass = effect.postType.chain.passes.single()
        assertEquals("main", pass.name)
        assertEquals(id("post/heat_haze.fsh"), pass.fragment)
        assertEquals(PostEffectOutput.FINAL_SCREEN, pass.output)
        assertEquals(
            listOf(PostEffectInputSource.SCENE_COLOR, PostEffectInputSource.SCENE_DEPTH),
            pass.inputs.map { it.source }
        )
        assertTrue(RenderBackendCapability.SCENE_COLOR_COPY in effect.postType.requiredCapabilities)
        assertTrue(RenderBackendCapability.SCENE_DEPTH_READ in effect.postType.optionalCapabilities)
    }

    @Test
    fun `play creates an isolated uniform snapshot and returns a stoppable handle`() {
        val effect = CooShaderEffects.register(id("shader_effect/play")) {
            fragment(id("post/play.fsh"))
            inputSceneColor()
            outputToScreen()
        }

        val playback = effect.play {
            duration(30)
            uniform("strength", 0.12F)
            uniform("radius", 0.35F)
        }

        val instance = CooPostEffects.client.activeInstances().single()
        assertEquals(30, instance.lifecycle.durationTicks)
        assertEquals(PostEffectParamValue.FloatValue(0.12F), instance.params["strength"])
        assertEquals(PostEffectParamValue.FloatValue(0.35F), instance.params["radius"])
        assertEquals(setOf("strength", "radius"), instance.type.chain.passes.single().uniforms.map { it.name }.toSet())
        assertNotSame(effect.postType, instance.type)
        assertTrue(playback.isPlaying())

        playback.stop()
        assertFalse(playback.isPlaying())
    }

    @Test
    fun `advanced passes keep explicit dependency links`() {
        val effect = CooShaderEffects.register(id("shader_effect/multi_pass")) {
            val extract = pass("extract") {
                fragment(id("post/extract.fsh"))
                inputSceneColor("SceneColor")
                outputToTemporary()
            }
            val composite = pass("composite") {
                fragment(id("post/composite.fsh"))
                inputSceneDepth("SceneDepth", optional = true)
                input("Extracted", textureSlot = 1)
                outputToScreen()
            }
            line(extract.color(), composite.input("Extracted"))
        }

        val composite = effect.postType.chain.passes.single { it.name == "composite" }
        val passInput = composite.inputs.single { it.source == PostEffectInputSource.PASS_OUTPUT }
        assertEquals("extract", passInput.sourcePassName)
        assertEquals("Extracted", passInput.samplerName)
        assertEquals(1, passInput.textureSlot)
    }

    @Test
    fun `registered shader effect can be restored from the internal sync registry`() {
        val effect = CooShaderEffects.register(id("shader_effect/sync_registry")) {
            fragment(id("post/sync_registry.fsh"))
            inputSceneColor()
            outputToScreen()
        }

        val restored = effect.postType.create().toNetworkState().instantiate()

        assertEquals(effect.postType.id, restored?.type?.id)
        assertTrue(restored?.serverSynced == true)
    }

    @Test
    fun `network restore keeps play-time uniform providers`() {
        val effect = CooShaderEffects.register(id("shader_effect/sync_uniforms")) {
            fragment(id("post/sync_uniforms.fsh"))
            inputSceneColor()
            outputToScreen()
        }

        effect.play {
            uniform("strength", 0.42F)
        }
        val original = CooPostEffects.client.activeInstances().single()
        val restored = requireNotNull(original.toNetworkState().instantiate())
        val uniform = restored.type.chain.passes.single().uniforms.single { it.name == "strength" }

        assertEquals(PostEffectParamValue.FloatValue(0.42F), uniform.provider(restored))
    }

    @Test
    fun `network restore keeps matrix array uniform providers`() {
        val effect = CooShaderEffects.register(id("shader_effect/sync_matrix_array")) {
            fragment(id("post/sync_matrix_array.fsh"))
            inputSceneColor()
            outputToScreen()
        }
        val matrix = CooUniformValue.MatValue(2, 3, 1F, 2F, 3F, 4F, 5F, 6F)
        val value = CooUniformValue.ArrayValue(
            matrix,
            matrix.copy(components = matrix.components.reversed())
        )

        effect.play {
            uniform("Transforms", value)
        }
        val original = CooPostEffects.client.activeInstances().single()
        val restored = requireNotNull(original.toNetworkState().instantiate())
        val uniform = restored.type.chain.passes.single().uniforms.single { it.name == "Transforms" }

        assertEquals(PostEffectParamValue.UniformValue(value), uniform.provider(restored))
    }

    @Test
    fun `network restore does not turn texture parameters into uniforms`() {
        val effect = CooShaderEffects.register(id("shader_effect/sync_texture")) {
            fragment(id("post/sync_texture.fsh"))
            inputSceneColor()
            inputTexture("Noise")
            outputToScreen()
        }

        effect.play {
            uniform("mode", 7)
            texture("Noise", id("textures/effect/noise.png"))
        }
        val original = CooPostEffects.client.activeInstances().single()
        val restored = requireNotNull(original.toNetworkState().instantiate())
        val uniformNames = restored.type.chain.passes.single().uniforms.map { it.name }.toSet()

        assertTrue("mode" in uniformNames)
        assertFalse("Noise" in uniformNames)
    }

    private fun id(path: String): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, path)
    }
}
