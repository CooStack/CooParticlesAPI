package cn.coostack.cooparticlesapi.utils

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.state.BlockState
import java.util.Stack
import java.util.function.BiPredicate
import java.util.function.Predicate

/**
 * 用一种传播的算法设置方块的传播
 */
object BlockUtil {

    /**
     * 带有传播方向的方块位置。
     *
     * [direction] 表示传播从上一个方块到达 [blockPos] 时的方向，传播中心固定为 [Direction.UP]。
     * 同一位置有多个可用的入射方向时，传播 API 保留第一个通过条件的方向。
     *
     * @property blockPos 当前方块位置
     * @property direction 当前方块的传播方向
     */
    data class BlockWithDirection(
        val blockPos: BlockPos,
        val direction: Direction
    )

    /**
     * 分步计算方块传播，每次调用 [step] 只返回一个深度的结果。
     * [center] 是深度 0，达到 [totalStep] 后停止传播。
     * 返回值不包含之前深度的方块。
     * [step] 会阻塞当前线程，并在工作线程中并发读取 [BlockGetter] 和 [condition]。
    * 调用方需要保证两者可以安全地并发访问，同一个实例的 [step] 也需要串行调用。
     *
     * @property totalStep 最大传播深度
     * @property center 传播中心
     * @property condition 判断方块是否可传播的条件
     * @property threads 每层使用的工作线程数
     */
    class BlockStepSpareData @JvmOverloads constructor(
        val totalStep: Int,
        val center: BlockPos,
        val condition: Predicate<BlockState>,
        val threads: Int = 4
    ) {
        private var nextDepth = 0
        private var frontier: Set<BlockPos> = setOf(center)
        private val visited = mutableSetOf(center)

        /**
         * 在后台工作线程并发读取世界并推进一层传播。
         *
         * @param world 允许并发读取的方块世界
         * @return 本次新发现的一层方块
         */
        fun step(world: BlockGetter): Set<BlockPos> {
            if (totalStep < 0 || nextDepth > totalStep || frontier.isEmpty()) return emptySet()

            if (nextDepth == 0) {
                nextDepth++
                return frontier.toSet()
            }

            val candidates = collectUnvisitedNeighbors(frontier, visited)
            if (candidates.isEmpty()) {
                frontier = emptySet()
                return emptySet()
            }

            val nextFrontier = filterCandidatesAsync(candidates, world, condition, threads).toSet()
            visited += candidates
            frontier = nextFrontier
            nextDepth++
            return frontier.toSet()
        }

        /**
         * 在当前线程读取世界并推进一层传播。
         *
         * 该入口用于 Minecraft 主线程。世界区块和方块状态只能在主线程读取，不能把
         * [world] 或 [condition] 传给工作线程；需要后台计算时才使用 [step]。
         *
         * @param world 当前线程可安全读取的方块世界
         * @return 本次新发现的一层方块；没有可传播方块时返回空集合
         */
        fun stepOnMainThread(world: BlockGetter): Set<BlockPos> {
            if (totalStep < 0 || nextDepth > totalStep || frontier.isEmpty()) return emptySet()

            if (nextDepth == 0) {
                nextDepth++
                return frontier.toSet()
            }

            val candidates = collectUnvisitedNeighbors(frontier, visited)
            if (candidates.isEmpty()) {
                frontier = emptySet()
                return emptySet()
            }

            val nextFrontier = candidates
                .asSequence()
                .filter { condition.test(world.getBlockState(it)) }
                .toSet()
            visited += candidates
            frontier = nextFrontier
            nextDepth++
            return frontier.toSet()
        }
    }

