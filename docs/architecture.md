# 架构说明

面向要改代码的人。看完这篇应该能回答："我要改的这个行为，落在哪个文件、要不要改三份"。

---

## 一、源码布局

项目是**多版本**结构：

```
src/                        共享源码（所有 MC 版本共用）
└── client/java/com/selectiverendering/
    ├── SelectiveRendering.java          入口
    ├── SelectiveRenderingManager.java   ★ 全局状态 + getAlpha() 核心判定
    ├── WandHud.java                     手持魔杖时的 HUD（正文，由各版本 GuiMixin 转发调用）
    ├── BlockMatchRule.java              规则字符串的解析与匹配
    ├── Region.java                      选区几何（含 subtract 挖洞）
    ├── RegionMerger.java                散点坐标 → 长方体（贪心合并）
    ├── Preset.java / ModConfig.java     预设 / 磁盘配置
    ├── HiddenSections.java              "哪些 section 含隐藏方块"的记账本
    ├── HiddenRenderTypes.java           本帧需要套 alpha 的 RenderType 标记
    ├── TranslucentRenderTypes.java      为一个 RenderType 找半透明变体
    ├── BufferSourceHooks.java           取 VertexConsumer 时的挂钩
    ├── MovingBlockRenderContext.java    活塞移动中渲染的线程上下文
    ├── BlockChangeRecorder.java         变化记录器
    ├── BlockListMessage.java            屏幕下方的浮动提示
    ├── ModTranslations.java             自带翻译（劫持 ClientLanguage）
    ├── mixin/                           原版渲染管线补丁
    │   ├── ModelBlockRendererMixin      ★ 方块：取消 / 面剔除 / alpha / 换材质
    │   ├── LevelRendererMixin           选区线框（双注入点，兼容方法改名）
    │   ├── FluidRendererMixin           流体：alpha / 面剔除
    │   ├── FluidModelMixin              流体整体挪到半透明层
    │   ├── SectionCompilerMixin         可见性图不再把隐藏方块当不透明
    │   ├── LightEngineMixin             光照引擎把隐藏方块当空气
    │   ├── BlockModelLighterMixin       平滑光照(AO)把隐藏方块当空气
    │   ├── BlockEntityRenderDispatcherMixin  方块实体淡化
    │   ├── MouseHandlerMixin            ★ 魔杖的全部鼠标交互
    │   ├── ClientLevelMixin             给记录器喂方块变化
    │   ├── ClientLanguageMixin          翻译劫持（按 selective_rendering. 前缀过滤）
    │   ├── compat/                      Fabric Renderer API (Indigo) 补丁
    │   └── sodium/                      Sodium 补丁（含 LightDataAccessMixin）
    └── config/                          MaLiLib 配置界面与接线

versions/<mc版本>/           只放该版本独有的适配代码
└── src/client/java/com/selectiverendering/
    ├── compat/Platform.java             ★ 跨版本适配层（鼠标键码、重建入口、…）
    ├── AlphaVertexConsumer.java         强制覆盖顶点 alpha 的装饰器
    ├── SelectiveSubmitNodeCollector.java  方块实体淡化的提交包装器
    └── mixin/                           HUD / 移动方块 / 取 buffer 入口 等
```

> 各版本的 `src/client/resources/selective-rendering.client.mixins.json`
> **要列出该版本生效的全部 mixin**（共享的 + 本版本独有的）。
> 所以把一个 mixin 从 `versions/` 上移到 `src/` 时，json **不需要改**。

---

## 二、核心判定 `getAlpha(state, pos, moving)`

全 Mod 只有这一个"真理来源"，返回：

| 返回值 | 含义 |
|---|---|
| `-1` | 正常渲染 |
| `0` | 完全隐藏（透明度 100%），mixin 直接 `ci.cancel()` |
| `1..255` | 顶点 alpha，保留几何但改颜色 + 挪到半透明层 |

它被区块构建线程、光照线程、渲染线程**并发**调用，因此只读不可变快照、全程不加锁。

两个变体：

- **`getAlpha(...)`**：命中时会往 `HiddenSections` 记账（用于改透明度时增量重建）。
  只有真正参与画面渲染的路径用它。
- **`alphaNow(...)`**：只判定、不记账。光照 / AO 用它在光照线程上以百万次量级调用，
  不该有副作用。对外入口是 `airIfHidden(state, pos)`。

### 修改状态的标准姿势

```java
synchronized (LOCK) {
    ...改 RULES / REGIONS / PRESETS / 各标量字段...
    commit();              // 或 commitAndRemember()
}
...在锁外做重建 / 重算光照等耗时操作...
```

`commit()` = `publish() + persist()`，`commitAndRemember()` 再多一步记住用户输入的原始文本
（防止配置界面回灌触发回调的死循环）。**不要**再单独调 `publish()` / `persist()`。

---

## 三、渲染管线的注入点地图

同一个逻辑要打 3~4 份补丁，因为原版 / Sodium / Indigo 各有各的数据结构：

| 目标 | 原版 | Sodium | Indigo |
|---|---|---|---|
| 方块几何 + alpha | `ModelBlockRendererMixin` | `sodium.BlockRendererMixin` | `compat.FabricRendererApiBlockRendererMixin` |
| 可见性图 | `SectionCompilerMixin` | `sodium.ChunkBuilderMeshingTaskMixin` | — |
| 平滑光照当空气 | `BlockModelLighterMixin` | `sodium.LightDataAccessMixin` | `compat.FabricRendererApiLightMixin` |
| 光照引擎当空气 | `LightEngineMixin` | （共用） | （共用） |
| 流体 | `FluidRendererMixin` + `FluidModelMixin` | `sodium.DefaultFluidRendererMixin` | — |
| 移动方块 | `BlockFeatureRendererMixin` | — | `compat.FabricRendererApiMovingBlockMixin` |
| 方块实体 | `BlockEntityRenderDispatcherMixin` → `SelectiveSubmitNodeCollector` | — | — |

