# 注解：@CodecField / @CooAutoRegister

> 回到索引：[`index.md`](index.md)

你要求统一 Kotlin、并且强调 “Fabric 需要在主类注册包名”。这页会把“怎么写、怎么踩坑、怎么解决”一次讲完。

---

## 1) @CodecField：把字段纳入 Codec 同步

用途（按 README 示例推断）：
- 标记一个属性需要参与“自动生成的 codec”
- 使服务端在同步发射器/组合数据时，把这些字段一起传到客户端

典型场景：
- 发射方向、颜色、强度、寿命等参数
- 模板粒子数据（例如 `ControlableParticleData`）的初始值

示例（来自 README 结构，细节做了 Kotlin 化整理）：

```kotlin
import cn.coostack.cooparticlesapi.codec.CodecField
import net.minecraft.world.phys.Vec3

class TestEventEmitter(pos: Vec3, level: net.minecraft.world.level.Level) :
    cn.coostack.cooparticlesapi.emitters.ClassParticleEmitters(pos, level) {

    @CodecField
    var shootDirection: Vec3 = Vec3.ZERO

    @CodecField
    var templateData: cn.coostack.cooparticlesapi.particles.ControlableParticleData =
        cn.coostack.cooparticlesapi.particles.ControlableParticleData()
}
```

### 常见坑
- **不要标记无法序列化的类型**：Codec 如果不支持，客户端解码就会炸。
- **可变对象要小心**：如果字段是可变对象（例如 data 模板），建议：
  - 每次生成粒子时 clone，避免多个粒子共享同一个对象引用（README 也是这么做的）

---

## 2) @CooAutoRegister：自动注册发射器/组合/实体等

用途（按 README 描述）：
- 让库在启动扫描时，自动把你的类注册到内部注册表
- 避免你手写 registry + codec glue

关键要求（README 已写）：
- 提供**空构造函数**，或提供 `(pos: Vec3, level: Level)` 这种标准构造
- 类需要能被扫描到（Fabric 要先登记扫描包）

示例：

```kotlin
import cn.coostack.cooparticlesapi.autoregister.CooAutoRegister
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

@CooAutoRegister
class CustomEmitters(pos: Vec3, level: Level) :
    cn.coostack.cooparticlesapi.emitters.ClassParticleEmitters(pos, level) {

    companion object {
        const val ID = "custom-emitters-demo"
    }

    override fun doTick() {
        // 服务端 tick / 或内部逻辑 tick（依实现）
    }
}
```

---

## 3) Fabric 必做：注册扫描包（否则自动注册无效）

```kotlin
import cn.coostack.cooparticlesapi.CooAPIScanner

object YourModMain {
    fun init() {
        CooAPIScanner.registerPacket(YourModMain::class.java)
        // 之后你再触发事件/使用 emitters 才会生效
    }
}
```

---

## 4) 你应该怎么组织包结构（建议）

- `com.yourmod.particles.emitters.*`
- `com.yourmod.particles.compositions.*`
- `com.yourmod.particles.entities.*`
- `com.yourmod.events.*`

然后在 Fabric 里只注册一次根包：
- `registerPacket("com.yourmod")`

---

下一篇：
- [发射器：ParticleEmitters](emitters.md)
- [组合系统：ParticleComposition / SequencedParticleComposition](compositions.md)
