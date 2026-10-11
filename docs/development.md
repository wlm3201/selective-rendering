# 开发指南

## 一、构建与多版本机制（Stonecutter）

项目用 [Stonecutter](https://stonecutter.kikugie.dev/) 管多版本：**只有一份源码 `src/`**，
`settings.gradle.kts` 里声明的每个 MC 版本是一个"节点"（`versions/<版本>/`，只放构建产物）。

```
settings.gradle.kts      声明版本列表 + vcsVersion
stonecutter.gradle.kts   控制器：stonecutter active "26.3"（当前展开的版本）、让 .json 也被预处理
build.gradle.kts         所有节点共用的构建脚本；版本相关的依赖坐标在 versionDeps 表里
src/                     唯一的一份源码（含 //? if 条件）
versions/<ver>/build/    各节点的构建产物（含 Stonecutter 处理后的源码，不要手改）
```

### 它是怎么工作的

- 构建某个节点时，Stonecutter 把 `src/` 里所有 `//? if ... {` 条件按该节点的版本处理一遍，
  输出到 `versions/<ver>/build/generated/stonecutter/`，再拿去编译。**不存在"文件覆盖"**。
- 你在 `src/` 里看到的是 **`active` 版本展开后的样子**：当前版本的分支是正常代码，
  其它分支被 `/* ... */` 注释掉了。切换 `active` 会改写这些注释（即会改动 `src/` 里的文件）。
- `settings.gradle.kts` 的 `vcsVersion`（现为 26.3）是 **签入仓库的状态**：提交前请把 `active` 切回它
  （`stonecutter.gradle.kts` 里 `stonecutter active "26.3"` 必须与它一致），否则 diff 会被一大堆注释翻转淹没。

```powershell
.\gradlew build                                  # 构建全部版本，产物在 versions/*/build/libs
.\gradlew :26.2:build                            # 只构建 26.2（项目名就是版本号）
.\gradlew "Set active project to 26.2"           # 把 src/ 展开成 26.2 的样子（IDE 里就按 26.2 解析）
.\gradlew "Set active project to 26.3"           # 改完记得切回
.\gradlew :26.3:runClient                        # 运行某个版本的客户端
```

当前支持的版本：`26.1.2`、`26.2`、`26.3`。

### 条件写法

```java
//? if >=26.2 {
return minecraft.gameRenderer.mainCamera();
//?} else {
/*return minecraft.gameRenderer.getMainCamera();
*///?}
```

- 谓词：`>=26.2`、`<26.3`、`>=26.2 && <26.3`；可以接 `//?} else if <26.2 {`。
- **不用手写 `/* */`**：两个分支都写成普通代码，运行一次 `Set active project to ...`，
  Stonecutter 会自动把非当前版本的分支注释掉。
- 注释里的 `/** */` 会被转义成 `/^* ^/`，这是正常的。
- **`.mixins.json` 不放条件**，必须是纯 JSON。
- **mixin 只在某些版本有目标**（如 `BufferSourceMixin` 只有 26.1.2、`RenderTypeFeatureRendererMixin` 26.2 起才有）：
  json 里照常登记；源文件里真正的 mixin 用 `//? if` 包起来，另一个分支换成 dummy mixin ——
  `@Mixin(Minecraft.class)` 加一个空类体，不注入任何方法。这样所有版本的类名和 json 都一致。

---

## 二、新增一个 MC 版本

1. `settings.gradle.kts` 的 `versions(...)` 里加上版本号；若它是新的最新版，同时改 `vcsVersion`
   和 `stonecutter.gradle.kts` 的 `active`（这会让所有 `//? if` 的"展开分支"整体翻转，是一次大 diff）；
2. `build.gradle.kts` 的 `versionDeps` 里加一行依赖坐标（Fabric API / MaLiLib / Mod Menu / Sodium / Renderer API / MC 版本范围）；
3. `Set active project to <新版本>`，然后 `.\gradlew :<新版本>:compileClientJava`，
   逐个修编译错误：差异用 `//? if` 包起来；
4. 对照下表核对 `Platform` 的每个成员；
5. 该版本特有的 mixin 照常登记进 json，其它版本里对应的类写成 dummy mixin；
6. `.\gradlew build`。

### `Platform` 逐项对照（最容易漏）

| 成员 | 26.1.2 | 26.2 | 26.3 |
|---|---|---|---|
| `MOUSE_LEFT` / `MOUSE_RIGHT` | `0` / `1` | `0` / `1` | **`1` / `3`** |
| `ready()` | `levelRenderer != null` | 同左 | 同左 |
| `markSectionWithNeighbors` | `LevelRenderer.setSectionDirtyWithNeighbors` | `levelExtractor.setSectionDirtyWithNeighbors` | 同 26.2 |
| `rebuildAll()` | `LevelRenderer.allChanged()` | `levelExtractor.allChanged()` | 同 26.2 |
| `screen()` / `setScreen()` | `minecraft.screen` | `minecraft.gui.screen()` | 同 26.2 |
| `isKeyDown` | `InputConstants.isKeyDown(window, key)` | 单参 `isKeyDown(key)` | 同 26.2 |
| `keyName` | `Type.KEYSYM` | `Type.KEYSYM` | **`Type.KEYBOARD`** |
| `translucentMaterial` | `MaterialInfo` 6 参 | 同 26.1.2 | **8 参**（多 `itemGlintRenderType` / `shadeDirectionOverride`） |

---

## 三、改动前必读

本 Mod 的很多逻辑必须在 2~3 个**注入目标**里各写一遍（原版 / Sodium / Indigo），历史上真的漏过。
版本差异现在由 `//? if` 在同一个文件里解决，不用再"改三份"；但**改完要在三个节点上都编译一遍**：

```powershell
.\gradlew :26.1.2:compileClientJava :26.2:compileClientJava :26.3:compileClientJava
```

### 容易漏的具体情况

**1. 移动方块（moving_piston）的淡化** —— 各版本走完全不同的注入点（都在 `BlockFeatureRendererMixin` / `SubmitNodeCollectionMixin` 里用 `//? if` 区分）：

| 版本 | 管"半透明" | 管"完全不画" |
|---|---|---|
| 26.1.2 | `BlockFeatureRendererMixin#translucentPass`（改 `hasMaterialFlag`） | 同文件 `translucentBuffer` |
| 26.2 | `SubmitNodeCollectionMixin#forceTranslucentPhase`（改 `hasMaterialFlag`） | `SubmitNodeCollectionMixin` HEAD + 26.2 的 `BlockFeatureRendererMixin` |
| 26.3 | 同上 | 同上 |

**2. 光照"当空气"补丁** —— 四处，判定已收敛到 `LightSuppression.airIfHidden()`，但注入点仍要各写一遍：

```
mixin/lighting/LightEngineMixin.java            （光照引擎，三条后端共用）
mixin/lighting/BlockModelLighterMixin.java      （原版 AO）
mixin/compat/FabricRendererApiLightMixin.java   （Indigo AO）
mixin/sodium/LightDataAccessMixin.java          （Sodium AO）
```

**3. 方块几何 + alpha**：`ModelBlockRendererMixin` / `sodium.BlockRendererMixin` /
`compat.FabricRendererApiBlockRendererMixin`。改"alpha 的有效区间"时三处要一致
（历史上 Sodium 用的是 `alpha >= 0`，原版是 `0 < alpha < 255`，不一致过）。

**4. 语言文件**：新增词条要同时改 `zh_cn.json` **和** `en_us.json`。

---

## 四、提交前自检

```powershell
.\gradlew build --offline -x test
```

- 三个版本都要能编译过；
- `stonecutter.gradle.kts` 的 `active` 已切回 `vcsVersion`（26.3）；
- 新增 mixin 后，确认 `src/client/resources/selective-rendering.client.mixins.json` 列了它（只有一份）；
- 若改了 `ModConfig` 的字段名，注意 Gson 用字段名当 JSON key，会破坏旧配置（必要时加 `migrate` 分支并升 `VERSION`）。
