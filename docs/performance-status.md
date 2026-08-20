# Performance Status

Performance Status 以客户端 tick 为行采集性能数据，并把最近一次按需取得的服务端快照关联到同一行，便于在 Excel、Pandas、R 或其他数据工具中分析对象数量与 FPS/TPS/MSPT 的关系。

## 命令

命令需要权限等级 2，并且必须由玩家执行：

```text
/cooparticles status start
/cooparticles status gui
/cooparticles status stop
```

兼容根命令 `/cooparticlesapi status ...` 提供相同行为。

- `start`：开始持续采集和 CSV 写入；只有该命令会创建输出文件。
- `gui`：打开纯实时客户端/服务器/网络视图，不创建 CSV，关闭界面也不会导出。
- `stop`：停止由 `start` 创建的记录会话、取消未完成请求并关闭 CSV。

记录会话和 GUI 查看相互独立：关闭 GUI 不影响正在运行的记录，未执行 `start` 时反复打开或关闭 GUI 不会产生文件。断开服务器或关闭客户端时，活动 writer 会自动关闭。

## 刷新间隔

`APIConfig.statusServerRefreshIntervalTicks` 控制服务端快照请求间隔，默认 20 客户端 tick（约一秒），有效范围为 5 至 1200 tick。服务端把自己的配置值放入首个快照，客户端收到后按该值安排后续请求；因此远程客户端不需要和服务端手工同步配置文件。

客户端只有在 Status 记录会话或实时 GUI 活动时才请求服务端快照，并且同一时间最多保留一个未完成请求。服务端再次验证权限等级 2，并按玩家和服务端刷新间隔限流；未查看或未记录 Status 的其他客户端不会产生这类流量。

## 打开热键

默认同时按住 `F3` 和 Grave/波浪号键可直接打开 Status GUI。两个按键分别注册为原版 KeyMapping，可在“控制”设置的 CooParticlesAPI 分类中分别改绑；原版按键设置本身没有单条组合键配置模型，因此界面中会显示“Status 修饰键”和“打开 Status 面板”两个条目。

## 实时图表

客户端、服务器和网络表格中的单值指标都带有图表选择方框。点击一行可增加或移除该维度，切换分段不会清除已有选择；同时最多显示 6 条曲线，超过上限时自动移除最早选择的维度。默认显示 FPS、Server TPS 和 CParticles。

曲线使用 GUI 打开期间或记录会话中的最近 240 个客户端 tick，并在 `GuiGraphics` 原生批次内用旋转的两像素细矩形连接相邻采样点。该方式生成连续折线，同时避开不同显卡驱动对 OpenGL line primitive 和 GUI 外部直接 buffer 提交的差异。每项指标使用独立的零基线动态量程，量程上界不会低于该指标的基础性能范围；图例按“当前值 / 当前窗口量程上界”显示原始单位。因此可以把对象数量、FPS、TPS、MSPT、堆内存和网络 interval 等不同数量级指标叠加，比较变化发生的时间关系，而不会由最大数量级独占纵轴。

## 输出

CSV 使用 UTF-8（无 BOM）和稳定英文表头，输出目录为：

```text
<game directory>/cooparticlesapi-status/status-yyyyMMdd-HHmmss-SSS.csv
```

每个客户端 tick 写一行。完整历史直接流式写入文件；GUI 只保留最近 240 行趋势数据，因此长时间记录不会让内存随样本数持续增长。

主要列组：

- 客户端时间：采集 epoch、会话 elapsed、真实 tick interval、FPS、frame time、client TPS。
- 客户端对象：Particles、CParticles、CParticle systems、SoundInstances、Coo sound/loops、RenderEntities、DisplayEntities、Emitters、Compositions、CooFX scenes/mesh particles/models、Terrain effects/mappings、post effects、shader programs。
- 服务端性能：snapshot age、server tick、target TPS、TPS、平均/P95/最大 MSPT、在线玩家和 JVM heap。
- 服务端对象：ParticleGroups、RenderEntities、DisplayEntities、Emitters、Compositions、Terrain effects/mappings、sound/loops、Barrages、CooFX scenes。
- 网络：客户端每 tick 增量和累计值；服务端每次新快照间隔的增量和累计值。没有新服务端快照的客户端 tick 行会把 interval 列留空，而不是写入伪造的零流量。

服务端 `snapshot age` 使用客户端收到响应时记录的单调时钟计算，不依赖客户端与服务端墙钟同步。`server_captured_at_epoch_ms` 单独保留服务端墙钟时间，供已经完成时钟对齐的数据处理流程使用。

## 指标边界

`CooPacket` 字节列只统计业务 packet 成功编码后的 payload 字节。它不包含 CooPacket envelope、Minecraft custom payload framing、压缩、加密、TCP/IP 或其他传输层开销，也不包含旧的非 CooPacket custom payload。

客户端 `client_tps` 由最近 20 个有效客户端 tick 间隔的平均值计算，并限制到原版 20 TPS 上限；单 tick 调度提前不会再显示成超过 20 TPS。服务端 TPS 由当前目标 tickrate 与原版平均 MSPT 推导：`min(target TPS, 1000 / average MSPT)`。它表示当前 tick 成本可维持的 TPS；平均、P95 和最大 MSPT 直接来自原版服务器 tick 历史。

客户端 `SoundInstances` 来自原版 `SoundEngine` 当前实际声道映射；`managed_sound_instances` 和 `sound_loops` 仅表示 CooParticlesAPI 管理的声音。
