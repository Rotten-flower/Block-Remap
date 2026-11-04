# Block Remap / 方块置换

![Block Remap](art/icon.png)

## English

AI was used extensively during the development of this mod. These builds have been checked using automated in-game tests. Issues may still occur with different mod combinations and environments. Please consider this before downloading if you prefer not to use AI-assisted projects.

Replace selected blocks during world generation, or apply replacements to standard block writes throughout a world. Disable structures and features by registry ID. Inventory items stay unchanged.

### Use

Press **F9** in a world, or run `/blockgenban`. Select a source slot on the left and choose a target block on the right. Source and target searches support names and registry IDs. The target picker includes creative categories and paging; air and stone stay at the beginning of every page.

Configured source blocks appear first. Right-click a source slot to clear its rule. The check button saves changes after the server confirms; the cross button or Esc discards the draft. Reopen the world or restart the server to apply block rules. The world owner or a server operator can edit settings.

![Rule editor](docs/images/forge-editor.png)

### Configuration

File: `config/blockgen_ban.json`

- `replacementMode`: `generation_only` by default, or `global`; takes effect after reopening the world or restarting the server.
- `rules`: source block IDs mapped to target block IDs, with the same restart requirement.
- `disabledStructures`: structure IDs to skip.
- `disabledFeatures`: placed feature, configured feature or feature type IDs to skip.

Use `/blockgenban structure ban <id>` or `allow <id>`; `list` shows disabled entries. The same commands work with `feature`. Command changes to generation bans take effect immediately for generation that has not run yet.

`generation_only` preserves old chunks, player placement and ordinary runtime writes. `global` also replaces standard player, command and machine writes, and converts matching blocks when old chunks load. **Saved global conversions are permanent. Replaced containers and their contents are removed. Back up a world before using global mode.** Neither mode changes inventory item IDs or recipes.

Targets use their default block state; facing and waterlogging are not copied. Replacement chains resolve to a terminal target, and cycles are rejected. Generators that bypass standard block and section writes require separate compatibility tests.

### Installation and builds

| Minecraft | Loader tested | Java | Extra install |
| --- | --- | --- | --- |
| 1.20.1 | Forge 47.4.20 | 17 | None |
| 1.20.1 | Fabric Loader 0.16.14 | 17 | Fabric API 0.92.12+1.20.1 |
| 1.21.1 | NeoForge 21.1.265 | 21 | None |

Install the matching JAR in `mods`. For singleplayer, install it on the client. For multiplayer, install it on the server and on clients that need the editor. A server can also apply rules without clients installing the editor. Installed clients and servers should use the same mod version.

Build one platform with its Gradle Wrapper, for example:

```sh
cd platforms/fabric-1.20.1
./gradlew build
```

On Windows use `gradlew.bat`, or run `./build.ps1 -Platform all` from the repository root. Build outputs are in each platform's `build/libs`; the Windows helper copies installation JARs to `dist/1.0.0`. Tests and repeatable game fixtures are described in [tests/README.md](tests/README.md).

Code: [MIT](LICENSE). Project artwork: [separate terms](LICENSE-ASSETS.md). [Third-party notices](THIRD_PARTY.md).

If you encounter any issues, please leave a comment or join our QQ group. Include your Minecraft version, mod version, mod loader, and logs whenever possible to help us investigate. Suggestions are also welcome! Your feedback will help guide improvements in future updates.

QQ group: 1095622706 · Support the author: [Afdian](https://afdian.com/a/lbcm233)

## 中文

本模组开发过程中大量使用了 AI，当前构建通过自动游戏测试检查。在不同模组组合和运行环境下仍可能出现问题。介意 AI 辅助项目的玩家可在下载前了解这一点。

为指定方块设置替换目标，选择只替换世界生成，或覆盖世界中的标准方块写入。还可按注册名禁止结构和地物生成。背包物品保留。

### 操作

进入世界按 **F9**，或输入 `/blockgenban`。点击左侧来源槽，在右侧选择目标。左右均支持名称和注册名搜索，右侧提供创造分类与翻页，空气和石头固定在每页开头。

已设置替换的来源优先置顶。右键来源槽清除规则，✓ 等服务器确认后保存，× 或 Esc 放弃草稿。保存方块规则后，重新进入世界或重启服务器生效。单人世界拥有者和服务器管理员可以修改。

### 配置

配置文件：`config/blockgen_ban.json`

- `replacementMode`：替换范围，默认 `generation_only`，也可选 `global`；重新进入世界或重启服务器后生效。
- `rules`：来源方块 ID 与目标方块 ID 的对应关系；重新进入世界或重启服务器后生效。
- `disabledStructures`：禁止指定结构生成。
- `disabledFeatures`：禁止指定 PlacedFeature、ConfiguredFeature 或 Feature 类型生成。

`/blockgenban structure ban <id>` 禁止结构，`allow <id>` 恢复，`list` 查看列表。将 `structure` 换成 `feature` 可管理地物。命令修改即时作用于尚未执行的生成任务。直接修改文件需关闭游戏或服务器。

`generation_only` 保留旧区块、手动放置与普通运行时写入。`global` 同时替换手动放置、指令和机器等标准写入，并在旧区块加载时转换。**全局转换保存后无法自动还原，被替换容器及其物品不会保留，使用前备份存档。** 两种模式都不修改背包物品 ID 或合成。

目标使用默认状态，不复制朝向和含水状态。规则链整理到最终目标，循环规则会被拒绝。绕过标准方块、区段入口的生成器需单独验证。

### 安装与开发

支持与依赖见上表。单人安装客户端，多人安装服务器，需要编辑界面的客户端也安装；服务器也可单独安装并通过配置或命令使用。Fabric 端另需安装 Fabric API。

各端使用自己的 Gradle Wrapper 构建，Windows 根目录可运行 `./build.ps1 -Platform all`。安装包位于各端 `build/libs`，Windows 脚本汇总至 `dist/1.0.0`。游戏验证入口见 [tests/README.md](tests/README.md)。

代码采用 [MIT](LICENSE)，项目美术按[单独条款](LICENSE-ASSETS.md)使用，[第三方说明](THIRD_PARTY.md)另列。

遇到问题可在评论区或 QQ 群反馈，并尽量提供 Minecraft 版本、模组版本、加载器和日志，方便排查。 欢迎提出改进建议，反馈将用于后续更新。

QQ 交流群：1095622706 · 支持作者：[爱发电](https://afdian.com/a/lbcm233)
