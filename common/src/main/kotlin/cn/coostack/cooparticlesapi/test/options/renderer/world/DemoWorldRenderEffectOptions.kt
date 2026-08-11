package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.test.SimpleRendererEntityOption
import cn.coostack.cooparticlesapi.extend.plus
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import cn.coostack.cooparticlesapi.test.api.DoubleTestOptionValue
import cn.coostack.cooparticlesapi.test.api.IntTestOptionValue
import cn.coostack.cooparticlesapi.test.api.TestOption
import cn.coostack.cooparticlesapi.test.api.Vec3TestOptionValue
import cn.coostack.cooparticlesapi.test.api.Vector3fTestOptionValue
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f

object DemoWorldRenderEffectOptions {
    fun irisStraightLaser(player: Player): SimpleRendererEntityOption<DemoIrisStraightLaserRenderEntity> {
        val start = player.eyePosition.add(player.forward.scale(2.0))
        val end = player.eyePosition.add(player.forward.scale(48.0))
        return SimpleRendererEntityOption(
            testEntity = DemoIrisStraightLaserRenderEntity(player.level(), start, end).apply {
                brightness = 3f
                maxRadius = 4f
            },
            testingTick = 240,
            displayName = "render_entity/iris_straight_laser"
        )
    }

    fun maskBloomStraightLaser(player: Player): TestOption<DemoMaskBloomStraightLaserRenderEntity> {
        return SimpleRendererEntityOption(
            DemoMaskBloomStraightLaserRenderEntity(player.level(), player.position()),
            -1,
            "render_entity/mask_bloom_straight_laser"
        ).applyParam(
            Vec3TestOptionValue("target", "目标位置"),
            Vec3(0.0, 12.0, 0.0)
        ).applyParam(
            DoubleTestOptionValue("size", "大小"),
            3.0
        ).applyParam(
            Vector3fTestOptionValue("color", "颜色").asColor(),
            Vector3f(1F)
        ).applyParam(
            IntTestOptionValue("lifetime", "存活时间"),
            20
        ).applyParam(
            DoubleTestOptionValue("bright", "亮度"),
            1.0
        ).applyTo { entity ->
            entity.brightness = getParamOrThrow<Double>("bright").toFloat()
            entity.updateBeam(
                player.position(),
                player.position() + getParamOrThrow<Vec3>("target")
            )
            entity.maxRadius = getParamOrThrow<Double>("size").toFloat()
            entity.color = Vector3f(getParamOrThrow<Vector3f>("color"))
            entity.lifetime = getParamOrThrow("lifetime")
        }
    }

    fun blackHole(player: Player): SimpleRendererEntityOption<DemoBlackHoleRenderEntity> {
        return option(
            player = player,
            forwardDistance = 4.5,
            displayName = "render_entity/black_hole",
            factory = { world, center -> DemoBlackHoleRenderEntity(world, center) }
        )
    }

    fun shield(player: Player): SimpleRendererEntityOption<DemoShieldRenderEntity> {
        return option(
            player = player,
            forwardDistance = 3.5,
            displayName = "render_entity/shield",
            factory = { world, center -> DemoShieldRenderEntity(world, center) }
        )
    }

    fun lightBeam(player: Player): SimpleRendererEntityOption<DemoLightBeamRenderEntity> {
        return option(
            player = player,
            forwardDistance = 5.0,
            displayName = "render_entity/light_beam",
            factory = { world, center -> DemoLightBeamRenderEntity(world, center) }
        )
    }

    fun lightOrb(player: Player): SimpleRendererEntityOption<DemoLightOrbRenderEntity> {
        return option(
            player = player,
            forwardDistance = 4.0,
            displayName = "render_entity/light_orb",
            factory = { world, center -> DemoLightOrbRenderEntity(world, center) }
        )
    }

    fun waterBall(player: Player): SimpleRendererEntityOption<DemoWaterBallRenderEntity> {
        return option(
            player = player,
            forwardDistance = 4.0,
            displayName = "render_entity/water_ball",
            factory = { world, center -> DemoWaterBallRenderEntity(world, center) }
        )
    }

    fun trailOrb(player: Player): SimpleRendererEntityOption<DemoTrailOrbRenderEntity> {
        return option(
            player = player,
            forwardDistance = 5.0,
            displayName = "render_entity/trail_orb",
            factory = { world, center -> DemoTrailOrbRenderEntity(world, center) }
        )
    }

    private fun <T : RenderEntity> option(
        player: Player,
        forwardDistance: Double,
        displayName: String,
        factory: (net.minecraft.world.level.Level, net.minecraft.world.phys.Vec3) -> T
    ): SimpleRendererEntityOption<T> {
        val center = player.eyePosition.add(player.forward.scale(forwardDistance))
        return SimpleRendererEntityOption(
            testEntity = factory(player.level(), center),
            testingTick = 120,
            displayName = displayName
        )
    }
}
