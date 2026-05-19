package cn.coostack.cooparticlesapi.renderer.shader

import cn.coostack.cooparticlesapi.renderer.shader.api.CooComputeShaderProgram
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShader
import cn.coostack.cooparticlesapi.renderer.shader.api.glsl.GlShaderType
import cn.coostack.cooparticlesapi.renderer.shader.buffer.ShaderBufferLayout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShaderProgramBuilderCapabilityTest {
    @AfterTest
    fun tearDown() {
        ShaderProgramRegistry.releaseAll()
    }

    @Test
    fun `shader type enum exposes veil style extra stages`() {
        assertTrue(GlShaderType.entries.contains(GlShaderType.GEOMETRY))
        assertTrue(GlShaderType.entries.contains(GlShaderType.TESSELLATION_CONTROL))
        assertTrue(GlShaderType.entries.contains(GlShaderType.TESSELLATION_EVALUATION))
        assertTrue(GlShaderType.entries.contains(GlShaderType.COMPUTE))
    }

    @Test
    fun `advanced graphics builder retains optional geometry and tessellation stages`() {
        val vertex = FakeShader(GlShaderType.VERTEX)
        val fragment = FakeShader(GlShaderType.FRAGMENT)
        val geometry = FakeShader(GlShaderType.GEOMETRY)
        val tessControl = FakeShader(GlShaderType.TESSELLATION_CONTROL)
        val tessEvaluation = FakeShader(GlShaderType.TESSELLATION_EVALUATION)

        val program = AdvancedShaderProgramBuilder()
            .vertex(vertex)
            .fragment(fragment)
            .geometry(geometry)
            .tessellationControl(tessControl)
            .tessellationEvaluation(tessEvaluation)
            .build()

        assertTrue(program is SimpleShaderProgram)
        assertEquals(geometry, program.geometryShader())
        assertEquals(tessControl, program.tessellationControlShader())
        assertEquals(tessEvaluation, program.tessellationEvaluationShader())
        assertEquals(listOf(vertex, tessControl, tessEvaluation, geometry, fragment), program.attachedShaders())
    }

    @Test
    fun `advanced builder creates dedicated compute program`() {
        val compute = FakeShader(GlShaderType.COMPUTE)

        val program = AdvancedShaderProgramBuilder()
            .compute(compute)
            .buildCompute()

        assertTrue(program is CooComputeShaderProgram)
        assertEquals(compute, program.computeShader)
    }

    @Test
    fun `advanced builder carries shader buffer layouts into created programs`() {
        val layout = ShaderBufferLayout.builder<Unit>("OrbData")
            .float("time") { 1f }
            .build()

        val graphics = AdvancedShaderProgramBuilder()
            .vertex(FakeShader(GlShaderType.VERTEX))
            .fragment(FakeShader(GlShaderType.FRAGMENT))
            .bufferLayout(layout)
            .build()

        val compute = AdvancedShaderProgramBuilder()
            .compute(FakeShader(GlShaderType.COMPUTE))
            .bufferLayout(layout)
            .buildCompute()

        assertEquals(listOf(layout), graphics.shaderBufferLayouts())
        assertEquals(listOf(layout), compute.shaderBufferLayouts())
    }

    @Test
    fun `advanced builder registers created programs for reload lifecycle`() {
        AdvancedShaderProgramBuilder()
            .vertex(FakeShader(GlShaderType.VERTEX))
            .fragment(FakeShader(GlShaderType.FRAGMENT))
            .build()

        AdvancedShaderProgramBuilder()
            .compute(FakeShader(GlShaderType.COMPUTE))
            .buildCompute()

        assertEquals(1, ShaderProgramRegistry.graphicsCount())
        assertEquals(1, ShaderProgramRegistry.computeCount())
    }

    private class FakeShader(
        override val type: GlShaderType
    ) : GlShader {
        override fun shaderID(): Int = 0
        override fun compile() {}
        override fun assertCompiled() {}
        override fun deleteShader() {}
    }
}
