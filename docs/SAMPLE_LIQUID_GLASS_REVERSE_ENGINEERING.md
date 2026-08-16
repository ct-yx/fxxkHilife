# SAMPLE 液态玻璃实现逆向分析报告

> 文档性质：历史研究记录。v4.5.0 不采用该实现，也不在生产代码中保留第三方玻璃渲染依赖、effect/source 或相关配置；本报告只用于保留此前的分析证据。

> 本报告记录对用户提供的 `SAMPLE` 进行的只读静态分析。报告只保存分析结论、关键符号和可复现的证据位置，不把样本、DEX dump 或临时反编译目录加入仓库。

## 1. 结论先行

SAMPLE 不是只通过一个玻璃 Modifier 完成效果，而是把两层能力组合在一起：

1. **背景输入层**：包含 APP 的 source/effect 节点、背景采样、模糊、颜色和玻璃配置相关实现。
2. **光学层**：使用 Android `RuntimeShader` + `RenderEffect`，对圆角矩形边缘的背景采样坐标进行偏移；可选的 `RefractionWithDispersion` 再对同一折射方向进行多次彩色采样。

因此，当前项目即使已经调用 APP 的 typed glass API，只要没有形成类似的“真实 captured content → 边缘坐标折射 → 可选色散 → RenderEffect”链路，视觉上仍然可能只有模糊、着色、边框和高光；两者是否达到同等边缘折射效果需由定向截图确认。

当前最可信的改进方向不是继续增加 tint、噪声或边框，而是把 **APP 背景输入** 与 **独立的自定义光学 renderer** 分开验证，再决定两者的组合顺序。

## 2. 样本和证据范围

| 项目 | 结果 |
|---|---|
| 输入 | `SAMPLE` |
| 大小 | 6,680,277 bytes |
| SHA-256 | `79777621b8dd6643f7ab2c0c0c7e77f846a2cb2d6c4ed23b59ac8858798412e2` |
| DEX | `classes.dex` |
| Native 库 | `lib/arm64-v8a/libandroidx.graphics.path.so` |
| min SDK | 28 |
| target SDK | 36 |
| compile SDK 元数据 | 36 |
| UI 技术栈 | Jetpack Compose、Material 3 |

本轮使用的只读工具和命令：

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jre/Contents/Home

