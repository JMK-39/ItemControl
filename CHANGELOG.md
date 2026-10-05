## 26.10.5 — 2026-10-05

### English

- Enabled Minecraft 26.1.2 / NeoForge 26.1.2.112 (Java 25); releases now cover Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2. Optional JEI 29.43; KubeJS integration compiles against KubeJS 26.1.2-8 but its 26.1 build does not start on 26.1.2 yet.
- Every version uses the same screens. On 1.21.1 and 26.1.2, item component data (`[damage=5]`) is now edited in Core's NBT editor, the same editor Forge uses for NBT, instead of a separate page. Requires KineticCore 26.10.5+.
- Item properties on 26.1.2: food, eating time and enchantability are 26.1 item components and are applied when the game binds item components; stack size, durability, rarity, mining, attributes and block values keep their existing hooks. Armor slots, tool levels and mining speeds are read from the item's equippable and tool components.
- Item rules written for 1.21.1 keep their attribute ids on 26.1.2 (`minecraft:generic.attack_speed` matches `minecraft:attack_speed`). Item merging, merged tags, item protection, the trash bin, cleaner areas and creative tabs use the 26.1 item, entity and saved-data APIs.
- Fixed on every version: cleaner key bindings used a missing category name, so the Controls screen showed a raw key; they now appear under "Dropped-Item Cleanup".
- Fixed on 1.21.1: the drop-protection hint showed the 1.20.1 `item_id{components}` syntax instead of `item_id[components]`.
- No @OnlyIn annotations on 26.1.2, where NeoForge shows a mod-loading warning screen for them.
- Verified: all three versions build; the server runtime checks pass on 1.21.1 and 26.1.2; 150 English/Chinese 26.1.2 client captures at 854×480 and 1536×864 match the 1.21.1 layouts.

### 简体中文

- 启用 Minecraft 26.1.2 / NeoForge 26.1.2.112（Java 25）；发布版本覆盖 Forge 1.20.1、NeoForge 1.21.1 与 NeoForge 26.1.2。可选 JEI 29.43；KubeJS 联动按 KubeJS 26.1.2-8 编译，但其 26.1 版本目前无法在 26.1.2 上启动。
- 所有版本使用相同界面。1.21.1 与 26.1.2 的物品数据组件（`[damage=5]`）改用核心 NBT 编辑器编辑，与 Forge 编辑 NBT 的界面一致，不再使用单独页面。要求 KineticCore 26.10.5+。
- 26.1.2 的物品属性：食物、食用时间与附魔能力在 26.1 中属于物品组件，在游戏绑定物品组件时应用；堆叠、耐久、稀有度、挖掘、属性与方块数值沿用原有挂钩。盔甲槽位、工具等级与挖掘速度从物品的可装备与工具组件读取。
- 1.21.1 写下的属性规则在 26.1.2 上继续生效（`minecraft:generic.attack_speed` 对应 `minecraft:attack_speed`）。物品合并、合并标签、物品保护、垃圾桶、清理区域与创造标签页使用 26.1 的物品、实体与存档数据接口。
- 修复所有版本：清理按键使用了不存在的分类名，按键设置界面显示原始键名；现在显示在“掉落物清理”分类下。
- 修复 1.21.1：掉落物保护提示显示 1.20.1 的 `物品ID{数据组件}` 写法，现改为 `物品ID[数据组件]`。
- 26.1.2 不再使用 @OnlyIn 注解，NeoForge 会为它显示模组加载警告界面。
- 验证：三个版本均可构建；服务端运行时检查在 1.21.1 与 26.1.2 通过；26.1.2 客户端中英文 854×480 与 1536×864 共 150 张截图，与 1.21.1 布局一致。

---

## 26.10.4 — 2026-10-04

### English

- Keep long editor headings, labels, hints, autocomplete suggestions and list names inside their own bounds with shared scrolling text. Applies to item properties, protection, direct entity immunity, damage types, banned/merged items, tags, cleaner rules, creative tabs and component editing.
- Preserve scaled merge-source text while clipping in page coordinates; keep target names clear of expand symbols and counts.
- Move protection modal toggles below the status text. Limit cleaner navigation/header widths and hide vanilla container labels again after initialization.
- Require matching KineticCore 26.10.4+ for the current screen-fitting tooltip API. No gameplay, configuration syntax or language keys changed.

### 简体中文

- 物品属性、保护、直接实体免疫、伤害类型、禁用/合并物品、标签、清理规则、创造标签页及组件编辑器的长标题、标签、说明、自动补全与名称在各自范围内滚动。
- 合并来源保留文字缩放，并在页面坐标中裁剪；目标名称避开展开符号与计数。
- 保护弹窗的开关移到状态说明下方；清理页标题、历史页码及导航按钮限制宽度，初始化后重新隐藏原版容器标签。
- 要求匹配的 KineticCore 26.10.4+，使用当前屏幕适配悬浮提示；未修改玩法、配置语法或语言键。

---

2026年10月04日 — Language key validation / 语言键一致性检查

- Require identical authored English/Chinese keys and string values in source, version overrides and packaged resources; generated formatting keys are rejected during builds.

- 强制检查源码、版本覆盖与最终资源的中英文完整键名一致、值为字符串；构建禁止派生格式语言键。

---

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
