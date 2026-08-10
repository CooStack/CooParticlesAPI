package cn.coostack.cooparticlesapi.renderer.runtime

import cn.coostack.cooparticlesapi.reflect.SimpleClassInfo
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.renderer.pipeline.CooPipelines
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderEntityAutoRegisterTest {
    @BeforeTest
    fun setUp() {
        ClientRenderEntityRegistry.clear()
        TestRenderer.createdCount = 0
    }

    @AfterTest
    fun tearDown() {
        ClientRenderEntityRegistry.clear()
    }

    @Test
    fun `auto registration binds codec and renderer without eagerly creating renderer`() {
        RenderEntityAutoRegistry.registerClasses(
            entityClasses = listOf(TestRenderEntity::class.java),
            rendererClasses = listOf(TestRenderer::class.java)
        )

        val type = assertNotNull(ClientRenderEntityRegistry.get(TestRenderEntity.ID))
        val rendererFactory = assertNotNull(type.rendererFactory)
        assertEquals(0, TestRenderer.createdCount)
        assertIs<TestRenderer>(rendererFactory())
        assertEquals(1, TestRenderer.createdCount)
    }

    @Test
    fun `auto registration rejects renderer whose entity codec was not discovered`() {
        val error = assertFailsWith<IllegalStateException> {
            RenderEntityAutoRegistry.registerClasses(
                entityClasses = emptyList(),
                rendererClasses = listOf(TestRenderer::class.java)
            )
        }

        assertTrue("RenderEntity codec not discovered" in error.message.orEmpty())
        assertEquals(null, ClientRenderEntityRegistry.get(TestRenderEntity.ID))
    }

    @Test
    fun `auto registration rejects duplicate renderer bindings before changing registry`() {
        val error = assertFailsWith<IllegalStateException> {
            RenderEntityAutoRegistry.registerClasses(
                entityClasses = listOf(TestRenderEntity::class.java),
                rendererClasses = listOf(TestRenderer::class.java, DuplicateTestRenderer::class.java)
            )
        }

        assertTrue("Duplicate RenderEntity renderer binding" in error.message.orEmpty())
        assertEquals(null, ClientRenderEntityRegistry.get(TestRenderEntity.ID))
    }

    @Test
    fun `auto registration rejects duplicate entity ids before changing registry`() {
        val error = assertFailsWith<IllegalStateException> {
            RenderEntityAutoRegistry.registerClasses(
                entityClasses = listOf(TestRenderEntity::class.java, ConflictingRenderEntity::class.java),
                rendererClasses = emptyList()
            )
        }

        assertTrue("Duplicate RenderEntity id" in error.message.orEmpty())
        assertEquals(null, ClientRenderEntityRegistry.get(TestRenderEntity.ID))
    }

    @Test
    fun `auto registration rejects an existing id without matching entity ownership`() {
        val existing = ClientRenderEntityType(ConflictingRenderEntity.CODEC)
        ClientRenderEntityRegistry.register(TestRenderEntity.ID, existing)

        val error = assertFailsWith<IllegalStateException> {
            RenderEntityAutoRegistry.registerClasses(
                entityClasses = listOf(TestRenderEntity::class.java),
                rendererClasses = listOf(TestRenderer::class.java)
            )
        }

        assertTrue("RenderEntity id already registered by an unknown entity type" in error.message.orEmpty())
        assertSame(existing, ClientRenderEntityRegistry.get(TestRenderEntity.ID))
    }

    @Test
    fun `auto registration is idempotent for the same owned entity type`() {
        RenderEntityAutoRegistry.registerClasses(
            entityClasses = listOf(TestRenderEntity::class.java),
            rendererClasses = listOf(TestRenderer::class.java)
        )
        val first = assertNotNull(ClientRenderEntityRegistry.get(TestRenderEntity.ID))

        RenderEntityAutoRegistry.registerClasses(
            entityClasses = listOf(TestRenderEntity::class.java),
            rendererClasses = listOf(TestRenderer::class.java)
        )

        assertSame(first, ClientRenderEntityRegistry.get(TestRenderEntity.ID))
    }

    @Test
    fun `auto registration resolves a parameterized render entity type`() {
        RenderEntityAutoRegistry.registerClasses(
            entityClasses = listOf(GenericRenderEntity::class.java),
            rendererClasses = listOf(GenericRenderer::class.java)
        )

        val type = assertNotNull(ClientRenderEntityRegistry.get(GenericRenderEntity.ID))
        assertNotNull(type.rendererFactory)
    }

    @Test
    fun `auto registration resolves entity type through a generic renderer base class`() {
        RenderEntityAutoRegistry.registerClasses(
            entityClasses = listOf(TestRenderEntity::class.java),
            rendererClasses = listOf(InheritedGenericRenderer::class.java)
        )

        val factory = assertNotNull(ClientRenderEntityRegistry.get(TestRenderEntity.ID)?.rendererFactory)
        assertIs<InheritedGenericRenderer>(factory())
    }

    @Test
    fun `auto registration rejects an unresolved renderer type variable`() {
        val error = assertFailsWith<IllegalStateException> {
            RenderEntityAutoRegistry.registerClasses(
                entityClasses = listOf(TestRenderEntity::class.java),
                rendererClasses = listOf(UnresolvedGenericRenderer::class.java)
            )
        }

        assertTrue("Cannot resolve RenderEntity type" in error.message.orEmpty())
        assertEquals(null, ClientRenderEntityRegistry.get(TestRenderEntity.ID))
    }

    @Test
    fun `metadata loading can avoid renderer static initialization`() {
        val className = StaticInitializationRenderer::class.java.name

        SimpleClassInfo(className, hashSetOf()).toClass(false)

        assertFalse(StaticInitializationState.initialized)
        Class.forName(className, true, StaticInitializationRenderer::class.java.classLoader)
        assertTrue(StaticInitializationState.initialized)
    }

    private class TestRenderEntity : RenderEntity(null, Vec3.ZERO) {
        override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = CODEC

        override fun getRenderID(): ResourceLocation = ID

        companion object {
            val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "auto_registry_test")
            val CODEC: StreamCodec<FriendlyByteBuf, RenderEntity> = createCodec(::TestRenderEntity)
        }
    }

    private class ConflictingRenderEntity : RenderEntity(null, Vec3.ZERO) {
        override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = CODEC

        override fun getRenderID(): ResourceLocation = TestRenderEntity.ID

        companion object {
            val CODEC: StreamCodec<FriendlyByteBuf, RenderEntity> = createCodec(::ConflictingRenderEntity)
        }
    }

    private class GenericRenderEntity<T> : RenderEntity(null, Vec3.ZERO) {
        override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> = CODEC

        override fun getRenderID(): ResourceLocation = ID

        companion object {
            val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
                "cooparticlesapi",
                "generic_auto_registry_test"
            )
            val CODEC: StreamCodec<FriendlyByteBuf, RenderEntity> = createCodec(
                factory = { GenericRenderEntity<String>() }
            )
        }
    }

    private class TestRenderer : RenderEntityRenderer<TestRenderEntity> {
        init {
            createdCount++
        }

        override val pipeline = CooPipelines.DEFAULT

        override fun render(input: RenderInput<TestRenderEntity>) = Unit

        companion object {
            var createdCount = 0
        }
    }

    private class DuplicateTestRenderer : RenderEntityRenderer<TestRenderEntity> {
        override val pipeline = CooPipelines.DEFAULT
        override fun render(input: RenderInput<TestRenderEntity>) = Unit
    }

    private class GenericRenderer : RenderEntityRenderer<GenericRenderEntity<String>> {
        override val pipeline = CooPipelines.DEFAULT
        override fun render(input: RenderInput<GenericRenderEntity<String>>) = Unit
    }

    private abstract class GenericRendererBase<T : RenderEntity> : RenderEntityRenderer<T> {
        override val pipeline = CooPipelines.DEFAULT
        override fun render(input: RenderInput<T>) = Unit
    }

    private class InheritedGenericRenderer : GenericRendererBase<TestRenderEntity>()

    private class UnresolvedGenericRenderer<T : RenderEntity> : RenderEntityRenderer<T> {
        override val pipeline = CooPipelines.DEFAULT
        override fun render(input: RenderInput<T>) = Unit
    }

    private object StaticInitializationState {
        var initialized = false
    }

    private class StaticInitializationRenderer : RenderEntityRenderer<TestRenderEntity> {
        override val pipeline = CooPipelines.DEFAULT
        override fun render(input: RenderInput<TestRenderEntity>) = Unit

        companion object {
            init {
                StaticInitializationState.initialized = true
            }
        }
    }
}
