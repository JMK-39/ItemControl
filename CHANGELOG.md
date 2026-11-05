## 26.10.26 — 2026-10-26
- Item properties have a Never Breaks switch next to Max Durability (combat, tool and general categories): the item stops wearing out and hides its durability bar. Elytra-style chestplates, such as the ignitium elytra chestplate, keep gliding when set to never break or to no durability, even if the stack was already worn down.
- 物品属性在“最大耐久”旁新增“不会损坏”开关（战斗类、工具类、通用类）：物品不再消耗耐久并隐藏耐久条。带鞘翅的胸甲（例如腾炎鞘翅胸甲）设为不会损坏或无耐久后照常滑翔，即使物品之前已经磨损。
- Item tags can now also be taken off: in the tag editor right-click an original tag to remove it (right-click again to give it back), or use Clear original to take every original tag off the item. Removals apply when the data loads (/reload or rejoining).
- 物品标签现在也能移除：在标签编辑器里右键原生标签即可移除（再右键恢复），“清除原有”可一次去掉该物品的全部原有标签；移除在数据加载时生效（/reload 或重进世界）。
- Added and removed item tags are written into the loaded item tags themselves, so every tag check sees them (Curios slots, recipes, enchanting), not only item checks.
- 添加和移除的物品标签会直接写入载入后的物品标签数据，Curios 栏位、配方、附魔等所有按标签判断的地方都能识别，不再只对物品判断生效。
- Banned items no longer get into the world through other ways: they are removed from generated loot (chests, mob and block drops, fishing, loot modifiers), dropped item entities of a banned item vanish (including mob equipment that drops on death and items loaded from saves), items given straight into an inventory are used up, and banned items already sitting in a container or a player inventory are removed when the container is opened or the player joins. Before, they were only removed when put into a slot through a menu.
- 封禁物品不再从其他途径进入世界：生成的战利品（箱子、生物与方块掉落、钓鱼、战利品修改器）会去掉封禁物品；封禁物品的掉落物实体会直接消失（包括生物死亡掉落的装备和存档里已有的掉落物）；直接放进背包的物品（指令、奖励、拾取）会被吞掉；容器和玩家背包里原有的封禁物品会在打开容器或玩家进服时清除。之前只有通过界面放入格子时才会清除。
- Ban, merge and tag rules cost much less: @mod rules are a prefix check, #tag rules a direct tag lookup instead of going through every tag of the item, and the checks that run on every item comparison (merged items and merged tags) are prepared lookups that return at once when no rule exists.
- 封禁、合并和标签规则的开销大幅降低：@模组规则改为前缀判断，#标签规则直接查标签，不再逐个遍历物品的所有标签；每次物品比较都会运行的检查（合并物品、合并标签）改为预先建好的查找表，没有规则时立即返回。

## 26.10.24 — 2026-10-24
- 物品标签页的物品网格批量绘制图标，滚动更流畅。
- Item property editor: the item grid uses standard slots without padding and the right panel gets the freed space; Batch & Templates, Add Rule, Reset and Delete moved to the top of the panel, label-sized; the category menu and the other buttons are shorter; the edited item uses a standard slot.
- 物品属性编辑：物品网格改为无内边距标准格，右侧面板获得空出的宽度；批量与模板、添加规则、重置、删除移到面板顶部且按文字长度；分类菜单与其他按钮缩短；正在编辑的物品使用标准物品格。
- Accessory/equipment attribute and food effect editors share one layout (list at the same ratio, Apply and Delete next to Save, short fields in one row, no status line). Slot counts show as coloured cards; Batch & Templates keeps every button in the header; the comparison is a table (original → draft).
- 饰品/装备属性与食用效果编辑统一布局（列表比例一致，应用与删除在保存旁，短输入框同排，去掉状态文字）；槽位数量改为彩色卡片；批量与模板所有按钮放在顶栏；原值与草稿对照改为表格。
- Item tags use standard slots, the tag field and list moved up, and the tag suggestions open above the scrollbars. Trash bin slots match the player inventory below, and stack counts are drawn above the durability bar.
- 物品标签改用标准物品格，标签输入框与列表上移，标签补全列表不再被滚动条遮挡；垃圾桶格子与下方玩家背包样式一致，数量显示在耐久条上方。

