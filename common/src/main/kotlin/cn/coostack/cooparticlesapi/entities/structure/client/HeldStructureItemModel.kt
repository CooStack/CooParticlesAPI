package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.CooParticlesConstants
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.special.SpecialModelRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.resources.model.cuboid.ItemTransform
import net.minecraft.client.resources.model.cuboid.ItemTransforms
import net.minecraft.client.resources.model.cuboid.CuboidModel
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.core.component.DataComponents
import org.joml.Vector3f
import org.joml.Vector3fc
import java.util.function.Consumer

/**
 * 把携带结构的物品抽取为原版特殊模型层，替代已经移除的即时 ItemRenderer 入口。
 * 原版层只应用一次 [ItemTransform]；实际结构仍复用已有归一化与 collector 提交。
 */
object HeldStructureItemModel : SpecialModelRenderer<ItemStack> {
    /** 仅属于当前资源周期的物品展示变换。 */
    private var transforms: ItemTransforms? = null

    /**
     * 抽取独立物品快照并创建一个特殊模型层，不推进实体或世界状态。
     * 示例：`append(output, stack, context)`。
     * @param output 原版物品抽取状态
     * @param stack 携带结构载荷的原物品
     * @param context 原版物品展示上下文
     */
    fun append(output: ItemStackRenderState, stack: ItemStack, context: ItemDisplayContext) {
        val layer = output.newLayer()
        layer.setItemTransform(transforms().getTransform(context))
        layer.setUsesBlockLight(false)
        layer.setExtents {
            arrayOf(
                Vector3f(0F, 0F, 0F), Vector3f(1F, 0F, 0F), Vector3f(0F, 1F, 0F), Vector3f(0F, 0F, 1F),
                Vector3f(1F, 1F, 0F), Vector3f(1F, 0F, 1F), Vector3f(0F, 1F, 1F), Vector3f(1F, 1F, 1F),
            )
        }
        layer.setupSpecialModel(this, extractArgument(stack))
        output.appendModelIdentityElement(stack.get(DataComponents.CUSTOM_DATA) ?: stack.item)
        output.setAnimated()
    }

    /** 保存单件独立快照，延迟提交时不读取已经被消费或交换的原背包栈。 */
    override fun extractArgument(stack: ItemStack): ItemStack = stack.copyWithCount(1)

    /** 特殊模型已经归一化到物品单位空间，向原版公开保守包围范围。 */
    override fun getExtents(output: Consumer<Vector3fc>) {
        output.accept(Vector3f(0F, 0F, 0F))
        output.accept(Vector3f(1F, 1F, 1F))
    }

    /** 原版已经在层上应用展示变换；这里只移除渲染器内部的单位方块中心偏移。 */
    override fun submit(
        stack: ItemStack?,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        light: Int,
        overlay: Int,
        hasFoil: Boolean,
        outlineColor: Int,
    ) {
        if (stack == null) return
        val client = Minecraft.getInstance()
        if (client.level == null) return
        val cameraState = CameraRenderState()
        client.gameRenderer.mainCamera().extractRenderState(cameraState, client.deltaTracker)
        poseStack.pushPose()
        try {
            poseStack.translate(-0.5F, -0.5F, -0.5F)
            StructureModelClient.itemRenderer.submit(
                stack, poseStack, collector, cameraState, cameraState.cameraEntityPartialTicks,
            )
        } finally {
            poseStack.popPose()
        }
    }

    /** 资源重载、断线或更换世界后重新读取展示变换，不保留旧资源管理器。 */
    fun clearTransforms() {
        transforms = null
    }

    /** 只解析 display 对象，不要求旧 builtin/entity 父模型仍能被 26.3 烘焙。 */
    private fun transforms(): ItemTransforms = transforms ?: run {
        val id = Identifier.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID, "models/item/held_structure_model.json",
        )
        Minecraft.getInstance().resourceManager.getResourceOrThrow(id).openAsReader().use { reader ->
            CuboidModel.fromStream(reader).transforms() ?: ItemTransforms.NO_TRANSFORMS
        }.also { transforms = it }
    }
}
