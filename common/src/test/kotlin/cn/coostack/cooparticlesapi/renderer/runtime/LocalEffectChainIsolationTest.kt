package cn.coostack.cooparticlesapi.renderer.runtime

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class LocalEffectChainIsolationTest {
    @Test
    fun `two local effect chains keep separate identity and targets`() {
        val firstChain = LocalEffectChain(LocalRenderTargetPool())
        val secondChain = LocalEffectChain(LocalRenderTargetPool())

        assertNotEquals(firstChain.chainId, secondChain.chainId)
        assertNotEquals(firstChain.primaryTarget.id, secondChain.primaryTarget.id)
    }

    @Test
    fun `updating one chain does not mutate the other chain steps or targets`() {
        val firstChain = LocalEffectChain(LocalRenderTargetPool())
        val secondChain = LocalEffectChain(LocalRenderTargetPool())
        val firstOriginalTarget = firstChain.primaryTarget.id
        val secondOriginalTarget = secondChain.primaryTarget.id

        firstChain.addStep(LocalEffectStep { })
        firstChain.replaceSteps(listOf(LocalEffectStep { }, LocalEffectStep { }))

        assertEquals(2, firstChain.steps().size)
        assertTrue(secondChain.steps().isEmpty())
        assertEquals(firstOriginalTarget, firstChain.primaryTarget.id)
        assertEquals(secondOriginalTarget, secondChain.primaryTarget.id)
    }

    @Test
    fun `render entity instance source owns a local chain and a render target pool`() {
        val instanceSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/runtime/RenderEntityInstance.kt"
        )

        assertTrue("val localRenderTargetPool" in instanceSource)
        assertTrue("var localEffectChain" in instanceSource)
    }

    @Test
    fun `local render target pool tracks resize and release lifecycle`() {
        val pool = LocalRenderTargetPool()
        val first = pool.allocate("first")
        val second = pool.allocate("second")

        pool.resize(640, 360)

        assertEquals(640, first.width)
        assertEquals(360, first.height)
        assertEquals(first.generation, second.generation)
        assertEquals(2, pool.activeHandles().size)

        pool.release(first)

        assertTrue(first.released)
        assertEquals(1, pool.activeHandles().size)

        pool.releaseAll()

        assertTrue(second.released)
        assertTrue(pool.activeHandles().isEmpty())
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
