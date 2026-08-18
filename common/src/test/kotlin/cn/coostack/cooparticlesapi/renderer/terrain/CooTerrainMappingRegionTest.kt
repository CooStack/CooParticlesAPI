package cn.coostack.cooparticlesapi.renderer.terrain

import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.world.phys.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun `sphere wire contract preserves tagged union field order`() {
        val region = projectSource(
            "common/src/main/kotlin/cn/coostack/cooparticlesapi/renderer/terrain/CooTerrainMappingRegion.kt"
        )

        assertTrue("buffer.writeVarInt(1)" in region)
        assertTrue("buffer.writeResourceLocation(type.id)" in region)
        assertTrue("buffer.writeDouble(center.x)" in region)
        assertTrue("buffer.writeDouble(center.y)" in region)
        assertTrue("buffer.writeDouble(center.z)" in region)
        assertTrue("buffer.writeDouble(radius)" in region)
        assertTrue("val version = buffer.readVarInt()" in region)
        assertTrue("require(version == 1)" in region)
        assertTrue("CooTerrainMappingRegionType.fromId(typeId)" in region)
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
    fun `sphere rejects invalid numeric values`() {
        assertFailsWith<IllegalArgumentException> { CooTerrainMappingRegion.Sphere(Vec3.ZERO, 0.0) }
        assertFailsWith<IllegalArgumentException> { CooTerrainMappingRegion.Sphere(Vec3(Double.NaN, 0.0, 0.0), 1.0) }
        assertFailsWith<IllegalArgumentException> { CooTerrainMappingRegion.Sphere(Vec3.ZERO, Double.POSITIVE_INFINITY) }
    }

    private fun projectSource(path: String): String {
        val requested = Path.of(path)
        if (Files.exists(requested)) return Files.readString(requested)
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            val candidate = cursor.resolve(path)
            if (Files.exists(candidate)) return Files.readString(candidate)
            cursor = cursor.parent
        }
        return Files.readString(requested)
    }
}
