package cn.coostack.cooparticlesapi.renderer.pipeline

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.Bootstrap
import net.minecraft.SharedConstants
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.RedstoneLampBlock
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class CooBlockPipelinesTest {
    @BeforeTest
    fun bootstrapMinecraft() {
        SharedConstants.tryDetectVersion()
        Bootstrap.bootStrap()
    }

    @AfterTest
    fun clearBindings() {
        CooBlockPipelines.clearBindings()
    }

    @Test
    fun `exact block binding wins over predicate binding`() {
        val predicatePipeline = blockPipeline("predicate")
        val exactPipeline = blockPipeline("exact")

        CooBlockPipelines.bind({ it.isSolid }, predicatePipeline)
        CooBlockPipelines.bind(Blocks.STONE, exactPipeline)

        assertEquals(exactPipeline.id, CooBlockPipelines.resolve(Blocks.STONE.defaultBlockState()).id)
        assertEquals(predicatePipeline.id, CooBlockPipelines.resolve(Blocks.DIRT.defaultBlockState()).id)
    }

    @Test
    fun `state selector replaces an earlier exact binding`() {
        val first = blockPipeline("first")
        val second = blockPipeline("second")
        CooBlockPipelines.bind(Blocks.REDSTONE_LAMP, first)
        CooBlockPipelines.bind(Blocks.REDSTONE_LAMP) { state ->
            if (state.getValue(RedstoneLampBlock.LIT)) second else first
        }

        val unlit = Blocks.REDSTONE_LAMP.defaultBlockState()
        val lit = unlit.setValue(RedstoneLampBlock.LIT, true)
        assertEquals(first.id, CooBlockPipelines.resolve(unlit).id)
        assertEquals(second.id, CooBlockPipelines.resolve(lit).id)
    }

    @Test
    fun `exact state binding wins over its block binding`() {
        val allStates = blockPipeline("all_states")
        val litOnly = blockPipeline("lit_only")
        val unlit = Blocks.REDSTONE_LAMP.defaultBlockState()
        val lit = unlit.setValue(RedstoneLampBlock.LIT, true)

        CooBlockPipelines.bind(Blocks.REDSTONE_LAMP, allStates)
        CooBlockPipelines.bind(lit, litOnly)

        assertEquals(allStates.id, CooBlockPipelines.resolve(unlit).id)
        assertEquals(litOnly.id, CooBlockPipelines.resolve(lit).id)
    }

    @Test
    fun `binding changes advance revision`() {
        val before = CooBlockPipelines.revision
        CooBlockPipelines.bind(Blocks.STONE, blockPipeline("revision"))
        assertNotEquals(before, CooBlockPipelines.revision)
    }

    @Test
    fun `scoped bindings restore previous rules and do not remove another owner`() {
        val permanent = blockPipeline("permanent")
        val first = blockPipeline("first_scoped")
        val second = blockPipeline("second_scoped")
        val firstOwner = UUID.randomUUID()
        val secondOwner = UUID.randomUUID()

        CooBlockPipelines.bind(Blocks.STONE, permanent)
        CooBlockPipelines.bindScoped(firstOwner, Blocks.STONE, first)
        CooBlockPipelines.bindScoped(secondOwner, Blocks.STONE, second)

        assertSame(second, CooBlockPipelines.resolve(Blocks.STONE.defaultBlockState()))

        CooBlockPipelines.unbindScoped(firstOwner)
        assertSame(second, CooBlockPipelines.resolve(Blocks.STONE.defaultBlockState()))

        CooBlockPipelines.unbindScoped(secondOwner)
        assertSame(permanent, CooBlockPipelines.resolve(Blocks.STONE.defaultBlockState()))
    }

    @Test
    fun `scoped block binding covers every state before restoring exact state rules`() {
        val exactState = blockPipeline("exact_state")
        val scoped = blockPipeline("scoped_all_states")
        val owner = UUID.randomUUID()
        val unlit = Blocks.REDSTONE_LAMP.defaultBlockState()
        val lit = unlit.setValue(RedstoneLampBlock.LIT, true)

        CooBlockPipelines.bind(lit, exactState)
        CooBlockPipelines.bindScoped(owner, Blocks.REDSTONE_LAMP, scoped)

        assertSame(scoped, CooBlockPipelines.resolve(unlit))
        assertSame(scoped, CooBlockPipelines.resolve(lit))

        CooBlockPipelines.unbindScoped(owner)
        assertSame(CooPipelines.BLOCK_DEFAULT, CooBlockPipelines.resolve(unlit))
        assertSame(exactState, CooBlockPipelines.resolve(lit))
    }

    @Test
    fun `permanent block updates preserve an active scoped binding`() {
        val scoped = blockPipeline("scoped")
        val fixed = blockPipeline("fixed")
        val batch = blockPipeline("batch")
        val selected = blockPipeline("selected")
        val owner = UUID.randomUUID()

        CooBlockPipelines.bindScoped(owner, Blocks.STONE, scoped)
        CooBlockPipelines.bind(Blocks.STONE, fixed)
        CooBlockPipelines.bindBlocks(listOf(Blocks.STONE), batch)
        CooBlockPipelines.bind(Blocks.STONE) { selected }

        assertSame(scoped, CooBlockPipelines.resolve(Blocks.STONE.defaultBlockState()))

        CooBlockPipelines.unbindScoped(owner)
        assertSame(selected, CooBlockPipelines.resolve(Blocks.STONE.defaultBlockState()))
    }

    @Test
    fun `transient cleanup removes scoped bindings but keeps permanent rules`() {
        val permanent = blockPipeline("permanent_after_cleanup")
        val exactState = blockPipeline("exact_state_after_cleanup")
        val predicate = blockPipeline("predicate_after_cleanup")
        val firstScoped = blockPipeline("first_scoped_before_cleanup")
        val secondScoped = blockPipeline("second_scoped_before_cleanup")
        val lit = Blocks.REDSTONE_LAMP.defaultBlockState().setValue(RedstoneLampBlock.LIT, true)

        CooBlockPipelines.bind(Blocks.STONE, permanent)
        CooBlockPipelines.bind(lit, exactState)
        CooBlockPipelines.bind({ state -> state.block === Blocks.DIRT }, predicate)
        CooBlockPipelines.bindScoped(UUID.randomUUID(), Blocks.STONE, firstScoped)
        CooBlockPipelines.bindScoped(UUID.randomUUID(), Blocks.REDSTONE_LAMP, secondScoped)

        var notifications = 0
        val listener: (Long) -> Unit = { notifications++ }
        val revisionBeforeCleanup = CooBlockPipelines.revision
        CooBlockPipelines.addChangeListener(listener)
        try {
            CooBlockPipelines.clearScopedBindings()
        } finally {
            CooBlockPipelines.removeChangeListener(listener)
        }

        assertEquals(revisionBeforeCleanup + 1L, CooBlockPipelines.revision)
        assertEquals(1, notifications)
        assertSame(permanent, CooBlockPipelines.resolve(Blocks.STONE.defaultBlockState()))
        assertSame(exactState, CooBlockPipelines.resolve(lit))
        assertSame(predicate, CooBlockPipelines.resolve(Blocks.DIRT.defaultBlockState()))
    }

    @Test
    fun `batch block binding publishes one revision`() {
        val pipeline = blockPipeline("batch_blocks")
        val before = CooBlockPipelines.revision

        CooBlockPipelines.bindBlocks(listOf(Blocks.STONE, Blocks.DIRT), pipeline)

        assertEquals(before + 1L, CooBlockPipelines.revision)
        assertSame(pipeline, CooBlockPipelines.resolve(Blocks.STONE.defaultBlockState()))
        assertSame(pipeline, CooBlockPipelines.resolve(Blocks.DIRT.defaultBlockState()))
    }

    @Test
    fun `batch state binding leaves other states unchanged`() {
        val pipeline = blockPipeline("batch_states")
        val unlit = Blocks.REDSTONE_LAMP.defaultBlockState()
        val lit = unlit.setValue(RedstoneLampBlock.LIT, true)

        CooBlockPipelines.bindStates(listOf(lit), pipeline)

        assertSame(pipeline, CooBlockPipelines.resolve(lit))
        assertSame(CooPipelines.BLOCK_DEFAULT, CooBlockPipelines.resolve(unlit))
    }

    @Test
    fun `exact binding does not affect other blocks`() {
        CooBlockPipelines.bind(Blocks.LODESTONE, blockPipeline("local_only"))

        assertSame(CooPipelines.BLOCK_DEFAULT, CooBlockPipelines.resolve(Blocks.STONE.defaultBlockState()))
        assertSame(CooPipelines.BLOCK_DEFAULT, CooBlockPipelines.resolve(Blocks.DIRT.defaultBlockState()))
    }

    private fun blockPipeline(path: String): CooRenderPipeline<BlockState> {
        return CooPipelines.block(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", path)) {
            shader(ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "terrain/$path"))
        }
    }
}
