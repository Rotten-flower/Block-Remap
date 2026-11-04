# Block Remap maintenance / 方块置换维护

- Read README.md, CHANGELOG.md and the affected platform's build files first. Only the three listed platforms are maintained. Keep version, names, descriptions and fixes in sync.
- 先读 README.md、CHANGELOG.md 与受影响端的构建文件。只维护列明的三个端，同步版本、名称、功能与修复。
- Preserve modid `blockgen_ban`, package, command `/blockgenban`, JSON keys and world SavedData identifiers. Released versions are immutable.
- 保留 modid、包、命令、JSON 字段及世界快照标识。已发布的版本与产物不覆盖。
- `generation_only` preserves manual writes and old chunks. `global` also changes old palettes permanently. Inventory items remain unchanged in both modes.
- 仅生成模式保留手动放置与旧区块。全局模式会永久改写命中的旧调色板，两种模式都保留背包物品。
- Rules saved in the UI apply after reopening the world or restarting the server. Keep same-section palette-lock forwarding and nested worldgen scopes intact.
- 界面保存后重新进入世界或重启服务器生效。维护同区段锁传递及嵌套世界生成上下文，不恢复已删除的全区块清扫方案。
- Use short role-based class names. Explain non-obvious restrictions in Chinese and English; avoid comments that repeat the code.
- 类名简短并说明职责。非显然限制用中英注释，避免逐行复述代码。
- Build the affected platform, run relevant game fixtures, inspect real screenshots and the packaged JAR. Development fixtures must stay outside production artifacts.
- 构建受影响端，执行相关游戏夹具，检查真实截图和 JAR。开发验收代码不得进入正式包。
- Code is MIT. Artwork has separate terms in LICENSE-ASSETS.md. Preserve third-party notices. Do not redistribute extracted Minecraft assets or credentials.
- 代码 MIT，美术许可见 LICENSE-ASSETS.md。保留第三方许可，不分发提取的 Minecraft 资源或凭据。
- Follow the user's existing publication approval. For later releases, obtain approval for the concrete candidate and destination; prepare locally before asking.
- 遵循用户已有发布授权。后续新版本公开前准备完整候选，再按用户授权发布到指定平台。
