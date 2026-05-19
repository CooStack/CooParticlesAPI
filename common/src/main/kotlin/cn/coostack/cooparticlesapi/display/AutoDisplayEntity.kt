package cn.coostack.cooparticlesapi.display

import cn.coostack.cooparticlesapi.annotations.display.handle.DisplayEntityRegistryHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
/**
 * # 展示实体
 * - 自动注册 类注解[cn.coostack.cooparticlesapi.annotations.CooAutoRegister]
 * - 自动生成codec
 * - 参数注解: [cn.coostack.cooparticlesapi.annotations.CodecField]
 *
 * @constructor 必须保留空构造或者默认构造函数(Vec3, Level)
 *
 * @param pos
 * @param world
 */
abstract class AutoDisplayEntity(pos: Vec3, world: Level?) : DisplayEntity(pos, world) {
    override fun getCodec(): StreamCodec<FriendlyByteBuf, DisplayEntity> {
        return DisplayEntityRegistryHelper.generateCodec(this)
    }
}