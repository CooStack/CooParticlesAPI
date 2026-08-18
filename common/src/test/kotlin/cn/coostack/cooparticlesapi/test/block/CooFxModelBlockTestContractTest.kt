package cn.coostack.cooparticlesapi.test.block

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CooFxModelBlockTestContractTest {
    @Test
    fun `block test uses long lived server scene manager instead of a temporary packet`() {
        val option = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/CooFxModelBlockTestOption.kt"
        )
        val builder = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/test/block/builtin/" +
                "BlockAPITestGroupBuilder.kt"
        )

        assertTrue("CooFxModelBlockTestOption(player)" in builder)
        assertTrue("CooFxModelBlockTestOption.CAMERA_OPTION_ID" in builder)
        assertTrue("CooFxSceneMode.CAMERA_ONLY" in builder)
        assertTrue("resourceId = coofxAsset(" in option)
        assertTrue("resourceId = coofxAsset(CooParticlesConstants.MOD_ID, \"people\")" in option)
        assertTrue("cameraId = \"Camera\"" in option)
        assertTrue("clipId = null" in option)
        assertTrue("cameraPriority = if (sceneMode == CooFxSceneMode.MODEL) 0 else 10" in option)
        assertTrue("复用 people glTF 内置 Camera" in option)
        assertFalse("camera-control" in option)
        assertFalse("CameraControl" in option)
        assertFalse("camera_track" in option)
        assertTrue("鼠标" in option)
        assertTrue("恢复玩家" in option)
        assertTrue("CooFxSceneSpec(" in option)
        assertTrue("cameraTargetPlayer = player.uuid" in option)
        assertTrue("sceneMode == CooFxSceneMode.MODEL" in option)
        assertTrue("使用静态 bind pose" in option)
        assertTrue("sceneHandle?.stop()" in option)
        assertTrue("clearModel()" in option)
        assertTrue("ownerKey = logicalOwner" in option)
        assertTrue("clearModel()" in option.substringAfter("override fun start()"))
        assertTrue("override fun onFailed() = clearModel()" in option)
        assertTrue("override fun onSuccess() = clearModel()" in option)
        assertTrue("TestReviewMode.MANUAL_VISUAL" in option)
        assertFalse("PacketCooFxModelBlockTestS2C(" in option)
    }

    @Test
    fun `scene manager reuses render entity synchronization and client mirrors all modes`() {
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/server/CooFxSceneManager.kt"
        )
        val entity = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/server/CooFxSceneRenderEntity.kt"
        )
        val registry = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxSceneClientRegistry.kt"
        )
        val packetHandler = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/client/listener/" +
                "ClientRenderEntityPacketHandler.kt"
        )
        val clientLifecycle = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIClient.kt"
        )
        val fabricClient = readProjectFile(
            "fabric/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIFabricClient.kt"
        )
        val neoforgeClient = readProjectFile(
            "neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/listener/client/" +
                "CooParticlesAPINeoClientModListener.kt"
        )

        val clientRuntime = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxClientRuntime.kt"
        )
        val cameraSample = clientRuntime
            .substringAfter("fun sampleCamera(")
            .substringBefore("fun cancelQueued(")

        assertTrue("snapshot.assets[resourceId]" in cameraSample)
        assertTrue("prepared.compiled.resolveCamera(cameraSelector)" in cameraSample)
        assertTrue("prepared.compiled.nodeWorldMatrix" in cameraSample)
        assertFalse("modelHandle" in cameraSample)
        assertFalse("modelManager" in cameraSample)
        assertFalse("gpuPackages" in cameraSample)
        assertTrue("ServerRenderEntityManager.spawn(entity)" in manager)
        assertTrue("ServerRenderEntityManager.removeAllView(entity)" in manager)
        assertTrue("fun update(sceneId: UUID, patch: CooFxScenePatch)" in manager)
        assertTrue("private val sceneOwners" in manager)
        assertTrue("sceneOwners[owner]?.let(::stop)" in manager)
        assertTrue("sceneOwners.entries.removeIf" in manager)
        assertTrue("@CooAutoRegister" in entity)
        assertTrue("var resourceIdText: String" in entity)
        assertTrue("var rotationW: Float" in entity)
        assertTrue("var modeId: Int" in entity)
        assertTrue("var emitterCount: Int" in entity)
        assertTrue("CooFXClient.playModel(request)" in registry)
        assertTrue("CooFXClient.updateModel(handle, request)" in registry)
        assertTrue("CooFXClient.play(request)" in registry)
        assertTrue("CooFXClient.sampleCamera(" in registry)
        assertTrue("if (!rendersModel(mode))" in registry)
        assertTrue("state.modelHandle = null" in registry)
        assertTrue("CooFxSceneClientRegistry.onCreated" in packetHandler)
        assertTrue("CooFxSceneClientRegistry.onRemoved" in packetHandler)
        assertTrue("CooFxSceneClientRegistry.tick()" in clientLifecycle)
        assertTrue("CooFxSceneClientRegistry.clear()" in clientLifecycle)
        assertTrue("ClientTickEvents.START_WORLD_TICK.register" in fabricClient)
        assertTrue("CooParticlesAPIClient.tickClient(it)" in fabricClient)
        assertTrue("LevelTickEvent.Pre" in neoforgeClient)
        assertTrue("CooParticlesAPIClient.tickClient(level)" in neoforgeClient)
        assertTrue("[CooFX-CREATE]" in packetHandler)
        val autoRegistry = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityAutoRegistry.kt"
        )
        assertTrue("[CooFX-REGISTRY]" in autoRegistry)
        assertTrue("CooFxSceneRenderEntity.ID" in autoRegistry)
    }

    private fun readProjectFile(relativePath: String): String {
        return Files.readString(projectFile(relativePath))
    }

    private fun projectFile(relativePath: String): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) {
                return cursor.resolve(relativePath)
            }
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