## 26.10.20 — 2026-10-20
- The item property editor's category menu lists the plain category names (战斗类, 工具类 …) instead of repeating 当前： on every entry. / 物品属性编辑器的分类菜单直接列出分类名（战斗类、工具类……），不再每项都加“当前：”。
- The item property editor, the equipment and accessory attribute editors, the food effect editor and the accessory slot count editor have Save at the top right. The attribute and food editors use the standard header (Back at the top left, a divider under it), lists that reach the bottom, and Apply / Delete on the bottom row instead of floating halfway down.
- 物品属性编辑、装备与饰品属性编辑、食物效果编辑、饰品槽位数量编辑的“保存”统一放在右上角；属性与食物效果编辑器改用标准顶栏（左上角返回、下方分隔线），左侧列表延伸到底部，“应用规则/删除规则”移到最底行，不再浮在中间。
- Trash bin slots look like a vanilla chest's, without the item-slot background. The property editor's Cancel reads Back. / 垃圾桶物品格改为原版箱子样式，不再叠加物品格背景；属性编辑器的“取消”改为“返回”。
- Chinese text on 1.21.1 and 26.1.2 no longer puts spaces around 数据组件 (for example 编辑数据组件 instead of 编辑 数据组件). / 1.21.1 与 26.1.2 的中文不再在“数据组件”两侧留空格（例如“编辑数据组件”而不是“编辑 数据组件”）。
- Banned items and item protection rules use the Core item search (categories, @mod and #tag rules, green outlines for listed items). Protection: left-click opens the rule dialog (Back top-left, Delete and Apply top-right, shorter), right-click removes the rule. Bans: left-click toggles, Shift + left-click edits the NBT rule. / 封禁物品与物品保护规则改用核心物品搜索界面（分类、@模组与 #标签规则、已列入的物品绿色描边）。保护规则：左键打开规则弹窗（返回在左上角，删除与应用在右上角，弹窗更矮），右键移除；封禁：左键切换，Shift+左键编辑 NBT 规则。
- Item properties: Batch & Templates, Add Rule, Reset Item and Delete moved into their own block at the bottom of the property panel, with Save under them on the right; the item grid starts right under the header and shows 13 rows. / 物品属性：批处理与模板、添加规则、重置物品、删除移到属性面板底部单独一块区域，保存在其下方右侧；物品网格紧贴顶栏，显示 13 行。
- Item tags: normal 18 px item slots in ten columns, a wider tag list, 4 px margins, Save top-right and Add next to Copy Tags. Merges: expanded sources hang off their target on mind-map connector lines. Trash bin: slots sit 2 px apart, and the player inventory below is drawn from the chest texture in one piece, so it looks like a chest inventory under resource packs too. Creative tabs: Add Custom Item and Save in the header, page arrows as tall as the tab icons, a taller item grid and no separate colour blocks. Damage types: a shorter search leaves room for the page title. / 物品标签：物品格恢复 18 像素、每行 10 个，标签列表加宽，间距统一 4 像素，保存在右上角，添加在“复制标签”旁。物品合并：展开后的源物品用思维导图式连线挂在目标物品下。垃圾桶：每格间隔 2 像素，下方玩家背包整块取自箱子贴图，使用材质包时也与箱子背包一致。创造标签页：添加自定义物品和保存移到顶栏，翻页按钮与标签图标同高，物品网格更高，去掉单独色块。伤害类型：搜索框缩短，显示页面标题。
- The equipped slot count editor uses the standard 640×360 canvas like the other accessory editors: Back and title in the header, slot and amount on one row, the rule list below and Apply, Delete and Save along the bottom. / 佩戴槽位数量编辑页改用与其他饰品编辑页相同的 640×360 标准画布：返回与标题在顶栏，槽位与数量同一行，下方为规则列表，底部为应用、删除与保存。
- The item protection rule editor places Back in the top-left corner four pixels from the frame, with the title beside it, like the other editors. / 物品保护规则编辑器的返回按钮移至距外框 4 像素的左上角，标题紧随其后，与其他编辑器一致。
- The trash-button HUD instructions now explain its fixed size and the drag, reset and save controls. / 垃圾桶按钮位置编辑说明现明确尺寸固定，并保留拖动、重置与保存提示。
- Item rule, merge, tag, protection and creative-tab tooltips keep the full native item details and append their editing hints. Unsaved item-property previews also keep native tooltip images.
- 物品规则、统一、标签、保护及创造标签页保留完整原生物品提示并附加操作说明；未保存的属性预览也保留原生提示图像。
- Full-size item, attribute, food, batch and creative-tab editors use the standard four-pixel canvas safe margin. Manual ban, merge and tag hit tests now use the same rounded scroll position as their rendered cards and rows.
- 640×360 物品、属性、食物、批处理及创造标签编辑页统一使用 4 像素安全边距；禁用、统一和标签列表的点击/提示命中与绘制使用相同的滚动像素位置。

### English

- Editors share one white outer frame, with gray-white dividers between content regions instead of nested panel borders. Item cards, interactive rows, popups and native container textures retain their outlines.
- Trash-bin quantities render above item icons using native bottom-right decorations, retaining green counts and the existing large-count abbreviations.

### 简体中文

- 编辑器统一使用白色外框，内容区域通过灰白分隔线划分，移除嵌套面板边框；物品卡片、交互行、弹窗及原生容器贴图保持原样。
- 垃圾桶数量通过原生右下角装饰绘制在物品图标上方，保留绿色数量及原有的大数量缩写。

## 26.10.16 — 2026-10-16

### English

- Remove two fixed explanatory rows from item protection configuration. Widen searches without overlapping adjacent controls and use the full standard canvas for attribute/effect editing. Delete reuses existing selected/hovered rule removals; text editing retains priority.
- Configured accessories now use real Curios item capabilities and native slot eligibility. Curios itself displays every allowed slot and its attribute group, while original accessory behavior is preserved. Rules update existing stacks immediately.
- Damage immunity actions share the top toolbar. The entity immunity editor uses the Core entity grid with twelve columns and six full rows at the standard canvas size.
- Tag rows have individual white outlines and a scrollbar; redundant descriptions, item IDs and nested frames were removed. Merge items use more of the available space, and property controls retain gray labels when disabled.
- Cleaner white/blacklists open the Core searchable item multiselector directly. The area-tool setting opens the Core item picker directly.

### 简体中文

- 移除物品保护配置的两段固定说明；搜索框加宽并避让相邻控件，属性和效果编辑使用标准大画布。Delete 复用已有选中或悬停规则的删除动作，文本编辑优先。
- 配置饰品现在实际接入 Curios 物品能力与原生槽位判定，由 Curios 自己显示全部可用槽位及各槽属性组，并保留已有饰品的原始行为。修改立即作用于已有物品堆。
- 伤害免疫的操作集中到顶部；实体免疫页使用 Core 实体网格，标准画布可容纳十二列、六整行。
- 标签逐行使用白色描边并可拖动滚动条，删除重复说明、物品 ID 和内部双框；合并页扩大内容空间，属性页未选择物品时仍显示灰色按钮文字。
- 清理白名单、垃圾桶黑名单直接打开 Core 可搜索物品多选器；区域工具设置直接打开 Core 物品选择器。

## 26.10.15 — 2026-10-15

### English

- Items made wearable show it in their tooltip, worded like Curios accessories: the slot line under the name and, for items that are not accessories on their own, the "When worn" attribute lines. An accessory moved to other slots shows its new slots. Saved changes show at once, without a restart.
- Hovering an item in the property editor shows its full tooltip with the editor's changes already applied, before saving.
- The attribute and food effect editors list every attribute or effect on the left (with a search field); entries with a rule come first with a green outline and their value, and clicking one edits it on the right. The attribute dropdown and the New Rule button are gone.
- The property editor's item grid has one more column, the panel on the right is narrower, and the bottom buttons share one width.
- Choosing an equipment slot in the equipment attribute editor loads that attribute's rule for the slot.

- Items taken from the trash bin stay taken. Previously, leaving the world soon after taking them could bring the same items back into the trash bin on the next load.
- A block made edible or wearable still places when used on a block, like vanilla edible block items; it is eaten or equipped when used in the air.
- Accessory slots: choosing the generic slot clears the specific slots, and choosing a specific slot clears the generic one. Several specific slots can be combined.
- The property panel shows the item being edited (icon, name and ID) under the title.
- The accessory page lists the selected slots, attribute rules and equipped slot counts directly; selected slots come first in the slot list.
- Accessory and equipment attribute editors list every configured rule. Selecting a rule loads it into the form; New Rule, Apply and Delete act on that rule.

### 简体中文

- 设置为可佩戴的物品会在提示中说明，措辞与 Curios 饰品一致：名称下方显示栏位；本身不是饰品的物品还会显示“佩戴时”属性。改到其他栏位的饰品会显示新栏位。保存后立即显示，无需重启。
- 在属性编辑器中悬停物品，会显示应用了当前编辑内容的完整物品提示，无需先保存。
- 属性与食用效果编辑页在左侧列出全部属性或效果（带搜索框）；已有规则的条目排在前面，带绿色描边并显示数值，点击即可在右侧编辑。属性下拉框与“新建规则”按钮已移除。
- 属性编辑器的物品网格多显示一列，右侧面板更窄，底部按钮宽度统一。
- 在装备属性编辑页切换装备槽位时，会读取该属性在此槽位的规则。

- 从垃圾桶取出的物品会正确保存。此前取出后很快退出存档，重新进入时垃圾桶里会再次出现相同物品，导致刷物品。
- 设为可食用或可穿戴的方块对着方块使用时仍会放置，与原版可食用方块物品一致；对着空气使用时食用或穿戴。
- 饰品槽位：选择通用槽位会取消其他具体槽位，选择具体槽位会取消通用槽位；具体槽位可以多选。
- 属性面板在标题下方显示正在编辑的物品（图标、名称与 ID）。
- 饰品页直接显示已选佩戴槽位、属性规则和佩戴时的槽位数量；槽位列表中已选槽位排在最前。
- 饰品与装备属性编辑页列出所有已配置的规则，选中规则即可载入表单编辑，新建、应用与删除都针对该规则。

## 26.10.12 — 2026-10-12

### English

- Property Save reads the current input and keeps the selected item and editor open. Attribute child-page Save applies the current row and returns to the parent; attribute choices support ID and localized-name search.
- Saved item tags update online clients immediately, including Curios slot admission. Protection previews retain NBT/components. Restart notices are limited to enchantability on Minecraft 26.1.2.
- All four rule editors save current edits without an extra Apply click, preserve deletions, and leave default rules absent when only browsing choices.

### 简体中文

- 属性保存会读取当前输入，并保持选中物品与编辑界面；属性子页保存会应用当前行并返回父页，属性列表支持 ID 和本地化名称搜索。
- 物品标签保存后立即同步在线客户端，包括 Curios 槽位准入；掉落保护预览保留 NBT／数据组件。仅 Minecraft 26.1.2 的附魔能力提示需要重启。
- 四个规则子编辑器均可直接保存当前修改；删除后保存不再恢复规则，仅浏览选项不会新增默认规则。

## 26.10.11 — 2026-10-11

### English

- Items can combine melee weapon, wearable equipment, food and accessory abilities. Converted weapons and equipment consume durability when used or hit. Mixed equipment/food items eat on right-click and equip on sneak-right-click into an empty slot.
- Added visual equipment attribute editing with slot selection, add/replace/remove modes and arithmetic operations.
- Added food effect duration, level and chance editing, effect inheritance/replacement, and a visual returned-item selector. Minecraft 26.1.2 food changes now apply on save.
- Added selective batch edits, rule copying, named templates with JSON import/export, and original/draft comparison with application timing.
- Added equipped Curios slot-count modifiers, including updates on save and removal on unequip. Curios remains optional.
- Property categories preserve the selected item and support independent abilities instead of filtering out ordinary items. New controls include explanatory tooltips.
- The property editor now divides its item browser and controls equally, with twelve complete columns and rows at the standard layout and native-size item icons.

### 简体中文

- 物品可以同时拥有近战武器、装备、食物与饰品能力；转换后的武器与装备会在攻击或受击时消耗耐久。装备同时为食物时，右键食用，潜行右键穿戴到空槽位。
- 新增普通装备属性可视化编辑，支持槽位选择、追加／替换／移除和属性运算方式。
- 新增食物效果持续时间、等级与概率编辑，支持继承／替换原有效果；返还物品使用可视化选择器。26.1.2 的食物修改改为保存后生效。
- 新增只修改勾选字段的批量编辑、规则复制、命名模板与 JSON 文件导入导出，以及原值／草稿／生效时间对照。
- 新增佩戴饰品时的 Curios 槽位数量修饰，保存后更新，脱下后移除；Curios 仍为可选依赖。
- 切换属性分类保留当前物品，普通物品也可编辑各项独立能力；新增控件补充悬浮说明。
- 物品属性编辑器左右区域改为对半分配；标准布局可完整显示 12 列、12 行原始大小的物品图标。

---

2026年10月10日 — Item editor spacing and navigation / 物品编辑页间距与导航

- Item editor Back controls use the upper left, or the lower left when the header is occupied.
- Return controls use white text. Protection and merge item lists use their horizontal space for another complete item column, with the protection rule action and count area widened.
- Accessory slot choices have outlined text rows with two-pixel gaps and preserve their ID and translation colors when selected.
- The merge editor's item browser keeps two pixels of space above and below complete item rows. Hovering and clicking the padding no longer targets hidden items, and scrollbar dragging uses the same padded track.

- 物品编辑页的返回入口优先放在左上角，顶部空间不足时放在左下角。
- 返回入口统一使用白色文字；保护规则和物品合并列表利用横向空间增加完整的一列物品，并加宽保护规则操作与计数显示区域。
- 饰品槽位选择使用带描边的文字条，条目间保留两像素间距，选中后 ID 和翻译文字颜色保持不变。
- 物品合并编辑器的右侧网格在完整物品行的顶部和底部保留两像素间距；悬停或点击空白边距不会选中隐藏物品，拖动滚动条与有间距的轨道保持一致。

---

## 2026-10-09 — Item and accessory properties / 物品与饰品属性

### English

- Saving item property changes now updates held and equipped attack damage, armor and durability without rejoining or re-equipping. Resetting saved rules restores the original values.
- Renamed the entry to **Item and Accessory Properties** and added optional Curios accessory conversion, a compact striped dropdown showing slot IDs and localized names, multiple slot selection, any-slot compatibility and an unequip switch.
- Accessory attributes can be added, replaced or removed, including attributes supplied by other mods. The visual editor shows original values and preserves unrelated accessory attributes and behavior.
- Existing accessories show their native slots for editing, with a `#curios:` tag-search shortcut. Slot IDs stay white; localized names appear in gold only outside English, and selection changes only the border.
- The property item browser uses spare left-side space to show nine complete columns while keeping the existing divider and controls in place.
- Accessory controls are disabled with a dependency tooltip when Curios is not installed. On Minecraft 26.1.2, food components and enchantability still require restarting the game.

### 简体中文

- 保存物品属性后，已持有和装备物品的攻击伤害、护甲和耐久立即更新，无需重新进入或重新装备；保存重置规则后恢复原有数值。
- 入口改为**物品与饰品属性修改**，新增可选 Curios 饰品转换、显示 ID 与本地化名称的深浅交替细条下拉列表、多选槽位、任意槽位兼容及允许脱下开关。
- 饰品属性支持追加、替换与移除，也可调整其他模组提供的饰品属性；可视化编辑器展示原有数值，保留未指定的属性及其他饰品行为。
- 已有饰品会显示原生槽位以供修改，并提供 `#curios:` 标签搜索快捷入口；槽位 ID 始终纯白，非英文下的翻译为金色，选中仅改变边框颜色。
- 物品属性列表利用左侧留白显示九列完整物品，保留原有分隔线与控件位置。
- 未安装 Curios 时饰品控件禁用，悬浮提示说明依赖要求；Minecraft 26.1.2 的食物组件与附魔能力仍需重启游戏。

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
