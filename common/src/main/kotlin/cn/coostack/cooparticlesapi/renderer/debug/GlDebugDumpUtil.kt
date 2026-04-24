package cn.coostack.cooparticlesapi.renderer.debug

import net.minecraft.client.Minecraft
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL33.GL_RGBA
import org.lwjgl.opengl.GL33.GL_TEXTURE_2D
import org.lwjgl.opengl.GL33.GL_TEXTURE_BINDING_2D
import org.lwjgl.opengl.GL33.GL_UNSIGNED_BYTE
import org.lwjgl.opengl.GL33.glBindTexture
import org.lwjgl.opengl.GL33.glGetInteger
import org.lwjgl.opengl.GL33.glGetTexImage
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

object GlDebugDumpUtil {
    fun dumpTexture(textureId: Int, width: Int, height: Int, fileName: String) {
        if (textureId <= 0 || width <= 0 || height <= 0) {
            return
        }
        val outputDir: Path = Minecraft.getInstance().gameDirectory.toPath().resolve("cooparticlesapi-debug")
        Files.createDirectories(outputDir)
        val pixelBuffer = BufferUtils.createByteBuffer(width * height * 4)
        val previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D)
        glBindTexture(GL_TEXTURE_2D, textureId)
        glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixelBuffer)
        glBindTexture(GL_TEXTURE_2D, previousTexture)

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val sourceIndex = ((height - 1 - y) * width + x) * 4
                val r = pixelBuffer.get(sourceIndex).toInt() and 0xFF
                val g = pixelBuffer.get(sourceIndex + 1).toInt() and 0xFF
                val b = pixelBuffer.get(sourceIndex + 2).toInt() and 0xFF
                val a = pixelBuffer.get(sourceIndex + 3).toInt() and 0xFF
                image.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
        ImageIO.write(image, "png", outputDir.resolve(fileName).toFile())
    }
}
