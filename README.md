# PlanPeakMOTD

作者：**xhGG**

面向 Velocity 3.5.0、MiniMOTD 和 Folia 26.1.2 的双端插件。它把服务器列表人数显示改为：

```text
当前代理总在线人数 / 子服 Plan 今日峰值
```

鼠标悬浮人数时默认显示：

```text
服务器总在线人数: {online}
服务器今日巅峰: {today_peak}
```

Velocity 端不会修改 MiniMOTD 的 MOTD 文本、图标或版本信息。

## 主要功能

- 左侧人数始终显示 Velocity 当前总在线人数。
- 右侧人数可显示由 Plan 统计的今日巅峰在线人数。
- 人数悬浮内容支持最多 20 行自定义文字和颜色。
- 支持在悬浮内容中使用子服提供的服务器型 PlaceholderAPI 变量。
- 两端配置都支持插件自己的热重载命令。
- 不需要额外安装 MySQL、MariaDB、Redis，也不需要让 Velocity 直接连接 Plan 数据库。
- 与 MiniMOTD 共存，不修改 MOTD、图标、版本文字和模组信息。

## 工作方式

- `PlanPeakMOTD-Velocity` 在 `127.0.0.1` 上接收子服回报，修改 Ping 的在线人数、最大人数和悬浮列表。
- `PlanPeakBridge-Folia` 通过本子服的 Plan Query API 读取 `plan_tps` 中当天峰值，再用本机 HTTP 回报。
- 不需要 MySQL/MariaDB，不需要 Plan 安装在 Velocity，也不依赖玩家在线。
- Velocity 会把当天已收到的峰值写入本地缓存，所以子服暂时关闭或 Velocity 重启后仍可显示。

## 运行要求

- Velocity `3.5.0`，并已安装 MiniMOTD。
- Folia `26.1.2`，每个需要统计的子服均已安装 Plan。
- 如需额外变量，子服还要安装 PlaceholderAPI 和对应变量扩展。
- Folia 26.1.2 使用 Java 25；Velocity 插件本体编译为 Java 21 字节码。
- 当前通信方式仅允许本机回环地址，Velocity 和各子服应运行在同一台机器上。

## 性能设计

- Plan 查询和 PAPI 解析默认每 60 秒一次，并在子服独立异步线程执行，不阻塞 Folia 区域线程。
- 查询只对当前子服、当天时间范围执行 `MAX(players_online)`；Plan 已为服务器与时间字段建立索引。
- 子服与 Velocity 只走 `127.0.0.1` 的小型 HTTP 回报，不经过公网。
- Velocity 的 Ping 处理不访问 Plan、不发网络请求、不读写文件；它读取不可变内存快照。
- 在线数、峰值/PAPI 快照和配置都没变化时，已渲染的悬浮列表会直接复用。
- 峰值或 PAPI 值没有变化时不会重复写缓存文件。

## 下载插件本体

仓库的 `dist` 文件夹内包含两个可直接安装的 JAR：

- [`PlanPeakMOTD-Velocity-1.0.0.jar`](dist/PlanPeakMOTD-Velocity-1.0.0.jar)
- [`PlanPeakBridge-Folia-1.0.0.jar`](dist/PlanPeakBridge-Folia-1.0.0.jar)

不要把两个 JAR 放到同一个服务端。Velocity JAR 只放 Velocity，Folia JAR 只放子服。

## 详细安装教程

### 1. 安装 Velocity 端

1. 停止 Velocity。
2. 将 `PlanPeakMOTD-Velocity-1.0.0.jar` 放入 Velocity 的 `plugins` 文件夹。
3. 启动 Velocity 一次，等待插件生成配置。
4. 打开 `plugins/planpeakmotd/config.properties`。
5. 保留自动生成的 `shared-token`；稍后需要把它完整复制到每个子服。

推荐配置示例：

```properties
bind-address=127.0.0.1
port=18765
shared-token=这里保留插件自动生成的随机密钥

timezone=Asia/Shanghai
aggregate-mode=sum
modify-max-players=true

hover-line-1=&b—— 4V4D ——
hover-line-2=&b在线人数 &f{online} 人
hover-line-3=&b今日巅峰 &f{today_peak} 人
hover-line-4=&b————————
```

各项含义：

- `bind-address`：必须是本机回环地址，推荐保持 `127.0.0.1`。
- `port`：两端通信端口；修改后所有子服的 `velocity-url` 也要同步修改。
- `shared-token`：插件生成的共享密钥，至少 16 个字符；不要公开它。
- `timezone`：用于判断每天的开始和结束，两端必须一致。
- `aggregate-mode=sum`：将所有子服各自的今日峰值相加。
- `aggregate-mode=max`：只取所有子服中最高的单服峰值。
- `modify-max-players=true`：将服务器列表右侧人数改成今日峰值。
- `modify-max-players=false`：只添加悬浮文字，保留 MiniMOTD 的右侧人数。
- `hover-line-1` 至 `hover-line-20`：自定义悬浮文字；不需要的行可直接删除。

### 2. 安装 Folia 子服端

每个需要参与统计的 Folia 子服都要安装桥接插件：

1. 确认 Plan 已安装并能正常记录数据。
2. 停止子服。
3. 将 `PlanPeakBridge-Folia-1.0.0.jar` 放入子服的 `plugins` 文件夹。
4. 启动子服一次，等待插件生成配置。
5. 打开 `plugins/PlanPeakBridge/config.properties`。
6. 设置唯一的 `server-id`，并粘贴 Velocity 端完全相同的 `shared-token`。

配置示例：

