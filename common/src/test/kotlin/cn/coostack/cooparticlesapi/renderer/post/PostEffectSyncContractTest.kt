package cn.coostack.cooparticlesapi.renderer.post

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class PostEffectSyncContractTest {
    @Test
    fun `post effect packet serializes stable dto fields and no gl objects`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/server/PacketRendererPostEffectS2C.kt"
        )

        assertTrue("SyncedPostEffectState" in source)
        assertTrue("PostEffectBinding.writeTyped" in source)
        assertTrue("PostEffectParams" in source)
        assertTrue("CustomPacketPayload" in source)
        assertTrue("RenderEffectDescriptor" !in source)
        assertTrue("RenderTarget" !in source)
        assertTrue("GlFrameBuffer" !in source)
        assertTrue("ShaderPipeManager" !in source)
    }

    @Test
    fun `client render manager submits post effects into render effect graph`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderEntityManager.kt"
        )

        assertTrue("CooPostEffects.client.collectFramePost(context, graph)" in source)
        assertTrue("CooPostEffects.client.tick()" in source)
    }

    @Test
    fun `server shader effect play reuses the common packet on both loaders`() {
        val runtimeSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/post/CooPostEffects.kt"
        )
        val shaderEffectSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/pipeline/CooShaderEffects.kt"
        )
        val fabricSource = readProjectFile(
            "fabric/src/main/kotlin/cn/coostack/cooparticlesapi/platform/FabricServerNetworking.kt"
        )
        val neoForgeSource = readProjectFile(
            "neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/platform/NeoForgeServerNetworking.kt"
        )

        assertTrue("fun play(" in shaderEffectSource)
        assertTrue("player: ServerPlayer" in shaderEffectSource)
        assertTrue("CooPostEffects.server.send(player, instance)" in shaderEffectSource)
        assertTrue("CooParticlesServices.SERVER_NETWORK.send(PacketRendererPostEffectS2C.create" in runtimeSource)
        assertTrue("ServerPlayNetworking.send(player, packet)" in fabricSource)
        assertTrue("PacketDistributor.sendToPlayer(to, packet)" in neoForgeSource)
    }

    @Test
    fun `client post effect handler safely skips unregistered type and applies registered packet operations`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/client/listener/ClientRendererPostEffectHandler.kt"
        )

        assertTrue("internal fun apply(packet: PacketRendererPostEffectS2C)" in source)
        assertTrue("context.client().execute" in source)
        assertTrue("apply(packet)" in source)
        assertTrue("if (!PostEffectRuntimeRegistry.containsType(state.effectType))" in source)
        assertTrue("Skipping synced post effect id={} because type={} is not registered on the client" in source)
        assertTrue("return" in source)
        assertTrue("state.instantiate(serverSynced = true) ?: return" in source)
        assertTrue("PacketRendererPostEffectS2C.Operation.CREATE -> CooPostEffects.client.add(instance)" in source)
        assertTrue("PacketRendererPostEffectS2C.Operation.UPDATE -> CooPostEffects.client.update(instance)" in source)
        assertTrue("PacketRendererPostEffectS2C.Operation.REMOVE -> CooPostEffects.client.remove(packet.instanceId)" in source)
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
