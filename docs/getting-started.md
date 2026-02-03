# 快速上手

> 回到索引：[`index.md`](index.md)

这一页只解决三件事：
1. 依赖怎么加
2. Fabric / NeoForge 初始化应该放哪里
3. **Fabric 必须手动注册扫描包**（否则事件系统/自动注册不会生效）

---

## 1) 添加依赖

见仓库首页 README 的“仓库/依赖设置”段落：[`../README.md`](../README.md)

---

## 2) 初始化：你需要做什么

### 你一定要做的（两端通用）

- 在**你的 mod 主类初始化**时，做一次 “CooParticlesAPI 的初始化入口调用”（如果 API 提供）
- 注册你的发射器/组合/实体等（推荐用 `@CooAutoRegister`，见 [`annotations.md`](annotations.md)）

---

## 3) Fabric：必须注册扫描包（重点）

Fabric 缺少 NeoForge 那种“自动扫描 Mod 类”的能力：

```kotlin
import cn.coostack.cooparticlesapi.reflect.CooAPIScanner

object YourModInit {
    fun init() {
        // 推荐：传主类，让它自动取包名
        CooAPIScanner.registerPacket(YourModInit::class.java)

        // 或者：手动写包名前缀（会扫描这个前缀下所有类）
        // CooAPIScanner.registerPacket("com.example.yourmod")
    }
}
```

如果你不做这一步，**事件系统**（`@EventListener` / `@EventHandler`）以及 **自动注册**（`@CooAutoRegister`）在 Fabric 下通常不会生效。  
更多见：[`fabric-neoforge.md`](fabric-neoforge.md)

---

## 4) 下一步看什么

- 想先把“事件跑起来”：[`event-bus.md`](event-bus.md)
- 想用注解自动注册：[`annotations.md`](annotations.md)
- 想看一整套能跑的 Demo：[`framework-example.md`](framework-example.md)
