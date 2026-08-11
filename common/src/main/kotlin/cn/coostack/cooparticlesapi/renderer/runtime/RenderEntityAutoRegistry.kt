package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CooAutoRegisterRenderer
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import cn.coostack.cooparticlesapi.reflect.SimpleClassInfo
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

/**
 * RenderEntity codec 与客户端 renderer 的自动注册器。
 *
 * 实体继续使用 [CooAutoRegister]，renderer 使用 [CooAutoRegisterRenderer] 并实现
 * [RenderEntityRenderer]。注册器会先收集并校验两侧描述，最后统一提交，
 * 因此不依赖其他模组的客户端生命周期监听器顺序。
 */
object RenderEntityAutoRegistry {
    private var scannerRegistered = false

    /**
     * 扫描实体和客户端 renderer，并自动建立类型绑定。
     *
     * Fabric 集成服还会从服务端生命周期再次进入公共扫描流程，因此该入口只允许成功执行一次。
     */
    @Synchronized
    fun registerScanner() {
        if (scannerRegistered) return
        CooParticlesConstants.logger.info("正在自动注册 RenderEntity")
        val entityClasses = CooAPIScanner.getWithAnnotation(CooAutoRegister::class.java)
            .map { candidate -> loadClass(candidate, "RenderEntity") }
        val rendererClasses = CooAPIScanner.getWithAnnotation(CooAutoRegisterRenderer::class.java)
            .map { candidate -> loadClass(candidate, "RenderEntity renderer") }
        registerClasses(entityClasses, rendererClasses)
        scannerRegistered = true
    }

    /**
     * 注册已经收集好的实体和 renderer 类型。
     *
     * 该入口供扫描器和行为测试共用。任何描述不合法时都不会修改客户端注册表。
     */
    internal fun registerClasses(
        entityClasses: Collection<Class<*>>,
        rendererClasses: Collection<Class<*>>
    ) {
        val errors = ArrayList<String>()
        val failures = ArrayList<IllegalStateException>()
        val entities = entityClasses
            .asSequence()
            .filter { clazz -> RenderEntity::class.java.isAssignableFrom(clazz) }
            .sortedBy { clazz -> clazz.name }
            .mapNotNull { clazz ->
                try {
                    describeEntity(clazz)
                } catch (error: Exception) {
                    failures += descriptorFailure("RenderEntity", clazz, error)
                    null
                }
            }
            .toList()

        entities.groupBy { descriptor -> descriptor.id }
            .filterValues { descriptors -> descriptors.size > 1 }
            .forEach { (id, descriptors) ->
                errors += "Duplicate RenderEntity id $id: ${descriptors.joinToString { it.entityClass.name }}"
            }

        val renderers = rendererClasses
            .sortedBy { clazz -> clazz.name }
            .mapNotNull { clazz ->
                try {
                    describeRenderer(clazz)
                } catch (error: Exception) {
                    failures += descriptorFailure("RenderEntity renderer", clazz, error)
                    null
                }
            }

        renderers.groupBy { descriptor -> descriptor.entityClass }
            .filterValues { descriptors -> descriptors.size > 1 }
            .forEach { (entityClass, descriptors) ->
                errors += "Duplicate RenderEntity renderer binding for ${entityClass.name}: " +
                    descriptors.joinToString { it.rendererClass.name }
            }

        val entitiesByClass = entities.associateBy { descriptor -> descriptor.entityClass }
        renderers.forEach { renderer ->
            if (renderer.entityClass !in entitiesByClass) {
                errors += "RenderEntity codec not discovered for renderer ${renderer.rendererClass.name}: " +
                    renderer.entityClass.name
            }
        }
        throwIfInvalid(errors, failures)

        val renderersByEntity = renderers.associateBy { descriptor -> descriptor.entityClass }
        val registrations = LinkedHashMap<ResourceLocation, AutomaticClientRenderEntityType>()
        entities.sortedBy { descriptor -> descriptor.id.toString() }.forEach { entity ->
            val rendererFactory = renderersByEntity[entity.entityClass]?.toFactory(entity.id)
            val existing = ClientRenderEntityRegistry.get(entity.id)
            val existingEntityClass = ClientRenderEntityRegistry.getAutomaticEntityClass(entity.id)
            when {
                existing == null -> {
                    registrations[entity.id] = AutomaticClientRenderEntityType(
                        ClientRenderEntityType(entity.codec, rendererFactory),
                        entity.entityClass
                    )
                }
                existingEntityClass == null -> {
                    errors += "RenderEntity id already registered by an unknown entity type: ${entity.id}"
                }
                existingEntityClass != entity.entityClass -> {
                    errors += "RenderEntity id ${entity.id} belongs to ${existingEntityClass.name}, " +
                        "cannot bind ${entity.entityClass.name}"
                }
                existing.rendererFactory == null && rendererFactory != null -> {
                    registrations[entity.id] = AutomaticClientRenderEntityType(
                        existing.copy(rendererFactory = rendererFactory),
                        entity.entityClass
                    )
                }
            }
        }
        throwIfInvalid(errors, failures)
        ClientRenderEntityRegistry.applyRegistrations(registrations)
    }

