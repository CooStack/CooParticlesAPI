package cn.coostack.cooparticlesapi.network

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkSyncRegressionContractTest {
    @Test
    fun `emitter visibility is recorded after create packet`() {
        val source = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ParticleEmittersManager.kt"
        )
        val packet = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/server/PacketParticleEmittersS2C.kt"
        )
        val handler = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/client/listener/ClientParticleEmittersPacketHandler.kt"
        )

        assertTrue("visibleSet.add(emitters)" in source)
        assertTrue("distanceTo(emitters.pos) <= 256.0" in source)
        assertTrue("if (emitters.canceled)" in source)
        assertTrue("dirtyEmitters.remove(emitters.uuid)" in source)
        assertTrue("sendUpdate(emitters)" in source)
        assertTrue("CREATE" in packet)
        assertTrue("CHANGE" in packet)
        assertTrue("REMOVE" in packet)
        assertFalse("CHANGE_OR_CREATE" in packet)
        assertTrue("PacketType.CREATE" in source)
        assertTrue("PacketType.CHANGE" in source)
        assertTrue("context.client().execute" in handler)
        assertTrue("createClient" in handler)
        assertTrue("changeClient" in handler)
        assertTrue("removeClient" in handler)
        assertTrue("emitters.playing = false" in source)
        assertTrue("emitters.start()" in source)
        val serverTick = source.substringAfter("fun doTickServer()")
            .substringBefore("fun doTickClient()")
        assertTrue(serverTick.indexOf("if (emitters.canceled)") < serverTick.indexOf("emitters.tick()"))
        assertFalse("if (emitter.value.canceled)" in serverTick)
        val removePacket = source.substringAfter("private fun createRemovePacket")
            .substringBefore("private fun encodeEmittersToArray")
        assertFalse("encodeEmittersToArray" in removePacket)
    }

    @Test
    fun `emitter dirty updates are merged by manager`() {
        val emitter = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ParticleEmitters.kt"
        )
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ParticleEmittersManager.kt"
        )

        assertTrue("NetworkDirtyMarkable" in emitter)
        assertTrue("override fun markDirty()" in emitter)
        assertTrue("ParticleEmittersManager.enqueueDirty(this)" in emitter)
        assertTrue("private val dirtyEmitters" in manager)
        assertFalse("fun updateEmitters(" in manager)
    }

    @Test
    fun `composition and display managers do not send full state every tick`() {
        val composition = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/composition/manager/ParticleCompositionManager.kt"
        )
        val display = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/display/DisplayEntityManager.kt"
        )

        assertFalse("sendCreateOrUpdate(entry.value)" in composition)
        assertFalse("sendCreateOrUpdate(entry.value)" in display)
        assertTrue("PacketParticleCompositionStateS2C" in composition)
        assertTrue("PacketDisplayEntityStateS2C" in display)
        assertTrue("val createTargets" in composition)
        assertTrue("recreate = true" in composition)
        assertTrue("createTargets.add(player)" in composition)
        val createSend = composition.substringAfter("if (createTargets.isNotEmpty())")
            .substringBefore("if (forceUpdate || fullDirty)")
        assertTrue(
            createSend.indexOf("SERVER_NETWORK.send") <
                    createSend.indexOf("playerPlayerVisibleSet.getValue(player.uuid).add(composition)")
        )
        val compositionServerTick = composition.substringAfter("fun tickServer()")
            .substringBefore("fun removeVisibleComposition")
        val displayServerTick = display.substringAfter("fun tickServer()")
            .substringBefore("fun sendCreateOrUpdate")
        assertTrue(
            compositionServerTick.indexOf("syncVisible(entry.value, false)") <
                    compositionServerTick.indexOf("entry.value.tick()")
        )
        assertTrue(
            displayServerTick.indexOf("syncVisible(entry.value, false)") <
                    displayServerTick.indexOf("entry.value.tick()")
        )
        val spawnBody = composition.substringAfter("fun spawn(composition: ParticleComposition)")
            .substringBefore("fun register(randomInstance")
        assertTrue(spawnBody.indexOf("composition.display()") < spawnBody.indexOf("sendCreateOrUpdate(composition)"))
        assertFalse("composition.markDirty()" in spawnBody)
    }

    @Test
    fun `client create packets use envelope identity and report missing codecs`() {
        val composition = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/client/listener/ClientParticleCompositionHandler.kt"
        )
        val emitter = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/client/listener/ClientParticleEmittersPacketHandler.kt"
        )

        assertTrue("new.controlUUID = payload.uuid" in composition)
        assertTrue("未注册的 Composition codec" in composition)
        assertTrue("emitters.uuid = payload.emitterUUID" in emitter)
        assertTrue("未注册的 Emitter codec" in emitter)
    }

    @Test
    fun `composition and display packets enter the client thread`() {
        listOf(
            "ClientParticleCompositionHandler.kt",
            "ClientParticleCompositionStateHandler.kt",
            "ClientParticleCompositionRotateHandler.kt",
            "ClientDisplayEntityPacketHandler.kt",
            "ClientDisplayEntityStateHandler.kt",
        ).forEach { fileName ->
            val source = readProjectFile(
                "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/packet/client/listener/$fileName"
            )
            assertTrue("context.client().execute" in source, fileName)
        }
    }

    @Test
    fun `new payloads are registered on both loaders`() {
        val fabricCommon = readProjectFile(
            "fabric/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIFabric.kt"
        )
        val fabricClient = readProjectFile(
            "fabric/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIFabricClient.kt"
        )
        val neoForge = readProjectFile(
            "neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/listener/CooParticlesAPINeoModInitListener.kt"
        )

        listOf(
            "PacketParticleCompositionStateS2C",
            "PacketDisplayEntityStateS2C",
            "PacketParticleBatchS2C",
        ).forEach { packet ->
            assertTrue(packet in fabricCommon)
            assertTrue(packet in fabricClient)
            assertTrue(packet in neoForge)
        }
    }

    @Test
    fun `player disconnect clears composition and emitter visibility`() {
        val emitterManager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ParticleEmittersManager.kt"
        )
        val fabric = readProjectFile(
            "fabric/src/main/kotlin/cn/coostack/cooparticlesapi/CooParticlesAPIFabric.kt"
        )
        val neoForge = readProjectFile(
            "neoforge/src/main/kotlin/cn/coostack/cooparticlesapi/listener/server/CooParticlesAPINeoServerListener.kt"
        )

        assertTrue("fun clearVisibleFor(player: Player)" in emitterManager)
        assertTrue("visible.remove(player.uuid)" in emitterManager)
        listOf(fabric, neoForge).forEach { loader ->
            assertTrue("ParticleCompositionManager.clearVisibleFor" in loader)
            assertTrue("ParticleEmittersManager.clearVisibleFor" in loader)
        }
    }

    private fun readProjectFile(relativePath: String): String = Files.readString(findRepoRoot().resolve(relativePath))

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) return cursor
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }
}
