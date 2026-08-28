package cn.coostack.cooparticlesapi.cparticle.compat

import cn.coostack.cooparticlesapi.cparticle.CParticle
import cn.coostack.cooparticlesapi.cparticle.CParticleCapabilities
import cn.coostack.cooparticlesapi.cparticle.CParticleRenderLayer
import cn.coostack.cooparticlesapi.cparticle.CParticleSystem
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemManager
import cn.coostack.cooparticlesapi.cparticle.CParticleSystemMode
import cn.coostack.cooparticlesapi.cparticle.CParticleTextureBindingKey
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForce
import cn.coostack.cooparticlesapi.cparticle.force.CParticleForceSink
import cn.coostack.cooparticlesapi.cparticle.resolveTextures
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.network.particle.emitters.CParticleEmitterSpace
import cn.coostack.cooparticlesapi.network.particle.emitters.ControlableCParticleData
import cn.coostack.cooparticlesapi.network.particle.emitters.TransformableCParticleEmitter
import cn.coostack.cooparticlesapi.network.particle.emitters.environment.wind.GlobalWindDirection
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.UUID

/**
 * 保存一个可变换 emitter 渲染分组的可扩容 system。
 *
 * 坐标空间、渲染层和两类纹理 binding 都属于分组键。LOCAL 与 WORLD 必须分开，避免模式切换时
 * 改动已有粒子的矩阵。
 *
 * @property baseName 当前分组的 system 名称
 * @property layer 粒子渲染层
 * @property textureBindingKey 基础纹理 binding
 * @property maskTextureBindingKey 可选蒙版纹理 binding
 * @property space 粒子模拟坐标空间
 * @property capacityHint 当前批次粒子数，只用于首次分配
 * @property globalLimit 当前全局存活数量上限
 * @property onSystem 新 system 创建或重新取得后的登记回调
 */
private class TransformableEmitterSystemCursor(
    private val baseName: String,
    private val layer: CParticleRenderLayer,
    private val textureBindingKey: CParticleTextureBindingKey,
    private val maskTextureBindingKey: CParticleTextureBindingKey?,
    val space: CParticleEmitterSpace,
    var capacityHint: Int,
    var globalLimit: Int,
    private val onSystem: (CParticleSystem) -> Unit,
) {
    private var system: CParticleSystem? = null

    fun matches(
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
        space: CParticleEmitterSpace,
    ): Boolean = this.layer == layer &&
        this.textureBindingKey == textureBindingKey &&
        this.maskTextureBindingKey == maskTextureBindingKey &&
        this.space == space

    fun findAvailable(): CParticleSystem {
        val current = system?.takeUnless(CParticleSystem::released)
            ?: getOrCreateSystem().also { system = it }
        if (current.store.isFull()) {
            val nextCapacity = CParticleEmitterBridge.nextSystemCapacity(current.capacity, globalLimit)
            if (nextCapacity > current.capacity) current.growTo(nextCapacity)
        }
        return current
    }

    private fun getOrCreateSystem(): CParticleSystem {
        val system = CParticleSystemManager.getSystem(
            baseName,
            CParticleSystemMode.SIMULATED,
            layer,
            textureBindingKey,
            maskTextureBindingKey,
        ) ?: CParticleSystemManager.getOrCreateSystem(
            baseName,
            CParticleEmitterBridge.initialSystemCapacity(capacityHint, globalLimit),
            layer,
            CParticleSystemMode.SIMULATED,
            textureBindingKey,
            autoReleaseWhenEmpty = true,
            maskTextureBindingKey = maskTextureBindingKey,
        )
        onSystem(system)
        return system
    }
}

/**
 * [TransformableCParticleEmitter] 到 GPU system 的客户端桥接器。
 *
 * 每个 emitter 和坐标空间使用独立的 SIMULATED systems。LOCAL 粒子的槽位保存局部位置，
 * [CParticleSystem.groupTransform] 负责整体变换；WORLD 粒子保存世界位置并始终使用单位矩阵。
 */
object TransformableCParticleEmitterBridge {
    private data class EmitterSystems(
        val cursors: ArrayList<TransformableEmitterSystemCursor> = ArrayList(2),
        val systems: LinkedHashMap<CParticleSystem, CParticleEmitterSpace> = LinkedHashMap(),
        val forceSnapshot: CParticleForceSink = CParticleForceSink(),
        var forceTick: Int = Int.MIN_VALUE,
    )

    private val emitterSystems = HashMap<UUID, EmitterSystems>()

