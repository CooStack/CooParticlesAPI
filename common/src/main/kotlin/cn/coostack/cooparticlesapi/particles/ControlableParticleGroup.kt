package cn.coostack.cooparticlesapi.particles

import cn.coostack.cooparticlesapi.CooParticlesAPIClient
import cn.coostack.cooparticlesapi.compat.IrisCompat
import cn.coostack.cooparticlesapi.mixin.ParticleEngineAccessor
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Camera
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleEngine
import net.minecraft.client.particle.ParticleGroup
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.state.level.ParticleGroupRenderState
import net.minecraft.client.renderer.rendertype.RenderType

/**
 * 管理同一混合图层的普通粒子，提取几何快照后交由原版命令收集器绘制。
 *
 * @property renderType 本组固定的纹理、混合与深度写入标识
 */
internal class ControlableParticleGroup(
    engine: ParticleEngine,
    private val renderType: ParticleRenderType,
) : ParticleGroup<ControlableParticle>(engine) {
    /** 按配置驱逐最早的粒子，同时释放其控制器和原版限额计数。 */
    override fun add(particle: Particle): Boolean {
        if (particle !is ControlableParticle) {
            particle.remove()
            return false
        }
        val limit = CooParticlesServices.API_CONFIG_MANAGER.getConfig().particleCountLimit
        if (limit <= 0) {
            particle.removeFromQueue()
            return false
        }
        while (particles.size >= limit) {
            val evicted = particles.poll() ?: break
            evicted.removeFromQueue()
            evicted.particleLimit.ifPresent { particleLimit ->
                (engine as ParticleEngineAccessor).trackedParticleCounts.addTo(particleLimit, -1)
            }
        }
        // 自有配置负责容量，不套用原版 16384 上限和随机拒绝策略。
        return particles.add(particle)
    }

    /** 只保留当前视锥中粒子的不可变几何，避免命令执行时读取跨阶段状态。 */
    override fun extractRenderState(
        frustum: Frustum,
        camera: Camera,
        partialTickTime: Float,
    ): ParticleGroupRenderState {
        val captured = particles.asSequence()
            .filter { particle ->
                val position = particle.loc
                frustum.pointInFrustum(position.x, position.y, position.z)
            }
            .mapNotNull { particle ->
                particle.captureGeometry(camera, partialTickTime)?.let(::CapturedGeometry)
            }
            .toList()
        return CapturedParticleRenderState(
            CooParticleTextureSheet.getRenderType(renderType),
            CooParticleTextureSheet.getLayer(renderType),
            captured,
        )
    }

    /** 清空世界或重载资源前释放本组持有的全部控制器。 */
    fun disposeParticles() {
        particles.forEach(ControlableParticle::removeFromQueue)
        particles.clear()
    }

    /** @property vertices 一颗粒子在提取阶段生成的顶点快照 */
    private data class CapturedGeometry(val vertices: List<ParticleVertex>)

    /**
     * 原版延迟绘制入口，不持有实际粒子或其控制器。
     *
     * @property renderType 本组支持的原版绘制类型，null 表示不绘制
     * @property geometry 本帧可见粒子的不可变快照
     * @property layer 本组已经注册的纹理与混合资源
     */
    private data class CapturedParticleRenderState(
        val renderType: RenderType?,
        val layer: CooParticleTextureSheet.SheetLayer,
        val geometry: List<CapturedGeometry>,
    ) : ParticleGroupRenderState {
        /** 使用原版受支持的几何命令，不直接修改 OpenGL 或 Iris 的状态。 */
        override fun submit(submitNodeCollector: SubmitNodeCollector, camera: CameraRenderState) {
            val type = renderType ?: return
            if (geometry.isEmpty()) return
            if (layer.boundedAlpha && CooParticlesAPIClient.checkIrisShaderPackUsed()) {
                if (IrisCompat.isShadowPassActive()) return
                IrisControlableParticleRenderer.enqueue(this, layer, geometry.flatMap(CapturedGeometry::vertices))
                return
            }
            submitNodeCollector.submitCustomGeometry(PoseStack(), type) { pose, consumer ->
                geometry.forEach { captured ->
                    captured.vertices.forEach { vertex ->
                        consumer.addVertex(pose, vertex.x, vertex.y, vertex.z)
                            .setUv(vertex.u, vertex.v)
                            .setColor(vertex.color)
                            .setLight(vertex.light)
                    }
                }
            }
        }
    }
}
