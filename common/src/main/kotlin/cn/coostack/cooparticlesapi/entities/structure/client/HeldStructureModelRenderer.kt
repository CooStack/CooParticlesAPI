package cn.coostack.cooparticlesapi.entities.structure.client

import cn.coostack.cooparticlesapi.entities.structure.ModelItemData
import cn.coostack.cooparticlesapi.entities.structure.StructureModelEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemDisplayContext
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.ItemStack
import com.mojang.math.Axis
import net.minecraft.world.phys.Vec3

/** 物品直接绘制保存的结构，缩放仅用于展示；复用已有方块、流体和方块实体渲染路径。 */
class HeldStructureModelRenderer {
    /** 有界展示缓存，不将实体加入世界；空值缓存无效数据，避免每帧重复解码。 */
    private val displays = linkedMapOf<CustomData, StructureModelEntity?>()
    /** 展示实体只属于一个客户端世界，切换世界或断线时清空。 */
    private var cachedWorld: ClientLevel? = null

    /** 断线或更换世界时释放缓存，防止展示实体保留已经退出的世界。 */
    fun clear() {
        displays.clear()
        cachedWorld = null
    }

    fun render(
        stack: ItemStack, mode: ItemDisplayContext, matrices: PoseStack,
        consumers: MultiBufferSource, light: Int, overlay: Int
    ) {
        val client = Minecraft.getInstance()
        val world = client.level ?: run { clear(); return }
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return
        if (cachedWorld !== world) {
            clear()
            cachedWorld = world
        }
        if (!displays.containsKey(data)) {
            if (displays.size >= 8) displays.remove(displays.keys.first())
            displays[data] = runCatching {
                ModelItemData.fromNbt(data.copyTag(), world.registryAccess()).createEntity(world, Vec3.ZERO)
            }.getOrNull()
        }
        val model = displays[data] ?: return
        val bounds = model.boundingBoxForCulling
        // 外包框对角线归一化，使任意旋转和非均匀缩放后的结构都能放进物品展示空间。
        val diagonal = Vec3(bounds.xsize, bounds.ysize, bounds.zsize).length().coerceAtLeast(0.01)
        val scale = (1.17 / diagonal).toFloat()
        val center = bounds.center.subtract(model.position())
        matrices.pushPose()
        try {
            matrices.translate(0.5, 0.5, 0.5)
            matrices.mulPose(Axis.XP.rotationDegrees(20F))
            matrices.mulPose(Axis.YP.rotationDegrees(35F))
            matrices.scale(scale, scale, scale)
            matrices.translate(-center.x, -center.y, -center.z)
            client.entityRenderDispatcher.getRenderer(model).render(model, 0F, 0F, matrices, consumers, light)
        } finally {
            matrices.popPose()
        }
    }
}
