package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.client.ClientRenderPipelineManager
import cn.coostack.cooparticlesapi.renderer.client.ShaderPipeManagers

object TestShaderInit {
    @JvmStatic
    fun initOnClient() {
        ClientRenderPipelineManager.register(TestShaderPipelines.blackHoleDistortion)
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
            TestBillboardSmokeEntity.id,
            TestBillboardSmokeEntity.codec,
            ShaderPipeManagers.default.pipeID
        )
        ClientRenderEntityManager.register(
            TestGlowSphereEntity.id,
            TestGlowSphereEntity.codec,
            ShaderPipeManagers.simpleBloom.pipeID
        )
        ClientRenderEntityManager.register(
            TestBlackHoleEntity.id,
            TestBlackHoleEntity.codec,
            TestShaderPipelines.blackHoleDistortion.pipeID
        )
    }

    @JvmStatic
    fun initOnServer() {
    }
}
