package cn.coostack.cooparticlesapi.utils

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiPredicate
import java.util.function.Predicate
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BlockUtilTest {
    private val world = blockWorld { null }

    private val allBlocks = nullablePredicate { true }
    private val allBlocksWithDirections = nullableBiPredicate { _, _ -> true }

    @Test
    fun `zero total step returns only center`() {
        val spread = BlockUtil.BlockStepSpareData(0, BlockPos.ZERO, allBlocks)

        assertEquals(setOf(BlockPos.ZERO), spread.step(world))
        assertTrue(spread.step(world).isEmpty())
    }

    @Test
    fun `step returns one propagation depth per call`() {
        val center = BlockPos.ZERO
        val spread = BlockUtil.BlockStepSpareData(2, center, allBlocks, 2)

        assertEquals(setOf(center), spread.step(world))

        val depthOne = spread.step(world)
        assertEquals(6, depthOne.size)
        assertTrue(depthOne.all { distance(it, center) == 1 })

        val depthTwo = spread.step(world)
        assertEquals(18, depthTwo.size)
        assertTrue(depthTwo.all { distance(it, center) == 2 })

        assertTrue(spread.step(world).isEmpty())
    }

    /** 验证主线程入口不会把世界状态读取分派到工作线程。 */
    @Test
    fun `main thread step reads world on caller thread`() {
        val caller = Thread.currentThread()
        val observed = AtomicReference<Thread>()
        val spread = BlockUtil.BlockStepSpareData(
            totalStep = 1,
            center = BlockPos.ZERO,
            condition = nullablePredicate {
                observed.set(Thread.currentThread())
                true
            }
        )

        assertEquals(setOf(BlockPos.ZERO), spread.stepOnMainThread(world))
        assertEquals(6, spread.stepOnMainThread(world).size)
        assertEquals(caller, observed.get())
    }

    @Test
    fun `step stops after a depth has no matching blocks`() {
        val checks = AtomicInteger()
        val spread = BlockUtil.BlockStepSpareData(
            totalStep = 3,
            center = BlockPos.ZERO,
            condition = nullablePredicate {
                checks.incrementAndGet()
                false
            },
            threads = 2
        )

        assertEquals(setOf(BlockPos.ZERO), spread.step(world))
        assertTrue(spread.step(world).isEmpty())
        assertEquals(6, checks.get())
        assertTrue(spread.step(world).isEmpty())
        assertEquals(6, checks.get())
    }

    @Test
    fun `failed step can retry the same depth`() {
        val failOnce = AtomicBoolean(true)
        val spread = BlockUtil.BlockStepSpareData(
            totalStep = 1,
            center = BlockPos.ZERO,
            condition = nullablePredicate {
                if (failOnce.compareAndSet(true, false)) error("test failure")
                true
            },
            threads = 1
        )

        assertEquals(setOf(BlockPos.ZERO), spread.step(world))
        assertFailsWith<IllegalStateException> { spread.step(world) }

        val retriedDepth = spread.step(world)
        assertEquals(6, retriedDepth.size)
        assertTrue(retriedDepth.all { distance(it, BlockPos.ZERO) == 1 })
        assertTrue(spread.step(world).isEmpty())
    }

    @Test
    fun `spread with directions records center and arrival directions`() {
        val center = BlockPos.ZERO

        val result = BlockUtil.spreadBlocksWithDirections(
            depth = 1,
            center = center,
            world = world,
            condition = allBlocksWithDirections
        )

        assertEquals(7, result.size)
        assertEquals(BlockUtil.BlockWithDirection(center, Direction.UP), result.first())
        assertEquals(
            mapOf(
                center.above() to Direction.UP,
                center.below() to Direction.DOWN,
                center.west() to Direction.WEST,
                center.east() to Direction.EAST,
                center.north() to Direction.NORTH,
                center.south() to Direction.SOUTH
            ),
            result.drop(1).associate { it.blockPos to it.direction }
        )
    }

    @Test
    fun `direction condition controls synchronous and asynchronous propagation`() {
        val center = BlockPos.ZERO
        val onlyEast = nullableBiPredicate { _, direction -> direction == Direction.EAST }
        val expected = listOf(
            BlockUtil.BlockWithDirection(center, Direction.UP),
            BlockUtil.BlockWithDirection(center.east(), Direction.EAST),
            BlockUtil.BlockWithDirection(center.east(2), Direction.EAST),
            BlockUtil.BlockWithDirection(center.east(3), Direction.EAST)
        )

        assertEquals(
            expected,
            BlockUtil.spreadBlocksWithDirections(3, center, world, onlyEast)
        )
        assertEquals(
            expected,
            BlockUtil.spreadBlocksWithDirectionsAsync(3, center, world, onlyEast, 2)
        )
    }

    @Test
    fun `all arrival directions are checked before choosing one for a block`() {
        val center = BlockPos.ZERO
        val target = center.east().south()
        val lastReadPosition = ThreadLocal<BlockPos>()
        val branchingWorld = blockWorld { position ->
            lastReadPosition.set(position)
            null
        }
        val targetAcceptsEast = nullableBiPredicate { _, direction ->
            lastReadPosition.get() != target || direction == Direction.EAST
        }

        val synchronous = BlockUtil.spreadBlocksWithDirections(
            2,
            center,
            branchingWorld,
            targetAcceptsEast
        )
        val asynchronous = BlockUtil.spreadBlocksWithDirectionsAsync(
            2,
            center,
            branchingWorld,
            targetAcceptsEast,
            2
        )
        val backgroundStep = BlockUtil.BlockStepWithDirectionsSpareData(
            2,
            center,
            targetAcceptsEast,
            2
        )
        backgroundStep.step(branchingWorld)
        backgroundStep.step(branchingWorld)
        val backgroundDepthTwo = backgroundStep.step(branchingWorld)
        val mainThreadStep = BlockUtil.BlockStepWithDirectionsSpareData(
            2,
            center,
            targetAcceptsEast
        )
        mainThreadStep.stepOnMainThread(branchingWorld)
        mainThreadStep.stepOnMainThread(branchingWorld)
        val mainThreadDepthTwo = mainThreadStep.stepOnMainThread(branchingWorld)

        assertEquals(Direction.EAST, synchronous.single { it.blockPos == target }.direction)
        assertEquals(Direction.EAST, asynchronous.single { it.blockPos == target }.direction)
        assertEquals(Direction.EAST, backgroundDepthTwo.single { it.blockPos == target }.direction)
        assertEquals(Direction.EAST, mainThreadDepthTwo.single { it.blockPos == target }.direction)
    }

    @Test
    fun `step with directions returns one directed depth per call`() {
        val center = BlockPos.ZERO
        val onlyNorth = nullableBiPredicate { _, direction -> direction == Direction.NORTH }
        val spread = BlockUtil.BlockStepWithDirectionsSpareData(
            totalStep = 2,
            center = center,
            condition = onlyNorth,
            threads = 2
        )

        assertEquals(
            setOf(BlockUtil.BlockWithDirection(center, Direction.UP)),
            spread.step(world)
        )
        assertEquals(
            setOf(BlockUtil.BlockWithDirection(center.north(), Direction.NORTH)),
            spread.step(world)
        )
        assertEquals(
            setOf(BlockUtil.BlockWithDirection(center.north(2), Direction.NORTH)),
            spread.step(world)
        )
        assertTrue(spread.step(world).isEmpty())
    }

    /** 验证带方向的主线程入口不会把世界状态读取分派到工作线程。 */
    @Test
    fun `directed main thread step reads world on caller thread`() {
        val caller = Thread.currentThread()
        val observed = AtomicReference<Thread>()
        val spread = BlockUtil.BlockStepWithDirectionsSpareData(
            totalStep = 1,
            center = BlockPos.ZERO,
            condition = nullableBiPredicate { _, direction ->
                observed.set(Thread.currentThread())
                direction == Direction.DOWN
            }
        )

        assertEquals(
            setOf(BlockUtil.BlockWithDirection(BlockPos.ZERO, Direction.UP)),
            spread.stepOnMainThread(world)
        )
        assertEquals(
            setOf(BlockUtil.BlockWithDirection(BlockPos.ZERO.below(), Direction.DOWN)),
            spread.stepOnMainThread(world)
        )
        assertEquals(caller, observed.get())
    }

    private fun distance(first: BlockPos, second: BlockPos): Int {
        return abs(first.x - second.x) + abs(first.y - second.y) + abs(first.z - second.z)
    }

    private fun blockWorld(stateAt: (BlockPos) -> BlockState?): BlockGetter {
        return Proxy.newProxyInstance(
            BlockGetter::class.java.classLoader,
            arrayOf(BlockGetter::class.java)
        ) { _, method, arguments ->
            when (method.name) {
                "getBlockState" -> stateAt(arguments?.first() as BlockPos)
                "getHeight" -> 384
                "getMinBuildHeight" -> -64
                else -> null
            }
        } as BlockGetter
    }

    @Suppress("UNCHECKED_CAST")
    private fun nullablePredicate(test: (BlockState?) -> Boolean): Predicate<BlockState> {
        return Predicate<BlockState?> { test(it) } as Predicate<BlockState>
    }

    @Suppress("UNCHECKED_CAST")
    private fun nullableBiPredicate(
        test: (BlockState?, Direction) -> Boolean
    ): BiPredicate<BlockState, Direction> {
        return BiPredicate<BlockState?, Direction> { state, direction ->
            test(state, direction)
        } as BiPredicate<BlockState, Direction>
    }
}
