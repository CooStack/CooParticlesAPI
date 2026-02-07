package cn.coostack.cooparticlesapi.animation.timeline

import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.*


/**
 * 来自欸哎神力 ChatGPT5.2
 */
object Eases {
    /**
     * 线性变化（匀速）
     *
     * 整个过程变化速度恒定，没有加速也没有减速。
     * 数学上就是 f(t) = t
     *
     * 适合：
     * - 纯工具型插值
     * - 不希望有任何“动画感”的过渡
     * - 作为对照 / baseline
     */
    @JvmField
    val linear: Ease = Ease { it }

    /**
     * 缓出立方（先快后慢）
     *
     * 起始阶段变化速度快，越接近结束越慢，平滑停下。
     * 数学形式：f(t) = 1 - (1 - t)^3
     *
     * 视觉感受：
     * - 一开始“冲得很猛”
     * - 后期明显减速，稳稳落位
     *
     * 适合：
     * - 剑阵 / 粒子 “生长到位”
     * - 旋转、位移的自然停靠
     * - 大多数「出现型」动画（非常常用）
     */
    @JvmField
    val outCubic: Ease = Ease { x ->
        val t = x.coerceIn(0.0, 1.0)
        1 - (1 - t) * (1 - t) * (1 - t)
    }

    /**
     * 正弦缓入缓出（慢 → 快 → 慢）
     *
     * 起始和结束都很平缓，中段速度最快。
     * 数学形式：f(t) = (1 - cos(πt)) / 2
     *
     * 视觉感受：
     * - 像被轻轻推起来，又轻轻放下
     * - 没有明显的“冲击感”
     *
     * 适合：
     * - 往返型动画
     * - 呼吸 / 脉冲 / 悬浮感
     * - UI 或需要高度平滑的粒子运动
     */
    @JvmField
    val inOutSine: Ease = Ease { x ->
        val t = x.coerceIn(0.0, 1.0)
        (1 - cos(Math.PI * t)) / 2.0
    }

    /**
     * 指数缓出（爆发式启动，快速到位）
     *
     * 前期变化极快，后期几乎瞬间贴近目标。
     * 数学形式：f(t) = 1 - 2^(-10t)
     *
     * 视觉感受：
     * - 一下子“甩”出去
     * - 很快就接近终点，后面几乎不动
     *
     * 适合：
     * - 强烈的能量释放
     * - 瞬移 / 闪现 / 剑阵瞬间展开
     * - 需要“压缩时间感”的动画
     *
     * 注意：
     * - 动感很强，滥用会显得生硬
     */
    @JvmField
    val outExpo: Ease = Ease { x ->
        val t = x.coerceIn(0.0, 1.0)
        if (t >= 1.0) 1.0 else 1 - 2.0.pow(-10.0 * t)
    }

    /**
     * 缓入立方（先慢后快）
     *
     * 数学形式：f(t) = t³
     *
     * 视觉感受：
     * - 前期几乎不动
     * - 后期明显加速
     *
     * 适合：
     * - 蓄力动画
     * - 剑阵即将爆发前的“压缩感”
     */
    @JvmField
    val inCubic: Ease = Ease { x ->
        val t = x.coerceIn(0.0, 1.0)
        t * t * t
    }

    /**
     * 立方缓入缓出（慢 → 快 → 慢，比 sine 更有力量）
     *
     * 数学形式：
     *  t < 0.5 : 4t³
     *  t ≥ 0.5 : 1 - (-2t + 2)³ / 2
     *
     * 适合：
     * - 完整的出现 / 消失动画
     * - 剑阵展开 + 回收
     */
    @JvmField
    val inOutCubic: Ease = Ease { x ->
        val t = x.coerceIn(0.0, 1.0)
        if (t < 0.5)
            4 * t * t * t
        else
            1 - (-2 * t + 2).pow(3) / 2
    }

    /**
     * 缓出二次（比 cubic 更“柔”）
     *
     * 数学形式：f(t) = 1 - (1 - t)²
     *
     * 适合：
     * - 轻量粒子
     * - 需要一点点动画感但不抢戏的场合
     */
    @JvmField
    val outQuad: Ease = Ease { x ->
        val t = x.coerceIn(0.0, 1.0)
        1 - (1 - t) * (1 - t)
    }

    /**
     * 回弹缓出（略微超出后回弹）
     *
     * 数学形式：经典 Back 曲线
     *
     * 视觉感受：
     * - 到位前会“多冲一点”
     * - 随后回拉
     *
     * 适合：
     * - 魔法阵刻印
     * - 剑阵“咬住位置”的瞬间
     */
    @JvmField
    val outBack: Ease = outBack()

    /**
     * 回弹缓出（略微超出后回弹）
     *
     * 数学形式：经典 Back 曲线
     *
     * 视觉感受：
     * - 到位前会“多冲一点”
     * - 随后回拉
     *
     * 适合：
     * - 魔法阵刻印
     * - 剑阵“咬住位置”的瞬间
     *
     * @param overshoot 超出强度（越大越“弹”），默认值为 1.70158（与当前写法一致）
     */
    @JvmStatic
    @JvmOverloads
    fun outBack(overshoot: Double = 1.70158): Ease = Ease { x ->
        val t = x.coerceIn(0.0, 1.0)
        val c3 = overshoot + 1
        1 + c3 * (t - 1).pow(3) + overshoot * (t - 1).pow(2)
    }