后三项（光照引擎 / 方块实体）三条渲染后端是共用的，因为那里走的还是原版类。

---

## 四、跨版本适配的边界

### 必须留在 `versions/` 的

| 文件 | 原因 |
|---|---|
| `compat/Platform.java` | **每个方法在三个版本里都不一样**：鼠标键码 `0/1` vs `1/3`、`levelRenderer` vs `levelExtractor`、`minecraft.screen` vs `minecraft.gui.screen()`、`KEYSYM` vs `KEYBOARD`、`MaterialInfo` 构造参数个数。这就是它的职责。 |
| `AlphaVertexConsumer.java` | 实现 `VertexConsumer` 接口，接口成员随版本增删（26.3 多一个 `setUv3`）。 |
| `SelectiveSubmitNodeCollector.java` | 实现 `SubmitNodeCollector` 接口，方法集随版本变（三份分别 8.4 / 9.0 / 10.3 KB）。 |
| `mixin/BlockFeatureRendererMixin.java` | 26.1.2 打 `BlockFeatureRenderer.renderMovingBlockSubmits`；26.2+ 打 `MovingBlockFeatureRenderer.buildGroup`。目标类和注入策略都不是一回事。 |
| `mixin/RenderTypeFeatureRendererMixin`<br>`mixin/BufferSourceMixin`<br>`mixin/compat/ImmediatelyFastBufferSourceMixin` | 三个"取 VertexConsumer"的入口各自依赖**只在部分版本存在**的类：`MultiBufferSource` 26.3 已删除；`RenderTypeFeatureRenderer` 26.2+ 才有。共享源码无法引用。 |
| `mixin/FeatureRenderDispatcherMixin` | 差异不只是方法名，**返回类型也不同**：26.1.2 `endFrame` 返回 `void`，26.2+ `prepareFrame` 返回 `PreparedFrame`（26.1.2 里没有这个类）。 |

### 已经在共享里的"跨版本"技巧

- **`LevelRendererMixin`**：方法改名过（`collectPerFrameGizmos` → `collectPerFrameRenderThreadGizmos`），
  参数签名相同，于是写两个 `require = 0` 的 `@Inject` 委托给同一个 `emit()`。
- **`WandHud`**：HUD 正文三份逐字相同，但 mixin 目标类不同（26.1.2 是 `Gui`，26.2+ 是 `Hud`，
  且 26.1.2 里根本没有 `Hud`）。正文抽到共享类，各版本 mixin 只剩转发。
- **可选依赖的 mixin**（Sodium / Indigo / ImmediatelyFast）统一用
  `require = 0` + `remap = false` + 字符串 `targets`，缺席时静默跳过。

### ⚠️ `ImmediatelyFastBufferSourceMixin` 是活代码，别当成历史包袱删掉

对照 ImmediatelyFast 的各分支可以确认它**只对 26.1.2 有效、且正好必要**：

| IF 分支 | 目标 MC | `BatchableBufferSource` |
|---|---|---|
| `origin/26.1`（`1.15.4+26.1`） | **26.1 / 26.1.1 / 26.1.2** | ✅ 有（`MixinRenderBuffers` 用 `@Redirect` 把某个 `BufferSource` 换成它，且**覆写了 `getBuffer`**） |
| `origin/26.2` 起（`1.17.2+26.3`） | 26.2+ | ❌ 已删除，批处理改成只把 `RenderTypeFeatureRenderer$Group` 的 `canReorder` 改成 true |

也就是说：

- 26.1.2 + IF 1.15.x：IF 的子类覆写了 `getBuffer`，虚分派轮不到我们注入在父类方法体里的代码，
  少了这个补丁 → **被淡化的方块实体和移动中的方块完全不淡化**（地形不受影响，那条路走
  `ModelBlockRendererMixin`），且不报错，很难排查；
- 26.2 / 26.3：IF 已经不动 `BufferSource`，取 buffer 的入口本来就是
  `RenderTypeFeatureRenderer.getVertexBuilder`，**天然不需要补丁**，所以那两个版本里没有这个文件是对的。

顺带记录另一种可行思路（Lucidity 就是这么做的，见 `ControllableTransparentBuffersWrapper`）：
不注入 `getBuffer`，而是在调用点把整个 `BufferSource` 对象包一层壳再传给渲染器。
好处是天然免疫"provider 被换实现"，代价是要枚举所有渲染调用点（BE dispatcher / Sodium 的
`renderBlockEntity` / 粒子 / FeatureRenderDispatcher…）。我们选的是"枚举 provider 实现类"这条路。

⚠️ 用"两个 `require = 0` 注入点兼容改名"这种写法时，要假设**将来某天两个方法名同时存在**，
那时逻辑会被执行两次。`LevelRendererMixin` 已在注释里标明（后果只是描边变粗）。

---

## 五、配置层（MaLiLib）

`config/ModConfigs.java` 是接线中心，注意它的**防回环机制**：

把 manager 的值回灌到控件（`syncFromManager()`）会触发控件的 `setValueChangeCallback`，
回调又会去改 manager。所以用一个 `syncing` 标志在回灌期间屏蔽回调（见 `write(...)`）。
**新增配置项时务必照这个模式接线**，否则改一次会触发两次重建。

配置项本身只是"视图"，权威数据源永远是 `SelectiveRenderingManager`；
落盘由 `ModConfig.save()` 防抖异步完成。
