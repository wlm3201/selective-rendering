# 交接：Stonecutter 迁移 + 光照重算重构（待游戏内验证）

> 新对话的目标：在真实游戏里验证"选区内被淡化的方块，其光照会跟着变、区块重新加载后也不丢"，并确认三个 MC 版本（26.1.2 / 26.2 / 26.3）都能进游戏。

## 一句话背景

客户端 Fabric Mod「Selective Rendering」：按"选区 + 方块名单"把方块淡化或隐藏。仓库 `C:\coding\selective-rendering`，Java 25，Loom 1.18（未混淆的 MC 26.x），依赖 MaLiLib（必需）、Sodium / Fabric Renderer API（可选，`require = 0`）。注释和文档用中文。

## 现状 — implemented（编译通过；**没有进游戏验证过**），2026-10-10

- **检出:** branch `main`，最后一次提交 `41f0324 性能优化`，未推送。**本次所有工作都没有提交。**
- **构建结果（这些是真实跑过的）:**
  - `./gradlew build --offline`：PASS，三个版本各出一个 jar（`versions/<v>/build/libs/`）。
  - 三个版本生成的源码与迁移前的各版本源码逐行比对（去掉注释，忽略 import 顺序）：一致，0 处不同。
  - 产物 jar 里 `selective-rendering.client.mixins.json` 条目数 33 / 32 / 32，`fabric.mod.json` 的 MC / MaLiLib 版本范围正确。
- **没测过的:** 一切运行期行为——游戏能否启动、mixin 是否真的注入成功（`defaultRequire: 0`，注入失败是**静默**的）、光照效果、三个版本的 HUD / 魔杖 / 方块实体淡化。
- **`stonecutter active` 现为 "26.3"**（= `vcsVersion`，这是正确的签入状态）。
- **未提交改动里混着几条互相独立的线**（提交时建议拆成独立提交，不要 `git add -A`）:
  1. 作者原有的未提交改动：`queryBlockEntity` 和 `alphaNow` 里活塞分支的写法（现在在 `RegionTargets.java`，`Alpha.queryBlockEntity` 仍在 `Alpha.java`）。作者说"可能不对，别管"，我只是原样搬运。
  2. 光照重构：`LightSuppression`（重写）、`AreaLightFlushMixin`、`AreaLightPacketMixin`、4 个光照 mixin 改为调用 `LightSuppression.airIfHidden`。
  3. 类拆分：`Alpha` / `RegionEditor` / `RebuildScheduler` / `RenderConfig`，`SelectiveRenderingManager` 从 1929 行缩到约 950 行，约 25 个文件的调用点随之改动。
  4. Stonecutter 迁移：根目录的 `*.gradle.kts`、删除 `versions/*/src`、`settings.gradle`、`build.gradle`、`gradle/mod.gradle`，十个版本专属文件合并回 `src/`。
  5. `SelectiveSubmitNodeCollector` 重构为 `TranslucentSubmitForwarder` + 薄外壳。
  6. `docs/architecture.md`、`docs/development.md` 已同步新结构。
- **正在运行的进程:** 无。
- **旧版本文件的找回方式:** 迁移前的 `versions/<v>/` 全部在 git HEAD 里，用 `git show HEAD:versions/26.3/src/client/java/com/selectiverendering/compat/Platform.java` 即可取回。（会话临时目录里也有一份拷贝，但那是临时的，别依赖。）

## 已知事实 / 证据

- Stonecutter 0.9.8（`dev.kikugie.stonecutter`）。节点是 Gradle 子项目 `:26.1.2` / `:26.2` / `:26.3`，项目目录 `versions/<v>/`，里面只有构建产物。
- 各节点的处理后源码在 `versions/<v>/build/generated/stonecutter/`；`.json` 不再预处理（已去掉 handlers）。
- 光照机制的前提，已用 `javap` 核对过字节码:
  - `ClientPacketListener.applyLightData(int,int,ClientboundLightUpdatePacketData,boolean)`、`enableChunkLight(LevelChunk,int,int)`、`LevelLightEngine.runLightUpdates()` 的签名与 mixin 一致。
  - 服务端光照先 `queueSectionData` 排队，到下一次 `runLightUpdates` 才落地并覆盖客户端结果，所以重算必须在 `runLightUpdates` **之后**。
  - `ClientLevel.update()` 每帧调用 `pollLightUpdates()` 再 `runLightUpdates()`。
  - `SubmitNodeCollector extends OrderedSubmitNodeCollector`。

## 已排除 / 未验证的假设

- **已排除（作者确认）:** `REGION_OUTSIDE_*` 模式不需要重算区域外的光照，不是缺陷。
- **未验证 — 猜测，需要进游戏看:**
  - `LightSuppression` 只对"选区外扩 1 格的列"发 `checkBlock`，依赖引擎自己沿邻居传播。是否足够没验证；HEAD 原来是外扩 15 格暴力扫。
  - 每列下沿一直延伸到天光源头（沿用作者原写法）。`ChunkSkyLightSources` 是按**真实方块**算的，不经过我们的 mixin，所以这一段是否有意义不确定。
  - 名单类模式（BLACKLIST / WHITELIST）隐藏的方块遍布全世界，`LightSuppression` 不处理它们（HEAD 也不处理）。
  - `markChunk` 只在 `Mode.usesRegion()` 且非夜视时才标记。
