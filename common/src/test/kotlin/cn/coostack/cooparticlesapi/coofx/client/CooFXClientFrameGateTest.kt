package cn.coostack.cooparticlesapi.coofx.client

import cn.coostack.cooparticlesapi.coofx.adapter.CooFxFrameRequest
import org.joml.Matrix4f
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CooFXClientFrameGateTest {
    @Test
    fun `有限 view 和 projection matrix 可以进入 runtime`() {
        assertTrue(hasFiniteCooFxFrame(Matrix4f(), Matrix4f()))
    }

    @Test
    fun `非有限 camera 世界坐标不能进入 shader uniform`() {
        assertFailsWith<IllegalArgumentException> {
            CooFxFrameRequest(
                partialTick = 0F,
                backendCapabilitySignature = "vanilla",
                viewMatrix = Matrix4f(),
                projectionMatrix = Matrix4f(),
                cameraX = Double.NaN,
            )
        }
    }

    @Test
    fun `非有限 matrix 必须在构造 frame request 前被跳过`() {
        val invalidView = Matrix4f().m00(Float.NaN)
        val invalidProjection = Matrix4f().m11(Float.POSITIVE_INFINITY)

        assertFalse(hasFiniteCooFxFrame(invalidView, Matrix4f()))
        assertFalse(hasFiniteCooFxFrame(Matrix4f(), invalidProjection))
    }
}
