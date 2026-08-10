package cn.coostack.cooparticlesapi.network.particle.style

import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParticleStyleManagerBinaryCompatibilityTest {
    @Test
    fun `register remains an instance method for existing consumers`() {
        val methodAccess = readMethodAccess(
            projectFile(
                "common/build/classes/kotlin/main/cn/coostack/cooparticlesapi/network/particle/style/ParticleStyleManager.class"
            ),
            "register",
            "(Ljava/lang/Class;Lcn/coostack/cooparticlesapi/network/particle/style/ParticleStyleProvider;)V"
        )

        assertTrue(methodAccess and ACC_PUBLIC != 0)
        assertFalse(methodAccess and ACC_STATIC != 0)
    }

    private fun readMethodAccess(classFile: Path, name: String, descriptor: String): Int {
        DataInputStream(Files.newInputStream(classFile)).use { input ->
            check(input.readInt() == CLASS_MAGIC) { "Not a class file: $classFile" }
            input.readUnsignedShort()
            input.readUnsignedShort()
            val constantPool = readConstantPool(input)
            input.readUnsignedShort()
            input.readUnsignedShort()
            input.readUnsignedShort()
            repeat(input.readUnsignedShort()) {
                input.readUnsignedShort()
            }
            repeat(input.readUnsignedShort()) {
                skipMember(input)
            }
            repeat(input.readUnsignedShort()) {
                val access = input.readUnsignedShort()
                val methodName = constantPool[input.readUnsignedShort()]
                val methodDescriptor = constantPool[input.readUnsignedShort()]
                repeat(input.readUnsignedShort()) {
                    skipAttribute(input)
                }
                if (methodName == name && methodDescriptor == descriptor) {
                    return access
                }
            }
        }
        error("Could not find method $name$descriptor in $classFile")
    }

    private fun readConstantPool(input: DataInputStream): Array<String?> {
        val constantPool = arrayOfNulls<String>(input.readUnsignedShort())
        var index = 1
        while (index < constantPool.size) {
            when (input.readUnsignedByte()) {
                1 -> constantPool[index] = input.readUTF()
                3, 4 -> input.skipBytes(4)
                5, 6 -> {
                    input.skipBytes(8)
                    index++
                }
                7, 8, 16, 19, 20 -> input.skipBytes(2)
                9, 10, 11, 12, 17, 18 -> input.skipBytes(4)
                15 -> input.skipBytes(3)
                else -> error("Unsupported constant pool entry in ParticleStyleManager.class")
            }
            index++
        }
        return constantPool
    }

    private fun skipMember(input: DataInputStream) {
        input.readUnsignedShort()
        input.readUnsignedShort()
        input.readUnsignedShort()
        repeat(input.readUnsignedShort()) {
            skipAttribute(input)
        }
    }

    private fun skipAttribute(input: DataInputStream) {
        input.readUnsignedShort()
        val length = input.readInt()
        var skipped = 0
        while (skipped < length) {
            skipped += input.skipBytes(length - skipped)
        }
    }

    private fun projectFile(relativePath: String): Path {
        val repoRoot = findRepoRoot()
        return repoRoot.resolve(relativePath)
    }

    private fun findRepoRoot(): Path {
        var cursor = Path.of(System.getProperty("user.dir")).toAbsolutePath()
        while (cursor.parent != null) {
            if (Files.exists(cursor.resolve("settings.gradle"))) {
                return cursor
            }
            cursor = cursor.parent
        }
        error("Could not locate repository root from ${System.getProperty("user.dir")}")
    }

    private companion object {
        const val CLASS_MAGIC = 0xCAFEBABE.toInt()
        const val ACC_PUBLIC = 0x0001
        const val ACC_STATIC = 0x0008
    }
}
