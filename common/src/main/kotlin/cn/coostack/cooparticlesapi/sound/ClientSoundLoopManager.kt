package cn.coostack.cooparticlesapi.sound

import cn.coostack.cooparticlesapi.network.packet.server.PacketSoundLoopS2C
import net.minecraft.client.Minecraft

object ClientSoundLoopManager {
    private val playing = HashMap<String, ServerControlledLoopSound>()

    @JvmStatic
    fun isTracking(key: String): Boolean {
        return isPlaying(key)
    }

    @JvmStatic
    fun isPlaying(key: String): Boolean {
        return playing[key]?.isStopped == false
    }

    @JvmStatic
    fun isStoppingAfterCurrentLoop(key: String): Boolean {
        return playing[key]?.isStoppingAfterCurrentLoop == true
    }

    fun handle(packet: PacketSoundLoopS2C) {
        if (packet.start) {
            start(packet)
        } else {
            stop(packet.key, packet.stopImmediately)
        }
    }

    fun tick() {
        val iterator = playing.iterator()
        while (iterator.hasNext()) {
            val (_, sound) = iterator.next()
            if (sound.isStopped) {
                iterator.remove()
            }
        }
    }

    fun clear() {
        playing.values.forEach { it.stopNow() }
        playing.clear()
    }

    private fun start(packet: PacketSoundLoopS2C) {
        stop(packet.key, true)
        val sound = ServerControlledLoopSound(
            soundId = packet.sound,
            source = packet.source,
            entityId = packet.entityId,
            fallbackPos = packet.pos,
            volume = packet.volume,
            pitch = packet.pitch
        )
        playing[packet.key] = sound
        Minecraft.getInstance().soundManager.play(sound)
    }

    private fun stop(key: String, interrupt: Boolean) {
        if (!interrupt) {
            playing[key]?.stopAfterCurrentLoop()
            return
        }
        val sound = playing.remove(key) ?: return
        sound.stopNow()
    }
}
