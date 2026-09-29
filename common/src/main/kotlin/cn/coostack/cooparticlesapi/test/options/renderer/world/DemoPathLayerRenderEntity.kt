package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathLibrary
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathPoint
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathSegmentType
import cn.coostack.cooparticlesapi.cparticle.path.CParticlePathSlot
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * # 路径图层共用示例实体
 *
 * 用于证明 RenderEntity 的 shader 可以读取 **CParticle 路径约束使用的同一份** GPU 数据图层：
 * 实体只持有一个 [CParticlePathSlot] 句柄，几何、预采样表与横截面参考基全部来自共享图层，
 * 不在 RenderEntity 内部重新复制一份路径。
 *
 * 绘制由 [DemoPathLayerRenderEntityRenderer] 的 shader 直接从 SSBO 采样；实体的
 * [pathLayerRevision] 只在路径基址发生变化时更新，renderer 据此重建 uniform。
 *
 * ## 释放规则
 * 路径按引用计数共享：本实体释放只减少一个引用，其他仍在使用的对象不受影响；
 * 最后一个引用者释放后才回收图层存储。因此同一个槽位可以被多个实体与 CParticle 命令同时引用。
 *
 * ## 同步边界
 * 路径句柄只在本客户端的路径资源库内有效，**不能**跨网络传输；这里让每个客户端各自建立
 * 一条进程内的共享路径，服务端只同步实体的位置与生命周期。
 */
@CooAutoRegister
class DemoPathLayerRenderEntity() : AutoRenderEntity(null, Vec3.ZERO) {
    constructor(world: Level?, pos: Vec3, durationTicks: Int = 200) : this() {
        this.world = world
        this.pos = pos
        this.durationTicks = durationTicks
    }

    @CodecField
    var durationTicks: Int = 200

    /**
     * 进程内共享的路径槽位；第一次 tick 时建立，生命周期由本实体负责释放。
     *
     * 禁止：把它当作跨网络稳定 ID；槽位号只在当前客户端的路径资源库内有效。
     */
    var path: CParticlePathSlot? = null
        private set

    /** 最近一次观察到的图层重建版本；路径基址变化时 renderer 需要重新读取表头。 */
    var pathLayerRevision: Int = -1
        private set

    override fun getRenderID(): ResourceLocation = ID

    /**
     * 建立一条空间贝塞尔路径。
     *
     * 几何位于实体的**本地空间**：多个对象可以共享同一条相对路径，各自保留自己的位置与旋转。
     */
    private fun ensurePath(): CParticlePathSlot {
        path?.takeIf { !it.released }?.let { return it }
        // 与 CParticle 发射器共用同一条路径实例：两者引用同一个槽位，因此读到的几何、
        // 预采样表与横截面参考基完全一致，不需要在 RenderEntity 内部复制一份路径。
        return SharedDemoPath.acquire().also { path = it }
    }

    override fun serverTick() {
        if (durationTicks in 1..age) remove()
    }

    override fun clientTick() {
        val slot = ensurePath()
        pathLayerRevision = CParticlePathLibrary.layerRevision
        // 路径图层在 CParticle 的模拟刷新与渲染阶段会被绑定到固定绑定点；这里只记录版本，
        // 真正的 GL 读取由 renderer 在渲染阶段完成。
        if (slot.released) path = null
        if (durationTicks in 1..age) remove()
    }

    override fun remove() {
        // 释放本实体持有的引用；共享路径仍被 CParticle 侧持有时不会回收。
        path?.let(CParticlePathLibrary::release)
        path = null
        super.remove()
    }

    /**
     * # 路径图层共用示例的共享路径
     *
     * 演示「一个 GPU 数据图层被 CParticle 与 RenderEntity 同时引用」的引用计数规则：
     * 第一个使用者建立路径，之后每个使用者调用 [acquire] 增加引用；各自的 [release] 只减少计数，
     * 最后一个释放者才让图层存储被回收。
     *
     * 禁止：把这里的槽位跨网络传输——句柄只在当前客户端进程内有效。
     */
    private object SharedDemoPath {
        private var slot: CParticlePathSlot? = null

        /**
         * 取共享路径，必要时建立并把引用计数加一。
         *
         * @return 可供本次使用者读取的路径槽位
         */
        @Synchronized
        fun acquire(): CParticlePathSlot {
            val current = slot?.takeIf { !it.released }
            if (current != null) {
                CParticlePathLibrary.retain(current)
                return current
            }
            val created = CParticlePathLibrary.create(bezierPoints(), CParticlePathSegmentType.BEZIER, closed = false)
            slot = created
            return created
        }

        /** 建立示例使用的空间贝塞尔曲线；几何位于使用者的本地空间。 */
        private fun bezierPoints(): List<CParticlePathPoint> = listOf(
            CParticlePathPoint(Vec3(0.0, 0.0, -2.0), outHandle = Vec3(0.0, 0.0, -1.4)),
            CParticlePathPoint(
                Vec3(1.8, 1.2, 0.0),
                inHandle = Vec3(-0.9, 0.0, -0.9),
                outHandle = Vec3(0.9, 0.0, 0.9),
            ),
            CParticlePathPoint(
                Vec3(0.0, 2.4, 2.0),
                inHandle = Vec3(0.0, -0.9, -1.1),
                outHandle = Vec3(0.0, 0.9, 1.1),
            ),
            CParticlePathPoint(Vec3(-1.8, 1.0, 0.2), inHandle = Vec3(0.9, 0.0, 0.9)),
        )
    }

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "demo_path_layer_render_entity",
        )
    }
}
