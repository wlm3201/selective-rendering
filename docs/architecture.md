# 架构说明

面向要改代码的人。看完这篇应该能回答："我要改的这个行为，落在哪个文件、哪些注入点要各写一遍"。

---

## 一、源码布局

项目用 Stonecutter 管多版本：**只有一份源码 `src/`**，版本差异用 `//? if` 条件解决
（机制与命令见 `docs/development.md` 第一节）。

```
src/
└── client/java/com/selectiverendering/
    ├── SelectiveRendering.java          入口
    ├── SelectiveRenderingManager.java   ★ 状态中心：全部可变状态 + 设置项 + 快照发布 + 落盘
    ├── RenderConfig.java                不可变快照（渲染线程只读它）；用 Builder 构造，加字段只改 Builder / 本类 / publish()
    ├── render/                          方块 / 区块渲染的判定与调度
    │   ├── RegionTargets.java           ★ 判定层：方块 / 实体 / 粒子是否被选区影响、透明度多少（共用一套模式判定）
    │   ├── Alpha.java                   方块判定的对外门面：getAlpha()/alphaNow()…，负责受影响区段记账
    │   ├── RebuildScheduler.java        改完之后重烘焙哪些 section（三档）
    │   ├── HiddenSections.java          "哪些 section 含隐藏方块"的记账本
    │   ├── MovingBlockRenderContext.java 活塞移动中渲染的线程上下文
    │   └── BlockRenderContextAccess.java Sodium 方块上下文里挂的 alpha 字段
    ├── blockentity/                     方块实体 / feature 渲染淡化
    │   ├── TranslucentSubmitForwarder.java   ★ 换半透明 RenderType 的提交转发（方法清单唯一一份）
    │   ├── SelectiveSubmitNodeCollector.java 方块实体淡化入口（继承上面那个）
    │   ├── AlphaVertexConsumer.java     给顶点乘 alpha 的 VertexConsumer 包装
    │   ├── BufferSourceHooks.java       取 VertexConsumer 时的挂钩
    │   ├── HiddenRenderTypes.java       本帧需要套 alpha 的 RenderType 标记
    │   └── TranslucentRenderTypes.java  为一个 RenderType 找半透明变体
    ├── lighting/
    │   └── LightSuppression.java        ★ 光照：伪装(airIfHidden) + 标记 + 分批重算
    ├── region/                          选区与魔杖
    │   ├── Area.java                    一个长方体范围（含 subtract 挖洞）
    │   ├── Region.java                  选区 = 一组 Area + 随机颜色 + RegionOptions，身份由 id 决定
    │   ├── RegionOutline.java           把一个选区的所有范围融合成整体线框
    │   ├── RegionOptions.java           选区自己的隐藏对象开关（方块/实体/粒子）和本地方块名单
    │   ├── RegionEditor.java            魔杖双角点 + 选区的增/删/裁/点选
    │   ├── RegionMerger.java            散点坐标 → 长方体（贪心合并）
    │   ├── CameraRay.java               ★ 魔杖射线：从渲染相机出发、穿过鼠标指针
    │   └── （HUD 在 render/information/WandHudRenderer：左下角的模式图标、选区状态、带图标的操作提示）
    ├── matchingrule/                    方块名单规则
    │   ├── BlockMatchRule.java          规则字符串的解析与匹配
    │   └── BlockListMessage.java        增删规则时屏幕下方的浮动提示
    ├── interaction/
    │   └── FadedBlockGetter.java        ★ 客户端射线专用：把淡化方块说成空气的 BlockGetter 外壳
    ├── recording/
    │   └── BlockChangeRecorder.java     变化记录器
    ├── config/                          配置：磁盘配置、预设、热键、MaLiLib 界面与接线
    │   ├── ModConfig.java / Preset.java 磁盘配置 / 预设
    │   ├── ModHotkeys.java              修饰键查询
    │   └── ModConfigs.java 等           MaLiLib 配置界面
    ├── compat/Platform.java             ★ 跨版本适配层
    ├── util/                            Log、BlockPosScratch
    └── mixin/                           补丁，按功能分包，与上面的包一一对应
        ├── render/
        │   ├── ModelBlockRendererMixin  ★ 方块：取消 / 面剔除 / alpha / 换材质
        │   ├── SectionCompilerMixin     可见性图不再把隐藏方块当不透明
        │   ├── FluidRendererMixin       流体：alpha / 面剔除
        │   └── FluidModelMixin          流体整体挪到半透明层
        ├── blockentity/
        │   ├── BlockEntityRenderDispatcherMixin  方块实体淡化
        │   ├── BlockFeatureRendererMixin 活塞移动中的方块
        │   ├── SubmitNodeCollectionMixin 移动方块进半透明相位
        │   ├── FeatureRenderDispatcherMixin 每帧清 HiddenRenderTypes 标记
        │   ├── RenderTypeFeatureRendererMixin / BufferSourceMixin  取 VertexConsumer 的入口（按版本）
        │   └── RenderTypeAccessor / RenderSetupAccessor / TextureBindingAccessor
        ├── lighting/
        │   ├── LightEngineMixin         光照引擎把隐藏方块当空气（→ LightSuppression）
        │   ├── AreaLightPacketMixin     服务端光照到达时标记区块（→ LightSuppression.markChunk）
        │   ├── AreaLightFlushMixin      runLightUpdates 之后消化待重算区块（→ LightSuppression.flush）
        │   ├── BlockModelLighterMixin   平滑光照(AO)把隐藏方块当空气
        │   └── LightmapRenderStateExtractorMixin
        ├── region/
        │   ├── MouseHandlerMixin        ★ 魔杖的全部鼠标交互
        │   ├── LevelRendererMixin       选区线框（双注入点，兼容方法改名）
        │   └── GuiMixin                 HUD 注入点（→ WandHudRenderer）
        ├── interaction/ClientLevelClipMixin  ★ 客户端射线：淡化方块当空气（见第六节）
        ├── recording/ClientLevelMixin   给记录器喂方块变化
        └── compat/                      第三方 Mod 补丁：sodium/、indigo/（Fabric Renderer API）、entityCulling/、ImmediatelyFast/
```