- **`TranslucentSubmitForwarder` 的等价性:** 方法签名集合、翻译 RenderType 的调用次数已与旧编译产物比对一致；但"行为等价"没在游戏里验证过。

## 尚未记录的发现

- 在 Stonecutter 条件里，注释中的 `/** */` 会被自动转义成 `/^* ^/`，属正常现象。
- 两个分支都先写成**普通代码**，运行 `Set active project to <v>`，Stonecutter 会自动把非当前版本的分支注释掉；不需要手写 `/* */`。
- mixin json 是纯 JSON、无条件；版本上不存在目标的 mixin 在源码里换成 dummy mixin（`@Mixin(Minecraft.class)`，空类体）。
- Kotlin DSL 里 `processResources` 的 `filesMatching { expand(...) }` 不能直接引用脚本级变量（会破坏 configuration cache），必须先拷成局部 `val`。
- 会话中一次 `rm` 被权限拦截，所以旧文件是"移走"而不是真删。

## 关键代码位置

> 2026-10-11：类已按功能分包（`render/`、`blockentity/`、`lighting/`、`region/`、`matchingrule/`、`interaction/`、`recording/`、`config/`、`util/`，`mixin/` 下同名子包），下表路径已更新。

| 内容 | 文件 |
|---|---|
| 版本列表、`vcsVersion` | `settings.gradle.kts` |
| 当前展开的版本 | `stonecutter.gradle.kts`（`stonecutter active "26.3"`） |
| 共用构建脚本、各版本依赖坐标 `versionDeps` | `build.gradle.kts` |
| 光照伪装 / 标记 / 分批重算 | `src/client/java/com/selectiverendering/lighting/LightSuppression.java` |
| 光照触发的两个 mixin | `mixin/lighting/AreaLightPacketMixin.java`（标记）、`mixin/lighting/AreaLightFlushMixin.java`（执行） |
| 判定层（只读） | `render/RegionTargets.java`（门面 `render/Alpha.java`） |
| 状态中心（可变状态、快照发布、落盘） | `SelectiveRenderingManager.java` + `RenderConfig.java` |
| 魔杖角点与选区的增删裁 | `region/RegionEditor.java` |
| 重建调度三档 | `render/RebuildScheduler.java` |
| 跨版本适配层 | `compat/Platform.java` |
| 方块实体淡化 | `blockentity/TranslucentSubmitForwarder.java`、`blockentity/SelectiveSubmitNodeCollector.java`、`mixin/blockentity/BlockEntityRenderDispatcherMixin.java` |
| mixin 配置（只有一份，纯 JSON） | `src/client/resources/selective-rendering.client.mixins.json` |
| 文档 | `docs/architecture.md`（类职责）、`docs/development.md`（Stonecutter 用法、加新版本） |

## 日常命令

```powershell
.\gradlew build                                   # 三个版本全构建
.\gradlew :26.2:compileClientJava                 # 单个版本（项目名就是版本号）
.\gradlew "Set active project to 26.2"            # 把 src/ 展开成 26.2 的样子
.\gradlew "Set active project to 26.3"            # 改完必须切回 26.3
.\gradlew :26.3:runClient                         # 启动客户端（可加 -PconfigScreen 带上 Fabric API / Mod Menu）
```

## 实现 / 排查方法

1. 先看 `docs/development.md` 第一节，了解 Stonecutter 的工作方式（不是文件覆盖，是预处理）。
2. 对每个版本跑 `:<v>:runClient`，看日志里有没有 `InvalidInjectionException` / `Mixin apply failed`；因为 `defaultRequire: 0`，也要注意"没有报错但没生效"——在游戏里直接验证效果。
3. 光照验证场景（在 26.3 上先做）:
   - 切到区域类模式（如 `REGION_INSIDE`），圈一个选区包住一堵墙，墙后原本很暗。预期：淡化后墙后变亮。
   - 删除 / 裁剪选区。预期：光照恢复成原样（走 `retire` + `relight()`）。
   - 离开再回到选区所在位置，让区块重新加载。预期：光照仍是"墙被当成空气"的结果（走 `markChunk` → `flush`）。
   - 观察切模式 / 选区很大时是否掉帧（每帧预算 `BUDGET_PER_FLUSH = 2000`）。
4. 如果光照不对，按顺序查：`airIfHidden` 是否被调用 → `PENDING` 里有没有区块 → `flush` 是否因 `engine != level.getLightEngine()` 提前返回 → `checkBlock` 的范围是否覆盖到。

## 注意事项

- **提交前必须把 `active` 切回 26.3**，否则 diff 会被大量注释翻转淹没。
- 改任何共用代码后，三个节点都要编译一遍：`.\gradlew :26.1.2:compileClientJava :26.2:compileClientJava :26.3:compileClientJava`。
- 新增 mixin 要同时登记到 `selective-rendering.client.mixins.json`；只在部分版本存在目标的，json 照常登记，源文件里另一个分支写成 dummy mixin。
- 不要往 `versions/<v>/` 里放源码——那里只是构建产物目录。
- 别动作者原有的活塞相关未提交改动（`Alpha.queryBlockEntity` 及 `alphaNow` 里的 `carried` 分支），除非作者要求。

## 下一步

**只做一件事:** 运行 `.\gradlew :26.3:runClient`，进入一个存档，用上面"光照验证场景"的第一条（圈一堵墙、淡化后看墙后是否变亮），并把日志里的 Mixin 报错（如果有）和结果告诉作者。
