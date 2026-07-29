package cn.coostack.cooparticlesapi.test.block

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.nbt.TagParser
import net.minecraft.world.phys.Vec3

/**
 * 负责动态轨道的 NBT 与网络 SNBT 转换。
 *
 * 示例：方块实体用 [writeTo] 保存，数据包用 [encode] 传输同一结构。
 * 禁止把解析失败暴露为界面崩溃；[decode] 会回退到默认轨道。
 */
object BlockTestAnimationTrackCodec {
    /**
     * 把轨道写入父 NBT 的指定键。
     *
     * 示例：`writeTo(tag, "positionTrack", track)`。
     * 禁止传入空键名，否则会产生不可维护的存档结构。
     *
     * @param parent 父 NBT
     * @param key 轨道字段名
     * @param track 待保存轨道
     */
    fun writeTo(parent: CompoundTag, key: String, track: BlockTestAnimationTrack) {
        parent.put(key, write(track))
    }

    /**
     * 从父 NBT 读取轨道，不存在或损坏时创建默认轨道。
     *
     * 示例：旧存档没有动态字段时会保留传入的静态偏移。
     * 禁止复用返回值作为全局默认实例。
     *
     * @param parent 父 NBT
     * @param key 轨道字段名
     * @param fallbackValue 默认关键帧值
     * @return 可编辑的独立轨道
     */
    fun readFrom(parent: CompoundTag, key: String, fallbackValue: Vec3): BlockTestAnimationTrack {
        if (!parent.contains(key, Tag.TAG_COMPOUND.toInt())) {
            return BlockTestAnimationTrack.create(fallbackValue)
        }
        return read(parent.getCompound(key), fallbackValue)
    }

    /**
     * 把轨道编码为数据包可直接传输的 SNBT。
     *
     * 示例：`packet.positionTrack = encode(track)`。
     * 禁止对编码结果做分隔符拼接或手工替换。
     *
     * @param track 待编码轨道
     * @return SNBT 文本
     */
    fun encode(track: BlockTestAnimationTrack): String {
        return write(track).toString()
    }

    /**
     * 从数据包 SNBT 恢复轨道，空串或非法文本会返回默认轨道。
     *
     * 示例：客户端收到旧版本空字段时传入静态值作为回退。
     * 禁止假定返回轨道与任何服务端对象共享引用。
     *
     * @param encoded SNBT 文本
     * @param fallbackValue 默认关键帧值
     * @return 解析后的轨道
     */
    fun decode(encoded: String, fallbackValue: Vec3): BlockTestAnimationTrack {
        if (encoded.isBlank()) {
            return BlockTestAnimationTrack.create(fallbackValue)
        }
        return runCatching { read(TagParser.parseTag(encoded), fallbackValue) }
            .getOrElse { BlockTestAnimationTrack.create(fallbackValue) }
    }

    /**
     * 生成轨道的结构化 NBT。
     *
     * 示例：存档和网络编码都调用该方法以保持字段一致。
     * 禁止修改传入轨道；写入过程只读取当前快照。
     *
     * @param track 待保存轨道
     * @return 新建的 NBT
     */
    private fun write(track: BlockTestAnimationTrack): CompoundTag {
        val tag = CompoundTag()
        tag.putInt("durationTicks", track.durationTicks)
        tag.putString("playbackMode", track.playbackMode.id)
        val frames = ListTag()
        track.keyframes.forEach { keyframe ->
            val frame = CompoundTag()
            frame.putInt("tick", keyframe.tick)
            writeVec3(frame, "value", keyframe.value)
            frame.putString("curveToNext", keyframe.curveToNext.id)
            frame.putDouble("outgoingTime", keyframe.outgoingTime)
            frame.putDouble("outgoingProgress", keyframe.outgoingProgress)
            frame.putDouble("incomingTime", keyframe.incomingTime)
            frame.putDouble("incomingProgress", keyframe.incomingProgress)
            frame.putBoolean("locked", keyframe.locked)
            frames.add(frame)
        }
        tag.put("keyframes", frames)
        return tag
    }

    /**
     * 从结构化 NBT 创建轨道并执行边界修正。
     *
     * 示例：重复 tick 会在归一化阶段合并。
     * 禁止跳过归一化后直接交给运行时。
     *
     * @param tag 轨道 NBT
     * @param fallbackValue 默认关键帧值
     * @return 已归一化轨道
     */
    private fun read(tag: CompoundTag, fallbackValue: Vec3): BlockTestAnimationTrack {
        val frames = ArrayList<BlockTestAnimationKeyframe>()
        val list = tag.getList("keyframes", Tag.TAG_COMPOUND.toInt())
        for (index in 0 until list.size) {
            val frame = list.getCompound(index)
            frames += BlockTestAnimationKeyframe(
                tick = frame.getInt("tick"),
                value = readVec3(frame, "value", fallbackValue),
                curveToNext = BlockTestCurveType.fromId(frame.getString("curveToNext")),
                outgoingTime = frame.getDouble("outgoingTime").takeIf { frame.contains("outgoingTime") } ?: 1.0 / 3.0,
                outgoingProgress = frame.getDouble("outgoingProgress").takeIf { frame.contains("outgoingProgress") } ?: 1.0 / 3.0,
                incomingTime = frame.getDouble("incomingTime").takeIf { frame.contains("incomingTime") } ?: 2.0 / 3.0,
                incomingProgress = frame.getDouble("incomingProgress").takeIf { frame.contains("incomingProgress") } ?: 2.0 / 3.0,
                locked = frame.getBoolean("locked")
            )
        }
        if (frames.isEmpty()) {
            frames += BlockTestAnimationKeyframe(0, fallbackValue, locked = true)
        }
        return BlockTestAnimationTrack(
            durationTicks = tag.getInt("durationTicks").coerceAtLeast(BlockTestAnimationTrack.MIN_DURATION_TICKS),
            playbackMode = BlockTestPlaybackMode.fromId(tag.getString("playbackMode")),
            keyframes = frames
        ).also { it.normalize(fallbackValue) }
    }

    /**
     * 写入一个三维向量。
     *
     * 示例：关键帧值保存为 `valueX/valueY/valueZ`。
     * 禁止把该结构当作整数方块坐标。
     *
     * @param tag 目标 NBT
     * @param key 字段前缀
     * @param value 三维值
     */
    private fun writeVec3(tag: CompoundTag, key: String, value: Vec3) {
        tag.putDouble("${key}X", value.x)
        tag.putDouble("${key}Y", value.y)
        tag.putDouble("${key}Z", value.z)
    }

    /**
     * 读取一个三维向量，字段不完整时返回回退值。
     *
     * 示例：旧关键帧缺少 `valueX` 时不会生成半截向量。
     * 禁止把零向量误判为字段缺失。
     *
     * @param tag 来源 NBT
     * @param key 字段前缀
     * @param fallback 回退值
     * @return 读取结果
     */
    private fun readVec3(tag: CompoundTag, key: String, fallback: Vec3): Vec3 {
        if (!tag.contains("${key}X") || !tag.contains("${key}Y") || !tag.contains("${key}Z")) {
            return fallback
        }
        return Vec3(tag.getDouble("${key}X"), tag.getDouble("${key}Y"), tag.getDouble("${key}Z"))
    }
}