按版本取舍的文件（内部用 `//? if` 区分，没有"另存一份"）：

| 文件 | 为什么随版本变 |
|---|---|
| `compat/Platform.java` | ★ 跨版本适配层：鼠标键码、重建入口、GUI 访问、按键名、`MaterialInfo` 构造参数 |
| `blockentity/AlphaVertexConsumer.java` | 实现 `VertexConsumer`，接口成员随版本增删 |
| `blockentity/TranslucentSubmitForwarder.java` | 实现 `OrderedSubmitNodeCollector`，方法集随版本变；**唯一一份**方法清单（换半透明 RenderType 的那几个也在这里） |
| `blockentity/SelectiveSubmitNodeCollector.java` | 方块实体淡化的入口，继承上面那个类，只管 `target()` / `order(n)` |
| `mixin/blockentity/BlockFeatureRendererMixin` 等 | 注入目标类 / 方法随版本变 |

> `src/client/resources/selective-rendering.client.mixins.json` **只有一份，是纯 JSON，不含任何条件**。
> 只在部分版本存在的 mixin（`blockentity.BufferSourceMixin`、`compat.ImmediatelyFast.ImmediatelyFastBufferSourceMixin`、
> `blockentity.RenderTypeFeatureRendererMixin`）在 json 里始终列出；目标不存在的版本里，
> 源文件用 `//? if` 把类换成 dummy mixin：`@Mixin(Minecraft.class)` + 空类体，不注入任何方法。

---

## 二、核心判定 `Alpha.getAlpha(state, pos, moving)`

全 Mod 只有这一个"真理来源"，返回：

| 返回值   | 含义                                              |
| -------- | ------------------------------------------------- |
| `-1`     | 正常渲染                                          |
| `0`      | 完全隐藏（透明度 100%），mixin 直接 `ci.cancel()` |
| `1..255` | 顶点 alpha，保留几何但改颜色 + 挪到半透明层       |

它被区块构建线程、光照线程、渲染线程**并发**调用，因此只读不可变快照、全程不加锁。

两个变体：

- **`getAlpha(...)`**：命中时会往 `HiddenSections` 记账（用于改透明度时增量重建）。
  只有真正参与画面渲染的路径用它。
