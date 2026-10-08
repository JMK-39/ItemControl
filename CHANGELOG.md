## 2026-10-08 — Item preview slots / 物品预览格

### English

- Dragged creative tabs and items show their previews on the same item-slot background as the editor, including small item previews.
- Item ban, merge, tag, protection and cleanup lists keep two pixels between slots and show complete item icons. Dense grids use fewer rows and columns within their existing panels; clicks in the gaps no longer select adjacent entries.
- Creative tab previews keep gaps between their slots and clear the surrounding panel borders. Longer button labels continue scrolling within fixed-size buttons.
- Creative tab editing loads vanilla items even when the creative inventory has not been opened yet.

### 简体中文

- 拖动创造模式标签页或物品时，预览使用与编辑器相同的物品格背景，小尺寸物品预览也包括在内。
- 物品封禁、合并、标签、保护与清理列表的物品格保持两像素间距，并完整显示图标。密集网格在现有面板内减少行列，点击间隙不再选中相邻条目。
- 创造模式标签页预览格之间保留间距，并避开外围面板描边；较长按钮文字继续在固定大小的按钮内滚动显示。
- 首次打开创造标签编辑器时，即使尚未打开过原版创造背包，也会加载原版物品列表。

## 26.10.6 — 2026-10-06

### English

- In the item merge editor, the rule rows (a target and its expanded source items) keep 2 px between them instead of 1 px, so neighbouring row frames no longer touch.
- The item merge editor always shows the rule list on the left and the item grid on the right. On smaller windows it used to stack them, leaving a large empty rule panel above the grid; now the Add, Save and Back buttons move down beside the item count instead.
- Merged items in the rule list show a full-size 16 px icon and normal-size name, instead of a half-size icon and small grey text. Rule and merged-item rows keep their slots 2 px inside the row frame.
- The cleaner whitelist, trash bin blacklist and area tool editors fit their content and sit in the middle of the window over the dimmed world, with the title and buttons inside the panel, instead of covering the whole window with a dark backdrop. The area tool editor is a small window with just the tool's icon; its name, ID and the editing hint are in the icon's tooltip. The rule lists show as many rows as their rules need plus one free row, and grow up to the full height as rules are added.
- The item protection rule dialog is shown on its own at its own size over the dimmed world, without the empty item-list frame and dark backdrop that were left behind it.
- The creative tab bar's arrow buttons sit inside the bar's frame instead of on its side lines, and the tag editor's suggestion list covers the text under it instead of showing it through. The item property editor's title and the protection list's hint keep 2 px from their frame lines.
- In the item tag editor, the selected-item line and the tag list each sit in their own outlined box, so the two are easy to tell apart.
- The trash bin's green stack counts are drawn the way vanilla draws counts, so the item icon no longer covers them in some modpacks.
- The trash bin header keeps the same layout in every language: the Previous/Next buttons have fixed widths and the page number starts after a fixed title area; longer text scrolls.

### 简体中文

- 物品合并编辑器中，规则行（目标及展开后的来源物品）之间的间距由 1 像素改为 2 像素，相邻行的边框不再相接。
- 物品合并编辑器始终左侧为规则列表、右侧为物品网格。之前在较小窗口中改为上下排列，网格上方留下一大块空白的规则面板；现在改为把新建、保存和返回按钮移到物品数量旁边。
- 规则列表中被合并的物品显示完整的 16 像素图标和正常大小的名称，不再是半尺寸图标和小号灰色文字。规则行与被合并物品行的物品格与行边框保持 2 像素。
- 清理白名单、垃圾桶黑名单与范围工具编辑器的窗口随内容大小变化，位于窗口中央并透出变暗的游戏画面，标题和按钮都在面板之内，不再用深色背景遮住整个窗口。范围工具编辑器为只显示工具图标的小窗口，名称、ID 与编辑提示放在图标的悬浮提示中。规则列表按规则数量显示所需行数并多留一行空行，添加规则时最多增长到整个高度。
- 物品保护规则对话框按自身大小单独显示并透出变暗的游戏画面，背后不再留下空的物品列表边框和深色背景。
- 创造标签栏的箭头按钮位于标签栏边框之内，不再压在两侧边框线上；标签编辑器的建议列表会遮住下方文字，不再透出。物品属性编辑器标题和保护列表提示与边框线保持 2 像素。
- 物品标签编辑器中，当前物品一行与标签列表各自放在带描边的框内，便于区分。
- 垃圾桶中的绿色堆叠数量改为按原版方式绘制，在部分整合包中不再被物品图标遮住。
- 垃圾桶标题栏在所有语言下排版相同：上一页/下一页按钮宽度固定，页码从固定的标题区域之后开始；过长文字滚动显示。

## 26.10.5 — 2026-10-05

### English