    /**
     * 弹性缓出（强烈弹性，震荡衰减）
     *
     * 数学形式：Elastic out
     *
     * 视觉感受：
     * - 强烈弹性
     * - 多次震荡后稳定
     *
     * ⚠️ 注意：
     * - 非常抢戏
     * - 不适合大量粒子同时使用
     *
     * 适合：
     * - Boss 技能
     * - 终极剑阵展开
     */
    @JvmField
    val outElastic: Ease = outElastic()

    /**
     * 弹性缓出（强烈弹性，震荡衰减）
     *
     * 数学形式：Elastic out
     *
     * 视觉感受：
     * - 强烈弹性
     * - 多次震荡后稳定
     *
     * ⚠️ 注意：
     * - 非常抢戏
     * - 不适合大量粒子同时使用
     *
     * 适合：
     * - Boss 技能
     * - 终极剑阵展开
     *
     * @param period 震荡周期，越小震荡越频繁；默认值为 (2π)/3
     * @param decay  衰减强度（指数项的系数），越大衰减越快；默认值为 10
     * @param shift  相位偏移（决定第一次波峰位置），默认值为 0.75
     */
    @JvmStatic
    @JvmOverloads
    fun outElastic(
        period: Double = (2 * Math.PI) / 3,
        decay: Double = 10.0,
        shift: Double = 0.75
    ): Ease = Ease { x ->
        val t = x.coerceIn(0.0, 1.0)
        if (t == 0.0 || t == 1.0) t
        else 2.0.pow(-decay * t) * sin((t * 10 - shift) * period) + 1
    }

    /**
     * 缓出反弹（模拟重力反弹）
     *
     * 视觉感受：
     * - 像物体落地反弹
     *
     * 适合：
     * - 下落型特效
     * - 粒子落点反馈
     */
    @JvmField
    val outBounce: Ease = outBounce()

    /**
     * 缓出反弹（模拟重力反弹）
     *
     * 视觉感受：
     * - 像物体落地反弹
     *
     * 适合：
     * - 下落型特效
     * - 粒子落点反馈
     *
     * @param n1 反弹曲线系数，默认值 7.5625
     * @param d1 分段阈值系数，默认值 2.75
     */
    @JvmStatic
    @JvmOverloads
    fun outBounce(n1: Double = 7.5625, d1: Double = 2.75): Ease = Ease { x ->
        val t = x.coerceIn(0.0, 1.0)

        when {
            t < 1 / d1 -> n1 * t * t
            t < 2 / d1 -> {
                val x = t - 1.5 / d1
                n1 * x * x + 0.75
            }

            t < 2.5 / d1 -> {
                val x = t - 2.25 / d1
                n1 * x * x + 0.9375
            }

            else -> {
                val x = t - 2.625 / d1
                n1 * x * x + 0.984375
            }
        }
    }

    /**
     * 基于三次贝塞尔曲线的 Ease
     *
     * P0 = (0, 0)
     * P1 = startHandle
     * P2 = target + endHandle
     * P3 = target
     *
     * @return y / target.y （归一化到 [0,1]）
     */
    fun bezierEase(
        startHandle: RelativeLocation,
        endHandle: RelativeLocation
    ): Ease {
        val target = RelativeLocation(1, 1, 0)
        val end = target + endHandle

        return Ease { x ->
            val t = x.coerceIn(0.0, 1.0)

            val u = 1.0 - t
            val u2 = u * u
            val t2 = t * t

            val y =
                (u2 * u * 0.0) +
                        (3 * u2 * t * startHandle.y) +
                        (3 * u * t2 * end.y) +
                        (t2 * t * target.y)

            if (target.y == 0.0) 0.0
            else (y / target.y).coerceIn(0.0, 1.0)
        }
    }

    /**
     * 基于三次贝塞尔曲线的 Ease
     *
     * P0 = (0, 0)
     * P1 = startHandle
     * P2 = target + endHandle
     * P3 = target
     *
     * @return y / target.y （归一化到 [0,1]）
     */
    fun bezierEase(
        startHandle: Vec3,
        endHandle: Vec3
    ): Ease {
        val target = Vec3(1.0, 1.0, 0.0)
        val end = target.add(endHandle)

        return Ease { x ->
            val t = x.coerceIn(0.0, 1.0)

            val u = 1.0 - t
            val u2 = u * u
            val t2 = t * t

            val y =
                (u2 * u * 0.0) +
                        (3 * u2 * t * startHandle.y) +
                        (3 * u * t2 * end.y) +
                        (t2 * t * target.y)

            if (target.y == 0.0) 0.0
            else (y / target.y).coerceIn(0.0, 1.0)
        }
    }

    /**
     * 基于三次贝塞尔曲线的 Ease
     *
     * P0 = (0, 0)
     * P1 = startHandle
     * P2 = target + endHandle
     * P3 = target
     *
     * @return y / target.y （归一化到 [0,1]）
     */
    fun bezierEase(
        startHandle: Vec2,
        endHandle: Vec2
    ): Ease {
        val target = Vec2(1f, 1f)
        val end = target.add(endHandle)

        return Ease { x ->
            val t = x.coerceIn(0.0, 1.0)

            val u = 1.0 - t
            val u2 = u * u
            val t2 = t * t

            val y =
                (u2 * u * 0.0) +
                        (3 * u2 * t * startHandle.y) +
                        (3 * u * t2 * end.y) +
                        (t2 * t * target.y)

            if (target.y == 0f) 0.0
            else (y / target.y).coerceIn(0.0, 1.0)
        }
    }


}