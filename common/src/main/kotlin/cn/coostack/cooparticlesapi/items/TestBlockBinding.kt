package cn.coostack.cooparticlesapi.items

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData

data class TestBlockBinding(
    val dimension: String,
    val pos: BlockPos,
)

object TestBlockBindings {
    private const val ROOT = "coo_test_block_bindings"
    private const val DIMENSION = "dimension"
    private const val X = "x"
    private const val Y = "y"
    private const val Z = "z"

    fun read(stack: ItemStack): List<TestBlockBinding> {
        val tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        if (!tag.contains(ROOT, Tag.TAG_LIST.toInt())) {
            return emptyList()
        }
        val list = tag.getList(ROOT, Tag.TAG_COMPOUND.toInt())
        val result = ArrayList<TestBlockBinding>(list.size)
        for (index in 0 until list.size) {
            val entry = list.getCompound(index)
            val dimension = entry.getString(DIMENSION)
            if (dimension.isBlank()) continue
            result += TestBlockBinding(
                dimension = dimension,
                pos = BlockPos(entry.getInt(X), entry.getInt(Y), entry.getInt(Z))
            )
        }
        return result.distinct()
    }

    fun add(stack: ItemStack, binding: TestBlockBinding): Boolean {
        val bindings = read(stack).toMutableList()
        if (binding in bindings) {
            return false
        }
        bindings += binding
        write(stack, bindings)
        return true
    }

    fun remove(stack: ItemStack, binding: TestBlockBinding): Boolean {
        val bindings = read(stack).toMutableList()
        val removed = bindings.remove(binding)
        if (removed) {
            write(stack, bindings)
        }
        return removed
    }

    fun contains(stack: ItemStack, binding: TestBlockBinding): Boolean {
        return binding in read(stack)
    }

    fun replaceAll(stack: ItemStack, bindings: List<TestBlockBinding>) {
        write(stack, bindings)
    }

    private fun write(stack: ItemStack, bindings: List<TestBlockBinding>) {
        val root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        if (bindings.isEmpty()) {
            root.remove(ROOT)
        } else {
            val list = ListTag()
            bindings.distinct().forEach { binding ->
                val entry = CompoundTag()
                entry.putString(DIMENSION, binding.dimension)
                entry.putInt(X, binding.pos.x)
                entry.putInt(Y, binding.pos.y)
                entry.putInt(Z, binding.pos.z)
                list.add(entry)
            }
            root.put(ROOT, list)
        }

        if (root.isEmpty) {
            stack.remove(DataComponents.CUSTOM_DATA)
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root))
        }
    }
}
