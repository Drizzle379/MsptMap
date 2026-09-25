# MsptMap

在 [Xaero 的世界地图](https://modrinth.com/mod/xaeros-world-map)上，把每个区块的 MSPT（每 tick 毫秒数）画成绿黄红热力图。

服务端采样随机刻、计划刻、方块实体、实体、刷怪五类工作的耗时，结果发给发起扫描的客户端，按区块铺色。

## 环境

| 依赖 | 必需性 | 说明 |
|---|---|---|
| Minecraft 26.2 + Fabric Loader ≥ 0.19.3 | 必需 | |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 必需 | |
| [Xaero 的世界地图](https://modrinth.com/mod/xaeros-world-map) | 客户端必需 | |
| [Carpet](https://modrinth.com/mod/carpet) | 可选 | 安装后可以管理指令使用权限 |
| [Mod Menu](https://modrinth.com/mod/modmenu) | 建议安装 | 安装后可以快捷进行设置 |

## 构建

需要 JDK 25；产物在 `build/libs/`，形如 `MsptMap-Fabric-26.2-v0.1.0.jar`（Windows 用 `gradlew.bat`）。

```shell
./gradlew build
```

## 安装

jar 放进 `mods/`。采样在服务端进行，**服务端也要装**；客户端另外需要 Xaero 的世界地图。单人档两侧同机，装一份即可。

## 用法

1. 打开世界地图，点左上角的柱状图按钮开始扫描（或输入 `/msptmap scan [秒数]`）
2. 采样成功后地图按区块卡顿情况铺色：绿 → 黄 → 红；弱加载区块为淡灰色
3. 悬停任意区块看详情：坐标、加载等级、各类耗时
4. ✕ 按钮清空热力图
5. 设置：Mod Menu → 设置，或 `/msptmap config`；存在 `config/msptmap-client.properties`

**单人档**：地图打开时世界暂停，点击扫描按钮后要关闭地图等待。

服务端控制台与管理员另有同名命令，结果只打到服务端控制台。

## 许可

[MIT](LICENSE)。
