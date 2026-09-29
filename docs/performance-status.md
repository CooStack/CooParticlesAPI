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

`APIConfig.statusVanillaPacketAggregationTicks` 控制客户端原版 Connection 包数量的聚合窗口，单位为客户端 tick，默认和最小值都是 1。窗口大于 1 时，只有窗口完成的样本行写入该窗口的原版上传/下载包数，中间行留空；GUI 标签会显示当前窗口 tick 数。服务端原版包数量受按需快照刷新间隔限制，因此按快照间隔输出，不伪装成逐 tick 值。

客户端只有在 Status 记录会话或实时 GUI 活动时才请求服务端快照，并且同一时间最多保留一个未完成请求。服务端再次验证权限等级 2，并按玩家和服务端刷新间隔限流；未查看或未记录 Status 的其他客户端不会产生这类流量。

## 打开热键

默认同时按住 `F3` 和 Grave/波浪号键可直接打开 Status GUI。两个按键分别注册为原版 KeyMapping，可在“控制”设置的 CooParticlesAPI 分类中分别改绑；原版按键设置本身没有单条组合键配置模型，因此界面中会显示“Status 修饰键”和“打开 Status 面板”两个条目。

## 实时图表

客户端、服务器和网络表格中的单值指标都带有图表选择方框。点击一行可增加或移除该维度，切换分段不会清除已有选择；关闭 GUI 后再次打开也会恢复上次选择。同时最多显示 12 条曲线，超过上限时自动移除最早选择的曲线。默认显示 FPS、Server TPS 和 CParticles。

GUI 顶部的独立“相关性”页签提供“性能项”和“影响项”两个输入补全框。输入名称后会显示匹配候选，支持鼠标点击或 `↑`/`↓` + `Tab`/`Enter` 选择；选择输入后点击“添加比值”，会加入一条 `影响项 ÷ 性能项` 曲线。相关性控件和已添加比值列表不会占用客户端、服务器或网络页面。性能项包括 FPS、TPS、MSPT、堆内存、GC 和网络流量等；影响项包括 Particles、CParticles、声音、实体、Emitters、Compositions 和地形对象等。

图表底部的时间轴显示当前保留历史。底部“历史秒”输入框默认是 60 秒，最小值为 1 秒，也可以输入更大的秒数来保留更长的趋势历史。拖动左、右把手向内可放大时间跨度，向外可缩小；拖动两个把手之间的选区可平移窗口。时间跨度只属于当前 GUI，重新打开时回到完整历史，但曲线维度选择和历史秒设置会保留。

面板不执行原版菜单背景模糊，所有曲线的两像素线段直接写入同一个 `RenderType.gui()` 批次，再统一提交；不再逐线段调用会立即刷新批次的 `GuiGraphics.fill()`，也不逐线段旋转矩阵。继续使用原版 GUI 渲染路径，不增加自定义 shader 或 framebuffer。

图表数据以最高 10Hz 更新，采样和 CSV 仍按客户端 tick 进行。拖动时间窗口、改变曲线选择或绘图区尺寸时立即刷新。每条已选曲线使用增量多级块索引，正常更新仅吸收新增样本；窗口查询读取完整块统计及块边缘，不反复扫描完整历史。首次选择曲线或重新打开长历史面板仍需一次建索引，成本与保留样本数有关。

可见窗口按真实 `elapsed_ms` 划分最多 128 个时间桶，每桶保留原始首、谷、峰、末样本并保持时间顺序，每条曲线最多 512 点。短暂掉帧和稀疏网络增量不会再因固定 128 点等距抽样而消失；同一横坐标的垂直峰谷也会保留。时间轴不再把不等间隔的样本伪装成等间隔。

底部“量程”可切换零起和局部模式：零起保持零基线，局部围绕当前窗口真实峰谷放大，并保留边界留白。帧耗时、MSPT 和比值不再受固定 50ms 或 1.0 的最小上界限制。各曲线独立缩放，因此不能用不同曲线的像素高度直接比较绝对值。图例悬停可查看完整名称、当前值、最低/最高值及实际纵轴范围；选满 12 条曲线时采用紧凑多列图例，不挤占整个绘图区。