    /**
     * 把一份可控 GPU data 生成到当前坐标空间。
     *
     * 纹理无效或达到全局上限时直接丢弃；GPU 渲染能力缺失时直接报错，不会退回 CPU 粒子路径。
     */
    @JvmStatic
    fun trySpawn(
        emitter: TransformableCParticleEmitter,
        world: ClientLevel,
        spawnPos: Vec3,
        relative: RelativeLocation,
        data: ControlableCParticleData,
        capacityHint: Int,
    ): Boolean {
        if (!world.isClientSide || !CParticleSystemManager.enabled) return false
        CParticleCapabilities.detect()
        CParticleCapabilities.requireGpuParticleRendering()

        val storagePosition = resolveStoragePosition(emitter, spawnPos, relative) ?: return true
        val worldPosition = storageToWorld(emitter, storagePosition)
        val particle = CParticle.from(data).apply {
            pos = worldPosition
            velocity = resolveStorageVelocity(emitter, data.velocity)
            mass = emitter.mass.toFloat()
        }
        val resolved = particle.resolveTextures(worldPosition)
        if (!resolved.isValid || !CParticleSystemManager.hasAvailableParticleCapacity()) return true

        val layer = CParticleRenderLayer.fromSheetName(data.getTextureSheet().toString())
        val system = findAvailableSystem(
            emitter,
            layer,
            resolved.base.bindingKey,
            resolved.mask?.bindingKey,
            capacityHint,
        )
        configureSystem(system, emitter, emitter.space)
        if (data.visibleRange.toDouble() > system.visibleRange) {
            system.visibleRange = data.visibleRange.toDouble()
        }
        val slotPosition: Vec3? = if (emitter.space == CParticleEmitterSpace.LOCAL) {
            system.origin + storagePosition
        } else {
            null
        }
        system.spawnResolved(particle, resolved, slotPosition)
        return true
    }

    /** 计算当前发射结果实际显示和环境采样使用的世界坐标。 */
    internal fun resolveWorldPosition(
        emitter: TransformableCParticleEmitter,
        spawnPos: Vec3,
        relative: RelativeLocation,
    ): Vec3? {
        val storagePosition = resolveStoragePosition(emitter, spawnPos, relative) ?: return null
        return storageToWorld(emitter, storagePosition)
    }

    /** 把 emitter data 的速度换算为槽位存储语义。LOCAL 只应用新粒子的出生旋转。 */
    internal fun resolveStorageVelocity(
        emitter: TransformableCParticleEmitter,
        velocity: Vec3,
    ): Vec3 {
        if (emitter.space == CParticleEmitterSpace.WORLD) return velocity
        val storedVelocity = Matrix4f()
            .rotation(emitter.particleRotation)
            .transformDirection(velocity.toVector3f())
        return Vec3(storedVelocity.x.toDouble(), storedVelocity.y.toDouble(), storedVelocity.z.toDouble())
    }

    /** 把当前变换同步到这个 emitter 已创建的全部 LOCAL systems。 */
    internal fun syncSystems(emitter: TransformableCParticleEmitter) {
        val systems = emitterSystems[emitter.uuid] ?: return
        systems.systems.entries.removeIf { it.key.released }
        ensureForceSnapshot(emitter, systems)
        systems.systems.forEach { (system, space) ->
            configureSystem(system, emitter, space)
        }
    }

    /** 停止接收新粒子，并在存活数量归零后释放这个 emitter 的全部 systems。 */
    internal fun finishEmitter(emitterId: UUID) {
        emitterSystems.remove(emitterId)
        CParticleSystemManager.releaseSystemsWhenEmpty("transformable_emitter/$emitterId/")
    }

    private fun findAvailableSystem(
        emitter: TransformableCParticleEmitter,
        layer: CParticleRenderLayer,
        textureBindingKey: CParticleTextureBindingKey,
        maskTextureBindingKey: CParticleTextureBindingKey?,
        capacityHint: Int,
    ): CParticleSystem {
        val globalLimit = CParticleSystemManager.particleCountLimit
        val state = emitterSystems.getOrPut(emitter.uuid) { EmitterSystems() }
        val spaceName = emitter.space.name.lowercase()
        val cursor = state.cursors.firstOrNull {
            it.matches(layer, textureBindingKey, maskTextureBindingKey, emitter.space)
        } ?: TransformableEmitterSystemCursor(
            "transformable_emitter/${emitter.uuid}/$spaceName/${layer.name.lowercase()}",
            layer,
            textureBindingKey,
            maskTextureBindingKey,
            emitter.space,
            capacityHint,
            globalLimit,
        ) { system ->
            state.systems.entries.removeIf { it.key.released }
            state.systems[system] = emitter.space
        }.also(state.cursors::add)
        cursor.capacityHint = capacityHint
        cursor.globalLimit = globalLimit
        return cursor.findAvailable()
    }

