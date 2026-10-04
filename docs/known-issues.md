## ⚠️ 残留的已知限制（改之前请先读）

### `HiddenRenderTypes` 的标记按 RenderType 共享实例生效

半透明变体 `RenderTypes.entityTranslucent(贴图)` 是共享实例，
所以本帧内其它用了同一张贴图的 BE / 实体也可能被一起套上隐藏用的 alpha。
因为标记每帧清空，影响限制在一帧内。

_没有低成本的修法_：新版渲染是"先 submit 节点、后统一取 buffer"，
想在取 buffer 时区分"是不是我们标的那个"，只能改到 submit 端，代价很大。

### `getAlpha` 仍然是 O(规则数 + 选区块数) 的线性扫描，没有缓存

这是最大的性能杠杆（尤其 `LightEngineMixin`）。要加缓存就得处理失效
（模式 / 名单 / 选区一变就要清），容易引入"改了但画面没变"的 bug，所以暂未做。
真要做建议按 section 粒度缓存，并在所有 `commit*` 路径上统一失效。

### `getAlpha` 里跨线程访问客户端世界

处理"活塞推动中的方块"时会 `Minecraft.getInstance().level.getBlockEntity(pos)`，
而这发生在区块构建工作线程上。只在 `carried` 为真（遇到 `moving_piston`）时触发，
实际很少见，但属于不该有的跨线程访问。

### 关掉"夜视"后，重算光照仍然可能很贵

Y 方向已经收窄成"选区自己的高度 ±15"，但**外壳层被完整重算**——理论上只需要
重算外壳 + 内部受影响的格子，代价是要写一套更精细的遍历，暂未做。
默认开启"夜视"时 `relight()` 直接跳过，不受影响。

### `SelectiveSubmitNodeCollector` 是 280 行样板代码 × 3 个版本

每次 MC 增删 `SubmitNodeCollector` 的接口方法都要改三份，
而且**漏实现的方法会静默地不被包装**（表现为那个类型不变淡，不会报错）。
升级 MC 版本时这是最容易踩的坑——见 `docs/development.md` 的移植清单。

### `FluidRendererMixin` / `DefaultFluidRendererMixin` 的 `@ModifyVariable(ordinal = 0)`

Mixin 的 ordinal 是"在**同类型**局部变量里数"，不是"第 0 个参数"。
这里 `int` 型局部变量中第 0 个恰好是 `color`。语义很反直觉，改方法签名时务必重新核对。

### `toggleRegionAlongRay` 的语义不对称

"删"用的是视线穿过的最近那个选区盒子（不需要点到方块），
"加"用的是当前双角点选区。行为是刻意的，已在代码注释里标注。

### 关掉"夜视"后，光照伪造仍然很贵

`airIfHidden` 会在光照引擎和 AO 里把被隐藏的方块伪装成空气，好让光"穿过"它们。
这是全 Mod 最热的调用点（`LightEngine.getState` 上百万次量级），而且没有缓存。

默认的"夜视"开启时这一步被**完全跳过**（画面本来就全亮，伪造毫无意义），
所以正常情况不受影响；**关掉夜视才会走上这条路**。
要加缓存就得处理失效（模式 / 名单 / 选区一变就要清），
容易引入"改了但画面没变"的 bug，所以暂未做。真要做建议按 section 粒度缓存，
并在所有 `commit*` 路径上统一失效。

### `FeatureRenderDispatcherMixin` 仍是三份

差异不只是方法名，返回类型也不同（26.1.2 `endFrame` 返回 `void`，
26.2+ `prepareFrame` 返回 `PreparedFrame`，而 26.1.2 里没有 `PreparedFrame` 这个类），
共享源码无法引用，所以只能按版本保留。

### 线框用的是 `Gizmos` API，依赖原版的帧内配对契约

`Gizmos` 的"收集"（`Minecraft.renderFrame` → `LevelRenderer.collectPerFrame*Gizmos`）
和"落地"（`GameRenderer.render` → `LevelRenderer.render` → `submitFeatures`
→ `finalizeGizmoCollection`）分属两个调用点，中间夹着整个世界渲染。
任何一步抛异常，我们上一帧加的线框就会残留并叠加（填充 alpha 只有 9%，
叠十几次就变实心）。

已加守卫（发现上一帧没 drain 就这一帧不画），但**根本原因是别的 mod 在错误的
时机改画质选项**：26.3 的 `LevelRenderer.render` 开头会调
`submitNodeStorage.setUseImprovedTransparency(...)`，它在 storage 非空时抛
`IllegalStateException`（原版注释自己写着 "improved transparency is likely
toggled in a wrong place"）。装了"失去焦点时降画质"的 mod（如 Dynamic-FPS）
时容易踩中。日志里会有 `Storage is not empty`。

彻底不依赖这个契约需要改成自绘（像 Litematica / Lucidity 那样自己 submit），
工作量不小，暂未做。

### EntityCulling 兼容层是字符串目标，失效时静默退化

`EntityCullingProviderMixin` 用 `@Mixin(targets = "dev.tr7zw.entityculling.Provider")`
注入它的 `isOpaqueFullCube`（体素光线追踪的"是不是实心墙"判定），
让被淡化的位置不算实心——否则隔着淡化方块看活塞/生物会时隐时现。

风险：EntityCulling 是多版本预处理项目，类名/方法名若在某个版本变了，
`require = 0` 会让整个 mixin **静默跳过**（不会崩游戏，但兼容失效），
用户需要手动在 EntityCulling 的"跳过方块实体剔除"名单里勾 `minecraft:piston`。
由于没装 EntityCulling 时目标类不存在，本条**无法在构建期验证**，
升级 EntityCulling 后建议隔着淡化方块看一眼活塞确认还在。
