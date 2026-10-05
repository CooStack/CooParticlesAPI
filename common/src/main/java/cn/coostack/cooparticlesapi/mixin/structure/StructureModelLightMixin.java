package cn.coostack.cooparticlesapi.mixin.structure;

import cn.coostack.cooparticlesapi.entities.structure.client.StructureModelMeshes;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 只把光照和区块更新排入失效队列，不跨线程读写 GPU 缓冲。 */
@Mixin(ClientChunkCache.class)
public abstract class StructureModelLightMixin {
    /** 区段光照更新只使实际依赖该区段的网格失效。 */
    @Inject(method = "onLightUpdate", at = @At("TAIL"))
    private void light(LightLayer layer, SectionPos section, CallbackInfo callback) {
        StructureModelMeshes.invalidateSection(section.asLong());
    }

    /** 生物群系包更新后重新采样混色。 */
    @Inject(method = "replaceBiomes", at = @At("TAIL"))
    private void biomes(int x, int z, FriendlyByteBuf buffer, CallbackInfo callback) {
        StructureModelMeshes.invalidateChunk(x, z);
    }

    /** 区块卸载使相邻混色边界一起失效。 */
    @Inject(method = "drop", at = @At("TAIL"))
    private void unload(ChunkPos pos, CallbackInfo callback) {
        StructureModelMeshes.invalidateChunk(pos.x, pos.z);
    }

    /** 区块数据替换后丢弃旧采样结果。 */
    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void load(CallbackInfoReturnable<LevelChunk> callback) {
        LevelChunk chunk = callback.getReturnValue();
        if (chunk != null) StructureModelMeshes.invalidateChunk(chunk.getPos().x, chunk.getPos().z);
    }
}
