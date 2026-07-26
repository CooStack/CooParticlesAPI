package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.renderer.AutoRenderEntity
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import org.joml.Vector4f

/**
 * 拖尾演示实体：一个绕中心点盘旋的光球，身后跟随动态拖尾条带。
 *
 * 拖尾几何完全由客户端 renderer 每帧重建（点在不断变化的动态模型），
 * 同时演示 mask bloom 的强泛光 / 曝光补偿 / 距离聚光补偿可选项。
 */
@CooAutoRegister
class DemoTrailOrbRenderEntity() : AutoRenderEntity(null, Vec3.ZERO), DemoWorldRenderEffectSpec {
    constructor(world: Level?, pos: Vec3, durationTicks: Int = 200) : this() {
        this.world = world
        this.pos = pos
        this.durationTicks = durationTicks
    }

    /** 盘旋轨道半径。 */
    @CodecField
    override var radius: Float = 1.6f

    @CodecField
    override var intensity: Float = 1.25f

    @CodecField
    override var durationTicks: Int = 200

    override val modelPipeKey: String = "trail_orb"
    override val displayName: String = "render_entity/trail_orb"
    override val effectColor: Vector4f = Vector4f(1.0f, 0.62f, 0.25f, 0.85f)

    override fun serverTick() {
        if (durationTicks in 1..age) remove()
    }

    override fun clientTick() {
        if (durationTicks in 1..age) remove()
    }

    override fun getRenderID(): ResourceLocation = ID

    companion object {
        val ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(
            CooParticlesConstants.MOD_ID,
            "demo_trail_orb_render_entity"
        )
    }
}
