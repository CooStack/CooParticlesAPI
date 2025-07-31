package cn.coostack.cooparticlesapi.utils

import cn.coostack.cooparticlesapi.config.APIConfigManager
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.coroutines.*
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleTextureSheet
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.BufferRenderer
import net.minecraft.client.render.Camera
import net.minecraft.client.render.Tessellator
import net.minecraft.client.texture.TextureManager
import net.minecraft.particle.ParticleGroup
import net.minecraft.util.crash.CrashException
import net.minecraft.util.crash.CrashReport
import java.util.concurrent.ForkJoinPool

/**
 * TODO 会导致游戏奔溃
 */
object ParticleAsyncHelper {
    @JvmStatic
    val threadCount: Int
        get() = APIConfigManager.getConfig().calculateThreadCount

    private val forkJoin = ForkJoinPool(threadCount)
    private val forkJoinTick = ForkJoinPool(threadCount)

    @OptIn(DelicateCoroutinesApi::class)
//    private var scope = CoroutineScope(newFixedThreadPoolContext(threadCount, "particle-async"))
    private var scope = CoroutineScope(forkJoin.asCoroutineDispatcher())

    @OptIn(DelicateCoroutinesApi::class)
//    private var tickScope = CoroutineScope(newFixedThreadPoolContext(threadCount, "particle-tick-async"))
    private var tickScope = CoroutineScope(forkJoinTick.asCoroutineDispatcher())
    private val tessellatorInstances = ArrayList<Tessellator>()

    init {
        repeat(threadCount) {
            tessellatorInstances.add(Tessellator())
        }
    }

    val particleAliveTickLock = ReentrantLock()
    val tickTasks = ArrayList<Deferred<Unit>>()
    fun tickParticlesAsync(
        particles: Array<Particle>,
        originCollection: MutableCollection<Particle>,
        addMethod: (ParticleGroup, Int) -> Unit?
    ) {
        var actualThreads = threadCount
        val size = particles.size
        if (size <= actualThreads) {
            actualThreads = particles.size
        }
        if (size <= 0) return
        // 计算每一个线程处理的点的平均个数
        val taskPreThreadCount = size / actualThreads
        var notHandledTaskCount = size % actualThreads
        // 划分索引范围 从0开始
        // 索引计算规则如下 从0开始 到 taskPreThreadCount + n 结束 左闭右开
        // 下一个thread就是 taskPreThreadCount + n 开始 n一般为1或者0
        var currentIndex = 0
        repeat(actualThreads) {
            var next = currentIndex + taskPreThreadCount // 取到  taskHandledIndexStart ..< next
            if (notHandledTaskCount > 0) {
                next++
                notHandledTaskCount--
            }
            val taskHandledIndexStart = currentIndex
            currentIndex = next
            // 复用子数组 taskHandledIndexStart - next
            val s = taskHandledIndexStart
            val n = next
            // 创建任务
            val job = tickScope.async {
                for (i in s..<n) {
                    val it = particles[i]
                    it.tick()
                    if (!it.isAlive) {
                        // 用锁确保线程安全
                        particleAliveTickLock.lock()
                        it.group.ifPresent { group -> addMethod(group, -1) }
                        originCollection.remove(it)
                        particleAliveTickLock.unlock()
                    }
                }
            }
            tickTasks.add(job)
        }
        runBlocking {
            tickTasks.awaitAll()
        }
        tickTasks.clear()
    }

    /**
     * 异步渲染粒子
     * 使用协程
     */
    val renderTasks = ArrayList<Deferred<BufferBuilder>>()

    @OptIn(ExperimentalCoroutinesApi::class)
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
        repeat(actualThreads) {
            val tessellator = tessellatorInstances[it]
            var next = currentIndex + taskPreThreadCount // 取到  taskHandledIndexStart ..< next
            if (notHandledTaskCount > 0) {
                next++
                notHandledTaskCount--
            }
            val taskHandledIndexStart = currentIndex
            currentIndex = next
            val builder = instance.begin(tessellator, textureManager) ?: return@repeat
            // 复用子数组 taskHandledIndexStart - next
            val s = taskHandledIndexStart
            val n = next
            // 创建任务
            val job = scope.async {
                submitParticlesRender(particles, s, n, builder, camera, tickDelta)
            }
            renderTasks.add(job)
        }
        runBlocking {
            renderTasks.awaitAll().forEach { builder ->
                val built = builder.endNullable() ?: return@forEach
                BufferRenderer.drawWithGlobalProgram(built)
            }
        }
        renderTasks.clear()
    }


    fun submitParticlesRender(
        particles: Array<out Any>,
        left: Int,
        right: Int,
        builder: BufferBuilder,
        camera: Camera,
        tickDelta: Float
    ): BufferBuilder {
        for (i in left..<right) {
            render(particles[i] ?: continue, builder, camera, tickDelta)
        }
        return builder
    }

    // 渲染一个粒子
    fun render(p: Any, builder: BufferBuilder, camera: Camera, tickDelta: Float) {
        p as Particle
        runCatching {
            p.buildGeometry(builder, camera, tickDelta)
        }.onFailure {
            val crash = CrashReport.create(it, "Rendering Particle Async by CooParticlesAPI")
            val section = crash.addElement("Particle being rendered")
            section.add("Particle", p::toString)
            section.add("Particle Type", p.type::toString)
            throw CrashException(crash)
        }
    }


}