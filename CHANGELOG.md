2026年10月03日 19时06分 — 26.10.3

- Added NeoForge 1.21.1 support alongside Forge 1.20.1, using Java 21 and matching KineticCore 26.10.3+.
- The 1.21.1 item rules and creative tabs use native components; legacy item-NBT syntax/schema is rejected and is not converted. Forge retains its NBT behavior.
- Fixed 1.21.1 trash-stack counts and saved data, startup component constraints, merge-rule checks, and food-property handling.
- Both builds and targeted NeoForge world checks passed; Forge game startup was skipped and all GUI, script, and multiplayer scenarios have not been tested.
- The 26.1.2 node is reserved and disabled; it is not a supported release.

- 新增 NeoForge 1.21.1 支持，同时保留 Forge 1.20.1；使用 Java 21 和对应版本的 KineticCore 26.10.3+。
- 1.21.1 物品规则与创造标签页使用原生组件，拒绝且不转换旧物品 NBT 写法和配置格式；Forge 保留原有 NBT 行为。
- 修复 1.21.1 回收物品数量与存盘、启动阶段组件约束、合并规则检查和食物属性处理。
- 两个版本构建及针对性的 NeoForge 世界检查通过；跳过 Forge 游戏启动，GUI、脚本与多人场景尚未全部测试。
- 26.1.2 节点仅预留、未启用，不代表已支持。

---

2026年10月02日 13时53分

- Removed 24 unused imports, seven unused private methods, and redundant fields without changing item rules or editor callbacks.
- Removed the source-level warning suppression; intentional build compiler options remain unchanged.
- Enabled the KineticCore addon architecture check. Full build and final-JAR reference verification pass; the development client enters a world and opens the Item Control configuration page.

- 删除 24 处未使用引用、7 个未使用私有方法及冗余字段，保留物品规则和编辑器回调行为。
- 移除源码级警告抑制，保留构建中主动配置的编译选项。
- 接入 KineticCore 附属架构检查。完整构建及最终 JAR 引用验证通过；开发客户端可进入世界并打开 Item Control 配置页。

---

2026年09月29日（原记录未标注小时、分钟）

- Updated the interfaces for item cleanup, trash, attributes, tags, protection rules, damage types, merge rules, and entity immunity.
- Updated item and attribute lookups and event handling for the current platform APIs.
- Updated compatibility with KineticCore 26.9.29.

- 更新物品清理、垃圾桶、属性、标签、保护规则、伤害类型、合并规则和实体免疫界面。
- 按当前平台 API 更新物品与属性查询及事件处理。
- 更新对 KineticCore 26.9.29 的兼容。
