package cn.coostack.cooparticlesapi.cparticle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CParticleDynamicContractTest {
    @Test
    fun `sprite sets are uploaded once and selected by the vertex shader`() {
        val sprites = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSprites.kt"
        )
        val resolver = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleTextureResolver.kt"
        )
        val descriptors = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleTextureDescriptors.kt"
        )
        val store = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/storage/CParticleStore.kt"
        )
        val shader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/cparticle.vsh"
        )

        assertTrue("CParticleTextureDescriptors" in sprites)
        assertTrue("MutableSpriteSetAccessor" in resolver)
        assertTrue("if (!hasEffectFrames(typeId)) return missingTexture()" in resolver)
        assertTrue("effectSetCache" in resolver)
        assertTrue("glTexBuffer" in descriptors)
        assertTrue("samplerBuffer uAnimationLookup" in shader)
        assertTrue("texelFetch(uAnimationLookup" in shader)
        assertTrue("resolveUv" !in store)
        assertTrue("CParticleSprites.UvRect" !in store.substringAfter("fun prepareDynamicVisuals("))
    }

    @Test
    fun `dynamic visuals are prepared once per render frame`() {
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystemManager.kt"
        )
        val system = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystem.kt"
        )
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )
        val manualRender = manager.substringAfter("fun renderWorld(").substringBefore("/** 断线")

        assertTrue("renderFrameId++" in manager)
        assertTrue("renderFrameId++" in manualRender)
        assertTrue("lastDynamicPrepareFrame" in system)
        assertTrue("prepareDynamicVisuals(frameId)" in renderer)
        assertTrue(
            renderer.indexOf("prepareDynamicVisuals(frameId)") <
                    renderer.indexOf("CParticleSprites.bindLookup(2)")
        )
    }

    @Test
    fun `scripted age writes do not schedule a full pool convergence upload`() {
        val system = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystem.kt"
        )
        val setAge = system.substringAfter("fun scriptedSetAge(").substringBefore("fun scriptedSetRotation(")
        val visualWrites = system.substringAfter("fun scriptedSetColor(").substringBefore("fun scriptedSetAge(")

        assertTrue("store.setAge(slot, age, lifecycleEpochTick(slot))" in setAge)
        assertTrue("settleTicks" !in setAge)
        assertTrue("store.markDirty(slot)" in visualWrites)
        assertTrue("settleTicks" !in visualWrites)
    }

    @Test
    fun `scripted rotation writes do not schedule position convergence uploads`() {
        val system = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystem.kt"
        )
        val rotationWrites = system.substringAfter("fun scriptedSetRotation(")
            .substringBefore("fun scriptedSetVelocity(")

        assertTrue("store.setBaseRotation" in rotationWrites)
        assertTrue("store.setRotationDirection" in rotationWrites)
        assertTrue("store.setAngularVelocity" in rotationWrites)
        assertTrue("store.addRoll" in rotationWrites)
        assertTrue("settleTicks" !in rotationWrites)
    }

    @Test
    fun `gpu simulation uploads killed flags without stale simulation fields`() {
        val system = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/CParticleSystem.kt"
        )
        val buffer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleGlBuffer.kt"
        )
        val patchFlags = buffer.substringAfter("fun patchFlags(").substringBefore("/** compute 模拟")
        val gpuTick = system.substringAfter("private fun tickSimulated()").substringBefore("private fun tickScripted()")

        assertTrue("patchFlags(store.data, store.killedSlots, store.killedCount)" in system)
        assertTrue("fun patchFlags(data: FloatArray, slots: IntArray, count: Int)" in buffer)
        assertTrue("glBufferSubData" in patchFlags)
        assertTrue("glMapBufferRange" !in patchFlags)
        assertTrue(gpuTick.indexOf("uploadSlots") < gpuTick.indexOf("CParticleGpuSimulator.simulate"))

        val compute = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/compute/cparticle_sim.comp"
        )
        assertTrue("vec4 animation;" in compute)
        assertTrue("vec4 angularEpoch;" in compute)
        assertTrue("particles[i].appearance.y" in compute)
        assertTrue("particles[i].animation" !in compute)
        assertTrue("particles[i].angularEpoch" !in compute)
    }

    @Test
    fun `color curve multiplies the selected base color before transition override`() {
        val shader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/cparticle.vsh"
        )
        val colorWrites = shader.substringAfter("vec3 particleColor = iColor.rgb;")
            .substringBefore("float baseAlpha")

        val cycle = colorWrites.indexOf("particleColor = 0.5 + 0.5 * cos")
        val curve = colorWrites.indexOf("sampleColorCurve(curveT)")
        val transition = colorWrites.indexOf("particleColor = mix(uTransitionColorFrom")
        assertTrue(cycle >= 0)
        assertTrue(curve > cycle)
        assertTrue(transition > curve)
    }

    /**
     * 独立 alpha transition 覆盖实例 alpha，生命周期和 visual transition 曲线继续作为倍率。
     */
    @Test
    fun `alpha transition overrides instance alpha before lifetime multipliers`() {
        val shader = readProjectFile(
            "common/src/main/resources/assets/cooparticlesapi/shaders/core/vertex/cparticle.vsh"
        )
        val renderer = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleRenderer.kt"
        )
        val alphaWrites = shader.substringAfter("float baseAlpha = iColor.a;")
            .substringBefore("vColor = vec4")
        val scalarCurveUpload = renderer.substringAfter("private fun setScalarCurve(")
            .substringBefore("private fun setColorCurve(")

        val transitionOverride = alphaWrites.indexOf("baseAlpha = sampleScalarCurve(")
        val lifetimeCurves = alphaWrites.indexOf(
            "float alphaScale = sampleParticleAlphaCurve(lifeT) * sampleAlphaCurve(curveT);"
        )
        val visualTransition = alphaWrites.indexOf("alphaScale *= sampleScalarCurve(")
        assertTrue("if (uAlphaTransitionKeys > 0)" in alphaWrites)
        assertTrue(transitionOverride >= 0)
        assertTrue(lifetimeCurves > transitionOverride)
        assertTrue(visualTransition > lifetimeCurves)
        assertFalse("alphaScale *= sampleScalarCurve(\n        uAlphaTransitionProgress" in alphaWrites)
        assertTrue("baseAlpha * alphaScale" in shader)
        assertTrue("alphaTransition?.alphaCurve?.takeIf { alphaTransitionProgress != null }" in renderer)
        assertTrue("curve?.keyCount ?: 0" in scalarCurveUpload)
    }

    @Test
    fun `emitter particles can select dynamic or static data mode`() {
        val emitter = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ClassParticleEmitters.kt"
        )
        val bridge = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/compat/CParticleEmitterBridge.kt"
        )
        val stressEmitter = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/particle/emitter/TestCParticleEmitter.kt"
        )

        assertTrue("var updateMode: CParticleUpdateMode" in readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ControlableCParticleData.kt"
        ))
        assertTrue("if (data is ControlableCParticleData &&" in emitter)
        assertTrue("data: ControlableCParticleData" in bridge)
        assertTrue("updateMode = CParticleUpdateMode.STATIC" in stressEmitter)
        assertTrue("alphaCurve = LIFETIME_ALPHA" in stressEmitter)
        assertTrue("scaleCurve = LIFETIME_SCALE" in stressEmitter)
        assertTrue("this.colorCurve = colorCurve" in stressEmitter)
        assertTrue("configureCParticleSystem" !in stressEmitter)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(findRepoRoot().resolve(relativePath))
    }

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
