package cn.coostack.cooparticlesapi.platform

import cn.coostack.cooparticlesapi.display.CooRenderTypesProvider

/** Public-constructor ServiceLoader adapter for the Kotlin singleton provider. */
class NeoRenderTypesProviderService : CooRenderTypesProvider by NeoRenderTypesProvider
