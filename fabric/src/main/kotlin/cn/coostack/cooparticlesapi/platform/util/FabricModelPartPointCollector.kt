package cn.coostack.cooparticlesapi.platform.util

import cn.coostack.cooparticlesapi.extend.asVec3
import cn.coostack.cooparticlesapi.utils.MinecraftRendererUtil
import cn.coostack.cooparticlesapi.utils.api.ModelPartPointCollector
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f

class FabricModelPartPointCollector : ModelPartPointCollector {

    /**
     * @param root 你的实体模型 root ModelPart
     * @param poseStack 渲染时 PoseStack（已经含实体的平移旋转缩放）
     * @param density 每个三角形采样密度：4~16 常用；越大点越多
     * @param pixelToUnit 是否将模型坐标 /16 转为方块单位（一般 true）
     */
    override fun collectSamplePoints(
        root: ModelPart,
        poseStack: PoseStack,
        density: Int,
        pixelToUnit: Boolean
    ): List<Vec3> {
        val out = ArrayList<Vec3>(4096)
        visit(root, poseStack, density, pixelToUnit, out)
        return out
    }

    private fun visit(
        part: ModelPart,
        poseStack: PoseStack,
        density: Int,
        pixelToUnit: Boolean,
        out: MutableList<Vec3>
    ) {
        poseStack.pushPose()
        part.translateAndRotate(poseStack)

        val m = poseStack.last().pose()

        // cubes -> polygons -> vertices
        for (cube in part.cubes) {
            for (poly in cube.polygons) {
                val n = poly.vertices.size
                if (n < 3) continue

                // 顶点转 Vec3（应用 pose 变换）
                val vs = Array(n) { idx ->
                    val p: Vector3f = poly.vertices[idx].pos
                    transformPos(m, p, pixelToUnit)
                }

                when (n) {
                    3 -> MinecraftRendererUtil.sampleTriangle(vs[0], vs[1], vs[2], density, out)
                    4 -> MinecraftRendererUtil.sampleQuad(vs[0], vs[1], vs[2], vs[3], density, out)
                    else -> {
                        // n>4：简单扇形剖分
                        for (i in 1 until n - 1) {
                            MinecraftRendererUtil.sampleTriangle(vs[0], vs[i], vs[i + 1], density, out)
                        }
                    }
                }
            }
        }

        // children 递归
        for (child in part.children.values) {
            visit(child, poseStack, density, pixelToUnit, out)
        }

        poseStack.popPose()
    }

    private fun transformPos(m: Matrix4f, p: Vector3f, pixelToUnit: Boolean): Vec3 {
        val tmp = Vector3f(p)
        if (pixelToUnit) tmp.mul(1f / 16f)

        // position vector (w=1)
        tmp.mulPosition(m)

        return tmp.asVec3()
    }
}