- The damage type list is plain striped rows instead of a column of green boxes; only the hovered row (blue) and invalid entries (red) are outlined.
- Saving the banned-item rules or the creative tab layout answers only the player who saved; the rules are no longer sent to every online player, and other players receive them at login. Saving the item settings page updates online admins only. Item property changes (stack size, durability and so on) are still sent to everyone, because inventories must match the server.
- Added Minecraft 26.1.2 / NeoForge 26.1.2.112 support (Java 25); releases now cover Forge 1.20.1, NeoForge 1.21.1 and NeoForge 26.1.2. Optional JEI 29.43; the current KubeJS 26.1 release cannot start on 26.1.2.
- Every version uses the same screens. On 1.21.1 and 26.1.2, item component data (`[damage=5]`) is now edited in Core's NBT editor, the same editor Forge uses for NBT, instead of a separate page. Requires KineticCore 26.10.5+.
- On 26.1.2, item property rules support food, eating time, enchantability, stack size, durability, rarity, mining, attributes and block values. Armor slots, tool levels and mining speeds follow the item's native components.
- Item rules written for 1.21.1 keep their attribute ids on 26.1.2 (`minecraft:generic.attack_speed` matches `minecraft:attack_speed`). Item merging, merged tags, item protection, the trash bin, cleaner areas and creative tabs work on 26.1.2.
- Fixed on every version: cleaner key bindings used a missing category name, so the Controls screen showed a raw key; they now appear under "Dropped-Item Cleanup".
- Fixed on 1.21.1: the drop-protection hint showed the 1.20.1 `item_id{components}` syntax instead of `item_id[components]`.
- Fixed a NeoForge mod-loading warning screen on 26.1.2.

### 简体中文

- 伤害类型列表改为条纹纯文字行，不再是一列绿色方框；只有光标所在行（蓝色）与无效条目（红色）带边框。
- 保存禁用物品规则或创造标签页布局只回复保存的玩家，不再发送给所有在线玩家，其他玩家在登录时获得。保存物品设置页面只更新在线管理员。物品属性修改（堆叠数量、耐久等）仍发送给所有人，因为背包必须与服务端一致。
- 新增 Minecraft 26.1.2 / NeoForge 26.1.2.112 支持（Java 25）；发布版本覆盖 Forge 1.20.1、NeoForge 1.21.1 与 NeoForge 26.1.2。可选 JEI 29.43；KubeJS 当前的 26.1 版本无法在 26.1.2 上启动。
- 所有版本使用相同界面。1.21.1 与 26.1.2 的物品数据组件（`[damage=5]`）改用核心 NBT 编辑器编辑，与 Forge 编辑 NBT 的界面一致，不再使用单独页面。要求 KineticCore 26.10.5+。
- 26.1.2 的物品属性规则支持食物、食用时间、附魔能力、堆叠、耐久、稀有度、挖掘、属性与方块数值。盔甲槽位、工具等级与挖掘速度遵循物品的原生组件。
- 1.21.1 写下的属性规则在 26.1.2 上继续生效（`minecraft:generic.attack_speed` 对应 `minecraft:attack_speed`）。物品合并、合并标签、物品保护、垃圾桶、清理区域与创造标签页可在 26.1.2 中使用。
- 修复所有版本：清理按键使用了不存在的分类名，按键设置界面显示原始键名；现在显示在“掉落物清理”分类下。
- 修复 1.21.1：掉落物保护提示显示 1.20.1 的 `物品ID{数据组件}` 写法，现改为 `物品ID[数据组件]`。
- 修复 26.1.2 显示 NeoForge 模组加载警告界面的问题。

---

## 26.10.4 — 2026-10-04

### English

- Keep long editor headings, labels, hints, autocomplete suggestions and list names inside their own bounds with shared scrolling text. Applies to item properties, protection, direct entity immunity, damage types, banned/merged items, tags, cleaner rules, creative tabs and component editing.
- Merged-item names keep their text size and stay within their rows; target names stay clear of expand symbols and counts.
- Move protection dialog toggles below the status text. Keep cleaner navigation and headers within their space and hide duplicate vanilla container labels.
- Requires matching KineticCore 26.10.4+ for tooltips that fit the screen.

### 简体中文

- 物品属性、保护、直接实体免疫、伤害类型、禁用/合并物品、标签、清理规则、创造标签页及组件编辑器的长标题、标签、说明、自动补全与名称在各自范围内滚动。
- 合并来源的名称保留文字大小，并限制在各自行内；目标名称避开展开符号与计数。
- 保护弹窗的开关移到状态说明下方；清理页标题、历史页码及导航按钮限制在各自区域内，隐藏重复的原版容器标签。
- 要求匹配的 KineticCore 26.10.4+，悬浮提示按屏幕范围适配。

---

2026年10月03日 19时06分 — 26.10.3

- Added NeoForge 1.21.1 support alongside Forge 1.20.1, using matching KineticCore 26.10.3+.
- The 1.21.1 item rules and creative tabs use native components; legacy item-NBT syntax/schema is rejected and is not converted. Forge retains its NBT behavior.
- Fixed 1.21.1 trash-stack counts and saved data, startup component constraints, merge-rule checks, and food-property handling.
- Minecraft 26.1.2 is not yet supported.

- 新增 NeoForge 1.21.1 支持，同时保留 Forge 1.20.1；使用对应版本的 KineticCore 26.10.3+。
- 1.21.1 物品规则与创造标签页使用原生组件，拒绝且不转换旧物品 NBT 写法和配置格式；Forge 保留原有 NBT 行为。
- 修复 1.21.1 回收物品数量与存盘、启动阶段组件约束、合并规则检查和食物属性处理。
- 尚不支持 Minecraft 26.1.2。

---

2026年09月29日（原记录未标注小时、分钟）

- Updated the interfaces for item cleanup, trash, attributes, tags, protection rules, damage types, merge rules, and entity immunity.
- Updated compatibility with KineticCore 26.9.29.

- 更新物品清理、垃圾桶、属性、标签、保护规则、伤害类型、合并规则和实体免疫界面。
- 更新对 KineticCore 26.9.29 的兼容。
