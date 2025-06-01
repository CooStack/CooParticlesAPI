package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.config.APIConfigManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlinx.coroutines.runBlocking
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleTextureSheet
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.BufferRenderer
import net.minecraft.client.render.BuiltBuffer
import net.minecraft.client.render.Camera
import net.minecraft.client.render.Tessellator
import net.minecraft.client.texture.TextureManager
import net.minecraft.util.crash.CrashException
import net.minecraft.util.crash.CrashReport
import org.joml.Vector3d
import java.util.ArrayList
import java.util.Arrays
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask

/**
 * TODO 会导致游戏奔溃
 */
object ParticleAsyncRenderHelper {
    @JvmStatic
    val threadCount: Int
        get() = APIConfigManager.getConfig().calculateThreadCount
    private var scope = CoroutineScope(newFixedThreadPoolContext(threadCount, "particle-async"))
    /**
     * 异步渲染粒子
     * TODO 会因为非法访问内存导致崩溃(原因未知)
     * 好像是顶点渲染的部分导致的
     */
    fun renderParticlesAsync(
        particles: Array<Any>,
        instance: ParticleTextureSheet,
        textureManager: TextureManager,
        camera: Camera,
        tickDelta: Float
    ) {
        var actualThreads = threadCount
        val size = particles.size
        if (size <= actualThreads) {
            actualThreads = particles.size
        }
        // 计算每一个线程处理的点的平均个数
        val taskPreThreadCount = size / actualThreads
        var notHandledTaskCount = size % actualThreads
        // 划分索引范围 从0开始
        // 索引计算规则如下 从0开始 到 taskPreThreadCount + n 结束 左闭右开
        // 下一个thread就是 taskPreThreadCount + n 开始 n一般为1或者0
        var currentIndex = 0
        val tasks = ArrayList<Deferred<BufferBuilder>>()
        repeat(actualThreads) {
            val tessellator = Tessellator.getInstance()
            var next = currentIndex + taskPreThreadCount // 取到  taskHandledIndexStart ..< next
            if (notHandledTaskCount > 0) {
                next++
                notHandledTaskCount--
            }
            val taskHandledIndexStart = currentIndex
            currentIndex = next
            val builder = instance.begin(tessellator, textureManager) ?: return@repeat
            // 复用子数组 taskHandledIndexStart - next
            val array = particles.sliceArray(taskHandledIndexStart..<next)
            // 创建任务

            val job = scope.async {
                submitParticlesRender(array, builder, camera, tickDelta)
            }
            tasks.add(job)
        }
        runBlocking {
            tasks.awaitAll().forEach { builder ->
                val built = builder.endNullable() ?: return@forEach
                BufferRenderer.drawWithGlobalProgram(built)
            }
        }
    }

    fun submitParticlesRender(
        particles: Array<out Any>,
        builder: BufferBuilder,
        camera: Camera,
        tickDelta: Float
    ): BufferBuilder {
        particles.forEach { particle ->
            render(particle, builder, camera, tickDelta)
        }
        return builder
    }

    // 渲染一个粒子
    fun render(p: Any, builder: BufferBuilder, camera: Camera, tickDelta: Float) {
        if (p !is Particle) return
        try {
            p.buildGeometry(builder, camera, tickDelta)
        } catch (throws: Throwable) {
            val crash = CrashReport.create(throws, "Rendering Particle Async by CooParticlesAPI")
            val section = crash.addElement("Particle being rendered")
            section.add("Particle", p::toString)
            section.add("Particle Type", p.type::toString)
            throw CrashException(crash)
        }
    }
}