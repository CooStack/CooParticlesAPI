package cn.coostack.cooparticlesapi.renderer.terrain

import io.netty.buffer.Unpooled
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CooTerrainMappingRegionTest {
    @Test
    fun `sphere contains boundary and rejects outside block centers`() {
        val sphere = CooTerrainMappingRegion.Sphere(Vec3(0.5, 0.5, 0.5), 1.0)
        assertTrue(sphere.contains(Vec3(0.5, 0.5, 0.5)))
        assertFalse(sphere.contains(Vec3(2.5, 0.5, 0.5)))
        assertEquals(-1, sphere.bounds().minX)
    }

    @Test
    fun `sphere section intersection rejects boxes outside the curved boundary`() {
        val sphere = CooTerrainMappingRegion.Sphere(Vec3.ZERO, 10.0)

        assertTrue(sphere.intersects(-2, -2, -2, 2, 2, 2))
        assertFalse(sphere.intersects(8, 8, 8, 23, 23, 23))
    }

    @Test
    fun `box contains boundaries and uses exact axis aligned bounds`() {
        val box = CooTerrainMappingRegion.Box(Vec3.ZERO, Vec3(2.0, 3.0, 4.0))

        assertTrue(box.contains(Vec3(2.0, 3.0, 4.0)))
        assertFalse(box.contains(Vec3(2.01, 0.0, 0.0)))
        assertEquals(-2, box.bounds().minX)
        assertEquals(4, box.bounds().maxZ)
        assertTrue(box.intersects(-2, -3, -4, 2, 3, 4))
        assertFalse(box.intersects(3, -1, -1, 6, 1, 1))
    }

    @Test
    fun `cylinder contains radial and height boundaries`() {
        val cylinder = CooTerrainMappingRegion.Cylinder(Vec3.ZERO, 3.0, 8.0)

        assertTrue(cylinder.contains(Vec3(3.0, 4.0, 0.0)))
        assertFalse(cylinder.contains(Vec3(3.01, 0.0, 0.0)))
        assertFalse(cylinder.contains(Vec3(0.0, 4.01, 0.0)))
        assertTrue(cylinder.intersects(-1, 3, -1, 1, 5, 1))
        assertFalse(cylinder.intersects(4, -1, -1, 8, 1, 1))
    }

    @Test
    fun `region type ids preserve stable shape values`() {
        assertEquals(CooTerrainMappingRegionType.SPHERE, CooTerrainMappingRegionType.fromId(CooTerrainMappingRegionType.SPHERE.id))
        assertEquals(CooTerrainMappingRegionType.BOX, CooTerrainMappingRegionType.fromId(CooTerrainMappingRegionType.BOX.id))
        assertEquals(CooTerrainMappingRegionType.CYLINDER, CooTerrainMappingRegionType.fromId(CooTerrainMappingRegionType.CYLINDER.id))
        assertEquals(0, CooTerrainMappingRegionType.SPHERE.shaderValue)
        assertEquals(1, CooTerrainMappingRegionType.BOX.shaderValue)
        assertEquals(2, CooTerrainMappingRegionType.CYLINDER.shaderValue)
    }

    @Test
    fun `version two round trips every built in region`() {
        val regions = listOf(
            CooTerrainMappingRegion.Sphere(Vec3(1.0, 2.0, 3.0), 4.0),
            CooTerrainMappingRegion.Box(Vec3(-1.0, 0.5, 8.0), Vec3(2.0, 3.0, 4.0)),
            CooTerrainMappingRegion.Cylinder(Vec3(5.0, -2.0, 7.0), 3.5, 9.0),
        )
        regions.forEach { expected ->
            val buffer = FriendlyByteBuf(Unpooled.buffer())
            try {
                expected.encode(buffer)
                assertEquals(expected, CooTerrainMappingRegion.decode(buffer))
            } finally {
                buffer.release()
            }
        }
    }

    @Test
    fun `version one decodes legacy sphere only`() {
        val sphereBuffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            sphereBuffer.writeVarInt(1)
            sphereBuffer.writeResourceLocation(CooTerrainMappingRegionType.SPHERE.id)
            sphereBuffer.writeDouble(1.0)
            sphereBuffer.writeDouble(2.0)
            sphereBuffer.writeDouble(3.0)
            sphereBuffer.writeDouble(4.0)
            assertEquals(
                CooTerrainMappingRegion.Sphere(Vec3(1.0, 2.0, 3.0), 4.0),
                CooTerrainMappingRegion.decode(sphereBuffer),
            )
        } finally {
            sphereBuffer.release()
        }

        val boxBuffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            boxBuffer.writeVarInt(1)
            boxBuffer.writeResourceLocation(CooTerrainMappingRegionType.BOX.id)
            assertFailsWith<IllegalArgumentException> { CooTerrainMappingRegion.decode(boxBuffer) }
        } finally {
            boxBuffer.release()
        }
    }

    @Test
    fun `region decoder rejects unknown and truncated payloads`() {
        val unknownBuffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            unknownBuffer.writeVarInt(2)
            unknownBuffer.writeResourceLocation(ResourceLocation.fromNamespaceAndPath("test", "unknown"))
            assertFails { CooTerrainMappingRegion.decode(unknownBuffer) }
        } finally {
            unknownBuffer.release()
        }

        val truncatedBuffer = FriendlyByteBuf(Unpooled.buffer())
        try {
            truncatedBuffer.writeVarInt(2)
            truncatedBuffer.writeResourceLocation(CooTerrainMappingRegionType.SPHERE.id)
            truncatedBuffer.writeDouble(1.0)
            assertFails { CooTerrainMappingRegion.decode(truncatedBuffer) }
        } finally {
            truncatedBuffer.release()
        }
    }

    @Test
    fun `region wire contract preserves all tagged union branches`() {
        val region = projectSource(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingRegion.kt"
        )

        assertTrue("private const val WIRE_VERSION = 2" in region)
        assertTrue("buffer.writeVarInt(WIRE_VERSION)" in region)
        assertTrue("buffer.writeResourceLocation(type.id)" in region)
        assertTrue("buffer.writeDouble(center.x)" in region)
        assertTrue("buffer.writeDouble(center.y)" in region)
        assertTrue("buffer.writeDouble(center.z)" in region)
        assertTrue("buffer.writeDouble(radius)" in region)
        assertTrue("buffer.writeDouble(halfExtents.x)" in region)
        assertTrue("buffer.writeDouble(halfExtents.y)" in region)
        assertTrue("buffer.writeDouble(halfExtents.z)" in region)
        assertTrue("buffer.writeDouble(height)" in region)
        assertTrue("val version = buffer.readVarInt()" in region)
        assertTrue("require(version in 1..WIRE_VERSION)" in region)
        assertTrue("version != 1 || type == CooTerrainMappingRegionType.SPHERE" in region)
        assertTrue("CooTerrainMappingRegionType.fromId(typeId)" in region)
        assertTrue("CooTerrainMappingRegionType.BOX -> Box(" in region)
        assertTrue("CooTerrainMappingRegionType.CYLINDER -> Cylinder(" in region)
        assertTrue("Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())" in region)
    }

    @Test
    fun `region wire contract rejects unsupported versions and types`() {
        val region = projectSource(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingRegion.kt"
        )

        assertTrue("Unsupported terrain mapping region version" in region)
        assertTrue("Unknown terrain mapping region type" in region)
    }

    @Test
    fun `all built in regions reject invalid numeric values`() {
        assertFailsWith<IllegalArgumentException> { CooTerrainMappingRegion.Sphere(Vec3.ZERO, 0.0) }
        assertFailsWith<IllegalArgumentException> { CooTerrainMappingRegion.Sphere(Vec3(Double.NaN, 0.0, 0.0), 1.0) }
        assertFailsWith<IllegalArgumentException> { CooTerrainMappingRegion.Sphere(Vec3.ZERO, Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> {
            CooTerrainMappingRegion.Box(Vec3.ZERO, Vec3(1.0, 0.0, 1.0))
        }
        assertFailsWith<IllegalArgumentException> {
            CooTerrainMappingRegion.Cylinder(Vec3.ZERO, 1.0, 0.0)
        }
    }

    private fun projectSource(path: String): String {
        val requested = Path(path)
        if (requested.exists()) return requested.readText()
        var cursor = Path(System.getProperty("user.dir")).absolute()
        while (cursor.parent != null) {
            val candidate = cursor.resolve(path)
            if (candidate.exists()) return candidate.readText()
            cursor = cursor.parent
        }
        return requested.readText()
    }
}
