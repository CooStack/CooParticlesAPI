package cn.coostack.cooparticlesapi

import cn.coostack.cooparticlesapi.test.options.display.MCShaders
import cn.coostack.cooparticlesapi.test.options.renderer.TestAccretionDiskEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestBillboardSmokeEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestBlackHoleEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestGlowSphereEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestHybridGlowPipeEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestPersistentGlowSphereEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestRendererEntity
import cn.coostack.cooparticlesapi.test.options.renderer.TestTexturedBeamEntity
import net.minecraft.server.packs.resources.ResourceManager

object CooShaderReloadSupport {
    @JvmStatic
    fun reload(resourceManager: ResourceManager) {
        TestRendererEntity.reloadStaticResources()
        TestTexturedBeamEntity.reloadStaticResources()
        TestHybridGlowPipeEntity.reloadStaticResources()
        TestBillboardSmokeEntity.reloadStaticResources()
        TestGlowSphereEntity.reloadStaticResources()
        TestPersistentGlowSphereEntity.reloadStaticResources()
        TestBlackHoleEntity.reloadStaticResources()
        TestAccretionDiskEntity.reloadStaticResources()
        MCShaders.init(resourceManager)
        CooParticlesAPIClient.reloadShaderPrograms()
    }
}
