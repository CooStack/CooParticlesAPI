package cn.coostack.cooparticlesapi.cparticle.compat

import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.network.particle.composition.CompositionData
import cn.coostack.cooparticlesapi.particles.ParticleDisplayer
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * # CParticleDisplayer — "Composition 是特定的 display" 的落地
 *
 * 实现现有 [ParticleDisplayer] 接口: 任何 [ParticleComposition]
 * (含 ParticleShapeComposition / Sequenced 系列) 只需把槽位的
 * `displayerBuilder` 换成本类, 该槽位就渲染为 GPU 粒子 —
 * teleport / rotate / scale / remove 语义完整保留 (经 [CParticleControlable] 写入 SoA),
 * 渲染坍缩为每层一次 instanced draw.
 *
 * ```kotlin
 * CompositionData().setDisplayerSupplier { uuid ->
 *     CParticleDisplayer.of(uuid) {
 *         size = 0.2f
 *         color = Vector3f(0.4f, 0.8f, 1f)
 *     }
 * }
 * // 或直接: CParticleCompositions.data { size = 0.2f }
 * ```
 */
class CParticleDisplayer(
    /** composition 槽位 uuid (必须与 CompositionData.uuid 一致, 否则 scale 映射失效) */
    private val uuid: UUID,
    private val template: CParticle,
    internal val layer: CParticleRenderLayer = CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT,
    /** 为 null 时使用共享 scripted 系统 (按层) */
    private var system: CParticleSystem? = null,
) : ParticleDisplayer {

    internal val hasBoundSystem: Boolean
        get() = system != null

    internal fun bindSystemIfAbsent(target: CParticleSystem): Boolean {
        if (system != null) return false
        system = target
        return true
    }

    internal fun applyParticleInit(init: CParticle.() -> Unit) {
        init(template)
    }

    override fun display(loc: Vec3, world: ClientLevel): Controlable<*>? {
        if (!CParticleSystemManager.enabled) return null
        CParticleCapabilities.detect()
        if (CParticleCapabilities.detectionComplete && !CParticleCapabilities.instancingSupported) return null
        val p = template.clone()
        p.pos = loc
        var target = system ?: sharedScriptedSystem(layer)
        var slot = target.spawn(p)
        if (system == null) {
            var segment = 0
            while (slot < 0) {
                target = sharedScriptedSystem(layer, ++segment)
                slot = target.spawn(p)
            }
        }
        if (slot < 0) return null
        return CParticleControlable(
            target, slot, target.store.generations[slot], uuid,
            world,
            p.yaw, p.pitch, p.roll
        )
    }

    companion object {
        private const val SHARED_CAPACITY = 65536

        @JvmStatic
        fun sharedScriptedSystem(layer: CParticleRenderLayer): CParticleSystem =
            sharedScriptedSystem(layer, 0)

        private fun sharedScriptedSystem(layer: CParticleRenderLayer, segment: Int): CParticleSystem {
            val baseName = "composition/${layer.name.lowercase()}"
            val name = if (segment == 0) baseName else "$baseName/$segment"
            return CParticleSystemManager.getOrCreateSystem(
                name, SHARED_CAPACITY, layer, CParticleSystemMode.SCRIPTED,
                autoReleaseWhenEmpty = true,
            ).also {
                // 共享池会承载世界各处的 composition, 不能按 origin 距离整池剔除
                // (composition 自身有 visibleRange 剔除逻辑)
                it.visibleRange = Double.MAX_VALUE
            }
        }

        /** 快捷构造 (composition displayerBuilder 用) */
        @JvmStatic
        fun of(
            uuid: UUID,
            layer: CParticleRenderLayer = CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT,
            builder: CParticle.() -> Unit = {},
        ): CParticleDisplayer {
            return CParticleDisplayer(
                uuid,
                compositionTemplate(builder),
                layer,
            )
        }

        @JvmStatic
        fun of(
            uuid: UUID,
            layer: CParticleRenderLayer,
            system: CParticleSystem?,
            builder: CParticle.() -> Unit = {},
        ): CParticleDisplayer {
            return CParticleDisplayer(
                uuid,
                compositionTemplate(builder),
                layer,
                system,
            )
        }

        private fun compositionTemplate(builder: CParticle.() -> Unit): CParticle {
            return CParticle().apply {
                updateMode = CParticleUpdateMode.STATIC
                maxAge = Int.MAX_VALUE
            }.apply(builder)
        }
    }
}

/**
 * composition 快捷工具: 一行生成 GPU 粒子槽位数据
 */
object CParticleCompositions {
    /**
     * 生成 displayer 已接好 GPU 粒子的 [CompositionData]:
     * `getParticles()` 里直接 `CParticleCompositions.data { size = 0.2f } to RelativeLocation(...)`
     */
    @JvmStatic
    fun data(
        layer: CParticleRenderLayer = CParticleRenderLayer.ADDITION_BLEND_TRANSLUCENT,
        builder: CParticle.() -> Unit = {},
    ): CompositionData {
        return CompositionData()
            .setDisplayerSupplier { uuid -> ParticleDisplayer.withCParticle(uuid, layer) }
            .addCParticleInstanceInit(builder)
    }
}
