package cn.coostack.cooparticlesapi.network.packet.status

import cn.coostack.cooparticlesapi.CooParticlesConstants
import cn.coostack.cooparticlesapi.annotations.CodecField
import cn.coostack.cooparticlesapi.annotations.CooAutoRegister
import cn.coostack.cooparticlesapi.network.packet.api.CooPacket
import cn.coostack.cooparticlesapi.performance.PerformanceStatusNetworkTotals
import cn.coostack.cooparticlesapi.performance.PerformanceStatusServerSnapshot
import cn.coostack.cooparticlesapi.performance.PerformanceStatusVanillaPacketTotals
import net.minecraft.resources.ResourceLocation

/** 服务端返回给单个查看者的固定字段 Status 快照。 */
@CooAutoRegister
class PacketPerformanceStatusSnapshotS2C : CooPacket() {
    /** 服务端平均 MSPT。 */
    @CodecField var averageMspt: Double = 0.0

    /** 服务端 Barrage 数。 */
    @CodecField var barrages: Int = 0

    /** 服务端生成快照的 epoch 毫秒。 */
    @CodecField var capturedAtEpochMillis: Long = 0L

    /** 服务端 CooFX scene 数。 */
    @CodecField var cooFxScenes: Int = 0

    /** 服务端 Composition 数。 */
    @CodecField var compositions: Int = 0

    /** 服务端 DisplayEntity 数。 */
    @CodecField var displayEntities: Int = 0

    /** 服务端 Emitter 数。 */
    @CodecField var emitters: Int = 0

    /** 服务端 JVM 累计垃圾收集次数。 */
    @CodecField var gcCollectionCount: Long = 0L

    /** 服务端 JVM 累计垃圾收集耗时毫秒。 */
    @CodecField var gcCollectionTimeMs: Long = 0L

    /** 服务端最大堆字节数。 */
    @CodecField var heapMaxBytes: Long = 0L

    /** 服务端已使用堆字节数。 */
    @CodecField var heapUsedBytes: Long = 0L

    /** 服务端历史最大 MSPT。 */
    @CodecField var maxMspt: Double = 0.0

    /** 服务端累计收到的 CooPacket 业务字节数。 */
    @CodecField var networkReceivedBytes: Long = 0L

    /** 服务端累计收到的 CooPacket 数。 */
    @CodecField var networkReceivedPackets: Long = 0L

    /** 服务端累计发送的 CooPacket 业务字节数。 */
    @CodecField var networkSentBytes: Long = 0L

    /** 服务端累计发送的 CooPacket 数。 */
    @CodecField var networkSentPackets: Long = 0L

    /** 当前在线玩家数。 */
    @CodecField var onlinePlayers: Int = 0

    /** 服务端历史 P95 MSPT。 */
    @CodecField var p95Mspt: Double = 0.0

    /** 服务端旧 ParticleGroup 数。 */
    @CodecField var particleGroups: Int = 0

    /** 服务端配置的 Status 快照刷新间隔。 */
    @CodecField var refreshIntervalTicks: Int = 20

    /** 服务端 RenderEntity 数。 */
    @CodecField var renderEntities: Int = 0

    /** 服务端当前 tick 序号。 */
    @CodecField var serverTick: Long = 0L

    /** 服务端管理的声音实例数。 */
    @CodecField var soundInstances: Int = 0

    /** 服务端管理的循环声音数。 */
    @CodecField var soundLoops: Int = 0

    /** 当前目标 TPS。 */
    @CodecField var targetTps: Double = 20.0

    /** 服务端 Terrain effect group 数。 */
    @CodecField var terrainEffectGroups: Int = 0

    /** 服务端 Terrain mapping 实例数。 */
    @CodecField var terrainMappings: Int = 0

    /** 当前可维持 TPS。 */
    @CodecField var tps: Double = 20.0

    /** 服务端原版 Connection 累计收到的 Packet 数。 */
    @CodecField var vanillaNetworkReceivedPackets: Long = 0L

    /** 服务端原版 Connection 累计发送的 Packet 数。 */
    @CodecField var vanillaNetworkSentPackets: Long = 0L