    /**
     * 分步计算带方向的方块传播，每次调用 [step] 只返回一个深度的结果。
     * [center] 是深度 0，其方向固定为 [Direction.UP]，达到 [totalStep] 后停止传播。
     * 返回值不包含之前深度的方块。
     * [step] 会阻塞当前线程，并在工作线程中并发读取 [BlockGetter] 和 [condition]。
     * 调用方需要保证两者可以安全地并发访问，同一个实例的 [step] 也需要串行调用。
     *
     * @property totalStep 最大传播深度
     * @property center 传播中心
     * @property condition 根据方块状态和传播方向判断方块是否可传播
     * @property threads 每层使用的工作线程数
     */
    class BlockStepWithDirectionsSpareData @JvmOverloads constructor(
        val totalStep: Int,
        val center: BlockPos,
        val condition: BiPredicate<BlockState, Direction>,
        val threads: Int = 4
    ) {
        private var nextDepth = 0
        private var frontier: Set<BlockWithDirection> =
            setOf(BlockWithDirection(center, Direction.UP))
        private val visited = mutableSetOf(center)

        /**
         * 在后台工作线程并发读取世界并推进一层传播。
         *
         * @param world 允许并发读取的方块世界
         * @return 本次新发现的一层方块及其传播方向
         */
        fun step(world: BlockGetter): Set<BlockWithDirection> {
            if (totalStep < 0 || nextDepth > totalStep || frontier.isEmpty()) return emptySet()

            if (nextDepth == 0) {
                nextDepth++
                return frontier.toSet()
            }

            val candidates = collectUnvisitedNeighborsWithDirections(frontier, visited)
            if (candidates.isEmpty()) {
                frontier = emptySet()
                return emptySet()
            }

            val nextFrontier = filterCandidatesWithDirectionsAsync(
                candidates,
                world,
                condition,
                threads
            ).distinctBy(BlockWithDirection::blockPos).toSet()
            visited += nextFrontier.map(BlockWithDirection::blockPos)
            frontier = nextFrontier
            nextDepth++
            return frontier.toSet()
        }

        /**
         * 在当前线程读取世界并推进一层传播。
         *
         * 该入口用于 Minecraft 主线程。世界区块和方块状态只能在主线程读取，不能把
         * [world] 或 [condition] 传给工作线程；需要后台计算时才使用 [step]。
         *
         * @param world 当前线程可安全读取的方块世界
         * @return 本次新发现的一层方块及其传播方向；没有可传播方块时返回空集合
         */
        fun stepOnMainThread(world: BlockGetter): Set<BlockWithDirection> {
            if (totalStep < 0 || nextDepth > totalStep || frontier.isEmpty()) return emptySet()

            if (nextDepth == 0) {
                nextDepth++
                return frontier.toSet()
            }

            val candidates = collectUnvisitedNeighborsWithDirections(frontier, visited)
            if (candidates.isEmpty()) {
                frontier = emptySet()
                return emptySet()
            }

            val nextFrontier = candidates
                .asSequence()
                .filter {
                    condition.test(world.getBlockState(it.blockPos), it.direction)
                }
                .distinctBy(BlockWithDirection::blockPos)
                .toSet()
            visited += nextFrontier.map(BlockWithDirection::blockPos)
            frontier = nextFrontier
            nextDepth++
            return frontier.toSet()
        }
    }

    /**
     * 获取方块传播的结果
     *
     * @param depth 传播深度
     * @param center 传播中心
     * @param world 传播世界
     * @param condition 可以传播的条件
     * @return 符合要求的方块集合
     */
    fun spreadBlocks(
        depth: Int,
        center: BlockPos,
        world: BlockGetter,
        condition: Predicate<BlockState>
    ): List<BlockPos> {
        if (depth <= 0) return emptyList()
        val res = arrayListOf<BlockPos>()
        val visited = mutableSetOf(center)
        // 利用栈的方案
        val stack = Stack<StackDepthNode<BlockPos>>()

        stack.push(StackDepthNode(center, 0))

        val blockTemps = Array<BlockPos>(6) { BlockPos.ZERO }

        while (stack.isNotEmpty()) {
            // 入栈周围的内容
            val node = stack.pop()
            val data = node.data
            res += data
            // 这里代表最后一层， 加入到结果集之后直接过滤
            if (node.depth >= depth) continue
            // 根据next找到周围的上下左右前后6个方块
            blockTemps[0] = data.above()
            blockTemps[1] = data.below()
            blockTemps[2] = data.west()
            blockTemps[3] = data.east()
            blockTemps[4] = data.north()
            blockTemps[5] = data.south()

            blockTemps.forEach {
                if (visited.add(it) && condition.test(world.getBlockState(it))) {
                    stack.push(StackDepthNode(it, node.depth + 1))
                }
            }
        }

        return res
    }