aapt dump badging SAMPLE
apkanalyzer dex packages --defined-only SAMPLE
unzip -Z1 SAMPLE
unzip -p SAMPLE classes.dex > /tmp/sample-classes.dex
strings -a /tmp/sample-classes.dex
dexdump -d /tmp/sample-classes.dex
```

未执行样本安装、运行、动态注入、网络访问或修改；因此本文的“已确认”均指 DEX、Manifest 和资源层面的静态事实，不等同于 SAMPLE 在每个 Android 版本上的运行时表现。

## 3. APK 技术栈证据

### 3.1 Compose 和图形相关内容

Manifest 和 APK 条目确认：

- APK 使用 Compose 和 Material 3 相关组件。
- 包含 AndroidX graphics path native 库。
- DEX 中存在 `android.graphics.RuntimeShader` 和 `android.graphics.RenderEffect` 类型引用。
- DEX 字符串池中存在 APP 的 source/effect/config 节点符号，以及一套独立的 blur shader 源码。

这说明 SAMPLE 的“玻璃”不是单纯的静态 PNG、渐变背景或普通 `BorderStroke`。至少有一条平台图形 effect 路径被编译进 APK。

### 3.2 APP 与自定义光学代码的分工

DEX 字符串中同时出现了以下几组信息：

| 证据组 | 静态含义 |
|---|---|
| APP effect node、source element、glass config、style、tint | 背景输入、模糊、色调和 effect 节点管理 |
| `RuntimeShader`、`RenderEffect` | 平台级 shader/effect 执行路径 |
| `Refraction`、`RefractionWithDispersion` | 两个自定义折射 shader 的缓存名称 |
| `content.eval(...)` | shader 从输入 content 重新采样，而不是只返回固定颜色 |
| `cornerRadii`、`refractionHeight`、`refractionAmount`、`depthEffect` | 逐 surface 的几何和光学参数 |

这里的 APP 相关字符串能够证明相应实现被打包进了 SAMPLE，但单凭字符串尚不足以确认每一个页面都同时启用了所有 effect；页面级组合仍需要运行时截图或调用链追踪确认。

## 4. 自定义 RuntimeShader 结构

### 4.1 `Refraction` shader 的 uniforms

SAMPLE 内嵌的第一个折射 shader 声明了：

```text
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
```

第二个 shader 在此基础上增加：

```text
uniform float chromaticAberration;
```

这些参数不是装饰字段，它们都参与了 shader 的采样坐标或颜色计算。

### 4.2 圆角矩形距离场

shader 没有用一个简单的矩形边框判断，而是通过 signed distance field 计算当前像素到圆角矩形边界的距离：

```text
cornerCoord = abs(coord) - (halfSize - radius)
outside = length(max(cornerCoord, 0)) - radius
inside = min(max(cornerCoord.x, cornerCoord.y), 0)
sd = outside + inside
```

`radiusAt` 根据当前坐标所在象限从 `cornerRadii` 取对应半径，因此四个角可以使用不同的半径。这个细节会直接影响圆角边缘的折射方向；只绘制一个统一圆角边框尚未形成同样的几何输入。

### 4.3 只在边缘带产生折射

shader 的核心分支是：

```text
if (-sd >= refractionHeight) {
    return content.eval(coord)
}
```

这意味着：

- 卡片内部的大部分区域保持原始 content；
- 只有距离圆角边缘小于 `refractionHeight` 的区域进入折射计算；
- 折射强度随边缘距离变化，而不是把整张卡片统一平移或缩放。

这也是 SAMPLE 看起来像“边缘有镜片厚度”的关键。若把 `depth` 只用于颜色透明度或边框宽度，视觉上不会出现同样的边缘内容位移。

### 4.4 折射方向和位移

折射距离由圆形映射函数得到：

```text
circleMap(x) = 1 - sqrt(1 - x * x)
d = circleMap(1 - (-sd / refractionHeight)) * refractionAmount
```

方向由圆角距离场的梯度和中心方向共同决定：

```text
grad = normalize(
    gradSdRoundedRect(centeredCoord, halfSize, gradRadius)
    + depthEffect * normalize(centeredCoord)
)
refractedCoord = coord + d * grad
return content.eval(refractedCoord)
```

因此 `depthEffect` 的作用是改变边缘法线方向的偏移，而不只是增加暗色或阴影。

## 5. 色散实现

`RefractionWithDispersion` 先计算与普通折射相同的 `refractedCoord`，再计算：

```text
dispersionIntensity = chromaticAberration
    * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y))
dispersedCoord = d * grad * dispersionIntensity
```

随后沿正负折射方向进行七组采样：

```text
red    = content.eval(refractedCoord + dispersedCoord)
orange = content.eval(refractedCoord + dispersedCoord * 2/3)
yellow = content.eval(refractedCoord + dispersedCoord * 1/3)
green  = content.eval(refractedCoord)
cyan   = content.eval(refractedCoord - dispersedCoord * 1/3)
blue   = content.eval(refractedCoord - dispersedCoord * 2/3)
purple = content.eval(refractedCoord - dispersedCoord)
```

各次采样只把对应颜色通道按比例累加到输出，因此得到的是边缘彩色分离，而不是一条固定的彩虹边框。

需要注意两点：

1. 色散只发生在已经折射的边缘区域；内部区域仍直接返回原始 content。
2. SAMPLE 的当前 RenderEffect 创建链中，色散开关会把 `chromaticAberration` 写成 `1.0`；用户设置到这个底层调用点之间是否还有连续数值映射，静态证据不足，应将完整参数范围保留为待确认项。

## 6. RenderEffect 创建链

### 6.1 核心方法

混淆类 `Lmx3;` 的静态方法：

```text
Lmx3;.D0:(Lpe0;FFZZ)V
```

位于 `dexdump` 的代码地址 `0x41863c`。它的主要流程可以还原为：

```text
if (SDK_INT < 33) return
if (refractionHeight <= 0 || secondFloat <= 0) return
读取圆角形状和四角半径
如果不是 RoundedRectangularShape / CornerBasedShape，记录警告并返回

shaderName = dispersionFlag ? "RefractionWithDispersion" : "Refraction"
shader = shaderCache.getOrCreate(shaderName, shaderSource)

shader.setFloatUniform("size", width, height)
shader.setFloatUniform("offset", -offset, -offset)
shader.setFloatUniform("cornerRadii", radii[0..3])
shader.setFloatUniform("refractionHeight", firstFloat)
shader.setFloatUniform("refractionAmount", -secondFloat)
shader.setFloatUniform("depthEffect", depthFlag ? 1.0 : 0.0)
if (dispersionFlag) {
    shader.setFloatUniform("chromaticAberration", 1.0)
}

