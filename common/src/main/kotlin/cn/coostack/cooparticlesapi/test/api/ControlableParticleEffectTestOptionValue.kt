package cn.coostack.cooparticlesapi.test.api

class ControlableParticleEffectTestOptionValue(
    id: String,
    displayName: String = id
) : TestOptionParamType<ControlableParticleEffectBuilder>(
    id,
    ControlableParticleEffectBuilder::class.java,
    displayName,
    parser = ControlableParticleEffectRegistry::resolve,
    formatter = { it.id },
    codecClass = String::class.java,
    editor = TestOptionParamEditor.enum(ControlableParticleEffectRegistry.suggestions())
) {
    companion object {
        fun defaultBuilder(): ControlableParticleEffectBuilder {
            return ControlableParticleEffectRegistry.defaultBuilder()
        }
    }
}