- **`alphaNow(...)`**：只判定、不记账。光照 / AO 用它在光照线程上以百万次量级调用，
  不该有副作用。对外入口是 `airIfHidden(state, pos)`。

### 光照是怎么跟着隐藏集合走的

```
隐藏集合变了 ─► LightSuppression.relight()  ─┐
(切模式/增删裁选区)                            ├─► PENDING 区块集合 ─► flush()：每帧限额 checkBlock
服务端光照包到达 ─► markChunk()  ────────────┘     ▲
                                  AreaLightFlushMixin 挂在 runLightUpdates RETURN
```

- 伪装：`LightSuppression.airIfHidden` 被光照引擎 / 三套 AO 的 mixin 调用；
- 服务端发来的光照是按"方块真实存在"算的，会覆盖客户端的伪装，所以到达后要补算；
- 只对 **选区（外扩 1 格）所在的列** 发 `checkBlock`，扩散交给引擎，不再整块外扩 15 格暴力扫。

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

| 目标             | 原版                                                                | Sodium                                | Indigo                                       |
| ---------------- | ------------------------------------------------------------------- | ------------------------------------- | -------------------------------------------- |
| 方块几何 + alpha | `ModelBlockRendererMixin`                                           | `sodium.BlockRendererMixin`           | `compat.FabricRendererApiBlockRendererMixin` |
| 可见性图         | `SectionCompilerMixin`                                              | `sodium.ChunkBuilderMeshingTaskMixin` | —                                            |
| 平滑光照当空气   | `BlockModelLighterMixin`                                            | `sodium.LightDataAccessMixin`         | `compat.FabricRendererApiLightMixin`         |
| 光照引擎当空气   | `LightEngineMixin`                                                  | （共用）                              | （共用）                                     |
| 流体             | `FluidRendererMixin` + `FluidModelMixin`                            | `sodium.DefaultFluidRendererMixin`    | —                                            |
| 移动方块         | `BlockFeatureRendererMixin`                                         | —                                     | `compat.FabricRendererApiMovingBlockMixin`   |
| 方块实体         | `BlockEntityRenderDispatcherMixin` → `SelectiveSubmitNodeCollector` | —                                     | —                                            |

后三项（光照引擎 / 方块实体）三条渲染后端是共用的，因为那里走的还是原版类。

---

## 四、跨版本适配的边界

### 随版本变的文件（用 `//? if` 取舍）

| 文件 | 原因 |
| --- | --- |
| `compat/Platform.java` | **每个方法在三个版本里都不一样**：鼠标键码 `0/1` vs `1/3`、`levelRenderer` vs `levelExtractor`、`minecraft.screen` vs `minecraft.gui.screen()`、`KEYSYM` vs `KEYBOARD`、`MaterialInfo` 构造参数个数。这就是它的职责。 |
| `blockentity/AlphaVertexConsumer.java` | 实现 `VertexConsumer` 接口，接口成员随版本增删（26.3 多一个 `setUv3`）。 |
| `blockentity/TranslucentSubmitForwarder.java` | 实现 `OrderedSubmitNodeCollector` 接口，方法集随版本变；`SelectiveSubmitNodeCollector` 继承它。 |
| `mixin/blockentity/BlockFeatureRendererMixin.java` | 26.1.2 打 `BlockFeatureRenderer.renderMovingBlockSubmits`；26.2+ 打 `MovingBlockFeatureRenderer.buildGroup`。目标类和注入策略都不是一回事。 |
| `mixin/blockentity/RenderTypeFeatureRendererMixin`<br>`mixin/blockentity/BufferSourceMixin`<br>`mixin/compat/ImmediatelyFastBufferSourceMixin` | 三个"取 VertexConsumer"的入口各自依赖**只在部分版本存在**的类：`MultiBufferSource` 26.3 已删除；`RenderTypeFeatureRenderer` 26.2+ 才有。整文件包进条件里，不满足的版本是空文件。 |
| `mixin/blockentity/FeatureRenderDispatcherMixin` | 差异不只是方法名，**返回类型也不同**：26.1.2 `endFrame` 返回 `void`，26.2+ `prepareFrame` 返回 `PreparedFrame`（26.1.2 里没有这个类）。 |
| `mixin/region/GuiMixin` | 26.1.2 打 `Gui`，26.2+ 打 `Hud`（26.1.2 里没有这个类）；正文在共享的 `WandHudRenderer`。 |

