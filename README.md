# Item Control

## English

Item Control provides visual management for item rules, properties, dropped-item protection, cleanup, and creative tabs.

- Item bans, unification, tags, and item/accessory property editing with optional Curios integration.
- Automatic/manual cleanup with recoverable trash history and protected areas.
- Creative-tab curation and optional KubeJS/JEI integration.

Supports Minecraft **1.20.1 / Forge 47.4.2+** with **Java 17**, **1.21.1 / NeoForge 21.1+** (validated with 21.1.252) with **Java 21**, and **26.1.2 / NeoForge 26.1.2.0+** (validated with 26.1.2.112) with **Java 25**. Requires **KineticCore 26.10.7+ on Forge 1.20.1 (26.10.5+ on NeoForge)** matching the Minecraft/loader. Install required mods on the server and connecting clients.

Optional: Curios, KubeJS and JEI builds matching the Minecraft/loader.

### Item and accessory properties

Open **Item and Accessory Properties**, select an item, and switch categories to configure independent abilities. Any item can become a melee weapon, wearable equipment, food or a Curios accessory, including equipment that is also edible. Leave a field blank to inherit its original value. Save the main editor to apply the draft.

- Set attack damage and positive durability to turn an ordinary item into a weapon. Choose an equipment slot to make it wearable; that slot must be empty. For items that are both equipment and food, right-click eats and sneak-right-click equips.
- Edit registered equipment attributes visually by slot, operation and add/replace/remove mode. Food supports hunger, saturation, eating time, effects with duration/level/chance, and an item selector for the returned container.
- Open **Batch, Copy and Templates** to select target items, search by `@mod` or `#tag`, and apply only checked fields. Named templates support JSON file import/export; comparison shows original values, the draft and when changes take effect. Checking an inherited field clears that override on the targets.
- With Curios installed, configure compatible slots, accessory attributes and equipped slot-count bonuses. Slot-count bonuses disappear when the accessory is removed. Without Curios, accessory controls are disabled with an explanatory tooltip.

Saved changes apply to held and equipped items. On Minecraft 26.1.2, enchantability requires restarting the game; food changes apply on save. Converting an item into equipment does not create a custom armor model.

## 简体中文

Item Control 提供物品规则、属性、掉落物保护、清理与创造标签页的可视化管理。

- 物品封禁、统一、标签及物品与饰品属性编辑，可选联动 Curios。
- 自动与手动清理、历史回收及清理保护区域。
- 创造标签页管理及可选 KubeJS、JEI 联动。

支持 Minecraft **1.20.1 / Forge 47.4.2+**（**Java 17**）、**1.21.1 / NeoForge 21.1+**（验证使用 21.1.252，**Java 21**），以及 **26.1.2 / NeoForge 26.1.2.0+**（验证使用 26.1.2.112，**Java 25**）；必需与 Minecraft、加载器匹配的 **KineticCore 26.10.7+（Forge 1.20.1；NeoForge 为 26.10.5+）**。服务端与连接的客户端均安装必需模组。

可选：与 Minecraft 和加载器匹配的 Curios、KubeJS、JEI。

### 物品与饰品属性修改

打开**物品与饰品属性修改**，选择物品后切换分类，分别设置所需能力。任意物品都可以成为近战武器、可穿戴装备、食物或 Curios 饰品，也允许装备同时作为食物。字段留空表示继承原值，最后在主界面点击保存以应用草稿。

- 设置攻击伤害和正数耐久，可将普通物品变成武器。选择装备槽位后可穿戴，目标槽位必须为空。装备同时为食物时，普通右键食用，潜行右键穿戴。
- 可视化选择已注册属性、装备槽位、运算方式及追加／替换／移除模式。食物可设置饥饿值、饱和度、食用时间、状态效果的持续时间／等级／概率，并通过物品选择器指定返还容器。
- **批量、复制与模板**支持多选物品、`@模组` 或 `#标签` 搜索，只应用勾选字段；命名模板支持 JSON 文件导入导出。对照页显示原值、草稿与生效时间。勾选继承状态的字段，会清除目标物品的该项覆盖。
- 安装 Curios 后可设置兼容槽位、饰品属性和佩戴时的槽位数量加成；脱下饰品后槽位加成移除。未安装 Curios 时，相关控件变灰，悬浮提示说明依赖要求。

保存后更新已持有和装备的物品。Minecraft 26.1.2 的附魔能力需要重启游戏，食物修改在保存后生效。将物品变成装备不会自动生成专用盔甲模型。

[GitHub project / 项目仓库](https://github.com/JMK-39/ItemControl) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/itemcontrol)

See the [English Wiki tutorial](https://github.com/JMK-39/ItemControl/wiki/Tutorial) for detailed instructions.

详细用法见[中文 Wiki 教程](https://github.com/JMK-39/ItemControl/wiki/使用教程)。

[Changelog / 更新日志](CHANGELOG.md)
