# CooEventBus 事件系统

`CooEventBus` 提供轻量的事件分发能力，支持优先级、可取消与可中断事件，并且支持自动扫描监听器。

## 核心用法
### 1. 定义事件
所有事件必须继承 `CooEvent`。需要可取消/可中断时实现对应接口。

```kotlin
data class MyEvent(val player: ServerPlayer) : CooEvent(), EventCancelable {
    override var isCancelled: Boolean = false
}
```

### 2. 编写监听器
类上标注 `@EventListener(modId)`，方法上标注 `@EventHandler`。方法参数必须是 **单一事件类型**。

```kotlin
@EventListener("your_mod_id")
object MyEventListener {
    @EventHandler(EventPriority.HIGH)
    fun onMyEvent(event: MyEvent) {
        if (/* ... */) {
            event.isCancelled = true
        }
    }
}
```

### 3. 触发事件
```kotlin
val event = CooEventBus.call(MyEvent(player))
if (event.isCancelled) {
    return
}
```

## 事件优先级与中断
- `EventPriority` 顺序：`HIGHEST -> HIGH -> NORMAL -> LOW -> LOWEST`。
- 若事件实现 `EventInterruptible`，当 `isInterrupted = true` 时，**当前事件类型后续监听器不会再执行**，但仍会继续处理父类事件链。
- `EventCancelable` 只是状态字段，是否取消由调用方自行判断。

## 扫描与注册
扫描由 API 自动触发，无需手动调用 `scan()` 或 `loadScannerPackages()`。
### Fabric
只需要注册扫描包：
```kotlin
CooAPIScanner.registerPacket("your.mod.package")
```

### NeoForge
无需额外调用。

## 手动注册（不走扫描）
如果你想完全手动控制监听器：
```kotlin
CooEventBus.appendListenerTarget("your_mod_id", "com.example.MyEventListener")
CooEventBus.initListeners()
```

> 提示：扫描与手动注册可以混用，但要确保 `initListeners()` 在所有监听器都已注册之后执行。
