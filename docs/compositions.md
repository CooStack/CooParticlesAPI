# 组合系统：ParticleComposition / SequencedParticleComposition

> 回到索引：[`index.md`](index.md)

你点名要的两个核心：
- `ParticleComposition`：一个“效果组合体”，通常包含若干 `ParticleEmitters`
- `SequencedParticleComposition`：带时间轴/序列控制的组合（按阶段切换/叠加 emitters）

> 本页同样以“使用模式”为主，具体函数名以源码为准。

---

## 1) ParticleComposition：组合体的职责

建议把它理解为：
- 一个容器：持有若干 emitters
- 一个调度器：在 tick/lerp 时机更新 emitters、并产出粒子生成请求

常见能力：
- `tick()`：服务端/客户端逻辑更新
- `renderTick(lerp)`：客户端插值生成粒子
- `addEmitter(...)` / `removeEmitter(...)`
- 支持被挂在 `DisplayEntity` 上

---

## 2) SequencedParticleComposition：序列组合的职责

它相比普通 composition 多了：
- 时间轴（tick 计数 / 时间片）
- 阶段（Stage）
- 阶段切换条件（到时间 / 事件触发 / 外部参数）
- 每个阶段有自己的一组 emitters

典型用途：
- “技能释放”三段式：蓄力 -> 爆发 -> 残留
- “粒子演出”编排：按固定节奏刷出不同图案/形态

---

## 3) 推荐写法（Demo）

### 3.1 普通组合

```kotlin
import cn.coostack.cooparticlesapi.autoregister.CooAutoRegister

@CooAutoRegister
class DemoAuraComposition : cn.coostack.cooparticlesapi.composition.ParticleComposition() {

    private val auraEmitter = DemoAuraEmitter()

    override fun build() {
        // 伪代码：你的 API 可能是 addEmitter / emitters += ...
        addEmitter(auraEmitter)
    }

    override fun tick() {
        super.tick()
        // 在这里调节参数（颜色、半径、强度……）
    }
}
```

### 3.2 序列组合（Stage）

```kotlin
@CooAutoRegister
class DemoSpellSequence : cn.coostack.cooparticlesapi.composition.SequencedParticleComposition() {

    override fun buildSequence() {
        // 伪代码：你可能有 stage("charge", duration=20) { ... }
        stage("charge", duration = 20) { addEmitter(ChargeEmitter()) }
        stage("burst", duration = 5) { addEmitter(BurstEmitter()) }
        stage("afterglow", duration = 40) { addEmitter(AfterglowEmitter()) }
    }
}
```

---

## 4) 把 composition 挂到 DisplayEntity

见：[`entities.md`](entities.md) 与 [`framework-example.md`](framework-example.md)

---

下一篇：
- [发射器：ParticleEmitters](emitters.md)