    /**
     * 尝试把单个 class 注册成只有 codec 的 RenderEntity 条目。
     *
     * 保留该入口用于兼容已有测试和内部调用；完整自动注册应使用 [registerScanner]。
     */
    internal fun registerClass(clazz: Class<*>) {
        if (!RenderEntity::class.java.isAssignableFrom(clazz)) return
        val entity = describeEntity(clazz)
        if (ClientRenderEntityRegistry.get(entity.id) == null) {
            ClientRenderEntityRegistry.applyRegistrations(
                mapOf(
                    entity.id to AutomaticClientRenderEntityType(
                        ClientRenderEntityType(entity.codec),
                        entity.entityClass
                    )
                )
            )
        }
    }

    private fun loadClass(candidate: SimpleClassInfo, kind: String): Class<*> {
        try {
            return candidate.toClass(false)
        } catch (error: Exception) {
            throw IllegalStateException("Failed to load $kind class ${candidate.type}", error)
        }
    }

    private fun describeEntity(clazz: Class<*>): EntityDescriptor {
        @Suppress("UNCHECKED_CAST")
        val entityClass = clazz as Class<out RenderEntity>
        val instance = createInstance(entityClass)
        return EntityDescriptor(entityClass, instance.getRenderID(), instance.getCodec())
    }

    private fun describeRenderer(clazz: Class<*>): RendererDescriptor {
        if (!RenderEntityRenderer::class.java.isAssignableFrom(clazz)) {
            throw IllegalStateException(
                "Auto renderer must implement RenderEntityRenderer: ${clazz.name}"
            )
        }
        if (clazz.isInterface || Modifier.isAbstract(clazz.modifiers)) {
            throw IllegalStateException("Auto renderer must be a concrete class: ${clazz.name}")
        }
        val constructor = try {
            clazz.getConstructor()
        } catch (_: NoSuchMethodException) {
            throw IllegalStateException("Auto renderer requires a public no-arg constructor: ${clazz.name}")
        }
        val entityClass = resolveRendererEntityClass(clazz)
            ?: throw IllegalStateException("Cannot resolve RenderEntity type for auto renderer: ${clazz.name}")
        return RendererDescriptor(entityClass, clazz, constructor)
    }

    private fun resolveRendererEntityClass(rendererClass: Class<*>): Class<out RenderEntity>? {
        val entityType = findRendererEntityType(rendererClass, emptyMap()) ?: return null
        val resolvedClass = rawClass(resolveType(entityType, emptyMap())) ?: return null
        if (!RenderEntity::class.java.isAssignableFrom(resolvedClass)) return null
        @Suppress("UNCHECKED_CAST")
        return resolvedClass as Class<out RenderEntity>
    }

    private fun findRendererEntityType(
        type: Type,
        inheritedBindings: Map<TypeVariable<*>, Type>
    ): Type? {
        val rawClass: Class<*>
        val bindings = LinkedHashMap(inheritedBindings)
        when (type) {
            is Class<*> -> rawClass = type
            is ParameterizedType -> {
                rawClass = type.rawType as? Class<*> ?: return null
                rawClass.typeParameters.zip(type.actualTypeArguments).forEach { (variable, argument) ->
                    bindings[variable] = resolveType(argument, inheritedBindings)
                }
            }
            else -> return null
        }

        if (rawClass == RenderEntityRenderer::class.java) {
            val parameter = rawClass.typeParameters.single()
            return resolveType(bindings[parameter] ?: return null, bindings)
        }

        rawClass.genericInterfaces.forEach { parent ->
            findRendererEntityType(parent, bindings)?.let { return it }
        }
        val parent = rawClass.genericSuperclass ?: return null
        return findRendererEntityType(parent, bindings)
    }

