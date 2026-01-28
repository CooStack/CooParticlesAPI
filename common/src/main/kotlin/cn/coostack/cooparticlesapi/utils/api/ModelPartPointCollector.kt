package cn.coostack.cooparticlesapi.utils.api

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.world.phys.Vec3

interface ModelPartPointCollector {

    /**
     * @param root 你的实体模型 root ModelPart
     * @param poseStack 渲染时 PoseStack（已经含实体的平移旋转缩放）
     * @param density 每个三角形采样密度：4~16 常用；越大点越多
     * @param pixelToUnit 是否将模型坐标 /16 转为方块单位（一般 true）
     */
    fun collectSamplePoints(
        root: ModelPart,
        poseStack: PoseStack,
        density: Int,
        pixelToUnit: Boolean = true
    ): List<Vec3>

}