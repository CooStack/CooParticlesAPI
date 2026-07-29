package cn.coostack.cooparticlesapi.cparticle.compat

import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.render.nextScratchCapacity
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CParticleEmitterMemoryContractTest {
    /**
     * 当前可写段必须被复用，不能让每个新粒子重新扫描全部已满段。
     */
    @Test
    fun `segment cursor reuses the writable system for a large spawn batch`() {
        val fullSegments = (0 until 122).toHashSet()
        var systemLookups = 0
        val cursor = CParticleEmitterSegmentCursor(
            isReleased = { false },
            isFull = { it in fullSegments },
            getOrCreate = { index: Int ->
                systemLookups++
                index
            },
        )

        repeat(6_000) {
            val segment = cursor.findAvailable()
            assertEquals(122, segment)
        }

        assertEquals(123, systemLookups)

        fullSegments.add(122)
        fullSegments.remove(17)
        assertEquals(17, cursor.findAvailable())
        assertEquals(141, systemLookups)
    }

    /**
     * 首段容量必须在创建 system 前一次确定，不能先建小池再复制扩容。
     */
    @Test
    fun `emitter segment capacity follows the cparticle batch`() {
        assertEquals(16_384, CParticleEmitterBridge.segmentCapacityFor(1, 3_000_000))
        assertEquals(16_384, CParticleEmitterBridge.segmentCapacityFor(16_000, 3_000_000))
        assertEquals(20_000, CParticleEmitterBridge.segmentCapacityFor(20_000, 3_000_000))
        assertEquals(32_767, CParticleEmitterBridge.segmentCapacityFor(40_000, 3_000_000))
        assertEquals(8_192, CParticleEmitterBridge.segmentCapacityFor(20_000, 8_192))

        val emitterSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ClassParticleEmitters.kt"
        )
        assertTrue("particles.count { it.first is ControlableCParticleData }" in emitterSource)
        assertTrue("cparticleBatchSize" in emitterSource)
    }

    /**
     * Direct scratch 首次按实际上传量分配，后续增长也不能越过 system 上限。
     */
    @Test
    fun `upload scratch grows from actual demand`() {
        assertEquals(360, nextScratchCapacity(0, 360, 36_000))
        assertEquals(720, nextScratchCapacity(360, 400, 36_000))
        assertEquals(36_000, nextScratchCapacity(20_000, 35_000, 36_000))
        assertEquals(720, nextScratchCapacity(720, 400, 36_000))

        val bufferSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/render/CParticleGlBuffer.kt"
        )
        val scratchFunction = bufferSource.substringAfter("private fun scratchBuffer(")
            .substringBefore("private fun smallPatchBuffer(")
        assertTrue("requiredFloats" in scratchFunction)
        assertTrue("MemoryUtil.memAllocFloat(nextCapacity)" in scratchFunction)
        assertFalse("createFloatBuffer(capacity * CParticleStore.STRIDE)" in scratchFunction)
    }

    /**
     * emitter 已结束时等最后一个粒子死亡后释放；活跃的间歇 emitter 仍使用空闲阈值。
     */
    @Test
    fun `terminal emitter systems release only after particles die`() {
        assertFalse(CParticleSystemManager.shouldReleaseAutoSystem(1, terminal = true, idleTicks = 500))
        assertTrue(CParticleSystemManager.shouldReleaseAutoSystem(0, terminal = true, idleTicks = 0))
        assertFalse(CParticleSystemManager.shouldReleaseAutoSystem(0, terminal = false, idleTicks = 200))
        assertTrue(CParticleSystemManager.shouldReleaseAutoSystem(0, terminal = false, idleTicks = 201))

        val emittersManagerSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ParticleEmittersManager.kt"
        )
        assertTrue("CParticleEmitterBridge.finishEmitter" in emittersManagerSource)
    }

    /**
     * Emitter 声明的方块碰撞范围必须经过统一限制后写入每个 GPU system。
     */
    @Test
    fun `emitter declares a bounded block collision range`() {
        assertEquals(0, CParticleSystemManager.normalizeBlockCollisionRange(-1))
        assertEquals(24, CParticleSystemManager.normalizeBlockCollisionRange(24))
        assertEquals(96, CParticleSystemManager.normalizeBlockCollisionRange(200))

        val emitterSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/network/particle/emitters/ClassParticleEmitters.kt"
        )
        val bridgeSource = readProjectFile(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/cparticle/compat/CParticleEmitterBridge.kt"
        )
        assertTrue("open fun cparticleBlockCollisionRange(): Int" in emitterSource)
        assertTrue("CParticleSystemManager.updateBlockCollisionRange(" in bridgeSource)
        assertTrue("emitter.cparticleBlockCollisionRange()" in bridgeSource)
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
