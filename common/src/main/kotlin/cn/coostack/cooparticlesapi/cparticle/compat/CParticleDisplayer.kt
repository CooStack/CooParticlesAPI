package cn.coostack.cooparticlesapi.cparticle.compat

import cn.coostack.cooparticlesapi.api.controler.Controlable
import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleResolvedTextures
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.CParticleUpdateMode
import cn.coostack.cooparticlesapi.cparticle.resolveTextures
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

    internal fun bindDedicatedSystemIfAbsent(
        ownerName: String,
        capacity: Int,
        origin: Vec3,
    ) {
        if (system != null) return
        val name = "$ownerName/${layer.name.lowercase()}"
        val resolved = template.resolveTextures(origin)
        if (!resolved.isValid) return
        val bindingKey = resolved.base.bindingKey
        val maskBindingKey = resolved.mask?.bindingKey
        val existing = CParticleSystemManager.getSystem(
            name,
            CParticleSystemMode.SCRIPTED,
            layer,
            bindingKey,
            maskBindingKey,
        )
        if (existing != null && existing.capacity < capacity) {
            CParticleSystemManager.removeSystem(
                name,
                CParticleSystemMode.SCRIPTED,
                layer,
                bindingKey,
                maskBindingKey,
            )
        }
        val target = CParticleSystemManager.getOrCreateSystem(
            name,
            capacity.coerceAtLeast(1),
            layer,
            CParticleSystemMode.SCRIPTED,
            bindingKey,
            autoReleaseWhenEmpty = true,
            maskTextureBindingKey = maskBindingKey,
        )
        if (!bindSystemIfAbsent(target)) return
        target.setOriginIfEmpty(origin)
        target.visibleRange = Double.MAX_VALUE
    }

    internal fun applyParticleInit(init: CParticle.() -> Unit) {
        init(template)
    }

    /**
     * 返回模板在指定位置使用的基础纹理和蒙版解析结果。
     *
     * Example: composition 在建池前用它取得两个 binding。
     * Forbidden: 不要在每帧绘制时重新解析 STATIC 模板。
     *
     * @param position 粒子生成位置
     * @return 模板的双纹理解析结果
     */
    internal fun resolveTexturesAt(position: Vec3): CParticleResolvedTextures = template.resolveTextures(position)

    /**
     * 在共享或已绑定的 CParticle system 中显示一个粒子。
     *
     * Example: composition 调用 `display(loc, world)` 后持有返回的控制句柄。
     * Forbidden: 达到全局上限后不能继续创建分段 system。
     *
     * @param loc 粒子的世界坐标
     * @param world 当前客户端世界
     * @return 控制句柄；GPU 不可用、纹理无效或达到上限时返回 `null`
     */
    override fun display(loc: Vec3, world: ClientLevel): Controlable<*>? {
        if (!CParticleSystemManager.enabled) return null
        CParticleCapabilities.detect()
        if (CParticleCapabilities.detectionComplete && !CParticleCapabilities.instancingSupported) return null
        val p = template.clone()
        p.pos = loc
        val resolved = p.resolveTextures(loc)
        if (!resolved.isValid) return null
        var target = system ?: sharedScriptedSystem(
            layer,
            resolved.base.bindingKey,
            resolved.mask?.bindingKey,
        )
        var slot = target.spawnResolved(p, resolved)
        if (system == null) {
            var segment = 0
            while (slot < 0 && CParticleSystemManager.hasAvailableParticleCapacity()) {
                target = sharedScriptedSystem(
                    layer,
                    resolved.base.bindingKey,
                    resolved.mask?.bindingKey,
                    ++segment,
                )
                slot = target.spawnResolved(p, resolved)
            }
        }
        if (slot < 0) return null
        return CParticleControlable(
            target, slot, target.store.generations[slot], uuid,
            world,
            p.yaw, p.pitch, p.roll, p.axis
        )
    }

    companion object {
        private const val SHARED_CAPACITY = 65536

        @JvmStatic
        fun sharedScriptedSystem(layer: CParticleRenderLayer): CParticleSystem =
            sharedScriptedSystem(layer, CParticleTextureBindingKey.PARTICLE_ATLAS)

        /** 返回指定纹理绑定的共享 scripted 系统。 */
        @JvmStatic
        fun sharedScriptedSystem(
            layer: CParticleRenderLayer,
            textureBindingKey: CParticleTextureBindingKey,
            maskTextureBindingKey: CParticleTextureBindingKey? = null,
        ): CParticleSystem = sharedScriptedSystem(layer, textureBindingKey, maskTextureBindingKey, 0)

        private fun sharedScriptedSystem(
            layer: CParticleRenderLayer,
            textureBindingKey: CParticleTextureBindingKey,
            maskTextureBindingKey: CParticleTextureBindingKey?,
            segment: Int,
        ): CParticleSystem {
            val baseName = "composition/${layer.name.lowercase()}"
            val name = if (segment == 0) baseName else "$baseName/$segment"
            return CParticleSystemManager.getOrCreateSystem(
                name,
                SHARED_CAPACITY,
                layer,
                CParticleSystemMode.SCRIPTED,
                textureBindingKey,
                autoReleaseWhenEmpty = true,
                maskTextureBindingKey = maskTextureBindingKey,
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
