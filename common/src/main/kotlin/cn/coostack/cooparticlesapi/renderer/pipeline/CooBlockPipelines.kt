package cn.coostack.cooparticlesapi.renderer.pipeline

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

/**
 * 真实世界方块的 pipeline 绑定入口。
 *
 * 临时 [Block] 绑定优先于精确 [BlockState] 绑定，精确状态绑定优先于永久 [Block] 绑定，
 * 永久方块绑定优先于全局 predicate；同一优先级内后绑定的规则优先。
 * 绑定只保存不可变快照，区块编译线程可以无锁读取。
 */
object CooBlockPipelines {
    /**
     * @property block 要匹配的方块类型
     * @property owner 临时绑定所有者；永久规则为 `null`
     * @property selector 按方块状态选择 Pipeline 的逻辑
     */
    private data class BlockBinding(
        val block: Block,
        val owner: UUID?,
        val selector: (BlockState) -> CooRenderPipeline<BlockState>
    )

    private data class StateBinding(
        val state: BlockState,
        val pipeline: CooRenderPipeline<BlockState>
    )

    private data class PredicateBinding(
        val predicate: (BlockState) -> Boolean,
        val pipeline: CooRenderPipeline<BlockState>
    )

    private data class BindingSnapshot(
        val states: List<StateBinding> = emptyList(),
        val blocks: List<BlockBinding> = emptyList(),
        val predicates: List<PredicateBinding> = emptyList()
    )

    private val snapshot = AtomicReference(BindingSnapshot())
    private val revisionCounter = AtomicLong()
    private val changeListeners = CopyOnWriteArrayList<(Long) -> Unit>()

    /** 当前绑定修订号，客户端可据此判断 section 数据是否需要重建。 */
    @JvmStatic
    val revision: Long
        get() = revisionCounter.get()

    /** 把任意原版或模组方块绑定到固定 pipeline。 */
    @JvmStatic
    fun bind(block: Block, pipeline: CooRenderPipeline<BlockState>) {
        update { current ->
            current.copy(
                blocks = current.blocks.filterNot { it.owner == null && it.block === block } +
                    BlockBinding(block, null) { pipeline }
            )
        }
    }

    /** 批量绑定多个方块类型，只发布一次绑定快照和修订号。 */
    @JvmStatic
    fun bindBlocks(blocks: Iterable<Block>, pipeline: CooRenderPipeline<BlockState>) {
        val distinctBlocks = ArrayList<Block>()
        blocks.forEach { block ->
            if (distinctBlocks.none { it === block }) distinctBlocks += block
        }
        update { current ->
            current.copy(
                blocks = current.blocks.filterNot { binding ->
                    binding.owner == null && distinctBlocks.any { block -> binding.block === block }
                } + distinctBlocks.map { block -> BlockBinding(block, null) { pipeline } }
            )
        }
    }

    /** 添加带所有者的临时方块类型绑定，不覆盖已有永久规则或其他临时规则。 */
    internal fun bindScoped(owner: UUID, block: Block, pipeline: CooRenderPipeline<BlockState>) {
        update { current ->
            current.copy(
                blocks = current.blocks.filterNot { binding -> binding.owner == owner } +
                    BlockBinding(block, owner) { pipeline }
            )
        }
    }

    /** 撤销指定所有者的临时绑定，先前规则会重新参与匹配。 */
    internal fun unbindScoped(owner: UUID) {
        update { current ->
            current.copy(blocks = current.blocks.filterNot { binding -> binding.owner == owner })
        }
    }

    /** 把一个精确的 BlockState 绑定到固定 pipeline，其他状态不受影响。 */
    @JvmStatic
    fun bind(state: BlockState, pipeline: CooRenderPipeline<BlockState>) {
        update { current ->
            current.copy(
                states = current.states.filterNot { it.state == state } + StateBinding(state, pipeline)
            )
        }
    }

    /** 批量绑定多个精确方块状态，只发布一次绑定快照和修订号。 */
    @JvmStatic
    fun bindStates(states: Iterable<BlockState>, pipeline: CooRenderPipeline<BlockState>) {
        val distinctStates = states.distinct()
        update { current ->
            current.copy(
                states = current.states.filterNot { it.state in distinctStates } +
                    distinctStates.map { state -> StateBinding(state, pipeline) }
            )
        }
    }

    /** 按方块状态选择 pipeline。 */
    @JvmStatic
    fun bind(block: Block, selector: (BlockState) -> CooRenderPipeline<BlockState>) {
        update { current ->
            current.copy(
                blocks = current.blocks.filterNot { it.owner == null && it.block === block } +
                    BlockBinding(block, null, selector)
            )
        }
    }

    /**
     * 为任意 BlockState predicate 绑定 pipeline。
     *
     * predicate 绑定用于跨多个方块的规则；精确 Block 绑定仍会覆盖它。
     */
    @JvmStatic
    fun bind(predicate: (BlockState) -> Boolean, pipeline: CooRenderPipeline<BlockState>) {
        update { current ->
            current.copy(predicates = current.predicates + PredicateBinding(predicate, pipeline))
        }
    }

    internal fun resolve(state: BlockState): CooRenderPipeline<BlockState> {
        val current = snapshot.get()
        current.blocks.asReversed().firstOrNull { it.owner != null && it.block === state.block }?.let {
            return it.selector(state)
        }
        current.states.asReversed().firstOrNull { it.state == state }?.let {
            return it.pipeline
        }
        current.blocks.asReversed().firstOrNull { it.owner == null && it.block === state.block }?.let {
            return it.selector(state)
        }
        current.predicates.asReversed().firstOrNull { it.predicate(state) }?.let {
            return it.pipeline
        }
        return CooPipelines.BLOCK_DEFAULT
    }

    internal fun addChangeListener(listener: (Long) -> Unit) {
        changeListeners += listener
    }

    internal fun removeChangeListener(listener: (Long) -> Unit) {
        changeListeners -= listener
    }

    internal fun clearBindings() {
        update { BindingSnapshot() }
    }

    /** 清除世界切换或断线时遗留的临时绑定，并保留公开 API 注册的永久规则。 */
    internal fun clearScopedBindings() {
        update { current ->
            current.copy(blocks = current.blocks.filter { binding -> binding.owner == null })
        }
    }

    private fun update(transform: (BindingSnapshot) -> BindingSnapshot) {
        while (true) {
            val current = snapshot.get()
            val updated = transform(current)
            if (updated == current) return
            if (snapshot.compareAndSet(current, updated)) {
                val revision = revisionCounter.incrementAndGet()
                changeListeners.forEach { it(revision) }
                return
            }
        }
    }
}
