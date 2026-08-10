package cn.coostack.cooparticlesapi.renderer.post

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.platform.CooParticlesServices
import cn.coostack.cooparticlesapi.renderer.backend.RenderFrameContext
import cn.coostack.cooparticlesapi.renderer.effects.RenderEffectCollector
import cn.coostack.cooparticlesapi.network.packet.server.PacketRendererPostEffectS2C
import net.minecraft.server.level.ServerPlayer
import java.util.concurrent.atomic.AtomicLong

/**
 * post effect 的客户端/服务端统一入口。
 *
 * 用户通常接触三块：
 *
 * - [builtin]：内置效果的快速创建函数
 * - [client]：只在当前客户端本地播放、更新、移除效果
 * - [server]：服务端把效果同步给玩家或追踪某个区块的玩家
 *
 * 这个对象替代了调用方手写 packet、active instance 列表、tick 过期清理和 frame-post collector 接入。
 */
internal object CooPostEffects {
    private val sequence = AtomicLong()

    val client: ClientRuntime = ClientRuntime
    val server: ServerRuntime = ServerRuntime

    /** 生成默认实例 id。需要持续 update/remove 同一个效果时，可以在 `create(instanceId = ...)` 中自定义。 */
    fun nextInstanceId(): String = "post-${sequence.incrementAndGet()}"

    /**
     * 客户端本地 runtime。
     *
     * 本地 add 的效果不会同步给服务端，适合客户端反馈、调试面板、纯 UI 后处理。
     */
    object ClientRuntime {
        private val instances = LinkedHashMap<String, PostEffectInstance>()

        /** 添加或覆盖一个实例。相同 instanceId 会被替换。 */
        fun add(instance: PostEffectInstance): PostEffectInstance {
            instances[instance.instanceId] = instance
            return instance
        }

        /** 更新一个实例。当前实现与 [add] 一样按 instanceId 覆盖。 */
        fun update(instance: PostEffectInstance): PostEffectInstance {
            instances[instance.instanceId] = instance
            return instance
        }

        /** 移除指定实例。 */
        fun remove(instanceId: String) {
            instances.remove(instanceId)
        }

        /** 清空全部本地 post effect。通常只在测试、登出或重载时使用。 */
        fun clear() {
            instances.clear()
        }

        /** 返回当前活跃实例快照。 */
        fun activeInstances(): List<PostEffectInstance> = instances.values.toList()

        /** 推进生命周期并清理过期实例。 */
        fun tick() {
            val iterator = instances.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                val next = entry.value.tick()
                if (next.expired) {
                    iterator.remove()
                } else {
                    entry.setValue(next)
                }
            }
        }

        /**
         * 把活跃实例提交到当前帧的 post collector。
         *
         * 这里只检查 effect type 级能力；pass 级输入、scene depth、pass output 是否可用，
         * 由 [PostEffectFrameExecutor] 在构建 execution step 时再次判断。
         */
        fun collectFramePost(context: RenderFrameContext, collector: RenderEffectCollector) {
            instances.values.forEach { instance ->
                if (!context.backend.capabilities.containsAll(instance.type.requiredCapabilities)) {
                    CooParticlesConstants.logger.debug(
                        "Skipping post effect type={} id={} because backend lacks required capabilities {}",
                        instance.type.id,
                        instance.instanceId,
                        instance.type.requiredCapabilities - context.backend.capabilities
                    )
                    return@forEach
                }
                collector.submit(instance.type.toDescriptor(instance))
            }
        }
    }

    /**
     * 服务端同步入口。
     *
     * 服务端只发送 [SyncedPostEffectState]，真正的 shader 编译、FBO 管理和绘制都发生在客户端。
     */
    object ServerRuntime {
        /** 直接把 create 包发送给某个玩家。 */
        fun send(player: ServerPlayer, instance: PostEffectInstance): PostEffectInstance {
            CooParticlesServices.SERVER_NETWORK.send(PacketRendererPostEffectS2C.create(instance.toNetworkState()), player)
            return instance
        }

        /** 直接把 update 包发送给某个玩家。调用方应保持 instanceId 不变。 */
        fun update(player: ServerPlayer, instance: PostEffectInstance): PostEffectInstance {
            CooParticlesServices.SERVER_NETWORK.send(PacketRendererPostEffectS2C.update(instance.toNetworkState()), player)
            return instance
        }

        /** 通知某个玩家移除指定 instanceId。 */
        fun remove(player: ServerPlayer, instanceId: String) {
            CooParticlesServices.SERVER_NETWORK.send(PacketRendererPostEffectS2C.remove(instanceId), player)
        }

    }
}
