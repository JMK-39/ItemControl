# Item Control

[English](#english) | [简体中文](#chinese)

<a id="english"></a>

## English

Item Control is built around highly visual in-game management. Few item-management mods combine this breadth of controls in one place: item bans and unification, per-item property editing, dropped-item protection, cleanup with recoverable trash history, and creative-tab curation. Dedicated editors make these rules practical to browse and maintain without treating manual file editing as the main workflow.

### Installation and access

- Current build target: **Minecraft 1.20.1**, **Forge 47.4.2+**, and **KineticCore 26.9.25+**.
- Install Item Control and KineticCore on the client and server for multiplayer use.
- Optional integrations: **KubeJS** for dropped-item events and **JEI** for ingredient visibility integration.
- Enter a world and press **F6**, then choose **Item Control** in KineticCore. The key is configurable in Controls.
- Server item and creative-tab editors require **permission level 2**. Cleaner access for ordinary players is controlled separately by server settings.

### Item bans, merging, and tags

The item module contains separate visual editors for banned items, merged items, item tags, and protection rules.

| Rule input | Meaning | Example |
| --- | --- | --- |
| Item ID | Match one registered item | `minecraft:rotten_flesh` |
| `@modid` | Match a mod namespace | `@examplemod` |
| `#namespace:tag` | Match an item tag | `#forge:ingots/copper` |
| Item ID plus SNBT | Match an item with specified NBT | `minecraft:diamond_sword{Damage:10}` |

NBT matching checks the specified data rather than requiring the entire stack tag to be identical. Use the editor's NBT tool when a rule must distinguish variants of the same item.

- **Ban items:** select individual items or create mod/tag rules. A group ban must be removed at the group-rule level to release its members.
- **Merge items:** choose a canonical target and add source items that should become that target. This is useful for duplicate materials from several mods.
- Merging participates in stack creation/tag updates and item/tag checks; non-NBT replacement IDs also participate in JSON data reload processing.
- **Item tags:** add tags manually, or select a target and copy tags from a source item. Duplicate tags are removed automatically.
- The tag editor distinguishes native tags, tags inherited through merging, and manual tags. Only manual tags can be removed there; change the merge rule to remove inherited tags.

Example workflow: choose the ingot your pack should keep, add the other ingots as merge sources, save, then inspect recipes and newly obtained stacks. Creative-tab hiding is a separate presentation setting; it does not itself impose an item ban.

### Item properties and dropped-item protection

The **Item Property Editor** is the main visual workspace for changing item behavior. Its category selector filters the left-hand list to relevant items, and the property panel shows the selected item's original values. Unchanged values appear gray; edited values appear green. The Drop Protection category can also target all registered items, item tags, mod namespaces, and item NBT variants.

| Category | Available controls |
| --- | --- |
| Combat | Attack damage and speed, armor, toughness, knockback resistance, and durability |
| Tools | Attack damage and speed, mining speed and level, and durability |
| Food | Nutrition, saturation, eating time, full-hunger eating, and whether eating consumes the item |
| General | Stack size, durability, enchantability, and localized rarity choices |
| Blocks | Hardness and explosion resistance |
| Drop Protection | Fire and explosion immunity, glowing, no gravity, and persistence for dropped items |

Attack damage accepts **-1** for effectively unlimited damage; maximum durability accepts **-1** to make the item unbreakable. A non-consumable food still grants its normal effects. Block hardness and explosion resistance are block-wide settings: changing one item changes every placed instance of its block.

Property rules are saved as plain entries in `config/itemcontrol/item_properties.json`. Save changes and fully restart the game to apply them; the current session continues using its active values. Reset clears the pending override and immediately refreshes the editor preview, while the game itself changes after restart.

Drop protection controls only dropped item entities. It does not change a block's explosion resistance or an item's normal fire behavior. Existing per-item entries in `protection.toml` are migrated into the property file on startup. The remaining protection settings include void salvage, global damage-source immunity, and direct-entity immunity. Those immunity checks use saved rules immediately, while initial display and physics flags on already spawned drops are not all retroactively reset.

The old separate protection editor has been retired; maintain per-item drop rules in the **Drop Protection** category here.

### Automatic and manual cleanup

The cleaner checks loaded entities across server dimensions. Item drops are the main target; experience orbs and a configured entity-ID list can also be enabled.

- The generated configuration enables automatic cleaning every **600 seconds**. Experience-orb and additional entity cleaning are disabled by default.
- An item whitelist accepts IDs, `@mods`, and `#tags`. Configure it before relying on cleanup around valuable drops.
- Protected areas exclude item drops inside their selected volume. The trash-bin blacklist is also respected when selecting item drops for cleanup.
- The **Delete** key requests manual cleanup. Ordinary players may use it only when the server allows manual cleaning; a hard disable stops both manual and automatic cleanup.
- A countdown precedes cleanup, and large cleanup jobs are processed across ticks.
- The trash bin keeps recoverable item stacks and a limited number of history records; it does not restore experience or other deleted entities.

Trash history is shared server-wide and stored with the world, not separately per player. The generated defaults retain **3 records** with **28 rows** per record. Open the inventory trash button or use a command to retrieve items before their history record expires.

### Cleaner commands

| Command | Purpose / access |
| --- | --- |
| `/kt clean help` | Show cleaner commands |
| `/kt clean bin [index]` | Open trash history; index 1 is newest |
| `/del bin [index]` | Alternative trash-bin command |
| `/kt del bin [index]` | The same alias under `/kt` |
| `/kt clean trash` | Delete all stored trash history; level 2 |
| `/kt clean auto <true/false>` | Enable/disable the automatic schedule; level 2 |
| `/kt clean toggle <true/false>` | Enable/disable the cleaner as a whole; level 2 |
| `/kt reload` | Reload registered configuration and reset the cleaner timer; level 2 |

`/kt clean toggle false` also prevents manual cleaning. `/kt clean auto false` only turns off the automatic schedule.

### Protected areas

The default selection tool is a **golden hoe**, and the default area-action key is **Left Shift**. Hold the configured tool in either hand:

1. Left-click and right-click blocks to select the two corners.
2. Hold the area-action key and left-click to confirm the area.
3. Hold the area-action key and right-click to remove the targeted area.
4. Hold **Alt + area-action key** and right-click to clear your own areas.

Selections are limited by the server's configured block-volume quota and ownership checks. The additional Alt + action-key + left-click action clears all areas and is restricted by server permissions. The editor renders the selection so its bounds can be checked before saving.

### Creative tabs

Hide entire tabs or selected contents, restore hidden entries, and add custom items to a tab. Rules support individual stacks/NBT, item tags, and mod namespaces.

- Select a tab to browse its items; right-click tabs or items to hide them.
- Restore entries from the hidden-items side; Ctrl-drag also moves entries between sides.
- Shift-right-click an item toggles strict NBT matching for its visibility rule.
- Save to synchronize the server configuration, then reopen the creative inventory. Use **F3+T** if a full client resource refresh is needed.

### Configuration and integrations

| File below the instance's `config/` directory | Contents |
| --- | --- |
| `kineticcore/banitem.json` | Banned items, merge mappings, and manually added tags |
| `itemcontrol/item_properties.json` | Per-item properties and dropped-item protection rules |
| `kineticcore/protection.toml` | Void salvage, global damage-source immunity, and direct-entity immunity |
| `kineticcore/cleaner.toml` | Cleanup schedule, lists, protected areas, and trash settings |
| `kineticcore/creative_tabs.json` | Tab hiding, content removals, and additions |

Trash records are world saved data named `itemcontrol_trashbin`. Protected areas are also world data. The trash-button visibility and position controls are local client preferences, independent of server cleanup rules.

Most cleaner rule changes are immediately usable. After changing the interval or enabling a previously disabled schedule through configuration, use `/kt reload` or reload the world; reopen trash screens after changing their layout. Item and tab reload handlers reload and synchronize their configuration. JSON recipe/data rewrites run during data loading, so `/kt reload` alone should not be treated as a complete recipe rebuild.

With KubeJS installed, scripts can listen for dropped-item hurt, spawn, and removal events through `itemcontrolEvents`, or use the `ItemProtection` binding. These hooks are optional; ordinary item and drop rules are managed in the in-game editors.

<a id="chinese"></a>

## 简体中文

Item Control 以极致的游戏内可视化操作为核心，将物品封禁与统一、单物品属性编辑、掉落物保护、带历史回收的清理以及创造标签页管理放在同一套工具中。它的功能覆盖面很广，在同类模组中少见地把这些物品与掉落物管理流程集中到可视化编辑器里。

### 安装与入口

- 当前构建目标：**Minecraft 1.20.1**、**Forge 47.4.2+**、**KineticCore 26.9.25+**。
- 多人游戏时，客户端和服务端均安装 Item Control 与 KineticCore。
- 可选兼容：**KubeJS** 提供掉落物事件脚本接口；**JEI** 提供配方查看器中的物品可见性兼容。
- 进入世界后按 **F6**，在 KineticCore 中选择 **Item Control**；可在按键设置中修改入口快捷键。
- 服务端物品规则与创造标签页编辑器需要 **2 级权限**。普通玩家能否手动清理由服务端另外设置。

### 物品封禁、合并与标签

物品模块分别提供封禁、合并、物品标签和保护规则编辑器。

| 规则输入 | 含义 | 示例 |
| --- | --- | --- |
| 物品 ID | 匹配一个已注册物品 | `minecraft:rotten_flesh` |
| `@模组ID` | 匹配整个模组命名空间 | `@examplemod` |
| `#命名空间:标签` | 匹配物品标签 | `#forge:ingots/copper` |
| 物品 ID 与 SNBT | 匹配带指定 NBT 的物品 | `minecraft:diamond_sword{Damage:10}` |

NBT 匹配检查指定的数据，不要求整个物品标签完全一致。需要区分同一物品的不同变体时，可使用编辑器中的 NBT 工具。

- **物品封禁：**选择单个物品，或添加模组、标签规则。被分组规则封禁的物品需要移除对应分组规则才能解禁。
- **物品合并：**选择最终保留的目标物品，将其他物品添加为来源，适用于多个模组的重复材料。
- 合并参与物品堆创建、NBT 更新以及物品和标签判断；不含 NBT 的替换 ID 也参与 JSON 数据重载处理。
- **物品标签：**手动添加标签，或先选目标物品，再从来源物品复制标签，重复标签会自动去重。
- 编辑器区分原生标签、合并继承标签和手动标签。这里仅能删除手动标签；继承标签需通过修改合并规则移除。

典型流程：选择整合包要保留的锭，将其他锭加入合并来源，保存后检查配方和新获取物品。创造标签页隐藏是独立的展示设置，本身不会封禁物品。

### 物品属性与掉落物保护

**物品属性编辑器**是主要的可视化工作台：分类菜单会筛选左侧物品，右侧显示所选物品的原始数值。未修改的值显示灰色，覆盖值显示绿色。“掉落物保护”分类可面向所有已注册物品，也支持物品标签、模组命名空间和带 NBT 的物品规则。

| 分类 | 可调整内容 |
| --- | --- |
| 战斗类 | 攻击伤害、攻速、护甲、护甲韧性、击退抗性和耐久 |
| 工具类 | 攻击伤害、攻速、挖掘速度、挖掘等级和耐久 |
| 食物类 | 饱食度、饱和度、食用时间、满饥饿时可食用，以及食用后是否消耗 |
| 通用类 | 堆叠上限、耐久、附魔能力和本地化稀有度选项 |
| 方块类 | 硬度和爆炸抗性 |
| 掉落物保护 | 掉落物免火、免爆炸、发光、无重力和不消失 |

攻击伤害填 **-1** 表示实战无限伤害；最大耐久填 **-1** 表示物品不会损坏。设置为不消耗的食物仍会正常产生效果。方块硬度和爆炸抗性作用于该方块的所有实例，修改物品规则会影响已经放置的同种方块。

属性规则以普通 ID 到属性对象的 JSON 格式保存在 `config/itemcontrol/item_properties.json`。保存后需完整重启游戏；重启前，当前游戏继续使用原有活动值。重置会立即更新编辑器预览并清除待保存覆盖，实际游戏内数值在重启后恢复。

掉落物保护只控制掉落实体，不改变方块爆炸抗性或物品本身的抗火行为。启动时会把 `protection.toml` 中的旧版单物品保护条目迁移到属性文件。当前保护配置还保留虚空救援、全局伤害来源免疫和直接实体免疫；免疫规则保存后即可使用，但不会追溯重设所有已有掉落物的初始外观和物理状态。

旧版独立保护编辑入口已停用；请在这里的**掉落物保护**分类中维护单物品掉落规则。

### 自动与手动清理

清理器遍历服务端各维度已加载的实体，以物品掉落物为主要目标，也可开启经验球和指定实体 ID 列表的清理。

- 生成的配置默认每 **600 秒**自动清理一次，默认不清理经验球和附加实体。
- 物品白名单支持 ID、`@模组` 和 `#标签`；在重要掉落物附近使用前应先配置好规则。
- 保护区域内的物品掉落物会被排除；选择清理对象时也会排除垃圾桶黑名单中的物品。
- **Delete** 键请求手动清理。普通玩家必须获得服务端设置允许；总禁用会同时停止自动与手动清理。
- 清理开始前有倒计时，大批量任务会分散到多个 tick 处理。
- 垃圾桶保存可取回的物品及有限数量的历史记录，不恢复经验球或其他被删除的实体。

垃圾桶历史为整个服务器共享，并随世界保存，不按玩家分开。生成的默认设置保留 **3 条记录**，每条 **28 行**。可点击背包垃圾桶按钮或使用命令，在记录过期前取回物品。

### 清理命令

| 命令 | 用途与权限 |
| --- | --- |
| `/kt clean help` | 查看清理命令 |
| `/kt clean bin [序号]` | 打开历史记录，1 为最新 |
| `/del bin [序号]` | 垃圾桶命令别名 |
| `/kt del bin [序号]` | `/kt` 下的同一别名 |
| `/kt clean trash` | 删除全部垃圾桶历史，需要 2 级权限 |
| `/kt clean auto <true/false>` | 开关自动清理，需要 2 级权限 |
| `/kt clean toggle <true/false>` | 开关整个清理器，需要 2 级权限 |
| `/kt reload` | 重载已注册配置并重设清理计时，需要 2 级权限 |

`/kt clean toggle false` 也会阻止手动清理；`/kt clean auto false` 只关闭自动计划。

### 清理保护区域

默认选区工具是**金锄头**，默认区域操作键是 **左 Shift**。任意一只手持有配置的工具时：

1. 左键、右键方块分别选择两个角点。
2. 按住区域操作键并左键，确认创建区域。
3. 按住区域操作键并右键，删除指向的区域。
4. 按住 **Alt + 区域操作键**并右键，清除自己的区域。

选区受服务端方块体积配额及所有权检查限制。额外的 Alt + 操作键 + 左键为清除全部区域，受服务端权限限制。界面会渲染选区，方便保存前检查边界。

### 创造模式标签页

可隐藏整个标签页或其中的指定内容、恢复隐藏项，并向标签页添加自定义物品。规则支持单个物品堆、NBT、物品标签和模组命名空间。

- 选择标签页查看物品；右键标签页或物品可隐藏。
- 从隐藏区域恢复条目，也可使用 Ctrl 拖拽在两侧移动。
- Shift + 右键物品可切换可见性规则的严格 NBT 匹配。
- 保存会同步服务端配置，然后重新打开创造物品栏；需要完整客户端资源刷新时使用 **F3+T**。

### 配置文件与兼容接口

| 游戏实例 `config/` 目录下的文件 | 内容 |
| --- | --- |
| `kineticcore/banitem.json` | 物品封禁、合并关系和手动标签 |
| `itemcontrol/item_properties.json` | 单物品属性和掉落物保护规则 |
| `kineticcore/protection.toml` | 虚空救援、全局伤害来源免疫和直接实体免疫 |
| `kineticcore/cleaner.toml` | 清理计划、名单、保护区域和垃圾桶设置 |
| `kineticcore/creative_tabs.json` | 标签页隐藏、内容移除和新增 |

垃圾桶记录使用名为 `itemcontrol_trashbin` 的世界存储数据；保护区域也随世界保存。垃圾桶按钮的显示与位置是本地客户端偏好，独立于服务端清理规则。

大多数清理规则可立即使用。通过配置修改间隔，或重新启用已关闭的自动计划后，使用 `/kt reload` 或重新加载世界；更改垃圾桶布局后需重新打开界面。物品与标签页重载处理会读取并同步配置。JSON 配方与数据替换在数据加载阶段执行，因此 `/kt reload` 本身不应视为完整的配方重建。

安装 KubeJS 后，可用 `itemcontrolEvents` 监听掉落物受击、生成和移除事件，或使用 `ItemProtection` 绑定。这些脚本接口是可选扩展；普通物品规则仍通过游戏内编辑器管理。
