package cn.coostack.cooparticlesapi.cparticle

import cn.coostack.cooparticlesapi.network.particle.composition.AutoParticleComposition
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.network.particle.composition.manager.ParticleCompositionManager
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CParticleDebugCountContractTest {
    /**
     * 检查 F3 注入的五项客户端计数及其数据来源。
     *
     * 示例：修改调试行名称或容器入口后运行本测试，可及时更新契约。
     * 禁止重新把 CParticle 数量拼到原版 `P` 行。
     */
    @Test
    fun `f3 debug overlay lists all client runtime counts`() {
        val mixin = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/DebugScreenOverlayMixin.java"
        )
        val mixinConfig = readProjectFile("common/src/main/resources/cooparticlesapi.mixins.json")
        val mixinPlugin = readProjectFile(
            "common/src/main/java/cn/coostack/cooparticlesapi/mixin/plugin/CooParticleMixinLoaderPlugin.java"
        )
        val renderEntityManager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/client/ClientRenderEntityManager.kt"
        )
        val compositionManager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/composition/manager/ParticleCompositionManager.kt"
        )
        val commonMixins = mixinConfig.substringAfter("\"mixins\": [").substringBefore("]")
        val clientMixins = mixinConfig.substringAfter("\"client\": [").substringBefore("]")

        assertTrue("\"DebugScreenOverlayMixin\"" in clientMixins)
        assertFalse("\"DebugScreenOverlayMixin\"" in commonMixins)
        assertFalse("DebugScreenOverlayMixin" in mixinPlugin)
        assertFalse("ParticleEngineDebugMixin" in mixinConfig)
        assertTrue("@Mixin(DebugScreenOverlay.class)" in mixin)
        assertTrue(
            "@Inject(method = \"getGameInformation\", at = @At(\"RETURN\"), cancellable = true)" in mixin
        )
        assertFalse("countParticles" in mixin)
        assertTrue("[CPLib] Emitters: " in mixin)
        assertTrue("ParticleEmittersManager.INSTANCE.getClientEmitters().size()" in mixin)
        assertTrue("[CPLib] Compositions: " in mixin)
        assertTrue("ParticleCompositionManager.loadedClientCount()" in mixin)
        assertTrue("[CPLib] CParticles: " in mixin)
        assertTrue("CParticleSystemManager.totalAlive()" in mixin)
        assertTrue("[CPLib] DisplayEntities: " in mixin)
        assertTrue("DisplayEntityManager.INSTANCE.getClientView().size()" in mixin)
        assertTrue("[CPLib] RenderEntities: " in mixin)
        assertTrue("ClientRenderEntityManager.loadedEntityCount()" in mixin)
        assertTrue("fun loadedEntityCount(): Int = entities.size" in renderEntityManager)
        assertTrue("WeakReference<T>" in compositionManager)
        assertTrue("System.identityHashCode(referent)" in compositionManager)
        assertFalse("WeakHashMap<ParticleComposition, Boolean>()" in compositionManager)
    }

    /**
     * 检查 Composition 的显示、结束和世界清理路径都维护客户端活动计数。
     *
     * 示例：普通和序列 Composition 显示时登记，`clear(true)` 和换世界时移除。
     * 禁止只在顶层 manager 中计数，否则嵌套和 Emitter 直显实例会漏记。
     */
    @Test
    fun `client composition lifecycle updates debug count`() {
        val composition = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/composition/ParticleComposition.kt"
        )
        val sequencedComposition = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/composition/SequencedParticleComposition.kt"
        )
        val manager = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/composition/manager/ParticleCompositionManager.kt"
        )
        val clear = composition.substringAfter("open fun clear(cancel: Boolean) {")
            .substringBefore("internal fun resetLifecycleForSpawn()")
        val reset = composition.substringAfter("internal fun resetLifecycleForSpawn()")
            .substringBefore("open fun display()")
        val display = composition.substringAfter("open fun display()")
            .substringBefore("override fun spawn")
        val sequencedDisplay = sequencedComposition.substringAfter("override fun display()")
            .substringBefore("open fun beforeDisplaySequenced")

        assertTrue("if (cancel)" in clear)
        assertTrue("ParticleCompositionManager.setClientLoaded(this, false)" in clear)
        assertTrue("ParticleCompositionManager.setClientLoaded(this, false)" in reset)
        assertTrue("ParticleCompositionManager.setClientLoaded(this, true)" in display)
        assertTrue("ParticleCompositionManager.setClientLoaded(this, true)" in sequencedDisplay)
        assertTrue("loadedClientCompositions.clear()" in manager)
    }

    /**
     * 检查 Composition 活动计数按实例身份登记，并允许幂等移除。
     *
     * 示例：同一实例登记两次仍只增加一项，另一个实例会单独增加一项。
     * 禁止让测试结束后留下已登记实例，避免影响同一 JVM 中的其他测试。
     */
    @Test
    fun `client composition count tracks every active instance once`() {
        val first = EqualParticleComposition()
        val second = EqualParticleComposition()
        val initial = ParticleCompositionManager.loadedClientCount()

        try {
            assertEquals(first, second)
            ParticleCompositionManager.setClientLoaded(first, true)
            ParticleCompositionManager.setClientLoaded(first, true)
            assertEquals(initial + 1, ParticleCompositionManager.loadedClientCount())

            ParticleCompositionManager.setClientLoaded(second, true)
            assertEquals(initial + 2, ParticleCompositionManager.loadedClientCount())

            ParticleCompositionManager.setClientLoaded(first, false)
            ParticleCompositionManager.setClientLoaded(first, false)
            assertEquals(initial + 1, ParticleCompositionManager.loadedClientCount())
        } finally {
            ParticleCompositionManager.setClientLoaded(first, false)
            ParticleCompositionManager.setClientLoaded(second, false)
        }

        assertEquals(initial, ParticleCompositionManager.loadedClientCount())
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

private class EqualParticleComposition : AutoParticleComposition(Vec3.ZERO, null) {
    override fun getParticles(): Map<CompositionData, RelativeLocation> = emptyMap()

    override fun onDisplay() = Unit

    override fun equals(other: Any?): Boolean = other is EqualParticleComposition

    override fun hashCode(): Int = 0
}
