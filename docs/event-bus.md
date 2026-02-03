# 事件系统：CooEventBus

> 回到索引：[`index.md`](index.md)

这套事件系统是“Forge 风格”的：你定义事件类 -> 写监听器类 -> 标注注解 -> 启动时自动注册 -> 触发时广播。

来自仓库 README 的关键点：
- 监听器类需要 `@EventListener`
- 监听方法需要 `@EventHandler`
- 监听方法必须**只有一个参数**，且该参数类型必须继承 `CooEvent`
- 监听器类必须有**空构造**，或提供 `static INSTANCE`（Kotlin `object` 最方便）
- 一般不需要手动注册：启动时会自动注册（Fabric 记得先注册扫描包）

---

## 1) 定义事件

```kotlin
import cn.coostack.cooparticlesapi.event.CooEvent

data class TestEvent(val name: String) : CooEvent()
```

---

## 2) 编写监听器

```kotlin
import cn.coostack.cooparticlesapi.event.CooEvent
import cn.coostack.cooparticlesapi.event.CooEventBus
import cn.coostack.cooparticlesapi.event.annotation.EventHandler
import cn.coostack.cooparticlesapi.event.annotation.EventListener

@EventListener
object DemoListener {

    @EventHandler
    fun onTest(e: TestEvent) {
        // 你的逻辑
        // e.name ...
    }
}
```

> Kotlin `object` 自动满足“单例 + 无参构造”的要求。

---

## 3) 触发事件

```kotlin
import cn.coostack.cooparticlesapi.event.CooEventBus

fun fire() {
    CooEventBus.call(TestEvent("hello"))
}
```

---

## 4) Fabric 特别提醒

如果你在 Fabric 下发现监听不触发，99% 是你忘了：

```kotlin
CooAPIScanner.registerPacket(YourModMain::class.java)
```

见：[`fabric-neoforge.md`](fabric-neoforge.md)

---

## 5) 事件系统适用场景（建议）

- “粒子效果播放请求” -> 发事件 -> 若干监听器决定怎么播放
- “服务器技能释放” -> 事件携带参数 -> 客户端效果监听器渲染
- 作为你自己 mod 内部的轻量总线（比 Fabric 的回调事件更像 Forge 的体验）

---

下一篇：
- [注解：@CodecField / @CooAutoRegister](annotations.md)
