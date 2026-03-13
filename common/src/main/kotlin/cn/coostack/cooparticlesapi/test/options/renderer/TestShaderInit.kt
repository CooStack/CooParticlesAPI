package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.client.ShaderPipeManagers

object TestShaderInit {
    @JvmStatic
    fun initOnClient() {
        ClientRenderPipelineManager.register(TestShaderPipelines.blackHoleDistortion)
        ClientRenderPipelineManager.register(TestShaderPipelines.accretionDiskDistortion)
        ClientRenderPipelineManager.register(TestShaderPipelines.glowSphereDistortion)
        ClientRenderEntityManager.register(
            TestRendererEntity.id,
            TestRendererEntity.codec,
            ShaderPipeManagers.simpleBloom.pipeID
        )
        ClientRenderEntityManager.register(
            TestTexturedBeamEntity.id,
            TestTexturedBeamEntity.codec,
            ShaderPipeManagers.simpleBloom.pipeID
        )
        ClientRenderEntityManager.register(
            TestHybridGlowPipeEntity.id,
            TestHybridGlowPipeEntity.codec,
            ShaderPipeManagers.simpleBloom.pipeID
        )
        ClientRenderEntityManager.register(
            TestBillboardSmokeEntity.id,
            TestBillboardSmokeEntity.codec,
            ShaderPipeManagers.default.pipeID
        )
        ClientRenderEntityManager.register(
            TestGlowSphereEntity.id,
            TestGlowSphereEntity.codec,
            TestShaderPipelines.glowSphereDistortion.pipeID
        )
        ClientRenderEntityManager.register(
            TestPersistentGlowSphereEntity.id,
            TestPersistentGlowSphereEntity.codec,
            TestShaderPipelines.glowSphereDistortion.pipeID
        )
        ClientRenderEntityManager.register(
            TestBlackHoleEntity.id,
            TestBlackHoleEntity.codec,
            TestShaderPipelines.blackHoleDistortion.pipeID
        )
        ClientRenderEntityManager.register(
            TestAccretionDiskEntity.id,
            TestAccretionDiskEntity.codec,
            TestShaderPipelines.accretionDiskDistortion.pipeID
        )
    }

    @JvmStatic
    fun initOnServer() {
    }
}
