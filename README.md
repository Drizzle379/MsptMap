# MsptMap

**English** | [简体中文](README.zh-CN.md)

A green–yellow–red heatmap of per-chunk MSPT (milliseconds per tick) on [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map).

The server samples the time spent on seven kinds of tick work — random ticks, scheduled ticks, block updates, block events, block entities, entities and mob spawning — and sends the result to the client that started the scan, which paints the map chunk by chunk.

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| Minecraft 1.20–1.21.11 or 26.1.2 / 26.2 / 26.3 + Fabric Loader ≥ 0.19.3 | Yes | see the list below the table |
| [Fabric API](https://modrinth.com/mod/fabric-api) | Yes | |
| [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) | Yes, on the client | Injection anchors verified on Xaero 1.44.2 / 1.46.0 / 1.46.4 (most versions use 1.46.0); if a future Xaero update breaks the anchor, the button and heatmap are disabled and the game still starts |
| [Carpet](https://modrinth.com/mod/carpet) | Optional | Enables permission management for the command |
| [Mod Menu](https://modrinth.com/mod/modmenu) | Recommended | Provides a shortcut to the settings screen |

Supported versions: 1.20–1.20.2, 1.20.3–1.20.4, 1.21–1.21.1, 1.21.2–1.21.4, 1.21.6–1.21.10, 1.21.11, and 26.1–26.1.2 / 26.2 / 26.3. 1.20.5, 1.20.6 and 1.21.5 are not supported.

## Building

Requires JDK 25. `./gradlew build` produces one jar per supported Minecraft version, under `versions/<minecraft-version>/build/libs/` (use `gradlew.bat` on Windows).

```shell
./gradlew build
```

## Installation

Drop the jar into `mods/`. Sampling runs on the server, so **the server needs it too**; the client additionally needs Xaero's World Map. In single-player both sides run on the same machine, so one copy is enough.

The server and the client may run different versions of MsptMap: a mismatch still yields a result, with a note in chat that it may be inaccurate. An incompatible packet is reported in chat as a failure — it never disconnects you.

## Usage

1. Open the world map and click the heatmap button in the top-left corner to start a scan (or run `/msptmap scan [seconds]`)
2. Once sampling finishes, chunks are painted by how much they lag: green → yellow → red; weakly-loaded chunks are pale grey
3. Hover any chunk for details: coordinates, load level, load ticket, total mspt, entity count and a per-category breakdown. The load ticket names what keeps that chunk loaded — `玩家加载` for a nearby player, `强制加载` for `/forceload`, `末影珍珠` for a thrown pearl, and so on; an `@x,z` after it is the chunk the ticket sits on (games through 1.21.4 have no queryable ticket table and do not show this part)
4. The ✕ button clears the heatmap
5. Settings: Mod Menu → Settings, or `/msptmap config`; stored in `config/msptmap-client.properties`

Total mspt is the sum of the seven categories: block updates triggered inside scheduled or random ticks are counted in both, so the total may slightly exceed the chunk's true per-tick cost.

**Single-player**: the world is paused while the map is open, so close it after clicking the scan button and wait.

The same command is available on the server console and to operators; its output goes to the server console only.

## License

[MIT](LICENSE).
