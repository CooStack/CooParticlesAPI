package cn.coostack.cooparticlesapi.test.block

import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

class BlockTestPlayer private constructor(
    private val virtualLevel: ServerLevel,
    private val virtualBlockPos: BlockPos,
    private val realPlayer: Player?,
    profile: GameProfile,
) : Player(virtualLevel, virtualBlockPos, realPlayer?.yRot ?: 0f, profile) {
    var offset: Vec3 = Vec3.ZERO
        set(value) {
            field = value
            syncVirtualPose()
        }
    private var virtualForward: Vec3 = DEFAULT_FORWARD
    private var virtualYaw: Float = 0f
    var yaw: Float
        get() = realPlayer?.yRot ?: virtualYaw
        set(value) {
            virtualYaw = value
        }
    private var virtualPitch: Float = 0f
    var pitch: Float
        get() = realPlayer?.xRot ?: virtualPitch
        set(value) {
            virtualPitch = value
        }
    var boxWidth: Double = 0.6
    var boxHeight: Double = 1.8
    var boxDepth: Double = 0.6

    constructor(level: ServerLevel, blockPos: BlockPos) : this(
        level,
        blockPos,
        null,
        virtualProfile(level, blockPos)
    )

    constructor(player: Player) : this(
        player.level() as? ServerLevel ?: error("BlockTestPlayer requires a server-side player"),
        player.blockPosition(),
        player,
        player.gameProfile
    )

    val level: ServerLevel
        get() = realPlayer?.level() as? ServerLevel ?: virtualLevel

    val blockPos: BlockPos
        get() = realPlayer?.blockPosition() ?: virtualBlockPos

    val origin: Vec3
        get() = realPlayer?.position() ?: blockPos.center

    val position: Vec3
        get() = origin.add(offset)

    val collisionBox: AABB
        get() = realPlayer?.boundingBox?.move(offset) ?: AABB.ofSize(position, boxWidth, boxHeight, boxDepth)

    fun setRotation(yaw: Float, pitch: Float) {
        this.yaw = yaw
        this.pitch = pitch
        if (realPlayer == null) {
            setForward(fromRotation(yaw, pitch))
            syncVirtualPose()
        }
    }

    fun copyFor(level: ServerLevel = this.level, pos: BlockPos = this.blockPos): BlockTestPlayer {
        return BlockTestPlayer(level, pos).also {
            it.offset = offset
            it.setForward(forward)
            it.yaw = yaw
            it.pitch = pitch
            it.boxWidth = boxWidth
            it.boxHeight = boxHeight
            it.boxDepth = boxDepth
        }
    }

    override fun getForward(): Vec3 {
        return realPlayer?.lookAngle?.let(::normalizeOrDefault) ?: virtualForward
    }

    fun setForward(value: Vec3) {
        virtualForward = normalizeOrDefault(value)
    }

    override fun isSpectator(): Boolean {
        return realPlayer?.isSpectator ?: false
    }

    override fun isCreative(): Boolean {
        return realPlayer?.isCreative ?: false
    }

    private fun syncVirtualPose() {
        if (realPlayer != null) {
            return
        }
        val pos = position
        moveTo(pos.x, pos.y, pos.z, yaw, pitch)
    }

    companion object {
        val DEFAULT_FORWARD: Vec3 = Vec3(0.0, 0.0, 1.0)

        private fun virtualProfile(level: ServerLevel, pos: BlockPos): GameProfile {
            val seed = "coo-block-test:${level.dimension().location()}:${pos.x},${pos.y},${pos.z}"
            return GameProfile(UUID.nameUUIDFromBytes(seed.toByteArray(StandardCharsets.UTF_8)), "CooBlockTest")
        }

        fun normalizeOrDefault(value: Vec3): Vec3 {
            if (value.lengthSqr() < 1.0E-8) {
                return DEFAULT_FORWARD
            }
            return value.normalize()
        }

        private fun fromRotation(yaw: Float, pitch: Float): Vec3 {
            val yawRad = Math.toRadians(yaw.toDouble())
            val pitchRad = Math.toRadians(pitch.toDouble())
            val horizontal = cos(pitchRad)
            val x = -sin(yawRad) * horizontal
            val y = -sin(pitchRad)
            val z = cos(yawRad) * horizontal
            return normalizeOrDefault(Vec3(x, y, z))
        }
    }
}
