# Utils（不含 buffers）

> 回到索引：[`index.md`](index.md)

你要求 “utils 下各种工具示例（除了 buffers）”。  
因为源码树无法稳定展开，我不会凭空猜每一个类名；但我会把**你肯定需要的工具类别**分组，并给出能直接复制进项目的 Kotlin 辅助函数（你也可以对照你现有的 utils，把调用替换成你库里的版本）。

---

## 1) 数学/插值（Lerp / SmoothStep）

```kotlin
object Mathx {
    fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t
    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun smoothStep(t: Float): Float {
        val x = t.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }
}
```

用途：
- 粒子生成时用 `lerpProgress` 平滑过渡
- SequencedComposition 阶段切换做缓入缓出

---

## 2) 随机工具（可重复、可控）

```kotlin
import kotlin.random.Random

object Randx {
    fun gaussian(r: Random, mean: Double = 0.0, std: Double = 1.0): Double {
        // Box–Muller
        val u1 = (r.nextDouble().coerceAtLeast(1e-12))
        val u2 = r.nextDouble()
        val z0 = kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
        return mean + z0 * std
    }
}
```

用途：
- 粒子散布的“自然抖动”
- 做噪声/能量体边缘不稳定

---

## 3) 向量/旋转（围绕、螺旋、环）

```kotlin
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

object Vec3x {
    fun ringXZ(radius: Double, angleRad: Double, y: Double = 0.0): Vec3 =
        Vec3(cos(angleRad) * radius, y, sin(angleRad) * radius)

    fun spiral(radius: Double, angleRad: Double, height: Double): Vec3 =
        Vec3(cos(angleRad) * radius, height, sin(angleRad) * radius)
}
```

用途：
- 光环/法阵/螺旋上升粒子

---

## 4) 安全限制（别让人把参数调爆）

```kotlin
object Clampx {
    fun finite(v: Float, fallback: Float = 0f): Float =
        if (v.isFinite()) v else fallback

    fun countLimit(n: Int, max: Int): Int = n.coerceIn(0, max)
}
```

用途：
- 你在 [`emitters.md`](emitters.md) 里说的“极端用户”情况
- 服务器下发参数必须做兜底

---

## 5) 相对坐标（RelativeLocation）辅助

如果你的库里 `RelativeLocation` 是一个“相对偏移”，建议提供：
- 从 Vec3 -> RelativeLocation
- RelativeLocation -> 世界坐标（基于 DisplayEntity 坐标）

示例（伪代码）：

```kotlin
fun RelativeLocation.setFromVec3(v: Vec3) {
    // x = v.x; y = v.y; z = v.z
}
```

---

下一篇：
- [完整示例：框架应用 Demo](framework-example.md)
