# 开发指南

## 一、构建

`src/` 是共享代码，`versions/<mc版本>/` 只放每个版本独有的适配代码
（由 `gradle/mod.gradle` 把两者的 `src/client/java` 合并进同一个 sourceSet）。

```powershell
.\gradlew build          # 构建全部版本，产物在 versions/*/build/libs
.\gradlew :v26_3:build   # 只构建 26.3
.\gradlew build --offline # 离线构建（loom 缓存已存在时）
```

当前支持的版本：`26.1.2`、`26.2`、`26.3`（在 `settings.gradle` 里注册）。

---

## 二、新增一个 MC 版本

1. 复制 `versions/26.3/` 为 `versions/<新版本>/`，改 `build.gradle` 里的
   `minecraftVersion` / `fabricApiVersion` / `fabricRendererApiVersion` /
   `malilibVersion` / `modmenuVersion` / `sodiumVersion` / `minecraftDependency`；
2. 在 `settings.gradle` 里加 `include` + `projectDir`；
3. 照着现有 `Platform` 改一份适配实现（**每个方法都要逐个核对**，见下）；
4. 逐个修 `AlphaVertexConsumer` / `SelectiveSubmitNodeCollector` / 各 mixin 的编译错误；
5. 把 `src/client/java/com/selectiverendering/mixin/` 下的共享 mixin
   补进新版本的 `selective-rendering.client.mixins.json`；
6. `.\gradlew :v<新版本>:build`。

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

## 三、移植清单（改动前必读）

**这是本篇最重要的部分。** 本 Mod 的很多逻辑必须在 2~4 个地方各写一遍
（原版 / Sodium / Indigo / 三个 MC 版本），历史上真的漏过。

改渲染管线相关的补丁时，逐条打勾：

```
□ src/client/java/.../mixin/              所有版本共用
□ versions/26.1.2/src/.../
□ versions/26.2/src/.../
□ versions/26.3/src/.../
```

### 容易漏的具体情况

**1. 移动方块（moving_piston）的淡化** —— 三个版本走完全不同的注入点：

| 版本 | 管"半透明" | 管"完全不画" |
|---|---|---|
| 26.1.2 | `BlockFeatureRendererMixin#translucentPass`（改 `hasMaterialFlag`） | 同文件 `translucentBuffer` |
| 26.2 | `SubmitNodeCollectionMixin#forceTranslucentPhase`（改 `hasMaterialFlag`） | `SubmitNodeCollectionMixin` HEAD + 26.2 的 `BlockFeatureRendererMixin` |
| 26.3 | 同上 | 同上 |

**2. 光照"当空气"补丁** —— 四处，判定虽然已收敛到 `airIfHidden()`，但注入点仍要各写一遍：

```
mixin/LightEngineMixin.java                     （光照引擎，三条后端共用）
mixin/BlockModelLighterMixin.java               （原版 AO）
mixin/compat/FabricRendererApiLightMixin.java   （Indigo AO）
mixin/sodium/LightDataAccessMixin.java          （Sodium AO，已上移共享）
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
- 新增/移动 mixin 后，确认**每个**版本的 `selective-rendering.client.mixins.json` 都列了它；
- 若改了 `ModConfig` 的字段名，注意 Gson 用字段名当 JSON key，会破坏旧配置（必要时加 `migrate` 分支并升 `VERSION`）。
