# ItemControl 多版本迁移设计

沿用用户已经确认的 KineticCore / KineticArmory 架构，在当前 ItemControl 检出目录的独立本地分支执行，保留已有六个文件的修改。原始补丁与 Forge 发布包保存在 `.gradle/migration/`。

启用 Forge 1.20.1 和 NeoForge 1.21.1，预留 26.1.2 节点；缺少对应核心及第三方依赖时不启用该节点。采用 Gradle 9.8.0、Stonecutter 0.9.8、MDG 2.0.148、Java 21。统一命名 `itemcontrol-<loader>-<minecraft>-<version>.jar`，由 `buildAll` 构建，输出 `D:/NEWMODS`。保留核心依赖下限 `[26.10.3,)`。

Forge 保持玩法、配置字段、NBT 规则、资源和语言。Neo 使用原生 `id[components]` 写法，不兼容或升级旧 `id{NBT}`；创造标签配置使用 components/matchComponents 字段。物品匹配维持子集规则，custom_data 内部使用 NBT 子集比较，其他组件比较组件值。编辑器使用核心文本组件并验证原生物品语法。世界 SavedData 仍可使用 NBT 容器，但物品存取必须使用带注册表上下文的组件序列化。

适配物品属性和保护 Mixin、资源重载、创造标签页、回收 SavedData/容器、事件、KubeJS 与 JEI。辅助业务代码放在 Mixin 包外。网络继续使用核心抽象。依赖本地优先，核心严格筛选 loader/MC，第三方固定坐标仅在本地缺少时从官方获取。

通过离线节点构建、架构检查、Mixin 目标检查、核心方法引用检查与发布包内容验证。Forge 对比原始 JAR；Neo 在现有 PCL2 安装中验证物品规则、组件编辑、回收和创造标签页。使用用户已有的游戏版本，不下载独立客户端，不改内存，不结束用户游戏进程。只本地提交，不推送或发布。
