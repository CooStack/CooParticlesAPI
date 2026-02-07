package cn.coostack.cooparticlesapi.test.options.renderer

import cn.coostack.cooparticlesapi.renderer.client.ClientRenderEntityManager
import cn.coostack.cooparticlesapi.renderer.client.ShaderPipeManagers

object TestShaderInit {
    @JvmStatic
    fun initOnClient() {
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
    }

    @JvmStatic
    fun initOnServer() {
    }
}