    /**
     * 获取带传播方向的方块传播结果。
     *
     * 传播中心的方向固定为 [Direction.UP]，其余方块的方向表示传播从上一个方块到达
     * 当前位置时的方向。
     * 返回结果按传播层排列。
     *
     * @param depth 传播深度
     * @param center 传播中心
     * @param world 传播世界
     * @param condition 根据方块状态和传播方向判断方块是否可传播
     * @return 符合要求的方块位置及其传播方向
     */
    fun spreadBlocksWithDirections(
        depth: Int,
        center: BlockPos,
        world: BlockGetter,
        condition: BiPredicate<BlockState, Direction>
    ): List<BlockWithDirection> {
        if (depth <= 0) return emptyList()

        val centerBlock = BlockWithDirection(center, Direction.UP)
        val result = arrayListOf(centerBlock)
        val visited = mutableSetOf(center)
        var frontier: List<BlockWithDirection> = listOf(centerBlock)

        repeat(depth) {
            if (frontier.isEmpty()) return result

            val candidates = collectUnvisitedNeighborsWithDirections(frontier, visited)
            if (candidates.isEmpty()) return result

            frontier = candidates
                .asSequence()
                .filter {
                    condition.test(world.getBlockState(it.blockPos), it.direction)
                }
                .distinctBy(BlockWithDirection::blockPos)
                .toList()
            visited += frontier.map(BlockWithDirection::blockPos)
            result += frontier
        }

        return result
    }

    /**
     * 并发计算方块传播结果。方法会等待协程任务结束，调用方不需要处在协程中。
     * 方法会阻塞当前线程，不要在 Minecraft 主线程或渲染线程调用。
     * [world] 和 [condition] 会在工作线程中并发读取，调用方需要保证它们可以安全地并发访问。
     * 返回结果按传播层排列，顺序与同步版本的栈遍历不同。
     *
     * @param depth 传播深度
     * @param center 传播中心
     * @param world 传播世界
     * @param condition 可以传播的条件
     * @param threads 每层最多创建的任务数
     * @return 符合要求的方块集合
     */
    fun spreadBlocksAsync(
        depth: Int,
        center: BlockPos,
        world: BlockGetter,
        condition: Predicate<BlockState>,
        threads: Int
    ): List<BlockPos> {
        if (depth <= 0) return emptyList()

        val result = arrayListOf(center)
        val visited = mutableSetOf(center)
        var frontier: List<BlockPos> = listOf(center)

        repeat(depth) {
            if (frontier.isEmpty()) return result

            val candidates = collectUnvisitedNeighbors(frontier, visited)
            if (candidates.isEmpty()) return result

            frontier = filterCandidatesAsync(candidates, world, condition, threads)
            visited += candidates
            result += frontier
        }

        return result
    }

    /**
     * 并发计算带传播方向的方块传播结果。方法会等待协程任务结束，调用方不需要处在协程中。
     * 方法会阻塞当前线程，不要在 Minecraft 主线程或渲染线程调用。
     * [world] 和 [condition] 会在工作线程中并发读取，调用方需要保证它们可以安全地并发访问。
     * 返回结果按传播层排列，与同步带方向版本使用相同的候选顺序。
     *
     * @param depth 传播深度
     * @param center 传播中心，返回结果中的方向固定为 [Direction.UP]
     * @param world 传播世界
     * @param condition 根据方块状态和传播方向判断方块是否可传播
     * @param threads 每层最多创建的任务数
     * @return 符合要求的方块位置及其传播方向
     */
    fun spreadBlocksWithDirectionsAsync(
        depth: Int,
        center: BlockPos,
        world: BlockGetter,
        condition: BiPredicate<BlockState, Direction>,
        threads: Int
    ): List<BlockWithDirection> {
        if (depth <= 0) return emptyList()

        val centerBlock = BlockWithDirection(center, Direction.UP)
        val result = arrayListOf(centerBlock)
        val visited = mutableSetOf(center)
        var frontier: List<BlockWithDirection> = listOf(centerBlock)

        repeat(depth) {
            if (frontier.isEmpty()) return result

            val candidates = collectUnvisitedNeighborsWithDirections(frontier, visited)
            if (candidates.isEmpty()) return result

            frontier = filterCandidatesWithDirectionsAsync(candidates, world, condition, threads)
                .distinctBy(BlockWithDirection::blockPos)
            visited += frontier.map(BlockWithDirection::blockPos)
            result += frontier
        }

        return result
    }

    private fun collectUnvisitedNeighbors(
        frontier: Collection<BlockPos>,
        visited: MutableSet<BlockPos>
    ): List<BlockPos> {
        val candidates = arrayListOf<BlockPos>()
        val discovered = mutableSetOf<BlockPos>()
        val blockTemps = Array(6) { BlockPos.ZERO }

        for (current in frontier) {
            blockTemps[0] = current.above()
            blockTemps[1] = current.below()
            blockTemps[2] = current.west()
            blockTemps[3] = current.east()
            blockTemps[4] = current.north()
            blockTemps[5] = current.south()

            for (blockPos in blockTemps) {
                if (blockPos !in visited && discovered.add(blockPos)) {
                    candidates += blockPos
                }
            }
        }

        return candidates
    }