### 已经在共享里的"跨版本"技巧

- **`LevelRendererMixin`**：方法改名过（`collectPerFrameGizmos` → `collectPerFrameRenderThreadGizmos`），
  参数签名相同，于是写两个 `require = 0` 的 `@Inject` 委托给同一个 `emit()`。
- **`WandHudRenderer`**：HUD 正文各版本相同，只有 mixin 目标类不同（26.1.2 是 `Gui`，26.2+ 是 `Hud`）。
  正文抽到共享类，`GuiMixin` 只剩转发和一个按版本切换的 `@Mixin` 目标。
- **可选依赖的 mixin**（Sodium / Indigo / ImmediatelyFast）统一用
  `require = 0` + `remap = false` + 字符串 `targets`，缺席时静默跳过。

### ⚠️ `ImmediatelyFastBufferSourceMixin` 是活代码，别当成历史包袱删掉

对照 ImmediatelyFast 的各分支可以确认它**只对 26.1.2 有效、且正好必要**：

| IF 分支                           | 目标 MC                    | `BatchableBufferSource`                                                                             |
| --------------------------------- | -------------------------- | --------------------------------------------------------------------------------------------------- |
| `origin/26.1`（`1.15.4+26.1`）    | **26.1 / 26.1.1 / 26.1.2** | ✅ 有（`MixinRenderBuffers` 用 `@Redirect` 把某个 `BufferSource` 换成它，且**覆写了 `getBuffer`**） |
| `origin/26.2` 起（`1.17.2+26.3`） | 26.2+                      | ❌ 已删除，批处理改成只把 `RenderTypeFeatureRenderer$Group` 的 `canReorder` 改成 true               |

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

新增配置项：在 `ModConfigs` 里 `new ConfigXxx(name, ...).apply(KEY)` 并加进 `OPTIONS`（界面按这个顺序列出），
然后在 `lang/*.json` 里补 `selective_rendering.config.name.<name>` / `.comment.<name>`
（快捷键类再补 `.prettyName.<name>`）。翻译走 Minecraft 标准语言文件，由 Fabric API 的 resource loader 加载（MaLiLib 本身就依赖它）。

### 选区行与选区设置弹窗

`ModConfigGui` 在全局配置项之后列出每个选区：`RegionRowConfig` 是个假配置项，由 `WidgetRegionRow` 画出
「范围 / 编辑 / 删除」。点编辑打开 `GuiRegionConfigs`（和 MaLiLib 的列表编辑一样是个子界面，关闭或点返回回到
选区列表并恢复滚动位置），里面是 `RegionConfigs` 生成的行：每个范围一组（`WidgetRegionArea` 的「独立 / 删除」加两个角点输入 `WidgetRegionCorner`）、
一行 `WidgetRegionTargets`（方块 / 实体 / 粒子，每项后跟一个方形小按钮），以及方块 / 实体 / 粒子三份本地名单
（标准 `ConfigStringList`），最后是 `WidgetRegionMerge` 的「合并到」（下拉框选目标，和渲染模式那一行一样需要列表控件在 `drawContents` 里补画展开的下拉框）。改动经 `RegionEditor.updateRegion` 按 id 替换 `REGIONS` 里的
对应项，并触发重建和光照重算。界面里持有的是 `RegionHandle`，每次按 id 取最新版本，所以独立 / 合并之后仍指向同一个选区。

方块 / 实体 / 粒子名单（全局方块名单、记录名单、选区里的三份本地名单）是 `SuggestingStringList`，带一个 `SuggestKind`。
它的行由 `WidgetSuggestingList` 画出，按钮打开 `GuiSuggestStringListEdit`：继承 malilib 的 `GuiStringListEdit`，
保留原来的列表编辑，只在输入框下面多一个补全提示框（Tab 补全、上下键选择、点击选择）。补全数据直接取自命令参数使用的接口：
方块用 `BlockStateParser.fillSuggestions`（`SuggestKind.BLOCK`，进入世界后才有标签数据），实体和粒子用
`SharedSuggestionProvider.suggestResource` 加对应注册表的 `keySet()`。新增一份需要补全的名单时用这个类型，
并在列表控件的 `createListEntryWidget` 里分派到 `WidgetSuggestingList`。