    private fun configureSystem(
        system: CParticleSystem,
        emitter: TransformableCParticleEmitter,
        space: CParticleEmitterSpace,
    ) {
        system.setOriginIfEmpty(emitter.pos)
        if (space == CParticleEmitterSpace.LOCAL) {
            system.transformsSimulatedParticleSpace = true
            localGroupTransform(emitter, system.origin, system.groupTransform)
        } else {
            system.transformsSimulatedParticleSpace = false
            system.groupTransform.identity()
        }

        val emitterState = emitterSystems.getValue(emitter.uuid)
        ensureForceSnapshot(emitter, emitterState)
        if (system.forcesSyncTick != emitterState.forceTick) {
            CParticleEmitterBridge.applyForceSnapshot(
                system,
                emitterState.forceSnapshot,
                emitterState.forceTick,
            )
        }
    }

    private fun resolveStoragePosition(
        emitter: TransformableCParticleEmitter,
        spawnPos: Vec3,
        relative: RelativeLocation,
    ): Vec3 {
        if (emitter.space == CParticleEmitterSpace.WORLD) {
            return spawnPos + relative
        }
        val localBirthOffset = Matrix4f()
            .rotation(emitter.particleRotation)
            .transformDirection(relative.toVector().toVector3f())
        val localPathOffset = Matrix4f()
            .rotation(emitter.emitterRotation)
            .scale(emitter.scale.toFloat())
            .invertAffine()
            .transformDirection(
                (spawnPos.x - emitter.pos.x).toFloat(),
                (spawnPos.y - emitter.pos.y).toFloat(),
                (spawnPos.z - emitter.pos.z).toFloat(),
                Vector3f(),
            )
        val stored = localBirthOffset.add(localPathOffset)
        return Vec3(
            stored.x.toDouble(),
            stored.y.toDouble(),
            stored.z.toDouble(),
        )
    }

    private fun storageToWorld(emitter: TransformableCParticleEmitter, storagePosition: Vec3): Vec3 {
        if (emitter.space == CParticleEmitterSpace.WORLD) return storagePosition
        val transformed = Matrix4f()
            .rotation(emitter.emitterRotation)
            .scale(emitter.scale.toFloat())
            .transformPosition(storagePosition.toVector3f())
        return emitter.pos + transformed
    }

    /** 返回 LOCAL system 使用的发射器矩阵，供无 OpenGL 上下文的数学测试复用。 */
    internal fun localGroupTransform(
        emitter: TransformableCParticleEmitter,
        systemOrigin: Vec3,
        destination: Matrix4f = Matrix4f(),
    ): Matrix4f {
        return destination
            .translation(
                (emitter.pos.x - systemOrigin.x).toFloat(),
                (emitter.pos.y - systemOrigin.y).toFloat(),
                (emitter.pos.z - systemOrigin.z).toFloat(),
            )
            .rotate(emitter.emitterRotation)
            .scale(emitter.scale.toFloat())
    }

    /** 每个 emitter tick 只构建一次共享 Command 快照。 */
    private fun ensureForceSnapshot(
        emitter: TransformableCParticleEmitter,
        state: EmitterSystems,
    ) {
        if (state.forceTick == emitter.tick) return
        state.forceTick = emitter.tick
        val target = state.forceSnapshot
        target.clear()
        if (emitter.gravity != 0.0) {
            target.submit(CParticleForce.Gravity(emitter.gravity))
        }
        if (emitter.airDensity > 0.0) {
            target.submit(CParticleForce.EnvDrag(emitter.airDensity))
        }
        val wind = emitter.wind
        if (wind is GlobalWindDirection && !wind.relative && wind.direction.lengthSqr() > 1e-12) {
            target.submit(CParticleForce.Wind({ wind.direction }, emitter.airDensity.coerceAtLeast(1e-4)))
        }
        emitter.submitCParticleForces(target)
        CParticleSystemManager.updateBlockCollisionRange(
            "transformable_emitter/${emitter.uuid}/",
            emitter.cparticleBlockCollisionRange(),
        )
        state.systems.keys.forEach { system ->
            if (!system.released) {
                CParticleEmitterBridge.applyForceSnapshot(system, target, state.forceTick)
            }
        }
    }
}
