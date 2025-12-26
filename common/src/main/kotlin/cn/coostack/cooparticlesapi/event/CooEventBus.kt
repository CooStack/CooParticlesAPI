package cn.coostack.cooparticlesapi.event

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.events.EventHandler
import cn.coostack.cooparticlesapi.annotations.events.EventListener
import cn.coostack.cooparticlesapi.event.api.CooEvent
import cn.coostack.cooparticlesapi.event.api.EventExecutor
import cn.coostack.cooparticlesapi.event.api.EventInterruptible
import cn.coostack.cooparticlesapi.event.api.EventPriority
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner
import java.lang.reflect.Modifier
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

object CooEventBus {
    /**
     * 这里记录所有需要创建实例化的Info对象
     */
    private val needListened = ConcurrentHashMap<String, HashSet<String>>()

    private var loaded = false

    private var init = false

    private val handlerLists =
        HashMap<Class<out CooEvent>, TreeMap<EventPriority, MutableList<EventExecutor>>>()

    /**
     * 在进行扫描的时候已经加载完所有类了罢
     * Fabric需要调用此方法进行类扫描
     * NeoForge则不需要再次扫描
     */
    fun scanListeners() {
        // 进行classpath搜索
        if (loaded || init) {
            return
        }
        loaded = true
        val start = System.currentTimeMillis()
        // class path
        CooAPIScanner.getClassesWithAnnotation(EventListener::class.java)
            .forEach {
                val value = it.getAnnotation(EventListener::class.java)
                appendListenerTarget(value?.modId ?: CooParticlesConstants.MOD_ID, it.name)
            }
        val end = System.currentTimeMillis()
        CooParticlesConstants.logger.info("事件扫描完毕 耗时 ${end - start} ms")
    }


    fun appendListenerTarget(modId: String, target: String) {
        needListened.getOrPut(modId) { HashSet() }.add(target)
    }

    @JvmStatic
    fun call(event: CooEvent) {
        val handleList = handlerLists[event::class.java] ?: return
        handleList.forEach {
            for (executor in it.value) {
                runCatching {
                    executor.executor.accept(event)
                }.onFailure { err ->
                    CooParticlesConstants.logger.error(
                        "处理 事件:${event::class.java.name} 时出现错误: 监听模组：${executor.modId}",
                        err
                    )
                }
                // 事件中断
                if (event is EventInterruptible && event.isInterrupted) {
                    return
                }
            }
        }
    }

    fun initListeners() {
        if (init) {
            return
        }
        init = true
        needListened.forEach { (modId, eventListeners) ->
            // 这里要搜索具体events
            // 要求就是 只有一个方法 并且注释了EventHandler的
            // 这里直接存储Listener实例 并且直接执行 需要包装 Listener 和 methods
            // 不过首先会判断他是否拥有INSTANCE实例（object兼容）
            // 然后再尝试进行实例化 (newInstance)
            // modId好像没有用 但是又好像是有用的 az
            eventListeners.forEach {
                findListenerHandlers(it, modId)
            }
        }
    }

    private fun findListenerHandlers(target: String, modId: String) {
        val clazz = Class.forName(target)
        // 获取instance
        val instance =
            clazz.declaredFields.find { it.name == "INSTANCE" && Modifier.isStatic(it.modifiers) }?.get(null)
                ?: clazz.getDeclaredConstructor()
                    .apply { isAccessible = true }
                    .newInstance()
        clazz.declaredMethods.filter {
            it.isAnnotationPresent(EventHandler::class.java) &&
                    it.parameterCount == 1 &&
                    CooEvent::class.java.isAssignableFrom(it.parameterTypes[0])
        }.forEach {
            it.isAccessible = true
            val eventType = it.parameterTypes[0] as Class<out CooEvent>
            val handlerAnnotation = it.getAnnotation(EventHandler::class.java)!!
            handlerLists.getOrPut(eventType) { TreeMap() }
                .getOrPut(handlerAnnotation.priority) {
                    ArrayList()
                }.add(EventExecutor(modId) { e ->
                    it.invoke(instance, e)
                })
        }
    }

}