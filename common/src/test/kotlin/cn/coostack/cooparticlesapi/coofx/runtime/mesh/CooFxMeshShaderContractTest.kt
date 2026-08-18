package cn.coostack.cooparticlesapi.coofx.runtime.mesh

import cn.coostack.cooparticlesapi.coofx.runtime.mesh.storage.CooFxMeshInstanceLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CooFxMeshShaderContractTest {
    @Test
    fun `shader consumes node matrix sidecar without changing instance ABI`() {
        val resource = javaClass.classLoader.getResourceAsStream(
            "assets/cooparticlesapi/shaders/coofx/mesh_particle.vsh"
        )
        val source = assertNotNull(resource).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val fragmentResource = javaClass.classLoader.getResourceAsStream(
            "assets/cooparticlesapi/shaders/coofx/mesh_particle.fsh"
        )
        val fragmentSource = assertNotNull(fragmentResource).bufferedReader(Charsets.UTF_8).use { it.readText() }

        assertTrue("#version 150 core" in source)
        assertTrue("in vec4 aNodeWorldRow0;" in source)
        assertTrue("in vec4 aNodeWorldRow2;" in source)
        assertTrue("nodeWorldMatrix * vec4(aPosition, 1.0)" in source)
        assertTrue("mat4 nodeWorldMatrix = transpose(mat4(" in source)
        assertTrue("transpose(mat4(" in source)
        assertTrue("aNodeWorldRow0" in source && "aNodeWorldRow1" in source && "aNodeWorldRow2" in source)
        assertTrue("aPosition, 1.0" in source)
        assertTrue("uniform vec3 uCameraPosition;" in source)
        assertTrue("worldPosition - uCameraPosition" in source)
        assertTrue("location = 16" !in source)
        assertTrue("transpose(inverse(combinedLinear))" in source)
        assertTrue("vec3 cameraRelativePosition" in source)
        assertTrue("tfEntityPosition = cameraRelativePosition" in source)
         assertTrue("uIrisEntitySpace ? worldPosition" !in source)
        assertTrue("tfEntityNormal" in source)
        assertTrue("aCurrentScaleLight.w" in source)
        assertTrue("uniform sampler2D uLightmap;" in fragmentSource)
        assertTrue("uniform vec3 uEmissiveFactor;" in fragmentSource)
        assertTrue("uniform float uEmissiveStrength;" in fragmentSource)
        assertTrue("minecraft_sample_lightmap" in fragmentSource)
        assertTrue("litColor + emissiveColor" in fragmentSource)
        assertEquals(36, CooFxMeshInstanceLayout.FLOAT_COUNT)
        assertEquals(144, CooFxMeshInstanceLayout.BYTE_STRIDE)
    }
}
