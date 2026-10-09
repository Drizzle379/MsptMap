<div align="center">

<img src="src/main/resources/assets/msptmap/icon.png" alt="MsptMap" width="128">

# MsptMap

**在 [Xaero 的世界地图](https://modrinth.com/mod/xaeros-world-map)上，绘制区块 mspt 热力图。**
定位造成服务端卡顿的具体区块。

[![Modrinth](https://img.shields.io/badge/Modrinth-msptmap-00AF5C?style=flat-square&logo=modrinth&logoColor=white)](https://modrinth.com/mod/msptmap)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.20--26.3-62b47a?style=flat-square&logo=data:image/svg%2Bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI%2BPHBhdGggZmlsbD0iI2ZmZiIgZD0iTTQgMmgxNmEyIDIgMCAwIDEgMiAydjE2YTIgMiAwIDAgMS0yIDJINGEyIDIgMCAwIDEtMi0yVjRhMiAyIDAgMCAxIDItMm0yIDR2NGg0djJIOHY2aDJ2LTJoNHYyaDJ2LTZoLTJ2LTJoNFY2aC00djRoLTRWNnoiLz48L3N2Zz4%3D)](#支持版本)
[![Fabric Loader](https://img.shields.io/badge/Fabric_Loader-0.19.3%2B-dbd0b4?style=flat-square&logo=data:image/svg%2Bxml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAxNiAxNiI%2BPHBhdGggZmlsbD0iI2ZmZiIgZmlsbC1ydWxlPSJldmVub2RkIiBkPSJNOCAxdjFIN3YySDZ2MUg1djFINHYxSDN2MUgydjFIMXYyaDF2MWgxdjFoMXYxaDF2MWgydi0xaDF2LTJoMXYtMWgxdi0xaDFWOWgyVjhoMVY2aC0xVjVoLTFWNGgtMVYzaC0xVjJIOVYxeiIvPjwvc3ZnPg%3D%3D)](#-环境要求)
[![Java](https://img.shields.io/badge/Java-17--25-e76f00?style=flat-square&logo=openjdk&logoColor=white)](#支持版本)
[![Environment](https://img.shields.io/badge/Environment-client_%2B_server-4c8eda?style=flat-square)](#-安装)
[![License](https://img.shields.io/github/license/Drizzle379/MsptMap?style=flat-square&label=License&color=3da639)](#-许可)
[![Release](https://img.shields.io/github/v/release/Drizzle379/MsptMap?style=flat-square&label=Release&sort=semver&color=8957e5)](https://github.com/Drizzle379/MsptMap/releases)
[![Stars](https://img.shields.io/github/stars/Drizzle379/MsptMap?style=flat-square&label=Stars&color=e3b341)](https://github.com/Drizzle379/MsptMap)

[English](README.md) · **简体中文**

[功能](#-功能) · [环境要求](#-环境要求) · [安装](#-安装) · [用法](#-用法) · [指令](#-指令) · [监控](#-监控) · [设置](#-设置) · [工作原理](#-工作原理) · [构建](#-构建)

</div>

---

## ✨ 功能

| | |
| :--- | :--- |
| 🌡️ **区块级热力图** | 按区块所占用的刻耗时着色，由绿经黄至红。整段窗口内未产生可测耗时的弱加载区块保持淡灰，以免掩盖真正的高负载区域。 |
| 🧩 **七类耗时分别计量** | 随机刻、计划刻、方块更新、方块事件、方块实体、实体与刷怪各自独立计量；悬停详情指出耗时所属的类别。 |
| 🏷️ **加载来源反查** | 反查各区块的加载票来源：`玩家加载`、`强制加载`、`传送门`、`末影珍珠`、`末影龙` 等。持有加载票的区块以蓝框标出，悬停详情给出票名及其所在区块的 `@x,z` 坐标。 |
| 📊 **扫描总览** | 单一面板汇总总卡顿、实体数、加载源与卡顿区块 TOP5，且按**三个维度合计**；地图本身一次仅显示一个维度。 |
| 🖱️ **点击定位** | 点击总览中的条目可将地图定位至对应区块；折叠行可展开完整的加载源列表，并支持滚动。 |
| 📡 **常态化监控** | 默认关闭。开启后服务端持续监测 MSPT，持续超标即自动扫描，并把卡顿最重的五个区块以可点击的告警发给在线管理员。 |
| 🔁 **版本宽容** | 客户端与服务端不要求运行相同版本。版本不一致时仍会给出结果，并在聊天栏附注说明；无法解析的数据包仅在聊天栏报告失败，不会导致客户端断开连接。 |

## 📦 环境要求

| 依赖 | 必需性 | 说明 |
| :--- | :--- | :--- |
| **Minecraft** | 必需 | 可用版本见[下表](#支持版本) |
| **Fabric Loader** | 必需 | ≥ 0.19.3 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 必需 | |
| [Xaero 的世界地图](https://modrinth.com/mod/xaeros-world-map) | 仅客户端 | 注入锚点已在 Xaero 1.44.2 / 1.46.0 / 1.46.4 上验证（多数版本使用 1.46.0）。若 Xaero 后续更新导致锚点失配，按钮与热力图将被停用，游戏仍可正常启动。 |
| [Mod Menu](https://modrinth.com/mod/modmenu) | 建议安装 | 提供设置界面的快捷入口 |

### 支持版本

| Minecraft | 构建目录 | 运行期 Java | Fabric API 下限 |
| :--- | :--- | :--- | :--- |
| 1.20 – 1.20.2 | `versions/1.20.1` | 17 | `0.91.0+1.20.1` |
| 1.20.3 – 1.20.4 | `versions/1.20.4` | 17 | `0.97.1+1.20.4` |
| 1.21 – 1.21.1 | `versions/1.21.1` | 21 | `0.116.17+1.21.1` |
| 1.21.2 – 1.21.4 | `versions/1.21.4` | 21 | `0.119.4+1.21.4` |
| 1.21.6 – 1.21.10 | `versions/1.21.8` | 21 | `0.136.1+1.21.8` |
| 1.21.11 | `versions/1.21.11` | 21 | `0.141.6+1.21.11` |
| 26.1 – 26.1.2 | `versions/26.1.2` | 25 | `0.155.3+26.1.2` |
| 26.2 | `versions/26.2` | 25 | `0.158.0+26.2` |
| 26.3 | `versions/26.3` | 25 | `0.158.0+26.3` |

> [!NOTE]
> **1.20.5、1.20.6、1.21.5 暂不支持。** 上表每一行对应一个 jar；完整构建会为每一行各产出一个。

## 🚀 安装

从 [Modrinth](https://modrinth.com/mod/msptmap) 或 [Releases 页](https://github.com/Drizzle379/MsptMap/releases) 下载最新构建，再将 jar 放入 `mods/` 目录。

> [!IMPORTANT]
> 采样在**服务端**进行，故两侧均需安装本模组；客户端另需安装 Xaero 的世界地图。单人游戏时两侧运行于同一台机器，安装一份即可。

服务端与客户端可运行不同版本的 MsptMap。版本不一致时仍会给出结果，并在聊天栏附注说明（结果可能不准确）；数据包无法解析时仅在聊天栏报告失败，不会断开连接。

## 🎮 用法

1. 打开世界地图，点击左上角的**扫描**按钮以开始扫描，或输入 `/msptmap scan [秒数]`
2. 采样完成后，地图按区块的卡顿程度着色：绿 → 黄 → 红；弱加载区块为淡灰色
3. 悬停任意区块可查看详情：坐标、加载等级、加载票、实体数、合计 mspt 与各类耗时明细
4. **扫描总览**显示在扫描按钮旁，给出合计、加载源与卡顿区块 TOP5。点击某一行可将地图定位至该区块；折叠行可展开完整的加载源列表
5. 点击 ✕ 按钮清空热力图

### 加载票说明

加载票指明该区块被保持加载的机制：`玩家加载` 表示附近有玩家，`强制加载` 表示经由 `/forceload` 加载，`末影珍珠` 表示珍珠落点，等等。其后的 `@x,z` 为票所在区块的坐标；以蓝框标出的区块即持有加载票。

<details>
<summary>全部票名</summary>

| 票名 | 保持加载的原因 |
| :--- | :--- |
| `玩家加载` | 视距内有玩家（逐区块应用，无中心） |
| `玩家模拟` | 模拟距离内有玩家 |
| `强制加载` | 该区块被 `/forceload` 强制加载 |
| `传送门` | 附近存在传送门 |
| `末影珍珠` | 有末影珍珠落于此 |
| `世界出生点` | 该区块为世界出生点（1.21.10 及以前名为 `start`） |
| `出生点搜索` | 正在执行出生点搜索（1.21.11 起） |
| `末影龙` | 末影龙战斗占用 |
| `未知` | 原版的兜底票类型 |

> [!NOTE]
> 1.21.4 及以前的游戏没有可查询的票表，故不显示票名与 `@x,z`，仅显示加载等级。
</details>

> [!NOTE]
> 合计 mspt 不含方块更新：其耗时已计入触发它的类别，重复计入会使合计偏高。

> [!TIP]
> **单人游戏**：地图打开时世界暂停；点击扫描按钮后须关闭地图再等待。

## 🕹️ 指令

| 指令 | 端 | 作用 |
| :--- | :--- | :--- |
| `/msptmap scan [秒数]` | 两端 | 发起扫描并在地图上着色；省略秒数则用当前设置的秒数，范围 1 ~ 60。在控制台执行时结果只写入服务端日志。 |
| `/msptmap access <ops\|all>` | 两端 | 谁能发起扫描：仅 OP（默认）或所有玩家。 |
| `/msptmap config` | 客户端 | 打开设置界面（未装 Mod Menu 时的备用入口）。 |
| `/msptmap config <键>` | 客户端 | 打印一项设置；键名与 `config/msptmap-client.properties` 中一致。 |
| `/msptmap config <键> <值>` | 客户端 | 设置一项并保存；布尔值取 `on` / `off`。 |
| `/msptmap config reset` | 客户端 | 将全部设置恢复为默认值。 |
| `/msptmap status` | 客户端 | 打印本次扫描的状态与最近一次结果。 |
| `/msptmap top [个数]` | 客户端 | 列出最近一次扫描最重的区块；默认 5 个，最多 50 个。 |
| `/msptmap monitor` | 两端 | 打印监控状态与各项设置。 |
| `/msptmap monitor <on\|off>` | 两端 | 总开关，默认关闭；关闭时一并丢弃当前窗口与冷却。 |
| `/msptmap monitor threshold <mspt>` | 两端 | 触发阈值，1.0 ~ 1000.0（默认 `40.0`）。 |
| `/msptmap monitor cooldown <分钟>` | 两端 | 触发后多长时间内不再扫描，1 ~ 1440（默认 `5`）。 |
| `/msptmap monitor audience <op\|all>` | 两端 | 告警发给谁：仅在线 OP（默认 `op`），或所有在线玩家。 |

扫描默认限原版权限等级 2（OP）；`/msptmap access <ops\|all>` 可放开给所有玩家，或恢复默认。`access` 与 `monitor` 两条子命令本身始终限 OP。

## 📡 监控

默认关闭。开启后，服务端计量每一游戏刻的实际耗时，平滑均值持续超阈值即自行发起一次扫描，把卡顿最重的五个区块作为聊天告警发给在线管理员；点击其中一行可将世界地图打开并定位到该区块。扫描链路与下文相同，区别只在结果发往管理员而非发起者。

监控是管理员命令，用 `/msptmap monitor <on\|off>` 开关，其设置保存在 `config/msptmap-server.properties`（各项见[指令](#-指令)）。

## 🔧 设置

打开 **Mod Menu → 设置**，或输入 `/msptmap config`。设置保存在 `config/msptmap-client.properties`，每一项也可用 `/msptmap config <键> [值]` 在游戏内读写，键名与文件中一致。

<details>
<summary>全部设置项</summary>

| 分组 | 设置项 | 作用 |
| :--- | :--- | :--- |
| 扫描 | **秒数** | 采样窗口长度，单位为秒（按游戏刻换算） |
| 颜色 | **采用相对模式** | 颜色按**本次扫描**中的相对大小判定：最重的区块显示为红色，其余区块与之比较——整体负载较低时，相对卡顿的区块仍可分辨。取消勾选则一律按下方固定的红色阈值判定。 |
| 颜色 | **红色阈值** | 区块耗时达到此值（ms/tick）即显示为最高等级红色；低于此值的区块，颜色由绿经黄连续过渡到红。勾选相对模式时此项不参与判定，且不可调整。 |
| 颜色 | **不透明度** | 热力色填充的不透明度，0.05 ~ 1.00；数值越小，透出的底图越清晰。 |
| 颜色 | **显示弱加载区块** | 以淡灰显示加载等级 32 及以上、整段窗口未产生可测耗时的区块。 |
| 悬停详情 | **坐标** / **等级** / **实体数** | 决定哪些行出现在悬停详情中。 |
| 悬停详情 | **加载票** / **计算票** | 在加载等级 / 计算等级行后附带票的来源。 |
| 悬停详情 | **显示为缩写** | 类别名显示为二字母缩写：`RT ST BU EV BE EN SP`。 |
| 悬停详情 | **显示单位（mspt）** | 每类明细行后附 `mspt`；合计行的单位固定显示，不受此项影响。 |

**恢复默认** 可将以上各项恢复为默认值。
</details>

## 🧭 工作原理

```mermaid
flowchart LR
    A["客户端<br/>地图按钮 / /msptmap scan"] -->|扫描请求| B["服务端<br/>MsptSampler"]
    B --> C["逐区块计时<br/>七类工作"]
    C -->|扫描结果| D["客户端<br/>热力图 + 总览"]
```

服务端采样七类工作的耗时，并将结果发送给发起扫描的客户端，由客户端按区块着色。

| 类别 | 计量位置 |
| :--- | :--- |
| 随机刻 | `ServerLevel.tickChunk` |
| 计划刻 | `ServerLevel.tickBlock` / `tickFluid` |
| 方块更新 | `ServerLevel` 的邻居更新各入口 |
| 方块事件 | `ServerLevel.doBlockEvent` |
| 方块实体 | `LevelChunk$BoundTickingBlockEntity.tick` |
| 实体 | `ServerLevel.tickNonPassenger` |
| 刷怪 | `NaturalSpawner.spawnForChunk` |

> [!NOTE]
> 方块更新会被计量，但不计入合计：其耗时已包含在触发它的类别中，例如由计划刻引发的红石连锁。

## 🔨 构建

需要 **JDK 25**。

```shell
./gradlew build    # Windows 使用 gradlew.bat
```

为每个支持的 Minecraft 版本各产出一个 jar，命名为 `MsptMap-Fabric-v<模组版本>+<最高支持版本>.jar`，输出于 `versions/<MC 版本>/build/libs/` 下。

## 📄 许可

[MIT](LICENSE) © 2026 Drizzle379

<sub>问题反馈与功能建议：[提交 issue](https://github.com/Drizzle379/MsptMap/issues) · 作者：[Bilibili](https://space.bilibili.com/433418393)</sub>

<div align="right"><a href="#msptmap">↑ 回到顶部</a></div>
