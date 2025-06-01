package cn.coostack.cooparticlesapi.network.particle.emitters.type

import cn.coostack.cooparticlesapi.barrages.HitBox
import net.minecraft.network.PacketByteBuf
import net.minecraft.network.RegistryByteBuf
import net.minecraft.network.codec.PacketCodec
import net.minecraft.util.math.Vec3d

object EmittersShootTypes {

    private val types = HashMap<String, PacketCodec<PacketByteBuf, EmittersShootType>>()

    fun register(id: String, codec: PacketCodec<PacketByteBuf, EmittersShootType>) {
        types[id] = codec
    }

    fun fromID(id: String) = types[id]

    @JvmStatic
    fun point(): EmittersShootType = PointEmittersShootType()

    @JvmStatic
    fun box(box: HitBox): EmittersShootType = BoxEmittersShootType(box)

    @JvmStatic
    fun line(dir: Vec3d, step: Double): EmittersShootType = LineEmittersShootType(dir, step)

    /**
     * @param xe 粒子生成 x 相对于发射器的位置偏移量表达式
     * @param ye 粒子生成 y 相对于发射器的位置偏移量表达式
     * @param ze 粒子生成 z 相对于发射器的位置偏移量表达式
     *
     * @param dxe 粒子生成时 初始速度向量x表达式
     * @param dye 粒子生成时 初始速度向量y表达式
     * @param dze 粒子生成时 初始速度向量z表达式
     */
    @JvmStatic
    fun math(
        xe: String,
        ye: String,
        ze: String,
        dxe: String,
        dye: String,
        dze: String,
    ): EmittersShootType = MathEmittersShootType()
        .apply {
            x = xe
            y = ye
            z = ze
            dx = dxe
            dy = dye
            dz = dze
            setup()
        }

    init {
        register(
            PointEmittersShootType.ID, PointEmittersShootType.CODEC
        )
        register(
            BoxEmittersShootType.ID,
            BoxEmittersShootType.CODEC
        )
        register(
            MathEmittersShootType.ID,
            MathEmittersShootType.CODEC
        )
        register(
            LineEmittersShootType.ID,
            LineEmittersShootType.CODEC
        )
    }

    fun init() {}
}