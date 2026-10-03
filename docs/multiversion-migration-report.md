# ItemControl 多版本迁移验收（2026-10-03）

已启用 Forge 1.20.1 和 NeoForge 1.21.1，共享同一份 Stonecutter 源码。26.1.2 仅预留，没有启用或生成该版本发布包：目前缺少对应的 KineticCore 产物，第三方依赖也需要按该版本重新确认。

## 构建与产物

采用 Java 21、Gradle 9.8.0、Stonecutter 0.9.8、ModDevGradle 2.0.148。根构建由 `stonecutter.gradle` 控制，节点使用 `build.forge.gradle` / `build.neoforge.gradle`，共同配置在 `gradle/kinetic-node.gradle`。包装器、守护进程配置、检查脚本和两份发布工作流已同步迁移。

一次构建两个版本：

```powershell
./gradlew.bat buildAll --offline
```

产物位于 `D:/NEWMODS`：

| 文件 | SHA-256 |
| --- | --- |
| `itemcontrol-forge-1.20.1-26.10.3.jar` | `644F90E7B8D3360F8DAF21D52B1DD5347EEA12966090C0EA4967521C6C4F3D60` |
| `itemcontrol-neoforge-1.21.1-26.10.3.jar` | `007B9E02FA62215ABAD72C1160C1B4B5E13CF11A83A840AC2ED9F39C5411B54F` |

保留进入迁移前的 Java 21、核心版本固定值 `26.10.3` 和依赖范围 `[26.10.3,)`。原有六个文件的修改已保留，原始补丁与基线 JAR 在本地 `.gradle/migration/` 中。

第三方固定坐标优先解析 Gradle 用户目录上一级的 `libs`。核心单独验证 loader、Minecraft 和版本，优先本地匹配产物，保留联网比较最新版及离线回退。Neo JEI 使用现有游戏安装中的 19.57.0.450，已经复制到本地依赖池；KubeJS 所需的 Better Advanced Tooltips 显式列为依赖，避免 flatDir 缺少传递元数据。

## 版本行为

Forge 保持原有 NBT 物品规则和配置字段。Neo 使用原生 `id[components]`，拒绝旧 `id{NBT}`，不转换旧物品写法。Neo 创造标签项使用 `components` / `matchComponents`：

```json
{"id":"minecraft:diamond_sword","components":"[damage=5,custom_data={CaseKey:1}]","matchComponents":true}
```

组件匹配保留值的大小写、显式默认值和移除约束；`custom_data` 内部保持子集匹配。启动阶段保留原始约束，世界注册表就绪后严格解析，支持附魔等动态注册表组件。世界 SavedData 使用带注册表的物品组件序列化；大数量回收堆叠单独保存真实数量。

事件、KubeJS、JEI、物品属性 Mixin、创造标签、回收数据和编辑入口已按 Neo API 移植。组件编辑器使用核心文本控件，保存前解析原生组件语法。

## 验收结果

| 检查 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| 最终 `buildAll --offline` | 通过 | 通过 |
| 架构检查、核心引用检查 | 通过，0 引用问题 | 通过，0 引用问题 |
| Mixin 目标检查 | 12 个，0 问题、0 备注 | 12 个，0 问题、0 备注 |
| 发布包检查 | 172 个类，class 65，2 份 Mixin 配置 | 176 个类，class 65，2 份 Mixin 配置 |
| loader 元数据与核心依赖范围 | 通过 | 通过 |
| Mixin 发布配置 | JAVA_17、refmap、MixinConfigs 齐全 | Neo 元数据登记 Mixin，发布包检查通过 |
| 游戏内运行 | 按用户要求跳过 | PCL2 启动现有版本，进入现有世界通过 |

Forge 基线的原有 170 个类均保留；对 `javap -c -p` 输出归一化常量池编号后，原有类指令与基线完全一致。新增两个普通业务辅助类。除清单、loader 元数据、pack 元数据和 refmap 外，基线的 7 份资源（包括语言文件）内容一致。

Neo 最终验证在 `F:/game/异界战斗幻想/.minecraft/versions/1.21.1-NeoForge_21.1.252` 完成，没有另外下载游戏版本，也没有调整内存。2026-10-03 18:18:02 的日志包含组件 schema 通过、以下五组通过和最终 `ITEMCONTROL_VALIDATION_PASS`：

- 组件格式、大小写、默认值、组件移除约束、非法旧 NBT 和尾随文本拒绝；旧创造标签 NBT schema 拒绝。
- 百万数量回收插入与聚合、不同组件区分、SavedData 保存/加载、数量网络编码以及原版组件流编码往返。
- 食物营养与饱和系数、现有堆叠属性、属性操作枚举和未修改装备槽保留。
- 移除组件及应用校验补丁后重新检查合并规则。
- 启动阶段构造的附魔封禁、保护规则和创造标签配置进入世界后仍然有效。

已实际打开安装模块列表、物品配置页、封禁物品网格和创造库存/JEI 界面，组件术语正常显示。组件编辑器的鼠标保存流程、回收站菜单直接打开、KubeJS 三类脚本回调和独立多人服务器未逐项实测；回收容器与网络数据由运行时夹具验证。没有保存临时规则。

只读审查返回四项重要问题：Neo 容器数量截断、启动时丢弃动态组件规则、组件移除/校验补丁漏查合并、仅修改营养值时饱和系数变化。全部先在游戏中复现，再修正并验证通过；另补充修正创造标签的启动加载。审查代理在完整终审报告前达到使用限额，不将其记录为完整终审。没有发现新的 Mixin 目标问题。

验证记录保存在本地 `.gradle/migration/`：`final-build.log`、`forge-baseline-result.json` 和 `runtime-evidence/neo-*-red.log` / `neo-final-green.log`。运行夹具位于 `src/test`，通过 `:1.21.1-neoforge:runtimeValidationJar` 单独打包，不进入发布 JAR。

## 交付状态

最终 Neo JAR 已装入上述现有测试版本，安装文件哈希与 `D:/NEWMODS` 产物一致。测试客户端已关闭；测试夹具已移出 mods，放入该版本的 `codex-migration-backup/itemcontrol-validation-20261003/`。Forge 整合包未替换或启动。

保留本地分支 `codex/multiversion-migration-20261003`，只进行本地提交，不推送、不发布 Release。后续启用 26.1.2 时，需要先取得匹配的核心和依赖，再完成该版本 API 移植与单独验收。
