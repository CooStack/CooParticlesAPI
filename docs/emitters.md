# 发射器：ParticleEmitters

> 回到索引：[`index.md`](index.md)

你在 README 里已经给了一个非常关键的“自动生成 + codec + tick”的发射器结构。  
这里把它整理成一套你可以复用的模板。

---

## 1) ParticleEmitters 的核心思想

把它看成“粒子生成器”：
- 输入：时间（tick/lerp）、自身状态（@CodecField 同步字段）、外部环境（level/pos）
- 输出：一组 `(粒子数据, 相对位置)` 或类似结构
- 每个粒子可以绑定一个 `ParticleControler`，用于在之后的 tick 中更新其行为（物理、颜色渐变等）

---

## 2) 一个推荐模板（贴近 README）

```kotlin
import cn.coostack.cooparticlesapi.autoregister.CooAutoRegister
import cn.coostack.cooparticlesapi.codec.CodecField
import cn.coostack.cooparticlesapi.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.particles.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.RelativeLocation
import cn.coostack.cooparticlesapi.particles.control.ParticleControler
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

@CooAutoRegister
class DemoAuraEmitter(pos: Vec3, level: Level) : ClassParticleEmitters(pos, level) {

    @CodecField
    var template: ControlableParticleData = ControlableParticleData()

    @CodecField
    var radius: Float = 2.5f

    override fun doTick() {
        // 发射器自己的 tick（例如改变半径、旋转速度）
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        // 注意：clone，避免共享同一个 data 实例
        val data = template.clone().apply {
            // 伪字段：速度/颜色/寿命 等以你实际 data 字段为准
            // velocity = ...
        }
        val loc = RelativeLocation().apply {
            // 伪字段：相对坐标设置，以你实现为准
            // x = cos(...) * radius; y = ...; z = sin(...) * radius
        }
        return listOf(data to loc)
    }

    override fun singleParticleAction(
        controler: ParticleControler,
        data: ControlableParticleData,
        spawnPos: RelativeLocation,
        spawnWorld: Level,
        particleLerpProgress: Float,
        posLerpProgress: Float,
    ) {
        // 对“单个粒子”附加后续行为
        controler.addPreTickAction {
            // 例如：简易物理、向外扩散、渐隐等
            // updatePhysics(spawnPos, data, this) // 如果你 utils 里有现成方法就直接用
        }
    }
}
```

---

## 3) 你应该怎么做“危险参数”防护（给极端用户）

为了避免别人把你的效果调到把客户端干爆：
- 对 `radius / count / lifetime / speed` 做上限：
  - `radius = radius.coerceIn(0.0f, 64.0f)`
  - 单 tick 生成粒子数限制（比如 512）
- 对负值做纠正：
  - `lifetime = max(lifetime, 1)`
- 对 NaN / Infinity 兜底：
  - `if (!radius.isFinite()) radius = 0f`

建议把这些写在 `doTick()` 或 setter 里统一处理。

---

下一篇：
- [组合系统：ParticleComposition / SequencedParticleComposition](compositions.md)
- [完整示例：框架应用 Demo](framework-example.md)