图例统计覆盖当前可视窗口的所有有效原始样本，而非降采样点。比值的“总”值为有效影响项之和除以性能项之和，不是各点比值的简单平均；性能项为零、负数、非有限数或数据缺失的样本不参与计算。空窗口显示缺失而不是零流量；“当前”表示窗口内最后一个有效值。比值只描述负载关系，不等同于统计相关系数，也不能单凭它推断因果。

## 输出

CSV 使用 UTF-8（无 BOM）和稳定英文表头，输出目录为：

```text
<game directory>/cooparticlesapi-status/status-yyyyMMdd-HHmmss-SSS.csv
```

每个客户端 tick 写一行。完整历史直接流式写入文件；GUI 通过环形存储保留“历史秒”指定的逐 tick 样本，过期样本逐个移除，不再定期搬移整个有效数组。固定历史时长下，样本和索引的有效数据量不会随会话无限增长；主动增加保留时长仍会增加内存用量，数组容量也可能保留曾经的高水位。CSV 仍保留完整记录，写入方式和列结构未改变。

监控不能承诺零开销：采集、GUI 绘制、首次索引和 CSV 写盘仍消耗资源。回归测试验证峰谷、完整窗口比值、真实时间定位、缓存失效、滚动淘汰及长历史查询读取量，不代替客户端 FPS、长时间运行和 Iris 开关状态的实测。

主要列组：

- 客户端时间：采集 epoch、会话 elapsed、真实 tick interval、FPS、frame time、client TPS。
- 客户端对象：Particles、CParticles、CParticle systems、SoundInstances、Coo sound/loops、RenderEntities、DisplayEntities、Emitters、Compositions、CooFX scenes/mesh particles/models、Terrain effects/mappings、post effects、shader programs。
- JVM：客户端和服务端已用/最大堆内存、累计 GC 次数和累计 GC 耗时。
- 服务端性能：snapshot age、server tick、target TPS、TPS、平均/P95/最大 MSPT、在线玩家。
- 服务端对象：ParticleGroups、RenderEntities、DisplayEntities、Emitters、Compositions、Terrain effects/mappings、sound/loops、Barrages、CooFX scenes。
- 网络：CooPacket 客户端每 tick 增量和服务端快照间隔增量；原版 Connection 包数量按客户端可配置 tick 窗口及服务端快照间隔输出。没有完整窗口或新快照的行留空，而不是写入伪造的零流量。

服务端 `snapshot age` 使用客户端收到响应时记录的单调时钟计算，不依赖客户端与服务端墙钟同步。`server_captured_at_epoch_ms` 单独保留服务端墙钟时间，供已经完成时钟对齐的数据处理流程使用。

## 指标边界

`CooPacket` 字节列只统计业务 packet 成功编码后的 payload 字节。它不包含 CooPacket envelope、Minecraft custom payload framing、压缩、加密、TCP/IP 或其他传输层开销，也不包含旧的非 CooPacket custom payload。

原版 Packet 指标在 `Connection` 的入站处理和实际 Netty 提交边界统计所有 Minecraft `Packet` 数量，包含 CooPacket 所依附的 custom payload 原版包。当前不提供原版 Packet 字节列：Packet 对象大小不是线上编码字节数，不能用 JVM 对象大小代替；若后续增加字节统计，必须在压缩/加密口径明确的 codec 帧边界计量。

HEAP 使用 `Runtime.totalMemory() - freeMemory()` 表示采样时已用 JVM 堆。分配期间持续上升并在 GC 后快速下降是正常锯齿；它本身不能证明泄漏。客户端和服务端同时提供累计 GC 次数与累计 GC 耗时曲线。判断泄漏应关注多次 GC 后的堆内存最低点是否持续抬升，并与 RenderEntities、Compositions、CParticles 等对象数量同步分析。

客户端 `client_tps` 由最近 20 个有效客户端 tick 间隔的平均值计算，并限制到原版 20 TPS 上限；单 tick 调度提前不会再显示成超过 20 TPS。服务端 TPS 由当前目标 tickrate 与原版平均 MSPT 推导：`min(target TPS, 1000 / average MSPT)`。它表示当前 tick 成本可维持的 TPS；平均、P95 和最大 MSPT 直接来自原版服务器 tick 历史。

客户端 `SoundInstances` 来自原版 `SoundEngine` 当前实际声道映射；`managed_sound_instances` 和 `sound_loops` 仅表示 CooParticlesAPI 管理的声音。