    private fun resolveType(type: Type, bindings: Map<TypeVariable<*>, Type>): Type {
        var resolved = type
        val visited = HashSet<TypeVariable<*>>()
        while (resolved is TypeVariable<*> && visited.add(resolved)) {
            resolved = bindings[resolved] ?: return resolved
        }
        return resolved
    }

    private fun rawClass(type: Type): Class<*>? {
        return when (type) {
            is Class<*> -> type
            is ParameterizedType -> type.rawType as? Class<*>
            else -> null
        }
    }

    /**
     * 创建自动 codec 注册需要的临时实体。
     *
     * 支持公开无参构造器和公开 `(Level, Vec3)` 构造器。
     */
    private fun createInstance(type: Class<out RenderEntity>): RenderEntity {
        val noArgCtor = try {
            type.getConstructor()
        } catch (_: NoSuchMethodException) {
            null
        }
        if (noArgCtor != null) return newEntityInstance(type, noArgCtor)
        val levelVecCtor = try {
            type.getConstructor(Level::class.java, Vec3::class.java)
        } catch (_: NoSuchMethodException) {
            throw IllegalStateException(
                "RenderEntity requires public no-arg or (Level, Vec3) constructor: ${type.name}"
            )
        }
        return newEntityInstance(type, levelVecCtor, null, Vec3.ZERO)
    }

    private fun newEntityInstance(
        type: Class<out RenderEntity>,
        constructor: Constructor<out RenderEntity>,
        vararg arguments: Any?
    ): RenderEntity {
        try {
            return constructor.newInstance(*arguments)
        } catch (error: InvocationTargetException) {
            throw IllegalStateException("Failed to create RenderEntity ${type.name}", error.targetException)
        } catch (error: ReflectiveOperationException) {
            throw IllegalStateException("Failed to create RenderEntity ${type.name}", error)
        }
    }

    private fun descriptorFailure(kind: String, clazz: Class<*>, error: Exception): IllegalStateException {
        val detail = error.message?.takeIf { message -> message.isNotBlank() } ?: error.javaClass.name
        return IllegalStateException("Failed to inspect $kind ${clazz.name}: $detail", error)
    }

    private fun throwIfInvalid(errors: Collection<String>, failures: Collection<IllegalStateException>) {
        if (errors.isEmpty() && failures.isEmpty()) return
        val messages = errors + failures.mapNotNull { failure -> failure.message }
        val combined = IllegalStateException(messages.sorted().joinToString(separator = "\n"))
        failures.forEach { failure -> combined.addSuppressed(failure) }
        throw combined
    }

    private data class EntityDescriptor(
        val entityClass: Class<out RenderEntity>,
        val id: ResourceLocation,
        val codec: StreamCodec<FriendlyByteBuf, RenderEntity>
    )

    private data class RendererDescriptor(
        val entityClass: Class<out RenderEntity>,
        val rendererClass: Class<*>,
        val constructor: Constructor<*>
    ) {
        /**
         * 执行 `RendererDescriptor` 定义的 `toFactory` 操作；输入和返回值用于该组件当前的渲染职责。
         *
         * 示例：`toFactory(id = id)`。
         *
         * @param id 用于定位目标资源、实体或运行时实例的唯一标识
         */
        fun toFactory(id: ResourceLocation): () -> RenderEntityRenderer<out RenderEntity> = {
            try {
                @Suppress("UNCHECKED_CAST")
                constructor.newInstance() as RenderEntityRenderer<out RenderEntity>
            } catch (error: InvocationTargetException) {
                throw IllegalStateException(
                    "Failed to create RenderEntity renderer ${rendererClass.name} for $id",
                    error.targetException
                )
            } catch (error: ReflectiveOperationException) {
                throw IllegalStateException(
                    "Failed to create RenderEntity renderer ${rendererClass.name} for $id",
                    error
                )
            }
        }
    }
}