effect = RenderEffect.createRuntimeShaderEffect(shader, "content")
if (oldEffect != null && SDK_INT >= 31) {
    effect = RenderEffect.createChainEffect(effect, oldEffect)
}
保存 effect
```

上面的 `secondFloat` 符号变化是 DEX 中的真实指令行为；调用点的字段名称应与源码业务字段名称分开记录，因为类和字段已经被混淆。

### 6.2 关键 DEX 证据

| DEX 代码地址 | 证据 |
|---|---|
| `0x41863c` | `Lmx3;.D0:(Lpe0;FFZZ)V` 入口 |
| `0x4187d8` | 创建 `Refraction` shader 源字符串 |
| `0x4187ea` | 创建 `RefractionWithDispersion` shader 源字符串 |
| `0x418828` | 写入 `size` uniform |
| `0x418834` | 写入 `offset` uniform |
| `0x41883a` | 写入 `cornerRadii` uniform |
| `0x418840` | 写入 `refractionHeight` |
| `0x418848` | 写入 `refractionAmount` |
| `0x418856` | 写入 `depthEffect` |
| `0x418860` | 色散路径写入 `chromaticAberration` |
| `0x418866` | 生成 RuntimeShader RenderEffect |
| `0x418886` | 与已有 RenderEffect 组合 |
| `0x28b794` | `RenderEffect.createRuntimeShaderEffect(shader, "content")` |
| `0x2a0d68` | `RenderEffect.createChainEffect(first, second)` |

### 6.3 参数 setter 的对应关系

另一个混淆桥接类中的 setter 直接把参数名写入 `RuntimeShader`：

| DEX 方法 | uniform |
|---|---|
| `Lj1;.p:(RuntimeShader;FF)` | `size` |
| `Lj1;.z:(RuntimeShader;FF)` | `offset` |
| `Lj1;.r:(RuntimeShader;[F)` | `cornerRadii` |
| `Lj1;.B:(RuntimeShader;F)` | `refractionHeight` |
| `Lj1;.C:(RuntimeShader;F)` | `refractionAmount` |
| `Lj1;.D:(RuntimeShader;F)` | `depthEffect` |
| `Lj1;.A:(RuntimeShader;)` | `chromaticAberration = 1.0` |

这条 setter 链是“参数真正进入 shader”的直接证据，不只是配置对象或 UI 文本。

## 7. 配置字段和 UI 字符串

SAMPLE 的 DEX 字符串池保留了以下配置键：

```text
isLiquidGlassEnabled
glassBlurLevel
liquidGlassChromaticAberrationLevel
liquidGlassContrastLevel
liquidGlassDepthEffectLevel
liquidGlassReadabilityLevel
liquidGlassRefractionLevel
liquidGlassSaturationLevel
pendingGlassTypeEnabled
refractionLevel
```

这些键可以证明 SAMPLE 有独立的液态玻璃配置模型，并且至少区分了：

- 模糊；
- 折射；
- 深度；
- 色散；
- 对比度/可读性；
- 饱和度。

但配置键本身尚不足以确认每个滑块都直接映射到 shader uniform。已确认直接写入 D0 shader 的只有上一节列出的 uniforms；其余配置可能用于 APP 的 style、背景层或上游归一化。

## 8. 与当前项目的对照

### 8.1 当前项目已经具备的部分

当前项目已经完成了这些正确方向：

- `GlassHost` 持有窗口级共享状态，并在背景层之后绑定 source。
- `SurfaceRenderer` 集中选择 Material 3、APP glass、APP blur、tint 和 opaque 路径。
- `SurfaceRenderer.kt:177-197` 已建立 typed glass style、固定 optics、深度、折射高度、位移和色散强度参数。
- `SurfaceRenderer.kt:216-228` 已分别调用 typed glass 和 typed blur modifier。
- 无有效背景、硬件加速不可用或平台能力不足时，会进入可观测的 fallback。
- 长列表默认不为每一行创建独立 effect。

这说明当前问题不是“项目完全没有调用库”，而是“当前调用链是否实际形成了 SAMPLE 那种可见的边缘光学响应”仍未被证实，并且源码中没有 SAMPLE 这条独立的 `RuntimeShader + RenderEffect` lens 路径。

### 8.2 关键差异

| 能力 | SAMPLE | 当前项目 | 判断 |
|---|---|---|---|
| 真实背景输入 | shader 通过 `content.eval` 重新采样 | 有共享 source 和 typed glass input | 输入边界正确，但仍需截图确认 |
| 圆角几何 | 四角半径进入 `cornerRadii`，参与 SDF 和梯度 | 主要通过 Compose shape 和 typed style 传递 | 尚未证明四角几何参与实际像素采样 |
| 边缘折射 | 自定义 shader 只对边缘带偏移采样 | 当前源码没有本地 RuntimeShader lens | 这是最重要的能力缺口 |
| 深度 | 改变折射梯度方向 | 参与 style 参数和 surface depth | 参数存在不等于像素位移存在 |
| 色散 | 七次带颜色通道的偏移采样 | 设置 typed glass 的色散强度 | 尚未证明设备上产生彩色边缘 |
| effect 组合 | `createRuntimeShaderEffect` 后可与旧 effect chain | 当前主要由单一 typed modifier 负责 | 组合顺序需要运行时验证 |
| 低版本行为 | D0 在 SDK 33 以下直接返回 | 当前已有平台 fallback | 两者应统一为明确的 Material 3/blur 回退 |

### 8.3 为什么“被调用但没有效果”仍可能发生

从第一性原理看，玻璃效果必须满足：

```text
可见效果 = 有效背景输入 × 实际像素采样偏移 × 正确的 surface 几何 × 可运行的 GPU effect
```

只满足其中一项尚不构成最终效果：

- 有 source，但 effect surface 没有真实 attached input，只会得到 tint 或空白 fallback；
- 有 blur，但没有坐标偏移，只会得到毛玻璃；
- 有 `refractionStrength` 参数，但没有 shader 消费它，只会改变配置对象；
- 有 `chromaticAberrationStrength` 参数，但缺少多通道采样时，输出仍不会形成色散；
- 有 shader，但 size/radius/offset 没有按实际布局更新，旋转或重组后效果会错位；
- 有 effect，但被不透明的 Surface 颜色完全盖住，视觉上看不到背景响应。

SAMPLE 的 DEX 证据覆盖了“输入被重新采样”和“边缘坐标被偏移”这两个关键环节；当前项目的静态代码只能证明 typed API 被调用，真实截图和运行诊断仍是独立证据。

## 9. 对当前重构的建议

### 9.1 渲染层拆分

建议把当前 surface adapter 明确拆成以下 renderer，不让页面直接操作 APP 或 Android effect API：

```text
SurfaceRenderer
├── Material3Renderer
├── AppBlurRenderer
├── AppTypedGlassRenderer
└── CustomLensRenderer
    ├── Refraction shader
    └── RefractionWithDispersion shader
```

其中：

- `AppTypedGlassRenderer` 负责库提供的背景采样、模糊、tint 和平台适配。
- `CustomLensRenderer` 只负责 SAMPLE 证明过的边缘折射/色散，不承担设置持久化和页面状态。
- 页面只传 `SurfaceSpec`、实际尺寸、shape、背景资格和 typed glass config。
- Material 3、APP blur、APP glass、Custom lens 必须在诊断中分别记录实际 renderer 与用户模式，避免只记录“液态玻璃”选择。

### 9.2 第一阶段不要把自定义 lens 放进长列表

先只在以下 surface 做验证：

1. Home Hero；
2. 连接状态横幅；
3. Device 页面一至三个 FeatureCard。

StandardCard、CompactRow、Dialog 和长列表继续使用 tint/Material 3，必要时使用低强度 blur。这样可以先证明像素效果，再控制 GPU 成本，不把“效果问题”和“列表卡顿问题”混在一起。

### 9.3 CustomLensRenderer 的实现契约

实现时应固定以下契约：

```kotlin
data class LensUniforms(
    val sizePx: Size,
    val offsetPx: Offset,
    val cornerRadiiPx: FloatArray, // top-left, top-right, bottom-right, bottom-left
    val refractionHeightPx: Float,
    val refractionAmountPx: Float,
    val depthEffect: Float,
    val chromaticAberration: Float,
)
```

核心顺序：

```text
1. 由布局结果获得实际 size 和四角半径
2. 由同一个 background/source 提供 content input
3. 先计算 rounded-rect SDF 和 edge band
4. 非 edge band 直接返回原始 content
5. edge band 计算 grad、d 和 refractedCoord
6. 根据色散开关选择单次采样或七次通道采样
7. 通过 RenderEffect 绑定到 surface layer
8. 只在尺寸、shape、参数或背景输入变化时更新 effect
```

应避免把 `depth` 只映射到 border alpha，或把 `chromaticAberration` 只映射到一条彩色边框；这两种实现尚未形成 SAMPLE 的计算路径。

### 9.4 输入和 effect 顺序必须单独验证

SAMPLE 的 `content` 是 RuntimeShader 的输入 shader，而不是 shader 内部生成的纯色。当前重构至少需要对下面两种顺序各做一次运行截图：

```text
A. 背景 source → APP blur/glass → CustomLens
B. 背景 source → CustomLens → APP blur/glass
```

用非纯色、带明显文字或渐变的背景观察：

- 边缘内外的内容是否发生位移；
- blur 是否仍然存在；
- 彩色分离是否只发生在边缘；
- 文字是否被不透明 Surface 盖住；
- 旋转和尺寸变化后坐标是否仍然正确。

静态代码不足以直接决定 A/B 哪个是当前库的正确组合，应通过运行时诊断和截图选择。

## 10. 定向验收项目

本报告只增加与液态玻璃 renderer 直接相关的验证，不复跑蓝牙矩阵：

| 验证项 | 操作 | 通过条件 |
|---|---|---|
| `UI_LIQUID_RENDERER` | API 33+、硬件加速、非纯色背景打开 Hero/FeatureCard | 诊断显示真实 renderer，边缘背景有可见位移 |
| `UI_LIQUID_DISPERSION` | 开关色散，对比同一背景截图 | 只有边缘出现彩色分离，关闭后无彩边 |
| `UI_LIQUID_GEOMETRY` | 改变四角半径、旋转、改变窗口尺寸 | 四角折射范围随 shape/尺寸更新，不错位 |
| `UI_LIQUID_INPUT` | 无背景、背景加载中、加载失败、纯色背景 | 不出现透明空洞，明确回退 Material 3/tint |
| `UI_LIQUID_ORDER` | 分别验证两种 effect 组合顺序 | blur、折射和文字可读性均符合预期 |
| `UI_LIQUID_PERFORMANCE` | Home/Device 首屏与滚动各 3 轮 | 长列表不逐行创建 lens/effect，记录 effect 数和滚动 P95 |
| `UI_LIQUID_RECOMPOSITION` | 切换 route、重建、切换 classic/liquid | source/effect 数量不累积，surface 不变空或不透明错误色块 |

运行诊断至少记录：

```text
requestedRenderer
actualRenderer
sourceAttached
effectSurfaceCount
apiLevel
hardwareAccelerated
surfaceSizePx
cornerRadiiPx
shaderVariant
fallbackReason
```

不记录壁纸 URI、设备地址或协议 payload。

## 11. 证据边界和当前状态

### 已确认

- SAMPLE 的 DEX 内存在 APP 的 source/effect/glass 相关实现符号。
- SAMPLE 内存在 `Refraction` 和 `RefractionWithDispersion` 两个自定义 shader 源。
- 两个 shader 都从 `content` 重新采样。
- 折射只作用于圆角矩形边缘带。
- `depthEffect` 参与折射梯度，`chromaticAberration` 参与多次彩色采样。
- `RuntimeShader` 通过 `RenderEffect.createRuntimeShaderEffect` 绑定，并可用 `createChainEffect` 与已有 effect 组合。
- 当前项目的 typed glass API 已在 `SurfaceRenderer` 中调用，但当前项目没有与上述自定义 shader 等价的本地 lens 路径。

### 尚未确认

- SAMPLE 每一个页面具体使用了 APP glass、普通 blur、CustomLens 还是多种组合。
- SAMPLE 的每个设置键如何映射到 D0 的两个 float 和两个 boolean。
- 当前项目的 typed glass modifier 在目标设备上是否真的产生了边缘折射和色散。
- A/B 两种 effect 顺序中哪一种最接近 SAMPLE 的实际页面效果。

### 本轮交付边界

- 已新增本报告。
- 未修改 SAMPLE。
- 未把 SAMPLE、DEX dump 或临时分析目录写入仓库。
- 未修改 UI 代码，未执行本地 Gradle 构建。
- 后续代码实现应以本报告的 `CustomLensRenderer` 契约和定向验收项目为准。