```properties
server-id=survival
velocity-url=http://127.0.0.1:18765/report
shared-token=粘贴Velocity端完全相同的密钥

timezone=Asia/Shanghai
report-interval-seconds=60
connect-timeout-seconds=3
request-timeout-seconds=5

# 可选：映射服务器型 PAPI 变量
# papi.tps=%server_tps_1%
# papi.plan_online=%plan_server_players_online%
```

配置说明：

- `server-id` 在所有子服中必须唯一，只能包含字母、数字、点、下划线和短横线，最长 64 个字符。
- 多个子服可分别使用 `lobby`、`survival`、`minigame-1` 等 ID。
- 所有同机子服可以共用默认地址 `http://127.0.0.1:18765/report`。
- 所有子服必须使用 Velocity 端同一份 `shared-token` 和同一个 `timezone`。
- `report-interval-seconds` 默认 60 秒，允许 10 至 3600 秒；性能方面建议保持默认值。

### 3. 检查连接

先在 Folia 子服执行：

```text
/planpeakbridge send
```

该命令会立即读取一次 Plan 并向 Velocity 回报。再在 Velocity 控制台执行：

```text
/peakmotd status
```

如果状态中能看到对应的 `server-id` 和峰值，说明两端连接成功。最后刷新 Minecraft 多人游戏服务器列表即可看到效果。

## 变量与配置

Velocity 的 `hover-line-1` 到 `hover-line-20` 均可自由增删和修改，支持：

- `{online}`：Velocity 当前总在线人数。
- `{today_peak}`：所有已回报子服的今日峰值汇总。
- `{today_peak:survival}`：指定 `server-id` 的今日峰值。
- `{papi:survival:tps}`：子服 `survival` 回报的 PAPI 别名 `tps`。
- `&a`、`&6` 等传统颜色代码和 `&#RRGGBB` 十六进制颜色。

PAPI 变量先在对应 Folia 子服的 `config.properties` 中建立映射：

```properties
papi.tps=%server_tps_1%
papi.plan_online=%plan_server_players_online%
```

然后在 Velocity 配置中使用：

```properties
hover-line-3=&a生存服 TPS: &f{papi:survival:tps}
hover-line-4=&aPlan 当前人数: &f{papi:survival:plan_online}
```

这里不能可靠使用 `%player_name%`、个人金币等玩家型变量，因为查看服务器列表的人尚未登录，服务端没有可传给 PAPI 的玩家对象。所用 PAPI 扩展也必须能在 Folia 的异步环境安全运行。

多子服默认使用 `aggregate-mode=sum`，即相加各子服自己的今日峰值。也可改成 `max` 只取最高子服值。注意：各子服峰值发生时间可能不同，所以 `sum` 是各服峰值之和，不等同于严格意义上同一时刻的网络峰值。

如果只想增加悬浮文字而保留 MiniMOTD 的右侧人数，把 `modify-max-players=false`。

## MiniMOTD 注意事项

本插件使用 Velocity 事件的最低优先级（最后处理阶段）覆盖人数与悬浮列表。即使 MiniMOTD 配置了隐藏人数，本插件也会重新启用人数栏以满足悬浮显示要求；MiniMOTD 的 MOTD 文本、图标、版本文本和模组信息保持不变。

## 命令

- Velocity：`/peakmotd status`、`/peakmotd reload`，权限 `planpeakmotd.admin`。
- Folia：`/planpeakbridge status|send|reload`，权限 `planpeakbridge.admin`。

两个插件都支持配置热重载。不要使用 Bukkit/Paper 的整服 `/reload`；修改文件后分别执行上述 `reload` 子命令即可。Folia 端热重载会取消旧任务、重建 HTTP 客户端、重新检测 PAPI 并立即回报。

## 常见问题

### 子服提示 shared-token 错误

把 Velocity 的 `plugins/planpeakmotd/config.properties` 中完整的 `shared-token` 复制到子服。不要遗漏字符，也不要在前后添加空格。

### Velocity 一直没有收到子服数据

依次确认 Velocity 正在运行、两端端口一致、地址为 `http://127.0.0.1:18765/report`、密钥完全相同，并检查 Plan 是否正常启用。随后执行 `/planpeakbridge send` 查看子服控制台给出的具体错误。

### 右侧人数没有变化

确认 Velocity 配置中设置了 `modify-max-players=true`，执行 `/peakmotd reload`，然后重新刷新服务器列表。

### PAPI 变量没有解析

确认子服安装了 PlaceholderAPI 和相应扩展。这里只适合服务器型变量，不适合 `%player_name%`、个人金币、个人等级等需要在线玩家对象的变量。执行 `/planpeakbridge status` 可以查看失败的 PAPI 别名。

### 跨天后峰值日期不正确

确认 Velocity 和所有子服的 `timezone` 完全一致，推荐统一使用 `Asia/Shanghai`。

## 构建

构建整个项目需要 Java 25（Folia 26.1.2 API 本身要求 Java 25）；Velocity 产物仍编译为 Java 21 字节码：

```bash
./gradlew clean build
```

产物位于：

- `velocity-plugin/build/libs/PlanPeakMOTD-Velocity-1.0.0.jar`
- `folia-bridge/build/libs/PlanPeakBridge-Folia-1.0.0.jar`

## 项目结构

```text
plan-peak-motd/
├─ velocity-plugin/   Velocity 端源码
├─ folia-bridge/      Folia 子服端源码
├─ dist/              可直接安装的两个 JAR
├─ gradle/            Gradle Wrapper
├─ README.md          详细使用教程
└─ LICENSE            MIT 开源许可证
```

## 开源协议

本项目由 **xhGG** 开发，使用 [MIT License](LICENSE) 开源。
