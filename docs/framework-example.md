# 完整示例：框架应用 Demo（DisplayEntity + Composition + Emitters + EventBus + AutoRegister）

> 回到索引：[`index.md`](index.md)

目标：给你一套“从 0 到能跑”的链路（结构完整、可直接抄）。  
这套 demo 效果：在玩家释放指令/技能时，服务器生成一个 DisplayEntity，在 3 个阶段播放粒子：
1) 蓄力：小半径环
2) 爆发：瞬间喷射
3) 残留：缓慢衰减的环

---

## 0) 包结构建议

```
com.yourmod
 ├─ YourModMain.kt
 ├─ events/
 ├─ particles/
 │   ├─ emitters/
 │   ├─ compositions/
 │   └─ entities/
 └─ client/   (可选：渲染相关)
```

---

## 1) 主类初始化（Fabric/NeoForge 都要）

### Fabric（必须先注册扫描包）
```kotlin
import cn.coostack.cooparticlesapi.CooAPIScanner

object YourModMain {
    const val MODID = "yourmod"

    fun init() {
        CooAPIScanner.registerPacket(YourModMain::class.java)

        // 然后做你自己的注册/初始化
        // registerCommands()
        // ...
    }
}
```

### NeoForge
同样在 mod 初始化最早期调用 `YourModMain.init()` 即可（入口方式按你工程结构来）。

---

## 2) 事件：定义 “播放技能粒子” 的请求

```kotlin
import cn.coostack.cooparticlesapi.event.CooEvent
import net.minecraft.world.entity.player.Player

data class CastSpellFxEvent(
    val caster: Player
) : CooEvent()
```

---

## 3) 监听事件：生成 DisplayEntity 并挂载组合

```kotlin
import cn.coostack.cooparticlesapi.event.annotation.EventHandler
import cn.coostack.cooparticlesapi.event.annotation.EventListener

@EventListener
object SpellFxListener {

    @EventHandler
    fun onCast(e: CastSpellFxEvent) {
        val level = e.caster.level()
        if (level.isClientSide) return

        val pos = e.caster.position().add(0.0, 1.2, 0.0)

        // 伪代码：你的 DisplayEntity 创建方式可能不同
        val display = DemoSpellDisplayEntity(level).apply {
            setPos(pos.x, pos.y, pos.z)
            setupFor(e.caster)
        }

        level.addFreshEntity(display)
    }
}
```

---

## 4) DisplayEntity：承载组合并跟随玩家

```kotlin
import cn.coostack.cooparticlesapi.autoregister.CooAutoRegister
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

@CooAutoRegister
class DemoSpellDisplayEntity(level: Level) :
    cn.coostack.cooparticlesapi.entity.DisplayEntity(level) {

    @Transient
    private var follow: Player? = null

    fun setupFor(player: Player) {
        follow = player
        val seq = DemoSpellSequenceComposition()
        // 伪：你的 API 可能是 composition = seq
        setComposition(seq)
    }

    override fun tick() {
        super.tick()
        follow?.let { p ->
            // 跟随玩家头顶
            val pos = p.position().add(0.0, 1.2, 0.0)
            setPos(pos.x, pos.y, pos.z)
        }
    }
}
```

---

## 5) SequencedParticleComposition：三段式时间轴

```kotlin
import cn.coostack.cooparticlesapi.autoregister.CooAutoRegister

@CooAutoRegister
class DemoSpellSequenceComposition :
    cn.coostack.cooparticlesapi.composition.SequencedParticleComposition() {

    override fun buildSequence() {
        stage("charge", duration = 20) {
            addEmitter(ChargeRingEmitter())
        }
        stage("burst", duration = 5) {
            addEmitter(BurstEmitter())
        }
        stage("afterglow", duration = 40) {
            addEmitter(AfterglowRingEmitter())
        }
    }
}
```

---

## 6) 三个发射器：可控参数 + 防爆

> 这里只给一个“环”的 emitter，其它两个按同样模板写就行。  
> 你要的是“完整应用”，所以我把关键点都写上（clone、限制、防 NaN）。

```kotlin
import cn.coostack.cooparticlesapi.autoregister.CooAutoRegister
import cn.coostack.cooparticlesapi.codec.CodecField
import cn.coostack.cooparticlesapi.emitters.ClassParticleEmitters
import cn.coostack.cooparticlesapi.particles.ControlableParticleData
import cn.coostack.cooparticlesapi.particles.RelativeLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.PI

@CooAutoRegister
class ChargeRingEmitter(pos: Vec3, level: Level) : ClassParticleEmitters(pos, level) {

    @CodecField var radius: Float = 0.5f
    @CodecField var countPerTick: Int = 24
    @CodecField var template: ControlableParticleData = ControlableParticleData()

    private var tickAge = 0

    override fun doTick() {
        tickAge++
        // 上限兜底
        radius = radius.coerceIn(0.0f, 6.0f)
        countPerTick = countPerTick.coerceIn(0, 128)
    }

    override fun genParticles(lerpProgress: Float): List<Pair<ControlableParticleData, RelativeLocation>> {
        if (countPerTick <= 0) return emptyList()

        val out = ArrayList<Pair<ControlableParticleData, RelativeLocation>>(countPerTick)
        val base = (tickAge + lerpProgress) * 0.25f // 旋转速度

        for (i in 0 until countPerTick) {
            val t = (i.toFloat() / countPerTick.toFloat())
            val ang = (t * (2f * PI.toFloat()) + base).toDouble()

            val loc = RelativeLocation().apply {
                // 伪字段：你的 RelativeLocation 设置方式以实现为准
                // x = cos(ang) * radius
                // z = sin(ang) * radius
                // y = 0.0
            }

            val data = template.clone().apply {
                // 伪字段：颜色/寿命/速度，以你的 ControlableParticleData 为准
            }

            out += data to loc
        }
        return out
    }
}
```

---

## 7) 触发：在任意地方 call 事件

```kotlin
import cn.coostack.cooparticlesapi.event.CooEventBus

fun castSpellFx(player: net.minecraft.world.entity.player.Player) {
    CooEventBus.call(CastSpellFxEvent(player))
}
```

---

## 8) 自定义 ShaderPipe / RenderEntity 怎么接

- 如果你需要“爆发阶段”的屏幕扭曲/发光核：参考 [`shaderpipe.md`](shaderpipe.md)
- 你可以在 `DemoSpellDisplayEntity` 的客户端侧，挂一个 `RenderEntity` 或 `ShaderPipe` 让 burst 更炸裂

---

## 9) 你要的“应用层总结”

- **服务器**只负责：何时创建 DisplayEntity + 发送参数（codec）
- **客户端**只负责：tick + 生成粒子 + 渲染
- **Fabric**最关键：`registerPacket(...)` 让扫描生效，否则整套系统看起来像“坏了”
