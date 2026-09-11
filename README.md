# Item Control

[English](#english) | [简体中文](#简体中文)

## English

### Overview

An item-management toolkit for server rules, item filtering, dropped-item cleanup and recovery, creative-tab curation, NBT-aware matching, and inventory-facing administration.

The project is designed around in-game administration. Where a feature changes shared gameplay data or server rules, the server remains authoritative; client-only presentation features stay local to the client. Configuration screens use KineticCore's UI and configuration infrastructure.

### Key Features

- Item blocking and matching by exact item, mod, tag and NBT-aware rules.
- Dropped-item cleanup with whitelist/blacklist logic, areas, history and recovery tools.
- Creative-tab visibility and contents management for cleaner modpack presentation.
- Visual item selection and inspection through the KineticCore UI toolkit.
- Optional KubeJS and JEI interoperability where supported by the active feature.

### Requirements and Compatibility

| Type | Dependency |
|---|---|
| Required | Minecraft 1.20.1 |
| Required | Minecraft Forge 47+ |
| Required | KineticCore 26.9.8+ |
| Optional | KubeJS |
| Optional | JEI |

### Access and Configuration

- Open the KineticCore configuration center with its configured F6 entry and select **Item Control**.
- Server-owned settings are saved by the server and synchronized where the feature requires client awareness.
- Client-only presentation settings remain local.
- Individual feature areas document their own data/configuration paths below.
- Search, list selection, item/entity inspection, tooltips and return/navigation controls reuse KineticCore UI components where available.

## Detailed Feature Reference

### Item Rules

#### Overview

**Item Rules** is the item-management and dropped-item protection module of this project. It centralizes item bans, item unification, protected drops and void recovery for large modpacks.

#### Key Features

- Ban rules for exact item IDs, `@mod`, `#tag`, and NBT-aware variants.
- Item unification/merge rules that replace multiple source items with one target item.
- Visual ban and merge editors.
- Per-item fire, explosion, glow and no-gravity protection.
- Void salvage for protected dropped items.
- Global immunity by damage type.
- Global immunity by direct damaging entity type.
- Runtime and creative-tab integration for banned/unified items.
- Optional KubeJS compatibility.
- Server-authoritative gameplay rules.

#### Configuration

```text
config/kineticcore/banitem.json
config/kineticcore/banitem.old.json
config/kineticcore/protection.toml
```

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Void Salvage** | When enabled, protected items falling into the void will teleport to the nearest safe ground or world spawn. |
| **Item Drop Protection** | Dropped-item protection, damage immunity, and dedicated item rules can be configured here without opening config files manually. Server settings can only be saved by administrators. |
| **Enable Item Protection** | Master switch for custom dropped-item protection rules, including fire, explosion, glowing, and no-gravity properties. |
| **Dedicated Item Rule Editors** | Item banning and item merging use dedicated visual editors. Opening and saving are revalidated by the server for administrator permission. |
| **Open Item Ban Editor** | Edit item ban and replacement rules used by natural generation and structures. |
| **Open Item Merge Editor** | Edit canonical items and equivalence merge rules. The server validates configuration on save. |
| **Edit Protected Items** | Open the visual protected-item editor. Existing rules are loaded automatically without conversion. |
| **Edit Global Immune Damage Types** | Search all currently registered damage types and damage tags, then click a suggestion to add it. |
| **Item Tag Editor** | Search items and add, copy, or remove manual Item Tags with automatic deduplication. |

#### GUI and Editors
| Item | Description |
|---|---|
| **search** | Search items (@mod #tag) |
| **all view** | Currently viewing: All Items |
| **banned view** | Currently viewing: Banned Items |
| **merge** | Currently viewing: Valid items for merging |
| **Find by Target Item Tags** | The target item has %s tags. Click to show only items related to those tags. |
| **Item Protection Rule Editor** | Green outlines indicate existing protection rules; hovered items use blue. Left-click to edit, or right-click an existing rule to remove it quickly. |
| **search** | Search damage types or tags; click the field to expand all entries |
| **Global Immune Damage Type Editor** | Click a search suggestion, then Add. Right-click an existing entry to remove it. |
| **search** | Search item name, ID, @mod, or #tag |
| **search** | Search entity name or ID; enter a #tag to manage tag rules |
| **Global Immune Damage Entity Editor** | Left-click to toggle entities. Selected entries use a green outline and hovered entries use blue. Arrows, bullets, and other special entities are shown too. |

#### Editable Options
- Ban This Rule
- Unban This Rule
- Item Rule
- Tag Rule
- Mod Rule

#### Config Defaults
| Key | Default |
|---|---|
| `indestructible.enable` | `true` |
| `indestructible.global.damage.immunity_enable` | `true` |
| `indestructible.global.direct.entity.immunity_enable` | `true` |
| `indestructible.void_salvage` | `true` |

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/banitem.json`
- `config/kineticcore/banitem.old.json`
- `config/kineticcore/protection.toml`

### Dropped-Item Cleanup

#### Overview

**Dropped-Item Cleanup** is the cleanup and recovery module of this project. Instead of permanently deleting dropped items, it combines scheduled cleanup with protected areas and trash-bin history.

#### Key Features

- Automatic and manual cleanup.
- Optional experience-orb cleanup.
- Configurable cleanup of selected non-item entities.
- Item whitelist protection.
- Protected cleaner areas.
- Trash-bin history snapshots for recovery.
- Inventory trash-bin button with editable client position.
- Visual rule editors and cleanup status notifications.

#### Configuration

```text
config/kineticcore/cleaner.toml
```

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Edit Trash Bin Button Position** | Open the visual position editor and return to this configuration page when finished. |
| **Entities to Clean** | One entity ID per line, such as minecraft:arrow. |
| **Completely Disable Cleaner** | Stops both automatic and manual cleaner logic. |
| **Clean Whitelist** | Open the visual cleaner whitelist editor. Add specific items, #tags, or @mods through the item selector and right-click a rule to remove it. |
| **Items to not recycle** | Open the visual trash-bin blacklist editor. Add specific items, #tags, or @mods through the item selector and right-click a rule to remove it. |
| **Selection Tool Item ID** | Open the visual protected-area tool editor and choose the single tool item through the item selector. |
| **Cleaner Client Preferences** | Controls only this client's trash-bin button visibility and position. These preferences are stored locally and never written to server rules. |

#### GUI and Editors
| Item | Description |
|---|---|
| **button** | Open Trash Bin |

#### Commands
| Item | Description |
|---|---|
| **auto** | Admin: Toggle auto-clean |
| **bin** | Click to open Trash Bin GUI |
| **clean** | Sweeper/Trash Bin help |
| **toggle** | Admin: Completely disable Sweeper |
| **trash** | Admin: Clear all trash records |

#### Editable Options
- Item Tag Rule
- Whole Mod Rule

#### Config Defaults
| Key | Default |
|---|---|
| `cleaner.allowManualClean` | `true` |
| `cleaner.cleanExperienceOrbs` | `false` |
| `cleaner.enable` | `true` |
| `cleaner.enableEntityCleaning` | `false` |
| `cleaner.hard_disabled` | `false` |
| `cleaner.interval` | `600` |
| `protected_areas.enable` | `true` |
| `protected_areas.max_grid_count_per_player` | `6400` |
| `protected_areas.ray_trace_distance` | `32.0D` |
| `protected_areas.tool_item` | `"minecraft:golden_hoe"` |
| `trash_bin.button_x` | `148` |
| `trash_bin.button_y` | `61` |
| `trash_bin.enable` | `true` |
| `trash_bin.history_count` | `3` |
| `trash_bin.rows` | `28` |
| `trash_bin.show_button` | `true` |

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/cleaner.toml`

### Creative Tabs

#### Overview

**Creative Tabs** is the creative-tab organization module for this project. It can hide complete creative tabs, remove selected items from tab contents, append items to chosen tabs and keep optional JEI browsing synchronized with the configured view.

#### Key Features

- Hide complete creative-mode tabs.
- Remove items using item, mod, tag or NBT-aware rules.
- Append items to selected tabs.
- Append custom-NBT item variants.
- Visual unified creative-tab editor.
- Server snapshot workflow for remote editing.
- Server-side validation of tab IDs, item IDs, NBT and rule limits.
- Optional JEI filtering integration.

#### Configuration

```text
config/kineticcore/creative_tabs.json
```

Main sections:

- `removals`
- `additions`
- `hiddenTabs`

### Feature Reference
#### Config Details
| Item | Description |
|---|---|
| **Creative Tab Management** | Use the dedicated editor for creative-tab removals, additions, and hidden tabs instead of editing creative_tabs.json manually. The server revalidates administrator permission and every rule before saving. |
| **Open Creative Tab Editor** | Supports single items, NBT items, #item tags, @mod namespaces, hidden tabs, and item additions. The server validates tabs, items, and NBT before saving. |

#### GUI and Editors
| Item | Description |
|---|---|
| **add item** | Append custom item to the selected tab |
| **save** | Save modifications and sync to server |

#### Data Paths
Primary configuration/data paths:

- `config/kineticcore/creative_tabs.json`

### Building from Source

- Minecraft: `1.20.1`
- Java: `17`
- ForgeGradle: `6.0.24`
- Gradle: the project is pinned to the `8.1.1` Wrapper; do not import it with Gradle 9 directly.
- Local development JARs are controlled by `local_libs_dir` and can be overridden in `gradle.properties` or with a project property.
- Typical build command: `gradlew.bat build` on Windows or `./gradlew build` on Linux/macOS.
- Development and release artifacts use `itemcontrol` as the current project identifier.

## 简体中文

### 模组定位

面向服务器和整合包的物品管理工具，提供物品规则、NBT 匹配、掉落物清理与恢复、创造模式标签页整理等功能。

本项目以游戏内管理为核心。涉及共享玩法数据、世界规则或服务器规则的功能由服务端权威处理；仅影响显示的客户端功能保持本地生效。配置界面统一使用 KineticCore 提供的 GUI 与配置基础设施。

### 主要功能

- 支持按精确物品、模组、标签与 NBT 规则进行禁用、匹配和管理。
- 支持掉落物清理、黑白名单、区域规则、历史记录与恢复。
- 支持创造模式标签页隐藏、内容增删与整合包界面整理。
- 使用 KineticCore GUI 提供可视化物品选择与信息读取。
- 相关功能可选兼容 KubeJS 与 JEI。

### 运行环境与兼容

| 类型 | 依赖 |
|---|---|
| 必需 | Minecraft 1.20.1 |
| 必需 | Minecraft Forge 47+ |
| 必需 | KineticCore 26.9.8+ |
| 可选 | KubeJS |
| 可选 | JEI |

### 打开方式与配置

- 使用 KineticCore 配置中心对应的 F6 入口，选择 **Item Control**。
- 服务端规则由服务端保存，并在需要时同步给客户端。
- 纯显示类客户端设置只在本地生效。
- 各功能自己的配置/数据路径在下方详细功能说明中列出。
- 搜索、列表选择、物品/实体信息读取、悬浮提示、返回与导航等操作尽可能复用 KineticCore GUI 组件。

## 完整功能参考

### 物品规则

#### 模组定位

**Item Rules** 是 本项目中的物品管控与掉落物保护模组，面向大型整合包提供物品封禁、物品统一、掉落物防护、虚空救援与相关可视化管理能力，适合集中处理重复资源、限制物品和贵重掉落物。

#### 主要功能

- **物品封禁**：按具体物品 ID、`@模组ID`、`#物品标签` 或带 NBT 的精确规则禁用物品。
- **物品合并 / 统一**：把多个来源物品统一替换成指定目标物品，减少大型整合包中的重复资源。
- **NBT 规则支持**：可以只处理某个特殊 NBT 变种，而不是一刀切禁用整个物品类型。
- **可视化封禁编辑器**：提供物品搜索、筛选和规则维护界面。
- **可视化合并编辑器**：选择目标物品和来源规则，不需要直接手写映射表。
- **掉落物保护**：针对指定物品配置防火、防爆、发光、无重力等保护行为。
- **虚空救援**：受保护的重要掉落物掉入虚空后，可以尝试传送回最近可用地面。
- **全局伤害类型免疫**：可以让全部掉落物忽略指定伤害类型。
- **直接伤害实体免疫**：可以按直接攻击实体 ID 屏蔽掉落物伤害，例如只针对某类弹丸。
- **创造栏与运行时联动**：被封禁或被替换的物品会参与创造模式列表和运行时物品处理逻辑。
- **KubeJS 可选兼容**：安装 KubeJS 时可使用 Item Rules 提供的物品保护相关脚本桥接能力。
- **服务端权威规则**：影响游戏规则的封禁、合并和保护配置由服务端负责最终执行与同步。

#### 配置文件

```text
config/kineticcore/banitem.json
config/kineticcore/banitem.old.json
config/kineticcore/protection.toml
```

- `banitem.json`：当前物品封禁与合并规则。
- `banitem.old.json`：用于配置回退与异常恢复的备份文件。
- `protection.toml`：掉落物保护、虚空救援和全局免疫规则。

`banitem.json` 主要维护：

- `bannedItems`：封禁规则列表。
- `mergedItems`：目标物品到来源规则的合并映射。

#### 使用建议

优先通过 F6 中的 Item Rules 页面进入封禁和合并编辑器。大量使用 `@mod`、`#tag` 或 NBT 规则前，建议先在测试存档确认命中范围，避免一次性影响过多物品。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **虚空救援** | 开启后，受保护的物品掉进虚空时会尝试传送到最近的安全地面或世界出生点。 |
| **掉落物保护** | 掉落物保护、伤害免疫和专用物品规则均可在这里修改，不需要手动打开配置文件。服务器配置只允许管理权限玩家保存。 |
| **启用物品保护** | 控制自定义掉落物保护规则总开关，包括对应的防火、防爆、发光和无重力属性。 |
| **专用物品规则编辑器** | 物品封禁与物品合并使用可视化专用编辑器。打开和保存都会由服务器重新校验管理权限。 |
| **打开物品封禁编辑器** | 编辑自然生成和结构内容中的物品封禁/替换规则。 |
| **打开物品合并编辑器** | 编辑等价物品的主物品与合并规则。保存时由服务器校验配置。 |
| **编辑保护物品** | 打开保护物品可视化编辑器；旧配置会自动读取，不需要手动转换。 |
| **编辑通用免疫伤害类型** | 搜索游戏当前注册的全部伤害类型与伤害标签，点击候选项后即可添加。 |
| **物品 Tag 编辑器** | 搜索物品并添加、复制或移除手动 Item Tag；自动去重。 |

#### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **search** | 搜索物品 (支持@模组 #标签) |
| **all view** | 当前正在浏览：所有物品列表 |
| **banned view** | 当前正在浏览：已禁用物品列表 |
| **merge** | 当前正在浏览：可用于合并的合法物品 |
| **按主物品Tag查找** | 当前主物品拥有 %s 个Tag，点击后右侧只显示Tag相关物品。 |
| **物品保护规则编辑器** | 绿色描边表示已有保护规则；鼠标悬浮为蓝色。左键物品直接编辑，右键已有规则可快速删除。 |
| **search** | 搜索伤害类型或伤害标签，点击输入框可展开全部 |
| **通用免疫伤害类型编辑器** | 点击搜索候选项后按“添加”；右键现有条目可删除。 |
| **search** | 搜索物品名称、ID、@模组或#标签 |
| **search** | 搜索实体名称、ID；输入 #标签可管理标签规则 |
| **通用免疫伤害实体编辑器** | 左键勾选/取消实体；已勾选为绿色描边，鼠标悬浮为蓝色。箭矢、子弹和其他特殊实体也会直接显示。 |

#### 可编辑字段、模式与分类索引

- 封禁此规则
- 解封此规则
- 物品规则
- 标签规则
- 模组规则

#### 配置键与默认值

| 配置键 | 默认值 |
|---|---|
| `indestructible.enable` | `true` |
| `indestructible.global.damage.immunity_enable` | `true` |
| `indestructible.global.direct.entity.immunity_enable` | `true` |
| `indestructible.void_salvage` | `true` |

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/banitem.json`
- `config/kineticcore/banitem.old.json`
- `config/kineticcore/protection.toml`

### 掉落物清理

#### 模组定位

**Dropped-Item Cleanup** 是 本项目中的服务器掉落物清理与误删恢复模块。它不仅负责定时清理，还提供保护区域、垃圾桶历史和可视化规则编辑，避免传统扫地插件“一删了之”。

#### 主要功能

- **自动清理**：按配置周期清理地面掉落物。
- **手动清理**：允许玩家或管理员按规则主动触发清理。
- **经验球清理**：可独立决定是否清理经验球。
- **指定实体清理**：可选择清理箭、区域效果云等非物品实体。
- **物品白名单**：指定物品永远不会被自动清理。
- **保护区域**：可建立不会被扫地姬处理的区域，避免重要地点掉落物被误删。
- **垃圾桶历史**：被清理的物品可以进入历史快照，方便误删后找回。
- **背包垃圾桶按钮**：在物品栏提供垃圾桶入口，并支持客户端位置编辑。
- **规则编辑界面**：提供物品规则、垃圾桶和保护区域等可视化操作。
- **倒计时与结果提示**：清理前可提示剩余时间，完成后显示清理数量。

#### 常用命令

- `/kt clean help`：查看清理命令帮助。
- `/kt clean bin`：打开垃圾桶历史。
- `/kt clean trash`：垃圾桶相关操作入口。
- `/kt clean auto ...`：控制自动清理。
- `/kt clean toggle ...`：切换清理状态。

#### 配置文件

```text
config/kineticcore/cleaner.toml
```

主要包括自动清理开关、清理间隔、经验球、物品白名单、实体清理、垃圾桶历史数量和客户端垃圾桶按钮显示等设置。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **编辑垃圾桶按钮位置** | 打开可视化位置编辑器，完成后返回当前配置页面。 |
| **需要清理的实体** | 每行填写一个实体 ID，例如 minecraft:arrow。 |
| **彻底禁用扫地机** | 同时停止自动清理和手动清理逻辑。 |
| **掉落物清理白名单** | 打开可视化清理白名单编辑器。通过物品选择器添加具体物品、#标签或@模组，右键规则即可移除。 |
| **不回收的垃圾** | 打开可视化垃圾桶黑名单编辑器。通过物品选择器添加具体物品、#标签或@模组，右键规则即可移除。 |
| **框选工具物品 ID** | 打开可视化区域工具物品编辑器。通过物品选择器选择唯一的工具物品。 |
| **扫地姬客户端偏好** | 仅影响当前客户端的垃圾桶按钮显示与位置，直接保存在本地，不写入服务器规则。 |

#### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **button** | 打开回收站 (存放已清理垃圾) |

#### 命令功能说明

| 项目 | 说明 |
|---|---|
| **auto** | 管理员：切换自动清理开关 |
| **bin** | 点击打开回收站 GUI 界面 |
| **clean** | 扫地姬/回收站功能帮助 |
| **toggle** | 管理员：彻底禁用扫地姬 |
| **trash** | 管理员：清空所有回收站记录 |

#### 可编辑字段、模式与分类索引

- 物品标签规则
- 整个模组规则

#### 配置键与默认值

| 配置键 | 默认值 |
|---|---|
| `cleaner.allowManualClean` | `true` |
| `cleaner.cleanExperienceOrbs` | `false` |
| `cleaner.enable` | `true` |
| `cleaner.enableEntityCleaning` | `false` |
| `cleaner.hard_disabled` | `false` |
| `cleaner.interval` | `600` |
| `protected_areas.enable` | `true` |
| `protected_areas.max_grid_count_per_player` | `6400` |
| `protected_areas.ray_trace_distance` | `32.0D` |
| `protected_areas.tool_item` | `"minecraft:golden_hoe"` |
| `trash_bin.button_x` | `148` |
| `trash_bin.button_y` | `61` |
| `trash_bin.enable` | `true` |
| `trash_bin.history_count` | `3` |
| `trash_bin.rows` | `28` |
| `trash_bin.show_button` | `true` |

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/cleaner.toml`

### 创造模式标签页

#### 模组定位

**Creative Tabs** 是 本项目中的创造模式标签页整理模块。它用于重新组织大型整合包的创造模式物品页：隐藏不需要的标签页、从标签页中移除指定物品，或把物品追加到指定标签页，同时与客户端搜索/JEI 环境保持联动。

#### 主要功能

- **隐藏整个标签页**：指定的创造模式标签页不会继续显示在标签栏中。
- **从标签页移除物品**：可以按规则把不需要的物品从创造模式内容中剔除。
- **向指定标签页追加物品**：把任意注册物品添加到目标标签页。
- **NBT 物品追加**：追加条目可以保存自定义 NBT，适合药水、附魔书、枪械、饰品等特殊物品。
- **多种移除规则**：支持具体物品 ID、`@模组ID`、`#物品标签` 与 NBT 规则。
- **可视化统一编辑器**：通过实体物品列表、搜索和标签页列表直接编辑，而不是手写大型 JSON。
- **服务器配置快照**：编辑远程服务器配置时从服务器取得当前数据，避免使用本地旧副本覆盖服务器规则。
- **保存校验**：服务端会验证标签页 ID、物品 ID、NBT 和规则数量后再写入文件。
- **JEI 可选联动**：安装 JEI 时，被隐藏或移除的物品可以同步参与客户端物品浏览过滤。
- **需要重载的显示规则**：部分创造模式标签页变更需要重新构建客户端标签内容后才能完整体现。

#### 配置文件

```text
config/kineticcore/creative_tabs.json
```

主要数据：

- `removals`：从创造模式内容中移除的物品规则。
- `additions`：追加到指定标签页的物品列表。
- `hiddenTabs`：隐藏的创造模式标签页 ID。

#### 使用建议

通过 F6 打开 Creative Tabs 页面并进入专用编辑器。大量隐藏标签页时建议分批测试，尤其是某些模组会在运行时动态创建创造模式标签页或强制追加物品。

### 完整功能参考

#### 配置项详细说明

| 项目 | 说明 |
|---|---|
| **创造标签页管理** | 创造标签页的移除、追加与隐藏规则由专用编辑器管理，不需要手动修改 creative_tabs.json。服务器保存会重新校验管理员权限和所有规则。 |
| **打开创造标签页编辑器** | 支持单物品、带 NBT 物品、#物品标签、@模组命名空间删除规则，以及标签页隐藏和物品追加。保存前服务器会验证标签页、物品和 NBT。 |

#### 界面操作与编辑器说明

| 项目 | 说明 |
|---|---|
| **add item** | 向当前选中的标签页中追加自定义物品 |
| **save** | 将当前修改保存并同步到服务器生效 |

#### 配置与数据路径

主要配置/数据路径：

- `config/kineticcore/creative_tabs.json`

### 从源码构建

- Minecraft：`1.20.1`
- Java：`17`
- ForgeGradle：`6.0.24`
- Gradle：项目固定使用 `8.1.1` Wrapper，请不要使用 Gradle 9 直接导入。
- 默认本地依赖目录由 `local_libs_dir` 控制，可在 `gradle.properties` 或命令行参数中覆盖。
- 常用构建命令：`gradlew.bat build`（Windows）或 `./gradlew build`（Linux/macOS）。
- 生成的开发/发布文件以 `itemcontrol` 作为当前工程标识。