名单里用户写的每一项都原样保留，包括无法解析的：全局名单在 `SelectiveRenderingManager` 里存原始字符串
（`RULE_SOURCES` / `RECORD_SOURCES`），解析成功的另存一份（`RULES` / `RECORDED`）用于匹配；选区的 `RegionOptions` 同理
（`ruleSources()` 是原始写法，`rules()` 是能解析的）。无法解析的项只是不参与匹配，编辑界面
（`GuiSuggestStringListEdit`）里把它的文字标成淡红色（`SuggestKind.isValid` 判定）。

假配置项（`RegionRowConfig`、`RegionCornerConfig`、`RegionTargetsConfig`）的 `getConfigGuiDisplayName()` 必须返回空串：
MaLiLib 用它计算整张表的最大标签宽度，返回选区字符串会把所有配置项的输入框挤出屏幕。

- 方块判定只用 `blocks` 为真的选区（`RenderConfig.blockRegions`）；本地名单与全局名单取并集，
  只在选区内部且模式用到名单时生效（`RegionTargets.listedLocally`）。
- 一个选区（`Region`）由一个或多个范围（`Area`）组成，裁剪出的碎片留在原来的选区里，范围全被裁掉时选区消失。
  选区的字符串形式：范围之间用 `;` 分隔，末尾是设置和颜色的 SNBT：
  `0,0,0:9,9,9;20,0,0:25,5,5{color:16711680,entities:1b,rules:["minecraft:stone"],entity_types:["minecraft:zombie"]}`
  （键：`color`、`blocks` 默认真、`entities` / `particles` 默认假、`rules`、`entity_types`、`particle_types`；除 `color` 外只写与默认不同的项；
  没有 `color` 时读取时随机分配）。存档里每个选区是 `areas`（范围数组）加 `color`；旧的单个 `box` 仍能读取。
  `Region.parse` 用 `TagParser` 解析，预设里的选区就是这种字符串，所以应用预设会保留每个选区的设置。
- 角点是文本框 + `✔` 按钮，点按钮才应用，避免边输入边重建。
- 线框：`LevelRendererMixin` 用选区的颜色画 `Region.outline()`（`RegionOutline`：按所有范围的边界坐标压成网格，对每条网格边
  看周围四个格子，只保留融合形状的真实棱；格子数超过 40 万时退化成逐个范围画盒子），每个范围另画一层半透明填充。
  选中的选区线更粗、填充更深。
- 魔杖：中键对着角点选角点，对着选区选选区（`RegionEditor.selectAlongRay`），对着空气清空选择。选中了选区时，
  选区键 + 左键添加的范围和记录器记录出的范围并入它（`addAreas`），没选中时新建选区；选区键 + 左键对着某个范围时删除这个范围。
- 覆盖透明度：`RegionOptions.alphaOverride`，-1 跟随全局，0..255 是这个选区自己的 alpha（0 完全透明，255 不透明）。方块、实体、
  粒子命中时取位置所在选区的覆盖值（`RegionTargets.overriddenAlpha`；选区外模式没有"所在选区"，用全局值）；实体和方块实体的提交器
  （`SelectiveSubmitNodeCollector`）带着这个 alpha，不再读全局值。SNBT 里的键是 `alpha`，只有 >= 0 时才写。
- 合并（`Region.merge`）：目标选区的设置优先，勾选项和覆盖透明度丢弃被合并方的，三份名单取并集（`RegionOptions.mergedWith`）；
  独立（`RegionEditor.detachArea`）复制原选区的设置并分配新的随机颜色。