    private fun collectUnvisitedNeighborsWithDirections(
        frontier: Collection<BlockWithDirection>,
        visited: Set<BlockPos>
    ): List<BlockWithDirection> {
        val candidates = arrayListOf<BlockWithDirection>()

        for (current in frontier) {
            for (candidate in neighborsWithDirections(current.blockPos)) {
                if (candidate.blockPos !in visited) {
                    candidates += candidate
                }
            }
        }

        return candidates
    }

    private fun neighborsWithDirections(blockPos: BlockPos): Array<BlockWithDirection> {
        return arrayOf(
            BlockWithDirection(blockPos.above(), Direction.UP),
            BlockWithDirection(blockPos.below(), Direction.DOWN),
            BlockWithDirection(blockPos.west(), Direction.WEST),
            BlockWithDirection(blockPos.east(), Direction.EAST),
            BlockWithDirection(blockPos.north(), Direction.NORTH),
            BlockWithDirection(blockPos.south(), Direction.SOUTH)
        )
    }

    private fun filterCandidatesAsync(
        candidates: List<BlockPos>,
        world: BlockGetter,
        condition: Predicate<BlockState>,
        threads: Int
    ): List<BlockPos> {
        if (candidates.isEmpty()) return emptyList()

        val actualThreads = threads.coerceAtLeast(1).coerceAtMost(candidates.size)
        val dispatcher = Dispatchers.Default.limitedParallelism(actualThreads)
        val taskPerThreadCount = candidates.size / actualThreads
        var notHandledTaskCount = candidates.size % actualThreads
        var currentIndex = 0

        return runBlocking {
            val tasks = ArrayList<Deferred<List<BlockPos>>>(actualThreads)
            repeat(actualThreads) {
                var nextIndex = currentIndex + taskPerThreadCount
                if (notHandledTaskCount > 0) {
                    nextIndex++
                    notHandledTaskCount--
                }
                val startIndex = currentIndex
                currentIndex = nextIndex

                tasks += async(dispatcher) {
                    val matchedBlocks = arrayListOf<BlockPos>()
                    for (index in startIndex..<nextIndex) {
                        val blockPos = candidates[index]
                        if (condition.test(world.getBlockState(blockPos))) {
                            matchedBlocks += blockPos
                        }
                    }
                    matchedBlocks
                }
            }

            val result = arrayListOf<BlockPos>()
            for (taskResult in tasks.awaitAll()) {
                result += taskResult
            }
            result
        }
    }

    private fun filterCandidatesWithDirectionsAsync(
        candidates: List<BlockWithDirection>,
        world: BlockGetter,
        condition: BiPredicate<BlockState, Direction>,
        threads: Int
    ): List<BlockWithDirection> {
        if (candidates.isEmpty()) return emptyList()

        val actualThreads = threads.coerceAtLeast(1).coerceAtMost(candidates.size)
        val dispatcher = Dispatchers.Default.limitedParallelism(actualThreads)
        val taskPerThreadCount = candidates.size / actualThreads
        var notHandledTaskCount = candidates.size % actualThreads
        var currentIndex = 0

        return runBlocking {
            val tasks = ArrayList<Deferred<List<BlockWithDirection>>>(actualThreads)
            repeat(actualThreads) {
                var nextIndex = currentIndex + taskPerThreadCount
                if (notHandledTaskCount > 0) {
                    nextIndex++
                    notHandledTaskCount--
                }
                val startIndex = currentIndex
                currentIndex = nextIndex

                tasks += async(dispatcher) {
                    val matchedBlocks = arrayListOf<BlockWithDirection>()
                    for (index in startIndex..<nextIndex) {
                        val candidate = candidates[index]
                        if (
                            condition.test(
                                world.getBlockState(candidate.blockPos),
                                candidate.direction
                            )
                        ) {
                            matchedBlocks += candidate
                        }
                    }
                    matchedBlocks
                }
            }

            val result = arrayListOf<BlockWithDirection>()
            for (taskResult in tasks.awaitAll()) {
                result += taskResult
            }
            result
        }
    }

    private data class StackDepthNode<T>(val data: T, val depth: Int)


}
