2026年09月29日（原记录未标注小时、分钟）

- Updated the interfaces for item cleanup, trash, attributes, tags, protection rules, damage types, merge rules, and entity immunity.
- Updated item and attribute lookups and event handling for the current platform APIs.
- Updated compatibility with KineticCore 26.9.29.

- 更新物品清理、垃圾桶、属性、标签、保护规则、伤害类型、合并规则和实体免疫界面。
- 按当前平台 API 更新物品与属性查询及事件处理。
- 更新对 KineticCore 26.9.29 的兼容。

---

2026年10月02日 13时53分

- Removed 24 unused imports, seven unused private methods, and redundant fields without changing item rules or editor callbacks.
- Removed the source-level warning suppression; intentional build compiler options remain unchanged.
- Enabled the KineticCore addon architecture check. Full build and final-JAR reference verification pass; the development client enters a world and opens the Item Control configuration page.

- 删除 24 处未使用引用、7 个未使用私有方法及冗余字段，保留物品规则和编辑器回调行为。
- 移除源码级警告抑制，保留构建中主动配置的编译选项。
- 接入 KineticCore 附属架构检查。完整构建及最终 JAR 引用验证通过；开发客户端可进入世界并打开 Item Control 配置页。