- 实体：`mixin/entity/EntityRenderDispatcherMixin` 包装 `submit`，命中则完全隐藏（透明度 0）或套上
  `SelectiveSubmitNodeCollector`。选区可以带实体类型名单（空 = 所有实体）；名单里没有该类型的实体不受影响。
  透明度是按提交施加的：模型通过 tint 颜色的 alpha，自定义几何通过 `AlphaVertexConsumer`，不再标记共享的
  `RenderType`，所以同材质、不在选区内的实体不会跟着变淡。物品（掉落物）的 RenderType 是逐个四边形决定的、
  多半不混合，所以 `submitItem` 改为把四边形按半透明变体分组，用 `submitCustomGeometry` 自己画（会丢失附魔光效）；
  方块模型等少数路径仍走 `HiddenRenderTypes` 标记。
  `TranslucentRenderTypes.variant` 为没有混合的类型找顶点格式一致的半透明版本：普通实体贴图用 `entityTranslucent`，信标光柱用
  `beaconBeam(texture, true)`。`AlphaVertexConsumer` 把顶点原有的 alpha 乘上透明度（不是替换），所以外层本来就很淡的光柱不会变得更不透明。
  结构方块的边框线是 Gizmo，不经过提交器，不受影响。
- 粒子的类型：`ParticleEngineMixin` 在 `createParticle` 里把 `ParticleOptions` 的类型记到粒子上（`ParticleMixin` /
  `ParticleTypeHolder`），粒子类型名单按它匹配；不经 `createParticle` 加入的粒子没有类型，只在名单为空时命中。
- 粒子：透明度为 0 时 `mixin/particle/ParticleEngineMixin` 在 `add` 处丢弃；大于 0 时
  `mixin/particle/SingleQuadParticleMixin` 在 `extractRotatedQuad` 里把不透明层换成同图集的半透明层，
  并把颜色 alpha 乘上透明度（只覆盖 `SingleQuadParticle`，其它粒子类型不受影响）。
- 方块、实体、粒子的命中规则都在 `render/RegionTargets`（`blockAlpha` / `entityAlpha` / `particleAlpha`），最终判定共用
  同一个 `hidden(...)`：渲染模式的含义对三者一致：`…_名单内`要求在选区内（或外）且命中名单，
  `…_名单外`要求没命中名单，不带名单的模式忽略名单，反转同样生效；黑 / 白名单模式不影响它们。名单是选区自己的实体 / 粒子名单，
  为空时没有任何东西算命中。选区内用包含该位置的选区的名单，选区外模式用所有选区名单的并集。
- 实体名单项可以是 id，也可以在 id 后面跟 `{nbt}`：`minecraft:zombie{IsBaby:1b}`。NBT 用 `NbtUtils.compareNbt` 做子集匹配，
  实体的 NBT 通过 `Entity.saveWithoutId` 取得并按实体缓存几个 tick（`render/EntityNbt`）；实体 id 由
  `EntityRendererMixin` 在 `createRenderState` 返回处存进渲染状态（`EntityRenderStateMixin` / `EntityIdHolder`）。

配置项本身只是"视图"，权威数据源永远是 `SelectiveRenderingManager`；
落盘由 `ModConfig.save()` 防抖异步完成。

---

---

## HUD 图标

手持魔杖时 `WandHudRenderer` 在左下角从上到下画：渲染模式图标 + 名称（反转时标注）、选中的选区（颜色块、序号、范围数、
已启用的方块 / 实体 / 粒子图标）、角点和记录状态文字、带图标的操作提示（鼠标左 / 中 / 右键、滚轮、热键图标，
按住选区键或操作键时只列对应的组合）。图标在 `assets/selective_rendering/textures/gui/`，都是 32×32，
用 `GuiGraphicsExtractor.blit` 带缩放的重载画成需要的大小；模式到图标的对应关系在 `WandHudRenderer.modeIcon`。

---

## 操作键

`OperationActions`：按住操作键（原方块键，默认左 Alt，旧配置里的 `blocks_key` 会迁移）点左 / 右键，取视线上最近的方块或实体
加入或移出名单。选中了选区就写入该选区的本地名单（`RegionEditor.toggleLocalRule / toggleLocalEntityType`），否则方块写入全局名单；
实体没有全局名单，未选中选区时只提示。右键：方块带完整状态，物品实体带 `{Item:{id:..}}`。消息和 HUD 提示都带括号标出写入的名单。

---

## 半透明地形的常驻缓冲

