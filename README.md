# MsptMap

**English** | [简体中文](README.zh-CN.md)

A green–yellow–red heatmap of per-chunk MSPT (milliseconds per tick) on [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map).

The server samples the time spent on seven kinds of tick work — random ticks, scheduled ticks, block updates, block events, block entities, entities and mob spawning — and sends the result to the client that started the scan, which paints the map chunk by chunk.

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| Minecraft 26.2 + Fabric Loader ≥ 0.19.3 | Yes | |
| [Fabric API](https://modrinth.com/mod/fabric-api) | Yes | |
| [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) | Yes, on the client | |
| [Carpet](https://modrinth.com/mod/carpet) | Optional | Enables permission management for the command |
| [Mod Menu](https://modrinth.com/mod/modmenu) | Recommended | Provides a shortcut to the settings screen |

## Building

Requires JDK 25; the artifact is written to `build/libs/` as `MsptMap-Fabric-26.2-v0.2.3.jar` (use `gradlew.bat` on Windows).

```shell
./gradlew build
```

## Installation

Drop the jar into `mods/`. Sampling runs on the server, so **the server needs it too**; the client additionally needs Xaero's World Map. In single-player both sides run on the same machine, so one copy is enough.

The server and the client must run the **same version** of MsptMap: the packet format changes between versions, and a mismatch only produces a line in chat — it does not disconnect you.

## Usage

1. Open the world map and click the bar-chart button in the top-left corner to start a scan (or run `/msptmap scan [seconds]`)
2. Once sampling finishes, chunks are painted by how much they lag: green → yellow → red; weakly-loaded chunks are pale grey
3. Hover any chunk for details: coordinates, load level, load ticket, total mspt, entity count and a per-category breakdown. The load ticket names what keeps that chunk loaded — `玩家加载` for a nearby player, `强制加载` for `/forceload`, `末影珍珠` for a thrown pearl, and so on; an `@x,z` after it is the chunk the ticket sits on
4. The ✕ button clears the heatmap
5. Settings: Mod Menu → Settings, or `/msptmap config`; stored in `config/msptmap-client.properties`

**Single-player**: the world is paused while the map is open, so close it after clicking the scan button and wait.

The same command is available on the server console and to operators; its output goes to the server console only.

## License

[MIT](LICENSE).
