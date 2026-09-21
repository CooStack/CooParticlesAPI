package cn.coostack.cooparticlesapi.network.particle.emitters

import cn.coostack.cooparticlesapi.api.NetworkDirtyMarkable
import cn.coostack.cooparticlesapi.api.controler.server.ServerControler
import cn.coostack.cooparticlesapi.network.particle.emitters.event.ParticleEventHandler
import cn.coostack.cooparticlesapi.utils.RelativeLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

interface ParticleEmitters : ServerControler<ParticleEmitters>, NetworkDirtyMarkable {
    var pos: Vec3
    var world: Level?
    var tick: Int

    var maxTick: Int
    var delay: Int
    var uuid: UUID
    var canceled: Boolean
    var playing: Boolean

    fun addEventHandler(handler: ParticleEventHandler, innerClass: Boolean)

    fun getEmittersID(): String

    fun start()

    fun stop()

    fun tick()

    fun spawnParticle(pos: Vec3, lerpProgress: Float)

    fun update(emitters: ParticleEmitters)

    fun getCodec(): ForgeStreamCodec<PacketByteBuf, ParticleEmitters>

    override fun getValue(): ParticleEmitters {
        return this
    }

    override fun markDirty() {
        if (world?.isClientSide != true) {
            ParticleEmittersManager.enqueueDirty(this)
        }
    }

    override fun remove() {
        canceled = true
    }

    override fun spawn(world: Level, pos: Vec3) {
        if (world !is ServerLevel) return
        this.world = world
        this.pos = pos
        ParticleEmittersManager.spawnEmitters(this)
    }

    override fun isValid(): Boolean {
        return !canceled
    }

    override fun rotateAsAxis(radian: Double) {
    }

    override fun rotateToPoint(to: RelativeLocation) {
    }

    override fun rotateToWithAngle(to: RelativeLocation, radian: Double) {
    }

    override fun teleportTo(to: Vec3) {
        if (pos == to) return
        pos = to
    }

    override fun teleportTo(x: Double, y: Double, z: Double) {
        teleportTo(Vec3(x, y, z))
    }
}
