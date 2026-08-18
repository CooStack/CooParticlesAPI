package cn.coostack.cooparticlesapi.test.api

import cn.coostack.cooparticlesapi.test.APITestGroupBuilder
import cn.coostack.cooparticlesapi.test.TestManager
import cn.coostack.cooparticlesapi.test.block.BlockTestGroup
import cn.coostack.cooparticlesapi.test.block.BlockTestPlayer
import cn.coostack.cooparticlesapi.test.block.builtin.BlockAPITestGroupBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import sun.misc.Unsafe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TestApiPlayerAndResourceIdContractTest {
    @Test
    fun `test group ids use resource locations`() {
        assertEquals(
            ResourceLocation::class.java,
            TestGroupBuilder::class.java.getMethod("groupID").returnType
        )
        assertEquals(
            ResourceLocation::class.java,
            TestGroup::class.java.getMethod("groupID").returnType
        )
    }

    @Test
    fun `builtin builders return their resource ids`() {
        val field = Unsafe::class.java.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null) as Unsafe
        val apiBuilder = unsafe.allocateInstance(APITestGroupBuilder::class.java) as APITestGroupBuilder
        val blockBuilder = unsafe.allocateInstance(BlockAPITestGroupBuilder::class.java) as BlockAPITestGroupBuilder

        assertEquals(APITestGroupBuilder.ID, apiBuilder.groupID())
        assertEquals(BlockAPITestGroupBuilder.ID, blockBuilder.groupID())
    }

    @Test
    fun `duplicate resource ids replace the registered factory`() {
        val id = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "registration-id-contract")
        val replacement = ResourceLocation.fromNamespaceAndPath("cooparticlesapi", "registration-id-contract")
        val firstFactory: (Player) -> TestGroupBuilder = { error("factory must not run") }
        val replacementFactory: (Player) -> TestGroupBuilder = { error("factory must not run") }
        TestManager.register(id, firstFactory)
        TestManager.register(replacement, replacementFactory)

        try {
            assertEquals(1, TestManager.registeredIds().count { registeredId -> registeredId == id })
            assertSame(replacementFactory, TestManager.builders[id])
        } finally {
            TestManager.builders.remove(id)
        }
    }

    @Test
    fun `block test group and builder retain player contract`() {
        assertTrue(
            BlockTestGroup::class.java.declaredConstructors.any { constructor ->
                constructor.parameterTypes.contentEquals(
                    arrayOf(Player::class.java, ResourceLocation::class.java)
                )
            }
        )
        assertFalse(
            BlockTestGroup::class.java.declaredConstructors.any { constructor ->
                BlockTestPlayer::class.java in constructor.parameterTypes
            }
        )
        assertEquals(
            Player::class.java,
            BlockAPITestGroupBuilder::class.java.declaredFields.single { field ->
                field.type == Player::class.java
            }.type
        )
        assertFalse(
            BlockAPITestGroupBuilder::class.java.declaredFields.any { field ->
                field.type == BlockTestPlayer::class.java
            }
        )
    }

    @Test
    fun `block test manager methods accept player`() {
        listOf(
            "registeredBlockIds",
            "containsBlock",
            "buildBlock",
            "optionCount",
            "optionIds",
            "optionParamSpecs"
        ).forEach { methodName ->
            val methods = TestManager::class.java.declaredMethods.filter { method ->
                method.name == methodName
            }
            assertTrue(methods.isNotEmpty(), methodName)
            assertTrue(methods.all { method -> Player::class.java in method.parameterTypes }, methodName)
            assertFalse(methods.any { method -> BlockTestPlayer::class.java in method.parameterTypes }, methodName)
        }
    }

}
