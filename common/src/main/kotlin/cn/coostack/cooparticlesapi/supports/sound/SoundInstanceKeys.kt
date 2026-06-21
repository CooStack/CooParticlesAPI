package cn.coostack.cooparticlesapi.supports.sound

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import java.util.UUID

/**
 * 可控音频实例的 key 构建工具。
 *
 * key 是声音实例身份，不是单纯的名字。同 key 会更新/替换同一个声音，不同 key 才能重叠播放。
 */
object SoundInstanceKeys {
    @JvmStatic
    fun entity(entity: Entity, name: String): String {
        return "${entity.uuid}:$name"
    }

    @JvmStatic
    fun entity(entity: Entity, name: String, layer: String): String {
        return "${entity.uuid}:$name:$layer"
    }

    @JvmStatic
    fun soundName(soundId: ResourceLocation): String {
        return soundId.toString()
    }

    @JvmStatic
    @JvmOverloads
    fun unique(prefix: String = "sound"): String {
        return "$prefix:${UUID.randomUUID()}"
    }
}
