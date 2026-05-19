package cn.coostack.cooparticlesapi.renderer.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RenderStateGuardTest {
    @Test
    fun `active texture slot and texture bindings are restored`() {
        val state = RenderStateGuard.MutableRenderState(
            activeTextureSlot = 3,
            textureBindings = linkedMapOf(0 to 17, 1 to 42),
            blendEnabled = false,
            blendFuncSrc = 1,
            blendFuncDst = 0,
            depthTestEnabled = true,
            depthMask = true,
            cullEnabled = true
        )
        val guard = RenderStateGuard(state)

        guard.use { mutable ->
            mutable.activeTextureSlot = 7
            mutable.textureBindings[0] = 99
            mutable.textureBindings[2] = 123
        }

        assertEquals(3, state.activeTextureSlot)
        assertEquals(17, state.textureBindings[0])
        assertEquals(42, state.textureBindings[1])
        assertFalse(2 in state.textureBindings)
    }

    @Test
    fun `blend state and depth state are restored`() {
        val state = RenderStateGuard.MutableRenderState(
            activeTextureSlot = 0,
            textureBindings = linkedMapOf(),
            blendEnabled = false,
            blendFuncSrc = 1,
            blendFuncDst = 0,
            depthTestEnabled = true,
            depthMask = true,
            cullEnabled = true
        )
        val guard = RenderStateGuard(state)

        guard.use { mutable ->
            mutable.blendEnabled = true
            mutable.blendFuncSrc = 5
            mutable.blendFuncDst = 6
            mutable.depthTestEnabled = false
            mutable.depthMask = false
            mutable.cullEnabled = false
        }

        assertFalse(state.blendEnabled)
        assertEquals(1, state.blendFuncSrc)
        assertEquals(0, state.blendFuncDst)
        assertTrue(state.depthTestEnabled)
        assertTrue(state.depthMask)
        assertTrue(state.cullEnabled)
    }
}
