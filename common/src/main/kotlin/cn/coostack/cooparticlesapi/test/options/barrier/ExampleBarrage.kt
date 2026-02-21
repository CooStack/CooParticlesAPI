package cn.coostack.cooparticlesapi.test.options.barrier

import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.barrages.AbstractBarrage
import cn.coostack.cooparticlesapi.barrages.BarrageHitResult
import cn.coostack.cooparticlesapi.barrages.BarrageOption
import cn.coostack.cooparticlesapi.barrages.HitBox
import cn.coostack.cooparticlesapi.extend.asRelative
import cn.coostack.cooparticlesapi.test.options.display.BarrageItemDisplayEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3

/**
 * 示例弹幕
 *  ```kotlin
 *  // 生成弹幕示例
 *  BarrageManager.spawn(ExampleBarrage(pos,serverLevel,BarrageOption()))
 *  // BarrageOption是弹幕的具体数据设置， 可以调整一些新的参数
 *  ```
 * @constructor
 *
 * @param loc
 * @param world
 * @param options
 */
class ExampleBarrage(loc: Vec3, world: ServerLevel, options: BarrageOption) : AbstractBarrage(loc, world, options) {
    override fun filterHitEntity(livingEntity: LivingEntity): Boolean {
        return true
    }

    override fun createHitBox(): HitBox {
        return HitBox.of(2.0, 2.0, 2.0)
    }

    override fun createControler(): ServerControler<*> {
        return BarrageItemDisplayEntity(loc, world).apply {
            item = Items.DIAMOND_SWORD.defaultInstance
        }
    }

    /**
     * 这个方法可以手动重写
     * 用来添加一些新的操作
     */
    override fun tick() {
        super.tick()
        // 这里让controler强制转向
        val display = bindControl.get() as BarrageItemDisplayEntity
        // direction是弹幕移动的方向
        display.rotateToPoint(direction.asRelative())
    }

    override fun onHit(result: BarrageHitResult) {
        // 这里处理弹幕击中逻辑（碰撞重合）
    }
}