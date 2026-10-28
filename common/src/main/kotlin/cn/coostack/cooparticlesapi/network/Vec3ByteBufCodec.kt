package cn.coostack.cooparticlesapi.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.phys.Vec3

/**
 * 兼容项目现有数据包格式，将三维坐标委托给 26.3 原版 codec。
 *
 * 示例：`buffer.writeVec3(position)`。
 *
 * @receiver 写入坐标的网络缓冲区
 * @param value 待编码的世界坐标
 * @return 当前缓冲区，便于继续串联写入
 */
fun FriendlyByteBuf.writeVec3(value: Vec3): FriendlyByteBuf {
    Vec3.STREAM_CODEC.encode(this, value)
    return this
}

/**
 * 从当前缓冲区读取一个三维坐标。
 *
 * 示例：`val position = buffer.readVec3()`。
 *
 * @receiver 提供编码字节的网络缓冲区
 * @return 解码后的世界坐标
 */
fun FriendlyByteBuf.readVec3(): Vec3 = Vec3.STREAM_CODEC.decode(this)
