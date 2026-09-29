package cn.coostack.cooparticlesapi.test.block

import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathGeometry
import cn.coostack.cooparticlesapi.network.particle.emitters.AutoParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmitters
import cn.coostack.cooparticlesapi.network.particle.emitters.ParticleEmittersManager
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.TestOptionParamSpec
import cn.coostack.cooparticlesapi.test.api.TestOptionPlayerUpdateSupport
import cn.coostack.cooparticlesapi.test.api.TestReviewMode
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestPathCommandEmitter
import cn.coostack.cooparticlesapi.test.options.particle.emitter.TestPathGPUCParticleEmitter
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3

/**
 * # 路径位置约束的 BlockTest 用例
 *
 * ## 资源生命周期
 * BlockTest 的选项是 `Supplier`：`optionIds()` / `optionParamSpecs()` 都会**构建一次整个测试组**来读取
 * 元数据，`statusText()` 等入口也会重复构建。因此这里刻意保持轻量：构造选项只创建发射器对象，
 * 不分配任何路径资源。
 *
 * 路径由发射器自己持有：几何作为发射器的 `@CodecField` 随网络同步，每一侧在第一次真正需要时
 * 用它建立**自己的**本地路径槽位，并在发射器取消时释放。这里传递的是**几何数据**而不是资源编号，
 * 因为路径句柄不能跨网络传输，而路径形状又是任意数据。
 *
 * ## 观察要点
 * 粒子是「薄而长」的不等比外观，在 ROTATION 模式下像一支箭头：
 * - 箭头指向应当始终对齐**实际运动方向**（沿程与环绕的合成），而不是只沿切线。
 * - 环绕时箭头绕路径旋转，而不是沿切线直着走。
 * - 折线用例的急拐角处不应出现横向参考基突然翻转。
 * - 闭合圆环接缝处应当连续推进，不应有从终点跳回起点的穿越轨迹。
 * - 动态点用例改点后位置应当**立刻**贴到新几何上，不应有平滑追赶。
 * - 到达消失用例的粒子数量应当保持稳定，而不是只增不减。
 *
 * @param player 当前发起测试的玩家；发射器以它的位置为基准
 * @param geometry 本用例跟随的路径几何；写进发射器自己的字段并随其同步
 * @param description 状态行显示的用例说明
 * @param useCommandPath `true` 时走传统 `ParticleCommand`，否则走 GPU compute 路径约束
 * @param configureEmitter 在启动时对发射器做的配置
 */
class PathConstraintBlockTestOption(
    private val player: Player,
    geometry: CParticlePathGeometry,
    private val description: String,
    private val useCommandPath: Boolean = false,
    private val configureEmitter: (AutoParticleEmitters) -> Unit = {},
) : TestOption<ParticleEmitters> {

    /**
     * 用例的发射器。
     *
     * 构造它不分配路径：几何被写进发射器自己的 `pathGeometry` 字段并随发射器同步；
     * 真正的本地路径由发射器懒创建。
     */
    private val emitter: AutoParticleEmitters = if (useCommandPath) {
        TestPathCommandEmitter(player.position(), player.level()).apply { pathGeometry = geometry }
    } else {
        TestPathGPUCParticleEmitter(player.position(), player.level()).apply { pathGeometry = geometry }
    }

    init {
        emitter.maxTick = -1
        emitter.delay = 1
        configureEmitter(emitter)
    }

    override fun paramTarget(): ParticleEmitters = emitter

    override fun optionID(): String = description

    override fun optionParamSpecs(): List<TestOptionParamSpec<*>> = emptyList()

    override fun reviewMode(): TestReviewMode = TestReviewMode.MANUAL_VISUAL

    override fun start() {
        emitter.canceled = false
        emitter.pos = player.position()
        ParticleEmittersManager.spawnEmitters(emitter)
    }

    override fun stop() {
        // 走发射器自己的 stop：它负责释放自己持有的路径。
        emitter.stop()
    }

    /** 用例一直运行，直到操作员在界面上停止或切到下一个用例。 */
    override fun isValid(): Boolean = !emitter.canceled

    override fun doTick() {
        TestOptionPlayerUpdateSupport.dispatch(this, player)
    }

    override fun onSuccess() = Unit

    override fun onFailed() = Unit

    /** 供诊断使用：本用例绑定的玩家位置基准。 */
    fun anchor(): Vec3 = player.position()
}
