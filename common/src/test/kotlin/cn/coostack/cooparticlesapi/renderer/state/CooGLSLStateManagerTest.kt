package cn.coostack.cooparticlesapi.renderer.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CooGLSLStateManagerTest {
    @Test
    fun `manual states restore in last in first out order`() {
        var current = "root"
        val stack = GLSLStateStack(captureState = { current }, restoreState = { current = it })

        stack.createState()
        current = "first"
        stack.createState()
        current = "second"

        assertEquals("first", stack.resetState())
        assertEquals("first", current)
        assertEquals("root", stack.resetState())
        assertEquals("root", current)
    }

    @Test
    fun `managed states can be nested`() {
        var current = "root"
        val stack = GLSLStateStack(captureState = { current }, restoreState = { current = it })

        stack.useState {
            current = "outer"
            stack.useState {
                current = "inner"
            }
            assertEquals("outer", current)
        }

        assertEquals("root", current)
    }

    @Test
    fun `managed state restores after block failure`() {
        var current = "root"
        val stack = GLSLStateStack(captureState = { current }, restoreState = { current = it })
        val expected = IllegalArgumentException("draw failed")

        val actual = assertFailsWith<IllegalArgumentException> {
            stack.useState {
                current = "changed"
                throw expected
            }
        }

        assertSame(expected, actual)
        assertEquals("root", current)
    }

    @Test
    fun `managed state reports and cleans leaked manual states`() {
        var current = "root"
        val restored = mutableListOf<String>()
        val stack = GLSLStateStack(
            captureState = { current },
            restoreState = {
                current = it
                restored += it
            }
        )

        val error = assertFailsWith<IllegalStateException> {
            stack.useState {
                current = "manual-entry"
                stack.createState()
                current = "leaked"
            }
        }

        assertTrue(error.message.orEmpty().contains("1 createState()"))
        assertEquals(listOf("manual-entry", "root"), restored)
        assertEquals("root", current)
    }

    @Test
    fun `manual reset cannot close managed scope`() {
        var current = "root"
        val stack = GLSLStateStack(captureState = { current }, restoreState = { current = it })

        val error = assertFailsWith<IllegalStateException> {
            stack.useState {
                current = "changed"
                stack.resetState()
            }
        }

        assertTrue(error.message.orEmpty().contains("managed useState scope"))
        assertEquals("root", current)
    }

    @Test
    fun `reset rejects an empty stack`() {
        val stack = GLSLStateStack(captureState = { Unit }, restoreState = {})

        val error = assertFailsWith<IllegalStateException> {
            stack.resetState()
        }

        assertTrue(error.message.orEmpty().contains("stack is empty"))
    }

    @Test
    fun `render boundary reports and cleans leaked manual states`() {
        var current = "root"
        val restored = mutableListOf<String>()
        val stack = GLSLStateStack(
            captureState = { current },
            restoreState = {
                current = it
                restored += it
            }
        )

        stack.createState()
        current = "first"
        stack.createState()
        current = "second"

        val error = assertFailsWith<IllegalStateException> {
            stack.assertEmpty()
        }

        assertTrue(error.message.orEmpty().contains("2 createState()"))
        assertEquals(listOf("first", "root"), restored)
        assertEquals("root", current)
        stack.assertEmpty()
    }
}
