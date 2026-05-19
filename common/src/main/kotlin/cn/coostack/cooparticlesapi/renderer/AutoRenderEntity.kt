package cn.coostack.cooparticlesapi.renderer

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper
import cn.coostack.cooparticlesapi.annotations.renderer.handle.RenderEntityRegistryHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/**
 * 自动生成 codec 的 RenderEntity 基类。
 *
 * 这是 RenderEntity 的推荐默认基类。除非你需要完全自定义编解码，
 * 否则新写的 RenderEntity 都建议直接继承 `AutoRenderEntity`：
 *
 * - 在字段上加 `@CodecField`，编码与解码会自动生成；
 * - `loadProfileFromEntity` 也会自动把 `@CodecField` 字段写回客户端镜像。
 *
 * 直接继承 `RenderEntity` 仍然有效，适用于需要手写 `createCodec(...)`
 * 的特殊场景（例如要单独控制字段顺序、引入额外校验、或与历史 codec 对齐）。
 */
abstract class AutoRenderEntity(world: Level?, pos: Vec3 = Vec3.ZERO) : RenderEntity(world, pos) {
    /**
     * 返回基于注解自动生成的同步 codec。
     *
     * 适合不想手写 `createCodec(...)` 的场景：
     * 只要字段已经通过 `@CodecField` 声明，就会自动进入同步流。
     */
    override fun getCodec(): StreamCodec<FriendlyByteBuf, RenderEntity> {
        return RenderEntityRegistryHelper.generateCodec(this)
    }

    /**
     * 在基类同步字段之外，继续把 `@CodecField` 标注的字段回写到当前实例。
     *
     * 这让 `AutoRenderEntity` 子类通常不需要再手动覆盖
     * `loadProfileFromEntity(...)` 来同步普通字段。
     */
    override fun loadProfileFromEntity(another: RenderEntity) {
        super.loadProfileFromEntity(another)
        CodecHelper.updateFields(this, another)
    }
}
