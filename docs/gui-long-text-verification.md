# GUI verification / 界面验证

## English

2026-10-04: bounded custom text in 11 GUI files. Shared Core scrolling APIs, including page-coordinate clipping for scaled merge-source names; no new loader branches or language keys. Minimum Core version is 26.10.4.

- Offline buildAll and runtimeValidationJar passed for both enabled nodes. Architecture, reobfuscation/reference and language checks passed; both nodes checked 12 Mixins with zero problems. Source language keys: 497 per locale; NeoForge overrides: 15; packaged keys: Forge 497, NeoForge 502. No ordinary unit tests are present.
- The existing NeoForge 21.1.252 client opened 25 actual states: six item-property categories, protection list/new/existing modal, direct immunity, damage types, banned items/mods/tags, collapsed/expanded merges, item tags/suggestions, cleaner inventory, three cleaner-rule modes, creative tabs and valid/invalid components. English/Chinese at 854×480 and 1536×864, automatic GUI scale; extended translations captured twice.
- Full run: ITEM_GUI_PASS pages=25 captures=150 failures=0. Follow-up with repeated long item names and autocomplete IDs: 30 captures, zero failures. Screenshots were reviewed separately; long names, scaled sources, counts, expand symbols and neighboring fields stay separated.
- Coverage limits: the asynchronous search-cache callback replaced compact mod-autocomplete data in four follow-up captures (0-12, 2-12, 4-12 start/scroll). Wide mod autocomplete and compact tag autocomplete exercise the same renderer successfully. A hover tooltip obscures part of the left merge source in 4-15-scroll; other merge captures show the bounded source. New-modal start frames sometimes catch the parent opening animation above the screen; the settled stress frame is clear. No claim of additional cases is made.
- First fixture run failed when modifying an immutable suggestions list; the harness now supplies its own mutable draft. This was a fixture issue, not a production failure. Local drafts and a local cleaner container were used without save or inventory-changing callbacks; the original nullable creative-tab editing state is restored.
- Original options.txt bytes restored: SHA256 50498E5F02C94CAADB7B7752AE4F4C304CF3463FD27382E950FA6348F70AD919. Test clients stopped normally, memory settings unchanged. Production JAR SHA256 38B5B32294892A1B55D9F0A34B80C1CB7F4CDC47DEB99BC00632B6C23E52E017 matches D:/NEWMODS. Validation classes are excluded from release JARs.
- Numeric overlays and HUD text are unchanged. No Forge game run, per user instruction. The shared Core tall-tooltip limitation is tracked in ContentStudio/docs/tooltip-height-follow-up.md.

Evidence: .gradle/gui-long-text-20261004/build.log, build-fixture-overflow.log, client-full.log, client-overflow.log and phase-numbered PNGs.

## 简体中文

2026-10-04：修复11个界面文件中的固定区域文字，使用核心滚动API；合并来源保留缩放，在页面坐标中裁剪。未新增加载器分支或修改语言键，最低核心版本为26.10.4。

- 两个启用节点均通过离线buildAll与测试JAR构建，架构、重混淆/引用及语言检查通过；各12个Mixin，零问题。中英文源码各497键，NeoForge覆盖各15键；打包后Forge各497键、NeoForge各502键。没有普通单元测试。
- 使用现有NeoForge21.1.252客户端打开25种真实状态，覆盖物品属性六分类、保护列表/新建/已有弹窗、直接免疫、伤害类型、禁用物品/模组/标签、折叠/展开合并、物品标签/补全、清理容器、三种清理规则、创造标签页及有效/错误组件。中英文分别检查854×480和1536×864，GUI自动缩放；长翻译拍摄两帧。
- 完整运行150张截图，零执行失败；加长物品名称与补全ID后补测30张，零执行失败。截图单独检查，长名称、缩放来源、计数、展开符号与相邻控件保持分离。
- 验证局限：异步搜索缓存回调覆盖了补测中的4张紧凑模组补全截图（0-12、2-12、4-12开始/滚动）；宽窗口模组补全与紧凑标签补全已验证同一绘制方法。4-15滚动帧的悬浮提示遮挡部分来源，但其余合并截图可检查来源边界。新弹窗开始帧偶尔捕获父页面打开动画，稳定后的长文本帧显示正常。
- 首次测试因修改不可变建议列表失败，已改为测试自有可变草稿，不是产品错误。测试只操作本地草稿/清理容器，不调用保存或修改库存操作；原本可为null的标签编辑状态也恢复。
- options.txt逐字节恢复，SHA256为50498E5F02C94CAADB7B7752AE4F4C304CF3463FD27382E950FA6348F70AD919；测试客户端正常退出，内存设置未变。正式JAR与D:/NEWMODS哈希一致，正式产物不含验证类。
- 数值叠加与HUD保持原样；按用户要求未启动Forge游戏。核心过高悬浮提示仍单独交接给核心维护者。

本机证据见.gradle/gui-long-text-20261004。
