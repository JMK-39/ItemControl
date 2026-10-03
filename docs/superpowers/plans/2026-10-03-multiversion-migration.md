# ItemControl Multiversion Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 构建并验证 ItemControl 的 Forge 1.20.1 / NeoForge 1.21.1 发布包。

**Architecture:** 一个 Stonecutter 源码树、两个独立节点、共享构建检查；版本差异通过条件块和包名替换实现。原生组件规则与编辑器位于普通业务包，Forge 保持原有 NBT 逻辑。

**Tech Stack:** Java 21, Gradle 9.8.0, Stonecutter 0.9.8, MDG 2.0.148。

**Spec:** `docs/superpowers/specs/2026-10-03-multiversion-migration-design.md`

## Global Constraints

- 核心范围 `[26.10.3,)`；产物 `itemcontrol-<loader>-<minecraft>-<version>.jar`。
- Forge 1.20.1 使用 NBT；NeoForge 1.21.1 使用 components，不转换旧 NBT 写法。
- 本地依赖优先；26.1.2 仅预留；PCL2 使用已有版本与内存设置。
- 保留现有改动；只本地提交，不推送或发布。

## Review Focus

- 带组件值的规则保留大小写，拒绝旧 NBT 与尾随垃圾。
- 注册表上下文变化后规则重新解析，支持附魔等动态注册表组件。
- 合并物品保留未指定的源数据；创造标签配置使用新字段且保存校验真实语法。
- 回收物品保存/加载/网络往返保留组件与数量。
- Mixin 描述符与原版方法一致，包内辅助类没有被外部加载。

### Task 1: 多节点构建

**Files:** settings.gradle, stonecutter.gradle, build.forge.gradle, build.neoforge.gradle, gradle/kinetic-*.gradle, versions/*/gradle.properties, wrapper, workflows。
**Interfaces:** `kineticConfigureReleaseJar(TaskProvider<Jar>)` 接收 loader 发布任务；构建检查消费两个最终 JAR。

- [x] 移植已验证构建脚本，保留 Forge 依赖和 Mixin 配置，先只启用 Forge。
- [x] 运行 `gradlew :1.20.1-forge:build --offline`；记录既有 Mixin 目标问题，确认发布检查通过。
- [x] 对比基线类列表/资源/语言，验证 Java 65、JAVA_17、refmap、MixinConfigs。

### Task 2: NeoForge 原生物品与接口

**Files:** src/main/java/dev/xyat/itemcontrol 下版本 API 调用；新增 item/data 与 client/gui 组件工具；versions/1.21.1-neoforge/lang；src/test/java/dev/xyat/itemcontrolvalidation。
**Interfaces:** `ItemData` 提供 delimiter/empty/format/parse/patch 子集匹配；规则、编辑器、标签页消费同一接口。

- [x] 先编写运行时回归断言：组件字段、格式保留、非法 NBT 拒绝、组件子集/移除/注册表上下文、标签页和回收往返。
- [x] 启用 Neo 节点并按编译错误逐项移植事件、SavedData、KubeJS、JEI、Mixin 描述符。
- [x] 在现有 PCL2 Neo 世界运行回归夹具，记录预期失败，再完成规则/编辑器实现并验证通过。
- [x] 运行 `gradlew buildAll --offline`，两个节点检查通过。

### Task 3: 发布验证与交付

**Files:** gradle/Verify-ReleaseJar.ps1, docs/multiversion-migration-report.md。
**Interfaces:** 发布检查消费最终 JAR；测试夹具独立打包，不能进入发布包。

- [x] 验证两节点 JAR 内容、核心方法引用、Mixin 目标检查和 Forge 基线对比。
- [x] 通过 PCL2 测试已有 Neo 安装；按用户补充要求跳过 Forge 游戏运行，仅保留构建与基线检查。界面检查不保存临时规则。
- [x] 请求独立只读审查，处理实质问题并复验；移出测试夹具。
- [x] 更新验收报告和计划，`git diff --check` 通过后本地提交。
