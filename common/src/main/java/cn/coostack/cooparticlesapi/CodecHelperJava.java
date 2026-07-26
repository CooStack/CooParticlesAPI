package cn.coostack.cooparticlesapi;

import cn.coostack.cooparticlesapi.annotations.codec.CodecHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public class CodecHelperJava {
    static {
        CodecHelper.register(
                Boolean.class, StreamCodec.of(FriendlyByteBuf::writeBoolean, FriendlyByteBuf::readBoolean)
        );
        CodecHelper.register(
                Integer.class, StreamCodec.of(FriendlyByteBuf::writeInt, FriendlyByteBuf::readInt)
        );
        CodecHelper.register(
                Long.class, StreamCodec.of(FriendlyByteBuf::writeLong, FriendlyByteBuf::readLong)
        );
    }

    public static void init() {
    }
}