    /** 返回该业务包的稳定协议 ID。 */
    override fun id(): ResourceLocation {
        return ResourceLocation.fromNamespaceAndPath(CooParticlesConstants.MOD_ID, PACKET_ID)
    }

    /** 把协议字段恢复为不可变服务端快照。 */
    fun toSnapshot(): PerformanceStatusServerSnapshot {
        return PerformanceStatusServerSnapshot(
            capturedAtEpochMillis = capturedAtEpochMillis,
            serverTick = serverTick,
            refreshIntervalTicks = refreshIntervalTicks.coerceIn(5, 1_200),
            targetTps = targetTps,
            tps = tps,
            averageMspt = averageMspt,
            p95Mspt = p95Mspt,
            maxMspt = maxMspt,
            onlinePlayers = onlinePlayers,
            particleGroups = particleGroups,
            renderEntities = renderEntities,
            displayEntities = displayEntities,
            emitters = emitters,
            compositions = compositions,
            gcCollectionCount = gcCollectionCount,
            gcCollectionTimeMs = gcCollectionTimeMs,
            terrainEffectGroups = terrainEffectGroups,
            terrainMappings = terrainMappings,
            soundInstances = soundInstances,
            soundLoops = soundLoops,
            barrages = barrages,
            cooFxScenes = cooFxScenes,
            heapUsedBytes = heapUsedBytes,
            heapMaxBytes = heapMaxBytes,
            cooPackets = PerformanceStatusNetworkTotals(
                sentPackets = networkSentPackets,
                sentBytes = networkSentBytes,
                receivedPackets = networkReceivedPackets,
                receivedBytes = networkReceivedBytes,
            ),
            vanillaPackets = PerformanceStatusVanillaPacketTotals(
                sentPackets = vanillaNetworkSentPackets,
                receivedPackets = vanillaNetworkReceivedPackets,
            ),
        )
    }

    /** 协议注册常量和快照转换入口。 */
    companion object {
        /** 业务包稳定注册 ID，属于网络协议的一部分。 */
        private const val PACKET_ID = "performance_status_snapshot_s2c"

        /** 从不可变服务端快照创建可反射编码的业务包。 */
        fun from(snapshot: PerformanceStatusServerSnapshot): PacketPerformanceStatusSnapshotS2C {
            return PacketPerformanceStatusSnapshotS2C().also { packet ->
                packet.averageMspt = snapshot.averageMspt
                packet.barrages = snapshot.barrages
                packet.capturedAtEpochMillis = snapshot.capturedAtEpochMillis
                packet.cooFxScenes = snapshot.cooFxScenes
                packet.compositions = snapshot.compositions
                packet.displayEntities = snapshot.displayEntities
                packet.emitters = snapshot.emitters
                packet.gcCollectionCount = snapshot.gcCollectionCount
                packet.gcCollectionTimeMs = snapshot.gcCollectionTimeMs
                packet.heapMaxBytes = snapshot.heapMaxBytes
                packet.heapUsedBytes = snapshot.heapUsedBytes
                packet.maxMspt = snapshot.maxMspt
                packet.networkReceivedBytes = snapshot.cooPackets.receivedBytes
                packet.networkReceivedPackets = snapshot.cooPackets.receivedPackets
                packet.networkSentBytes = snapshot.cooPackets.sentBytes
                packet.networkSentPackets = snapshot.cooPackets.sentPackets
                packet.onlinePlayers = snapshot.onlinePlayers
                packet.p95Mspt = snapshot.p95Mspt
                packet.particleGroups = snapshot.particleGroups
                packet.refreshIntervalTicks = snapshot.refreshIntervalTicks
                packet.renderEntities = snapshot.renderEntities
                packet.serverTick = snapshot.serverTick
                packet.soundInstances = snapshot.soundInstances
                packet.soundLoops = snapshot.soundLoops
                packet.targetTps = snapshot.targetTps
                packet.terrainEffectGroups = snapshot.terrainEffectGroups
                packet.terrainMappings = snapshot.terrainMappings
                packet.tps = snapshot.tps
                packet.vanillaNetworkReceivedPackets = snapshot.vanillaPackets.receivedPackets
                packet.vanillaNetworkSentPackets = snapshot.vanillaPackets.sentPackets
            }
        }
    }
}
