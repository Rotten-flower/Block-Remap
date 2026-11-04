# Game verification / 游戏验证

Fixtures run inside a real Minecraft client or dedicated server. They are excluded from production JARs. They are automated checks, not a claim of human playtesting or arbitrary modpack compatibility.

游戏夹具在真实客户端或专服中自动执行，不进入正式安装包，不等同于人工试玩或任意整合包兼容验证。

## Client / 客户端

- Forge: `gradlew.bat runClient -PuiTest`
- Fabric: `gradlew.bat runUiClient -PuiTest`
- NeoForge: `gradlew.bat runClient -PuiTest`

The fixture checks F9, actual client/server rule packets, searches, target categories, resizing, cancel/save, JSON and SavedData, cyclic-save errors, and configured-source ordering after reopening. Screenshots are captured from the game framebuffer.

检查 F9、真实网络保存、搜索、分类、缩放、取消/保存、JSON 与世界快照、循环错误，以及重开后的来源置顶。截图来自游戏帧缓冲。

## Dedicated server / 专服

Fabric and NeoForge use `gradlew.bat runProbeServer -PserverTest -PprobePhase=<phase>`. Use a local isolated run directory with an accepted `eula.txt`, an empty mod config and normal terrain. Bind to localhost for isolated checks. The four phases share that test world:

1. `prepare`: empty config; save sample ore/container/light blocks and rules.
2. `generation`: restart with `generation_only`; confirm old blocks/container contents survive and fresh terrain/ore sources are replaced.
3. `global`: change only the isolated config's mode to `global`, restart, check old palette and entity conversion plus fresh chunks.
4. `generation-again`: return the isolated mode to `generation_only`, restart, check global conversion remains saved and manual writes stay original.

Both active phases check BlockItem placement, command and direct FULL section writes, item preservation, structure entry bans, feature ban commands, cycle rejection, JSON and world snapshots. The BlockItem probe uses a null-player placement context; client tests cover the real player connection. Feature command checks alone do not prove every feature type's generation behavior.

上述四阶段仅用于隔离测试存档。除原生新地形与矿物外，夹具检查方块物品放置、指令和 FULL 区段直接写入、物品保留、结构入口禁用、地物命令、循环规则及持久化。放置探针使用空玩家上下文；客户端夹具检查真实玩家连接。地物命令检查不代表所有地物类型均已实测。

The maintained Forge verification source set additionally contains the earlier full generation/mode-switch probes. Run it only in an isolated game directory as described by its source; do not run fixtures on a player's world.
