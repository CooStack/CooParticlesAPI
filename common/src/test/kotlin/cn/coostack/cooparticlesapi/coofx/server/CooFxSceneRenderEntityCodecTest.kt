package cn.coostack.cooparticlesapi.coofx.server

import cn.coostack.cooparticlesapi.coofx.adapter.CooFxWorldTransform
import cn.coostack.cooparticlesapi.coofx.coofxAsset
import net.minecraft.SharedConstants
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.Bootstrap
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CooFxSceneRenderEntityCodecTest {
    @Test
    fun `asset helper follows coofx directory convention`() {
        assertEquals(
            ResourceLocation.fromNamespaceAndPath("examplemod", "coofx/people/people.coofx.json"),
            coofxAsset("examplemod", "people"),
        )
        assertFailsWith<IllegalArgumentException> { coofxAsset("examplemod", "People") }
        assertFailsWith<IllegalArgumentException> { coofxAsset("examplemod", "people/main") }
        assertEquals(CooFxSceneMode.CAMERA_TRACKING, CooFxSceneMode.CAMERA_ONLY)
    }

    @Test
    fun `scene camera recipients support all legacy single and multiple players`() {
        val firstPlayer = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val secondPlayer = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val otherPlayer = UUID.fromString("33333333-3333-3333-3333-333333333333")
        val allPlayers = sceneEntity()
        val legacySinglePlayer = sceneEntity(cameraTargetPlayer = firstPlayer)
        val multiplePlayers = sceneEntity(cameraTargetPlayers = setOf(firstPlayer, secondPlayer))

        assertTrue(allPlayers.isCameraTargetedAt(otherPlayer))
        assertTrue(legacySinglePlayer.isCameraTargetedAt(firstPlayer))
        assertTrue(!legacySinglePlayer.isCameraTargetedAt(secondPlayer))
        assertEquals(setOf(firstPlayer), legacySinglePlayer.cameraTargetPlayers())
        assertTrue(multiplePlayers.isCameraTargetedAt(firstPlayer))
        assertTrue(multiplePlayers.isCameraTargetedAt(secondPlayer))
        assertTrue(!multiplePlayers.isCameraTargetedAt(otherPlayer))
        assertEquals(setOf(firstPlayer, secondPlayer), multiplePlayers.cameraTargetPlayers())
        assertFailsWith<IllegalArgumentException> {
            CooFxSceneSpec(
                resourceId = ResourceLocation.fromNamespaceAndPath("test", "coofx/scene.coofx.json"),
                transform = CooFxWorldTransform(0.0, 0.0, 0.0),
                requestSeed = 1L,
                cameraTargetPlayer = firstPlayer,
                cameraTargetPlayers = setOf(secondPlayer),
            )
        }
    }

    @Test
    fun `scene spec exposes model camera and emitter authority to server`() {
        val spec = CooFxSceneSpec(
            resourceId = ResourceLocation.fromNamespaceAndPath("test", "coofx/scene.coofx.json"),
            transform = CooFxWorldTransform(10.0, 20.0, 30.0),
            requestSeed = 91L,
            mode = CooFxSceneMode.MODEL_CAMERA_AND_EMITTER,
            clipId = "orbit",
            playbackSpeed = 1.5F,
            cameraId = "CameraMain",
            cameraPriority = 7,
            cameraTargetPlayer = UUID.fromString("11111111-1111-1111-1111-111111111111"),
            emitterId = "sparks",
            emitterCount = 12,
            emitterDelayTicks = 3,
            emitterLifetimeTicks = 40,
        )

        assertEquals(CooFxSceneMode.MODEL_CAMERA_AND_EMITTER, spec.mode)
        assertEquals("CameraMain", spec.cameraId)
        assertEquals(UUID.fromString("11111111-1111-1111-1111-111111111111"), spec.cameraTargetPlayer)
        assertEquals("sparks", spec.emitterId)
        assertEquals(12, spec.emitterCount)

        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
        val source = CooFxSceneRenderEntity().apply { setSceneSpec(spec) }
        val buffer = friendlyBuffer()
        source.getCodec().encode(buffer, source)
        val decoded = source.getCodec().decode(buffer) as CooFxSceneRenderEntity

        assertEquals(source.pos, decoded.pos)
        assertEquals(source.resourceIdText, decoded.resourceIdText)
        assertEquals(source.mode(), decoded.mode())
        assertEquals(source.cameraId(), decoded.cameraId())
        assertEquals(source.cameraTargetPlayer(), decoded.cameraTargetPlayer())
        assertEquals(source.emitterId(), decoded.emitterId())
        assertEquals(source.emitterCount, decoded.emitterCount)

        val firstPlayer = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val secondPlayer = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val multipleRecipients = sceneEntity(cameraTargetPlayers = setOf(firstPlayer, secondPlayer))
        val multipleBuffer = friendlyBuffer()
        multipleRecipients.getCodec().encode(multipleBuffer, multipleRecipients)
        val decodedMultiple = multipleRecipients.getCodec().decode(multipleBuffer) as CooFxSceneRenderEntity

        assertEquals(setOf(firstPlayer, secondPlayer), decodedMultiple.cameraTargetPlayers())
    }

    @Test
    fun `render entity marks every client controlled field for codec sync`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/server/CooFxSceneRenderEntity.kt"
        )

        listOf(
            "resourceIdText",
            "requestSeed",
            "rotationX",
            "rotationW",
            "scaleX",
            "modeId",
            "clipIdText",
            "playbackSpeed",
            "cameraIdText",
            "cameraPriority",
            "cameraTargetPlayerText",
            "cameraTargetPlayersText",
            "emitterIdText",
            "emitterCount",
            "emitterDelayTicks",
            "emitterLifetimeTicks",
        ).forEach { field ->
            assertTrue(Regex("@CodecField\\s+var $field:").containsMatchIn(source), "missing codec field $field")
        }
        assertTrue("fun applyPatch(patch: CooFxScenePatch)" in source)
        assertTrue("fun worldTransform(): CooFxWorldTransform" in source)
    }

    @Test
    fun `scene visibility keeps model broadcast separate from local camera target`() {
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/server/CooFxSceneManager.kt"
        )
        val registry = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxSceneClientRegistry.kt"
        )

        assertTrue("ServerRenderEntityManager.updateVisible(entity)" in manager)
        assertTrue("entity.isCameraTargetedAt(player.uuid)" in registry)
        assertTrue("CooFxCameraTrackingManager.remove(entity.uuid)" in registry)
        assertTrue("updateModel(state)" in registry)
    }

    @Test
    fun `asset camera FOV mixin is registered and nullable`() {
        val mixin = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/GameRendererCooFxCameraMixin.java"
        )
        val config = readProjectFile("common/src/main/resources/cooparticlesapi.mixins.json")
        val tracker = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/coofx/client/CooFxCameraTrackingManager.kt"
        )

        assertTrue("method = \"getFov\"" in mixin)
        assertTrue("GameRendererCooFxCameraMixin" in config)
        assertTrue("activePerspectiveFovDegrees(): Double?" in tracker)
        assertTrue("CooFxCompiledCameraProjection.Orthographic -> null" in tracker)
    }

    private fun sceneEntity(
        cameraTargetPlayer: UUID? = null,
        cameraTargetPlayers: Set<UUID>? = null,
    ): CooFxSceneRenderEntity = CooFxSceneRenderEntity().apply {
        setSceneSpec(CooFxSceneSpec(
            resourceId = ResourceLocation.fromNamespaceAndPath("test", "coofx/scene.coofx.json"),
            transform = CooFxWorldTransform(0.0, 0.0, 0.0),
            requestSeed = 1L,
            cameraTargetPlayer = cameraTargetPlayer,
            cameraTargetPlayers = cameraTargetPlayers,
        ))
    }

    private fun friendlyBuffer(): FriendlyByteBuf {
        val byteBuf = Class.forName("io.netty.buffer.Unpooled")
            .getMethod("buffer")
            .invoke(null)
        return FriendlyByteBuf::class.java.constructors
            .single { constructor -> constructor.parameterCount == 1 }
            .newInstance(byteBuf) as FriendlyByteBuf
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
