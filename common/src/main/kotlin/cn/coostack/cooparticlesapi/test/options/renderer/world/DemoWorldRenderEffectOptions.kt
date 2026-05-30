package cn.coostack.cooparticlesapi.test.options.renderer.world

import cn.coostack.cooparticlesapi.test.SimpleRendererEntityOption
import cn.coostack.cooparticlesapi.renderer.RenderEntity
import net.minecraft.world.entity.player.Player

object DemoWorldRenderEffectOptions {
    fun irisStraightLaser(player: Player): SimpleRendererEntityOption {
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

    fun blackHole(player: Player): SimpleRendererEntityOption {
        return option(
            player = player,
            forwardDistance = 4.5,
            displayName = "render_entity/black_hole",
            factory = { world, center -> DemoBlackHoleRenderEntity(world, center) }
        )
    }

    fun shield(player: Player): SimpleRendererEntityOption {
        return option(
            player = player,
            forwardDistance = 3.5,
            displayName = "render_entity/shield",
            factory = { world, center -> DemoShieldRenderEntity(world, center) }
        )
    }

    fun lightBeam(player: Player): SimpleRendererEntityOption {
        return option(
            player = player,
            forwardDistance = 5.0,
            displayName = "render_entity/light_beam",
            factory = { world, center -> DemoLightBeamRenderEntity(world, center) }
        )
    }

    fun lightOrb(player: Player): SimpleRendererEntityOption {
        return option(
            player = player,
            forwardDistance = 4.0,
            displayName = "render_entity/light_orb",
            factory = { world, center -> DemoLightOrbRenderEntity(world, center) }
        )
    }

    fun waterBall(player: Player): SimpleRendererEntityOption {
        return option(
            player = player,
            forwardDistance = 4.0,
            displayName = "render_entity/water_ball",
            factory = { world, center -> DemoWaterBallRenderEntity(world, center) }
        )
    }

    private fun option(
        player: Player,
        forwardDistance: Double,
        displayName: String,
        factory: (net.minecraft.world.level.Level, net.minecraft.world.phys.Vec3) -> RenderEntity
    ): SimpleRendererEntityOption {
        val center = player.eyePosition.add(player.forward.scale(forwardDistance))
        return SimpleRendererEntityOption(
            testEntity = factory(player.level(), center),
            testingTick = 120,
            displayName = displayName
        )
    }
}
