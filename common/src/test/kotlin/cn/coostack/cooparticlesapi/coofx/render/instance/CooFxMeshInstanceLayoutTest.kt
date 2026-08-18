package cn.coostack.cooparticlesapi.coofx.render.instance

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class CooFxMeshInstanceLayoutTest {
    @Test
    fun `layout derives every attribute from the same slot order`() {
        val attributes = CooFxMeshInstanceLayout.attributes(firstShaderLocation = 4)

        assertEquals(9, attributes.size)
        assertEquals((4..12).toList(), attributes.map { it.shaderLocation })
        assertEquals((0 until 144 step 16).toList(), attributes.map { it.byteOffset })
        assertEquals(List(9) { 1 }, attributes.map { it.divisor })
        assertEquals(36, CooFxMeshInstanceLayout.FLOAT_COUNT)
        assertEquals(144, CooFxMeshInstanceLayout.STRIDE_BYTES)
    }

    @Test
    fun `encode follows the independent 36 float instance ABI`() {
        val encoded = CooFxMeshInstanceLayout.encode(
            CooFxMeshInstanceData(
                currentPosition = CooFxFloat3(1.0F, 2.0F, 3.0F),
                ageTicks = 4.0F,
                previousPosition = CooFxFloat3(5.0F, 6.0F, 7.0F),
                lifetimeTicks = 8.0F,
                currentRotation = CooFxQuaternion(0.0F, 0.0F, 0.0F, 1.0F),
                previousRotation = CooFxQuaternion(0.0F, 0.0F, 0.0F, 1.0F),
                currentScale = CooFxFloat3(1.0F, 1.0F, 1.0F),
                packedLight = 10,
                previousScale = CooFxFloat3(2.0F, 2.0F, 2.0F),
                materialVariant = 11,
                color = CooFxColor(0.1F, 0.2F, 0.3F, 0.4F),
                clipTimeSeconds = 12.0F,
                previousClipTimeSeconds = 13.0F,
                playbackSpeed = 14.0F,
                clipIndex = 15,
                visibleSeed = 0x1234ABCDu,
                flags = 16,
                stableParticleIdLow24 = 17
            )
        )

        assertEquals(36, encoded.size)
        assertContentEquals(floatArrayOf(1.0F, 2.0F, 3.0F, 4.0F), encoded.copyOfRange(0, 4))
        assertContentEquals(floatArrayOf(12.0F, 13.0F, 14.0F, 15.0F), encoded.copyOfRange(28, 32))
        assertContentEquals(
            floatArrayOf(0xABCD.toFloat(), 0x1234.toFloat(), 16.0F, 17.0F),
            encoded.copyOfRange(32, 36)
        )
    }
}