26.3 + Sodium 下，半透明的方块和流体不进 Sodium 的区块 mesh：`BlockRendererMixin.bufferQuad` / `DefaultFluidRendererMixin.writeQuad`
把四边形交给 `TerrainTranslucency` 按区段保存（`ChunkBuilderMeshingTaskMixin` 在编译任务开始 / 结束时 begin / finish，
重建后整段替换）。顶点 alpha 恒为 255，截流时记下透明度，边界上的面向内收 0.005 格避免和相邻方块共面。

`RetainedTerrain` 为每个区段、每个透明度分组维护一块常驻 GPU 顶点缓冲，内容变化才重建（每帧有四边形预算）。
绘制在 `PreparedFrameMixin`（`FeatureRenderDispatcher.PreparedFrame.executeTranslucent` 开头），只画主画面的提交
（靠 `SubmitNodeStorage` 区分，光影的阴影图不画），用 `PreparedRenderType.drawFromBuffer` 在同一个 RenderPass 里发绘制：
- 透明度由每次绘制的 DynamicTransforms 里的 `ColorModulator` 提供；等于当时全局透明度的四边形标为 global，之后跟随配置，
  拖全局透明度拉条（两端都是半透明）不重建区块也不重建缓冲。
- 每个区段单独写一份带相机旋转和平移的 modelview，整体缩放 1.0005 倍把深度推远一点，避开共面闪烁。
- 每组四边形有按到相机距离从远到近排好的索引缓冲，相机移动超过 0.5 格才重排（每帧有预算），区段之间也从远到近画。
- 做视锥剔除（区段整体、再用实际包围盒）和渲染距离剔除；可见区段里用到的动画精灵每帧调用 `SpriteUtil.markSpriteActive`。
- 开着光影时用 `entityTranslucentCull(方块图集)`，必须在实体提交阶段建缓冲，Iris 才会按扩展格式写数据；
  没开光影时用原版区块的半透明管线 `translucentMovingBlock`，方块顶点格式，没有实体管线多余的顶点数据。
  光影开关切换时缓冲全部重建。
26.1.2 / 26.2 没有这套 API，不截流，保持原版区块渲染。流体和方块一样处理；没有 Sodium 时走原来的顶点 alpha 路径。

后台构建与原版路径：不开光影时顶点数据在后台线程池里建好（`buildData`），渲染线程只上传；排序也在后台做，
完成后渲染线程把索引原地写入常驻索引缓冲（`writeToBuffer`）。开着光影时 Iris 的扩展顶点格式只有在渲染线程提交阶段才能写对，
仍然同步构建，排序同样走后台。没有 Sodium 时，`ModelBlockRendererMixin`（方块，包裹 `BlockQuadOutput.put`）和
`FluidRendererMixin`（流体，拦截 `vertex`）通过 `VanillaCapture` 截流，`SectionCompilerMixin` 负责 begin / finish，
其余完全复用上面这套缓冲和绘制。

移植与「隐藏时仍构建网格」：26.2 用 `PreparedRenderType.drawFromBuffer`（无 RenderPass 参数，钩 `executeTranslucent()`）；
26.1.2 没有 `PreparedRenderType`，`RetainedTerrain.drawImmediate` 按 `RenderType.draw` 的做法自己开渲染通道，
钩 `FeatureRenderDispatcher.renderTranslucentFeatures`，用当时的 `RenderSystem` 模型视图矩阵。
配置 `build_hidden_mesh`（默认关）开启后，alpha 为 0 的方块 / 流体也截流（绘制时跳过），
全局透明度在完全隐藏和半透明之间变化时不重建区块。

---

## 选区撤销 / 重做

`RegionHistory` 在 `SelectiveRenderingManager.commitAndRemember` 里记录：每次提交前后各比一次选区列表，`Region` 不可变，
所以逐个比较对象是否是同一个就知道有没有变化；有变化就把变化前的快照（选区列表加选中的选区 id）压进撤销栈，并清空重做栈。
同一个选区在 1.2 秒内连续被原地修改（拖拉条）合并成一步。撤销 / 重做把快照写回 `REGIONS`，保存，再由
`RegionEditor.refreshAfterRestore` 重建受影响的范围并重算光照。历史最多 64 步，`load()` 后清空。键位在
`ModConfigs.UNDO_KEY / REDO_KEY`，默认 Ctrl+Z / Ctrl+Y，只在游戏内生效。

