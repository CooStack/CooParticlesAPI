package cn.coostack.cooparticlesapi.renderer

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.annotations.renderer.handle.RenderEntityHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 自动生成 codec 的 RenderEntity 基类。
 *
 * - 使用 @CodecField 标注字段后会自动参与编码与解码。
 * - loadProfileFromEntity 会自动回写 @CodecField 字段。
 */
abstract class AutoRenderEntity(world: Level?, pos: Vec3 = Vec3.ZERO) : RenderEntity(world, pos) {
    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> {
        return RenderEntityHelper.generateCodec(this)
    }

    override fun loadProfileFromEntity(another: RenderEntity) {
        super.loadProfileFromEntity(another)
        CodecHelper.updateFields(this, another)
    }
}
