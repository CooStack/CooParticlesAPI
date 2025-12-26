package cn.coostack.cooparticlesapi.reflect

import cn.coostack.cooparticlesapi.CooParticlesConstants
import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult

object CooAPIScanner {
    private val needSearchedPacket = HashSet<String>()
    private var loaded = false
    private lateinit var result: ScanResult
    private val classes = HashSet<SimpleClassInfo>()


    internal fun scan() {
        if (loaded) return
        loaded = true
        val start = System.currentTimeMillis()
        CooParticlesConstants.logger.info("开始使用ClassGraph扫描...")
        result = ClassGraph()
            .enableClassInfo()
            .enableAnnotationInfo()
            .acceptPackages(*needSearchedPacket.toTypedArray())
            .scan()
        result.allClasses.filter {
            it.annotations.isNotEmpty() // 只考虑存在类注解的类
        }.forEach {
            inputScanResult(SimpleClassInfo(it.name, it.annotations.map { it -> it.name }.toHashSet()))
        }
        val end = System.currentTimeMillis()
        CooParticlesConstants.logger.info("扫描结果处理完毕 耗时:${end - start}ms")
    }


    fun getWithAnnotation(anno: Class<out Annotation>): Collection<SimpleClassInfo> {
        return classes.filter {
            it.isAnnotationPresent(anno)
        }
    }

    fun getClassesWithAnnotation(anno: Class<out Annotation>): Collection<Class<*>> {
        return classes.filter { it.isAnnotationPresent(anno) }.map { it.toClass() }
    }

    /**
     * neoforge 需要导入的
     */
    fun inputScanResult(scan: SimpleClassInfo) {
        classes.add(scan)
    }

    /**
     * 在neoforge调用
     * 防止再次调用scan
     */
    internal fun neoLoaded() {
        loaded = true
    }

    /**
     * fabric 使用事件一定要执行这个方法
     *
     * 不然会就不会扫描事件了
     *
     * @param main
     */
    @JvmStatic
    fun registerPacket(main: Class<*>) {
        val packageName = main.packageName
        registerPacket(packageName)
    }

    @JvmStatic
    fun registerPacket(packageName: String) {
        needSearchedPacket.add(packageName)
        CooParticlesConstants.logger.info("注册事件包: $packageName")
    }
}