---

## 选区投影

`RegionOptions.offset`（x, y, z）是选区的投影位移，全 0 表示没有投影；保存在选区设置里（`offset:[x,y,z]` / `ModConfig.RegionEntry.offsetX/Y/Z`），
撤销 / 重做自然覆盖。`ProjectionAreas` 缓存当前有位移的范围（随配置快照更新）。

**方块和流体**：不重新烘焙，直接复用区块编译已经算好的四边形（光照、AO、群系染色都和原位置一致）。区块编译时
（`TerrainTranslucency.begin` 取一份有效范围的快照），Sodium 的 `BlockRendererMixin.bufferQuad` / `DefaultFluidRendererMixin.writeQuad`
和原版的 `ModelBlockRendererMixin` / `FluidRendererMixin` 发现方块落在某个源范围里，就把四边形（仍是区段内坐标）复制一份给那个范围
（`TerrainTranslucency.addProjected`），正常的区块 mesh 不受影响。按（范围, 区段）为单位存放，`finish` 时发布。
`RetainedTerrain` 把每一对当作一个来源，原点是区段原点加位移，和半透明地形共用上传、排序、视锥剔除、绘制；位移变化不需要任何重建，
只有第一次出现位移时 `RegionEditor.moveProjection` 会让范围内的区段重建一次，让编译开始收集四边形。

**实体、方块实体、粒子**：`ProjectionPass.submit`（在 `submitEntities` 末尾）对每个源范围里的实体和方块实体重新提取渲染状态，
再按位移提交一遍（实体用 `EntityRenderDispatcher.submit` 的坐标参数，方块实体平移 `PoseStack`）；这期间 `ProjectionPass.active()` 为真，
选区自己的隐藏 / 半透明 mixin 直接放行。粒子在 `SingleQuadParticleMixin` 里：源范围里的粒子提取完一次后，带着位移再提取一次
（`extractRotatedQuad` 的坐标加上位移）。被选区隐藏的粒子在创建时就丢弃了，所以不会有投影。

**操作**在 `ProjectionControls`：Ctrl + 方向键（`project_*` 热键）相对视线水平方向移动一格，Ctrl + 滚轮沿视线主轴移动（选中了选区时）。
`InfoRenderer` 在拿着魔杖时用同色的浅色框出投影区，并把 8 个顶点和原位置的框连线。

**投影自己的光照**：原版光照引擎不知道投影的存在，不会把光传进投影里，所以 `ProjectionLight` 为每个源范围单独算一份光照场：
把范围当作悬在空中的一块，外面一圈空气壳里天空光为 15，天空光从上往下直落、遇到会挡光的方块才衰减，范围里发光的方块
（`getLightEmission`）的光在范围内按 `max(1, 遮光)` 衰减传播。读方块状态和 BFS 都在后台线程，没有体积上限；范围里有区段重新编译
（`TerrainTranslucency.finish`）就让光照场作废、重算。绘制前 `ProjectionLight.relight` 把每个投影四边形的光照换成面朝向那一格
和方块自己那一格里更亮的（`RetainedTerrain` 缓存重算结果，源四边形或光照场变了才重算）；AO 和方向阴影仍保留在四边形的颜色里。
投影范围边界上朝着范围外的面不会因为外面有方块被剔除（`TerrainTranslucency.projectionBoundary`，在面剔除的返回值上放行），
否则投影出来的块底面和侧面会是空的。这些面是为了投影才保留的，所以放行时同时记下“这个面只给投影用”（`forcedFaces` 位掩码）：
四边形输出时只复制给投影，不再写进真实的区块网格，投影编译和真实编译互不影响（选区被隐藏时也不会露出多余的面）。
光照按顶点平滑：取顶点周围 8 格里朝着面外的那 4 格中不挡光的格子的平均，4 格都挡光时退回 8 格，再不行用天空光 15。不透明和镂空的投影方块在实心阶段绘制，玻璃、水等半透明的在半透明阶段绘制。

