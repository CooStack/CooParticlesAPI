package cn.coostack.cooparticlesapi.test.options.particle

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class EmitterOptionsContractTest {
    @Test
    fun `test options expose display entity pipeline render entity and auto emitters examples`() {
        val apiBuilder = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/APITestGroupBuilder.kt"
        )
        val displaySource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/display/CylinderBoardDisplayEntity.kt"
        )
        val renderSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/DemoBlackHoleRenderEntity.kt"
        )
        val renderRendererSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/DemoBlackHoleRenderEntityRenderer.kt"
        )
        val autoEmittersSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/particle/emitter/TestDisplayEntityAutoEmitters.kt"
        )
        val rendererUtilSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/utils/MinecraftRendererUtil.kt"
        )

        assertTrue("SimpleDisplayEntityOption" in apiBuilder)
        assertTrue("CylinderBoardDisplayEntity(player.eyePosition + player.forward * 4.0, player.level())" in apiBuilder)
        assertTrue("DemoWorldRenderEffectOptions.blackHole(player)" in apiBuilder)
        assertTrue("SimpleRendererEntityOption" in readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/options/renderer/world/DemoWorldRenderEffectOptions.kt"
        ))
        assertTrue("TestDisplayEntityAutoEmitters(player.eyePosition, player.level())" in apiBuilder)

        assertTrue("@CooAutoRegister" in displaySource)
        assertTrue("AutoDisplayEntity" in displaySource)
        assertTrue("var direction: Vec3" in displaySource)
        assertTrue("var length: Float" in displaySource)
        assertTrue("var lineWidth: Float" in displaySource)
        assertTrue("var velocity: Vec3" in displaySource)
        assertTrue("var maxAge: Int" in displaySource)
        assertTrue("override fun tick()" in displaySource)
        assertTrue("pos = pos.add(velocity)" in displaySource)
        assertTrue("color.w = initialAlpha * live" in displaySource)
        assertTrue("remove()" in displaySource)
        assertTrue("manageRotation = false" in displaySource)
        assertTrue("MinecraftRendererUtil.axialBillboardBasis(direction, camera, pos)" in displaySource)
        assertTrue("CooParticlesRenderTypes.entityCutoutEmissive(" in displaySource)
        assertTrue("addBoard(" in displaySource)
        assertTrue("data class AxialBillboardBasis" in rendererUtilSource)
        assertTrue("fun axialBillboardBasis(axisDirection: Vec3, camera: Camera, center: Vec3)" in rendererUtilSource)
        assertTrue("toCamera.subtract(axis.scale(toCamera.dot(axis)))" in rendererUtilSource)

        assertTrue("@CooAutoRegister" in renderSource)
        assertTrue("AutoRenderEntity" in renderSource)
        assertTrue("override fun getRenderID()" in renderSource)
        assertTrue("RenderEntityRenderer<DemoBlackHoleRenderEntity>" in renderRendererSource)
        assertTrue("override val pipeline = CooPipelines.MASK_BLOOM" in renderRendererSource)
        assertTrue(".intensity { entity: DemoBlackHoleRenderEntity -> entity.intensity }" in renderRendererSource)
        assertTrue("override fun render(input: RenderInput<DemoBlackHoleRenderEntity>)" in renderRendererSource)

        assertTrue("@CooAutoRegister" in autoEmittersSource)
        assertTrue("AutoEmitters" in autoEmittersSource)
        assertTrue("DisplayEntityEmittersData" in autoEmittersSource)
        assertTrue("CylinderBoardDisplayEntity" in autoEmittersSource)
        assertTrue("axisDirection = player.forward" in apiBuilder)
        assertTrue("lineWidth = 0.08F" in apiBuilder)
        assertTrue("lineLength = 1.35F" in apiBuilder)
        assertTrue("inwardSpeed = 0.18" in apiBuilder)
        assertTrue("lineLife = 22" in apiBuilder)
        assertTrue("DisplayEntityEmittersData(createBoard(offset))" in autoEmittersSource)
        assertTrue("inward.normalize().scale(-inwardSpeed)" in autoEmittersSource)
        assertTrue("entity.direction = if (entity.velocity.lengthSqr() > 1.0E-6)" in autoEmittersSource)
        assertTrue("direction = if (velocity.lengthSqr() > 1.0E-6) velocity else axisDirection" in autoEmittersSource)
        assertTrue("override fun genControls" in autoEmittersSource)
        assertTrue("override fun singleControlableAction" in autoEmittersSource)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(projectFile(relativePath))
    }

    private fun projectFile(relativePath: String): Path {
        val repoRoot = findRepoRoot()
        return repoRoot.resolve(relativePath)
    }

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) {
                return cursor
            }
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
