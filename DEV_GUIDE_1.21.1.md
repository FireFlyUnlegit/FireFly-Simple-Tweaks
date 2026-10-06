# DEV_GUIDE_1.21.1.md — 接手这个分支，从这一份开始

> **给新会话 / 新协作者的操作手册。**
> `MIGRATION.md` 是**流水账与决策记录**（append-only，只增不改，用来回答"当初为什么这么做"）；
> 这份文件回答"我现在要动手，该怎么做"。
>
> 读者假设：会 Kotlin / Java，知道 Mixin 是什么，但**不了解这个仓库**。

---

## 0. 三十秒速览

| 事实 | 值 |
|---|---|
| 两个**互相独立**的 Gradle 工程 | 本目录 = MC 1.21.1 / Fabric / Yarn；上一级 `../` = MC 1.12.2 / Forge，**原样保留供对照**，不要改 |
| 产物 | `build/libs/simple_tweaks-2.0.0.jar` |
| 部署 | 复制到 `F:\.minecraft\versions\1.21.1-Fabric 0.19.3\mods\`（旧的先存成 `.prev`） |
| 当前构建标识 | `build=cleanup6`（每次改动客户端可见行为都要**推进**这个标识，见 §2.4） |
| 附魔定义方式 | **两代并存**：56 个旧的（PS1 生成的表驱动）+ 新增的走 KSP `@ModEnchantment`（见 §3） |
| 验收 | **由作者本人在客户端进行**。代理**不要**跑 `runClient`（需要真实客户端 + 人工判读手感/画面） |
| 规模 | kotlin 127 文件 / java 30 文件 / 附魔 JSON 56 份 / mixin 注册 24 个 / 处理器 56 个 |

---

## 1. 项目结构

```
FireFly's Simple Tweaks-1.12.2/          ← 仓库根（1.12.2 Forge 工程，reference only）
├── build.gradle  src/  ...              ← 原版源码，随时对照；不要在此改动
└── fabric-1.21.1/                       ← 本工程（独立 Gradle build，有自己的 wrapper）
    ├── build.gradle                     ← Loom + Kotlin + KSP + fabric-api + fabric-language-kotlin
    ├── settings.gradle                  ← include 'ksp-processor'
    ├── gradle.properties                ← 版本坐标（**改前先看 §2.3**）
    ├── MIGRATION.md                     ← 流水账 / 决策记录（append-only）
    ├── DEV_GUIDE_1.21.1.md              ← 本文件
    ├── ksp-processor/                   ← 纯 Kotlin/JVM 库：注解处理器（**不应用 Loom**）
    │   └── src/main/kotlin/.../ksp/ModEnchantmentProcessor.kt
    │   └── src/main/resources/META-INF/services/...SymbolProcessorProvider
    ├── docs/                            ← 专项笔记（地图见 §9）
    ├── tools/                           ← gen-enchantments.ps1（旧表生成器）、yarn-query.ps1（离线名称查询）
    ├── run/                             ← runClient/runServer 的游戏目录（**与正式客户端是两份配置**）
    └── src/main/
        ├── java/dev/firefly/simpletweaks/
        │   ├── mixin/                   ← 所有 Mixin（24 个注册 + disabled/ 3 个未注册）
        │   └── interfaces/              ← 给 Mixin 用的接口注入（如 SimpleTweaksArrow）
        ├── kotlin/dev/firefly/simpletweaks/
        │   ├── SimpleTweaks.kt          ← 入口（ModInitializer）：config → 事件桥 → 处理器 → 网络 → 粒子
        │   ├── SimpleTweaksClient.kt    ← 客户端入口 + **构建标识日志**（§2.4）
        │   ├── compat/                  ← Forge 兼容层：ForgeEventBus / 事件类 / STLog / ChargeBoost
        │   │   ├── bridge/              ← Fabric 回调 → Forge 形状事件
        │   │   └── event/               ← 事件类型（ArrowLooseEvent / LivingHurtEvent / SubscribeEvent…）
        │   ├── core/                    ← 总线注册、EnchantmentManager（手写 handler 清单）、Listenable
        │   │   └── config/              ← GeneralConfig / DamageIndicatorConfig / SimpleTweaksConfig（JSON 读写）
        │   ├── client/                  ← 配置界面、图鉴界面、客户端指令、粒子、tooltip
        │   ├── damageindicator/         ← 伤害飘字
        │   ├── enchantments/
        │   │   ├── annotations/         ← **@ModEnchantment**（注解本体）
        │   │   ├── generated/           ← **KSP 生成**（不要手改）
        │   │   ├── EnchantmentMeta.kt   ← 手写门面：合并"旧表 + KSP 表"
        │   │   ├── ModEnchantmentKeys.kt / EnchantmentTiers.kt / EnchantmentNameColors.kt ← 旧表（PS1 生成，勿手改）
        │   │   └── handlers/{common,uncommon,rare,epic,legendary,mythic,mystery,unique}/
        │   ├── network/                 ← 自定义包（ID + 编解码 + 收发）
        │   ├── particle/                ← 粒子类型注册
        │   └── util/                    ← 附魔查询、玩家工具、伤害源
        └── resources/
            ├── fabric.mod.json  simple_tweaks.mixins.json
            ├── assets/simple_tweaks/lang/{en_us,zh_cn}.json    ← 附魔名与描述（**手写**）
            └── data/simple_tweaks/enchantment/*.json           ← 旧附魔的 datapack 定义（PS1 生成）
```

**handler 数量分布**（`enchantments/handlers/`）：mythic 14、rare 11、epic 9、uncommon 9、legendary 8、common 6、mystery 2、unique 1、infinitepower 7、disabled 3。

---

## 2. 构建

### 2.1 命令（**必须设这两个环境变量**）

```powershell
$env:JAVA_HOME='C:\Users\Administrator\.jdks\temurin-21.0.8'
$env:GRADLE_USER_HOME='E:\gradle'
cd 'F:\Codes\Mod\FireFly''s Simple Tweaks-1.12.2\fabric-1.21.1'
.\gradlew.bat clean build
```

| 变量 | 为什么 |
|---|---|
| `JAVA_HOME` | **本机默认是指向 JDK 8 的**（1.12.2 工程需要它）。不覆盖就会得到 `You are using an outdated version of Java (8). Java 17 or higher is required.` —— 看起来像 Loom 坏了，其实是环境变量。**这是第一个坑，每台新机器都会踩。** |
| `GRADLE_USER_HOME` | 本机 Gradle 缓存固定在 `E:\gradle`（默认位置不在这里）。不设会重新下载全部依赖。 |

也可以 `.\gradlew.bat build`（增量），但见 §2.2。

### 2.2 什么时候**必须** `clean`

**删除了任何源文件之后，必须 `clean build`。**

Kotlin 增量编译**不会删除**已移除源文件对应的 `.class`，而 `jar` 任务照打不误 —— 实测 `DevTestCommand.class` 就这样差一点被发出去（`MIGRATION.md` §11 记录了这次）。症状是"源码里明明删了，jar 里还在"。

### 2.3 版本坐标（`gradle.properties`，改前先读）

已核实的坐标全部锁死，其中两条有坑：

- `fabric_kotlin_version` 必须匹配 `kotlin_version`：**`1.12.1+kotlin.2.0.21` 不存在**（1.12.1/1.12.2 是 kotlin.2.0.20，2.0.21 从 1.12.3 起）。
- `ksp_version` 必须与 `kotlin_version` **同线**：`2.0.21-1.0.28` 是 Kotlin 2.0.21 的最后一个 KSP 发布。**KSP 版本号格式是 `<kotlin>-<ksp>`**，跨线会直接报插件解析失败。

### 2.4 构建标识（每次动客户端行为的改动都要推进）

`SimpleTweaksClient` 里有一行超长的启动日志，结尾是 `(build=cleanupN)`。**它的唯一作用是区分"功能没生效"和"客户端还在跑旧 jar"** —— 这个区别曾经浪费掉一整轮排查。

改动 → 推进编号 → 重新构建安装 → 在 `docs/acceptance-checklist.md` 页首同步 SHA256 / 字节数 / 标识。

### 2.5 安装

```powershell
$mods = 'F:\.minecraft\versions\1.21.1-Fabric 0.19.3\mods'
Copy-Item "$mods\simple_tweaks-2.0.0.jar" "$mods\simple_tweaks-2.0.0.jar.prev" -Force
Copy-Item 'build\libs\simple_tweaks-2.0.0.jar' "$mods\simple_tweaks-2.0.0.jar" -Force
```

装完顺手对比一次 SHA256（构建产物 vs 已安装），能挡住"复制了但没覆盖"这种低级事故。

> ⚠️ **`run/` 与正式客户端是两套配置与存档**。在 `runClient` 里改的设置不会出现在正式客户端，反之亦然。

### 2.6 IDEA 用户注意

`build.gradle` / `settings.gradle` / 依赖发生变化后，**必须在 IDEA 里 Reload Gradle Project**。否则 IDEA 用缓存的旧模型启动，症状是**mod 根本没被加载**（日志里 `Loading N mods` 列表中没有 `simple_tweaks`，所有指令都不存在），极易误判成"功能被删了"。

---

## 3. KSP：加一个附魔

**目标：写一个文件。** 其余（datapack JSON、注册表键、图鉴元数据、事件总线注册）全部编译期生成。

### 3.1 注解字段

```kotlin
@ModEnchantment(
    id = "fast_bow",              // 注册路径 / JSON 文件名 / lang 键后缀，必须 [a-z0-9_]
    category = "rare",            // 1.12.2 EnchantmentCategories，小写；图鉴按它分组（也是名字颜色来源）
    type = "bow",                 // 1.12.2 ModEnchantmentType，小写；图鉴显示为"适用"
    color = "blue",               // 可选；留空 = 用 category 的颜色
    maxLevel = 3,                 // >= 1
    weight = 5,                   // 附魔台权重，>= 1
    anvilCost = 6,
    minCostBase = 20,
    minCostPerLevel = 10,         // 可选，默认 0
    supportedItems = "#minecraft:enchantable/bow",
    primaryItems = "",            // 可选；留空 = 同 supportedItems
    slots = ["mainhand", "offhand"],
    maxCostBase = 65535,          // 可选
    maxCostPerLevel = 0,          // 可选
)
object EnchantFastBowHandler : Listenable { ... }
```

`category` 写错（不在 8 个值里）、`id` 不合规、`maxLevel < 1`、`slots` 为空、两个 `@ModEnchantment` 用了同一个 `id` —— 这些都会**在构建期报错**，不会静默通过。处理器会打印一行 `ModEnchantment: N annotated handler(s) -> M enchantment(s)`。

### 3.2 生成物

| 生成物 | 位置 |
|---|---|
| `<id>.json` | `build/generated/ksp/main/resources/data/simple_tweaks/enchantment/` |
| `GeneratedEnchantments.kt`（`<ID>` 键 / `KEYS` / `CATEGORY` / `TYPE` / `COLOR` / `MAX_LEVEL` / `HANDLERS`） | `build/generated/ksp/main/kotlin/.../enchantments/generated/` |

KSP 会把 `resources` 与 `kotlin` 两个输出目录自动挂到 `main` source set，**不需要在 `build.gradle` 里写任何 `sourceSets` 接线**。

### 3.3 你必须手写的两件事

1. **lang**：`enchantment.simple_tweaks.<id>` 与 `enchantment.simple_tweaks.<id>.desc`，两种语言各两条。
2. **handler 逻辑**（如果这个附魔需要事件）。

**注册是自动的**：`GeneratedEnchantments.HANDLERS` 被 `EnchantmentManager.initHandlers()` 合并进总线，**不要**再手写进那个清单。图鉴与工具提示也自动可见（走 `EnchantmentMeta`）。

### 3.4 写的 handler 长什么样

```kotlin
object EnchantXxxHandler : Listenable {          // 必须实现 Listenable

    @SubscribeEvent                               // priority 默认 NORMAL
    fun onSomething(e: SomeEvent) { ... }         // 方法名随意，靠注解发现
}
```

- 事件类型在 `compat/event/`。**没有需要的接缝时，先去 §4 加一个 Mixin**，不要在 handler 里做反射或轮询。
- 每 tick 的事件（`PlayerTickEvent` 等）**只在状态跃迁时打日志**，否则会刷爆日志。
- 读等级用 `util/EnchantmentsUtil.kt` 的 `getItemSpecificEnchantLevel(stack, key)`（按组件匹配，不需要世界/注册表）。

### 3.5 迁移旧附魔（一个一个搬，别一次全搬）

给旧附魔的 handler 加注解 → 从 `tools/gen-enchantments.ps1` 的表里删掉它。`EnchantmentMeta` 里 **KSP 侧优先**，两代并存不冲突。一次性迁移 56 个等于制造 56 个潜在回归。

### 3.6 处理器本身的两个反直觉点（改 processor 前必读）

1. **不要用 `validate()` 做 deferral。** KSP 的常规写法是"把未解析符号 return 出去等下一轮"，但被注解的 handler **自己引用了本 processor 即将生成的 `GeneratedEnchantments.<ID>`** —— `validate()` 每轮都是 false，结果是第 1 轮生成空文件、第 2 轮再生成撞 `FileAlreadyExistsException`。
2. **`process()` 每轮都会被调用**，而"某轮生成过文件"必然触发下一轮 → 必须防重（现有实现用 `done` 标志 + `codeGenerator.generatedFile` 双保险）。

---

## 4. 写一个 Mixin

### 4.1 放哪 + 注册

文件放 `src/main/java/dev/firefly/simpletweaks/mixin/`，然后**必须**注册进 `src/main/resources/simple_tweaks.mixins.json`：

| 列表 | 用于 |
|---|---|
| `mixins` | 双端都要的（逻辑/数据） |
| `client` | 纯视觉/客户端。**注意：单机下内置服务端也在这个 JVM 里，同一个类同样被改** —— 见 §4.5 |
| `server` | 目前为空 |

`"required": true` + `"injectors": { "defaultRequire": 1 }`：**注入目标找不到 = 启动崩溃**。这是好事（不静默），但意味着目标方法名写错会立刻炸。

### 4.2 动手前：override 审计（铁律二）

**先确认你要注入的方法，在运行时实际执行的是哪个类的实现** —— Mixin 只改 `@Mixin` 的那个类，**不会传播到子类的重写**。

```powershell
$javap = 'C:\Users\Administrator\.jdks\temurin-21.0.8\bin\javap.exe'
$jar = (Get-ChildItem -Recurse -Path '.gradle\loom-cache\minecraftMaven' `
        -Filter 'minecraft-merged-*.jar' |
        Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1).FullName

# 1) 谁声明了它、谁重写了它
foreach ($c in 'net.minecraft.entity.LivingEntity','net.minecraft.entity.player.PlayerEntity') {
  & $javap -p -cp $jar $c | Select-String 'methodName'
}
# 2) 方法体（判断是否调 super、常量是 40 还是 getstatic）
& $javap -p -c -cp $jar net.minecraft.item.BowItem | Select-String 'getPullProgress' -Context 0,3
```

`javap` 会按 ~100 列折行，**折行会把 `Method owner.name` 从中间劈开** —— 正则匹配前先 `-join '' -replace '\s',''`，否则会漏报（`MIGRATION.md` 铁律二记录了这次误判）。

### 4.3 注入点选择

| 场景 | 用法 |
|---|---|
| 精确改某个常量 | `@ModifyConstant`，**但先数清常量出现了几次** |
| 改方法参数 / 返回值 | `@ModifyArg` / `@ModifyReturnValue`（MixinExtras 可用） |
| 通知/取消 | `@Inject(at = @At("HEAD"), cancellable = true)` |
| 需要改局部变量 | `@ModifyVariable` |

**`ordinal` 是 `@Constant` 的元素，不是 `@ModifyConstant` 的。** 这条由 `javap` 查 `sponge-mixin` 注解定义确认过（`MIGRATION.md` §14.2）。

> ⚠️ **反面教材（铁砧）**：`AnvilScreenHandler.updateResult()` 里有 **三处** `bipush 40`（材料堆叠花费 / 创造模式钳位 / 真正的"过于昂贵"闸门）。裸 `@ModifyConstant(intValue=40)` 会同时改掉三处，把"用一叠材料修理"的代价变成 21 亿。**只有核实过常量个数，才敢写或不写 ordinal。**
>
> **正面例（弓）**：`BowItem.onStoppedUsing` 里 `getPullProgress(I)F` **只出现一次且是 static** → `@ModifyArg` 不需要 `slice`/`ordinal`。

### 4.4 验证注入是否"绑上"了

**refmap 按 mixin 类名索引，不是按目标类名** —— 用目标类名去查会得出"没有映射"的错误结论（我犯过）：

```powershell
# 解 jar 里的 simple_tweaks-refmap.json，看 mappings.<mixin 全限定名>
# 期望看到类似：getItemUseTimeLeft -> Lnet/minecraft/class_1309;method_6014()I
# 注意 @Shadow 字段不进 refmap（这不代表没生效）
```

**refmap 有映射 ≠ 接缝活着**（铁律一）。构建关过了只说明"方法名解析成功"，还必须过静态关（§4.2）与动态关（§7 的验收）。

### 4.5 客户端 Mixin 的陷阱：单机下它也改内置服务端

`client` 列表里的 mixin 打的是**共享类**。单机时客户端与内置服务端在同一个 JVM，所以服务端路径**也会被改**。如果两侧都乘一次倍率，会变成平方 —— 表现为"单机比多人强、且与画面不符"。

现成范例：`LivingEntityDrawSpeedMixin` 用 `if (!self.getWorld().isClient()) return original;` 明确让出服务端那条路，由服务端的 `@ModifyArg` 单独负责。

### 4.6 你改的可能是共用注入点

`BowItemArrowLooseMixin` 同时服务 `multishot` / `tracking_arrow` / `piercing_arrow` / `starfall`。改这类接缝时，验收清单里必须**显式列出回归项**。

---

## 5. 四条铁律

> 原文在 `MIGRATION.md` 开头（①②）与 §9 收尾（四条索引）。这里给操作版。

### ① refmap 有映射 ≠ 接缝活着

refmap 有映射**只**说明 Mixin 注解处理器成功解析了那个方法名；它**完全不能说明**这段代码运行时会不会被执行。

Mixin 只改写 `@Mixin` 那个类的方法体。1.21 里不少基类方法被**完整重写**（不调 `super`），注入永远不执行 —— 本项目曾因此有两个接缝一次都没生效、整个伤害层是死的，而**第一轮验收 2/2 通过**（那两个用例恰好走了没有重写子类的方法）。

**每个新接缝必须过三关**：静态关（override 审计，§4.2）→ 构建关（refmap 有映射，**最容易过、最不能说明问题**）→ 动态关（真实入口驱动一次，看到日志）。**没有动态证据的接缝一律记「未验证」，不得写成「已实现」。**

### ② Mixin 目标必须用字节码核实

不要靠"子类应该会调 super 吧"的直觉，也不要靠对 API 名的记忆。判定规则：

- 声明了该方法且**没有任何** `invokespecial <super>.METHOD` ⇒ "顶层重写"，**必须**成为注入目标。
- 声明了且调了 super ⇒ 会被上层目标覆盖，**不要重复 mixin**（否则触发两次）。
- `@Mixin({A.class, B.class})` 是多目标写法，用于"A、B 都是顶层重写"。

### ③ "看起来只做持久化"的方法往往同时承担状态同步职责

实例：`markDirty()` 被写成空实现 ⇒ 服务端改了内容却**从不通知客户端**。另一例：`getItemUseTimeLeft()` 看起来只是个字段 getter，实际**同时**喂给拉弓动画与射箭威力。

**改任何方法前，先问"还有谁在读它"**（`javap` 扫调用点），不要凭方法名推断职责。

### ④ 没有观测量时的改动等于猜

**先设计日志，再改代码。** 反面教材：Multishot 无敌帧"没生效"排查了多轮，最后发现它一直是对的，只是**没有观测手段**能区分"没触发"和"触发了但没效果"。

配套：`compat/STLog.kt` 提供 `STLog.log("Name") { "k=v, ..." }`，**默认关闭**（见 §7.3）。

---

## 6. 常见坑（全部真踩过）

| # | 现象 | 根因 / 规避 |
|---|---|---|
| 1 | Loom 报 `outdated version of Java (8)` | `JAVA_HOME` 指向 JDK 8（1.12.2 工程需要）。构建 1.21.1 前必须覆盖成 JDK 21（§2.1） |
| 2 | 源码里删了，jar 里还在 | Kotlin 增量编译不删 `.class`。**删源文件后必须 `clean build`**（§2.2） |
| 3 | 配置项能改能存，但游戏里没反应 | **配置键存在 ≠ 有代码读它**。已发生 3 次：`maxEnchantmentPower` / `disableEnchantmentTableLimit`、`anvilDisenchant`、`yStartFactor`（公式语义错）。新增配置项时，**必须同时写清"谁读它"**，并加一条验收 |
| 4 | 新生成的数据/表没生效 | **生成了 ≠ 接上了**。已发生 2 次：KSP 生成的 `COLOR` 表没人读（fast_bow 显示成 COMMON 的灰）、`FAST_BOW` 键没进 `ALL`（图鉴里看不见）。新增任何表/清单，**必须同时接消费点** |
| 5 | 改了"看起来对"的地方却没效果 | **先确认客户端真正读的是哪条路径**。fast_bow 第一版只改 `BowItem.getPullProgress`（松手威力），而 `HeldItemRenderer` 读的是 `getItemUseTimeLeft()` —— 拉弓动画完全没变（§5③） |
| 6 | 单机比预期强 / 与画面不符 | `client` mixin 在单机下也改内置服务端。两侧都乘会平方（§4.5） |
| 7 | KSP 报 `FileAlreadyExistsException` | `process()` 每轮都调用 + `validate()` deferral 死锁（§3.6） |
| 8 | 改了 `build.gradle` 后 mod 完全不加载 | IDEA 用缓存的旧 Gradle 模型启动。**Reload Gradle Project**（§2.6）。判据：日志 `Loading N mods` 里没有 `simple_tweaks` |
| 9 | 画面/手感类改动"看不出来" | **验收判据要可感知**：写清基准线（无附魔）与对比量（tick 数 / 落点 / 颜色），不要只写"变快了" |
| 10 | `Slot.x`/`y` 写入报 `IllegalAccessError` | 1.21.1 里它们是 `final`，需要 `@Mutable @Shadow` |
| 11 | 找不到 `[ST-*]` 日志 | **调试日志默认关闭**，要 `-Dsimpletweaks.debug=true`（或环境变量 `SIMPLETWEAKS_DEBUG=true`） |
| 12 | Git 提交后编辑工具报"文件已变化" | 仓库开了 `core.autocrlf`，`git add` 会按 CRLF 重写工作区文件。重新读一次即可（代理向的坑） |
| 13 | `AnvilScreen`/`AnvilScreenHandler` 的 40 改错 | 同一字面量在方法里出现多次，**必须核实个数再决定 ordinal**（§4.3） |
| 14 | `getMaxUseTime` 当成"拉弓时间" | 弓的它是 72000（最长持有时长），**不是**蓄满所需的 20 tick |

---

## 7. 验收脚手架

### 7.1 结构：`docs/acceptance-checklist.md`

- 页首固定写：**目标 jar 路径、SHA256、字节数、构建标识、安装位置、日志位置、回退点**。
- 按**字母分组**（A 附魔名颜色 / B 伤害飘字 / … / M 收尾 / N 铁砧 / O FastBow），每组一个小表：`# / 操作 / 期望`。
- **每条期望都必须可判定**（具体数字、具体颜色、具体"有/无"），并尽量成对给出**反例**（例：O2.4"拉满后与无附魔完全一致"用来证明没有超上限）。
- 编号**只增不重排**（缺号保留，如 §13 / J3），避免破坏别处引用。
- 未做的功能统一记在**「已知未做」表**，注明是"作者裁定不需要"还是"未移植"，**避免被当 bug 报**。

### 7.2 失败/成功都要留证据

分组末尾写清"若这条不符，请把什么发给我"（配置文件、日志片段、截图），并指明**下一步该查什么**（例：铁砧 ordinal、KSP 生成物）。这能把来回轮次压到最少。

### 7.3 调试日志开关

| 方式 | 写法 |
|---|---|
| JVM 参数 | `-Dsimpletweaks.debug=true` |
| 环境变量 | `SIMPLETWEAKS_DEBUG=true`（等价 `=1`），便于 `gradlew runClient` 不改 `build.gradle` |

**启动时读一次**，改了要重启。开出来的日志形如 `[ST-FastBow] boost=1.75, charge=20`，用 `Select-String '\[ST-'` 抓取。

**保持无条件输出**（不该被开关吞掉）：`loading` / `load complete` / `client ready: ... (build=cleanupN)` / `Config loaded from ...` / `Registered N enchantment handler(s)`，以及全部 `warn` / `error`。

### 7.4 谁验收

**作者本人在正式客户端验收。代理不要跑 `runClient`**（需要真实客户端与人工判读）。`runServer` 只用于冒烟（mixin 是否绑上、数据包是否加载），**不构成行为验收**。

---

## 8. 常用诊断命令

```powershell
$javap  = 'C:\Users\Administrator\.jdks\temurin-21.0.8\bin\javap.exe'
$mcJar  = (Get-ChildItem -Recurse -Path '.gradle\loom-cache\minecraftMaven' `
           -Filter 'minecraft-merged-*.jar' |
           Where-Object { $_.Name -notmatch 'sources' } | Select-Object -First 1).FullName

& $javap -p -cp $mcJar net.minecraft.item.BowItem              # 签名 + 字段
& $javap -p -c -cp $mcJar net.minecraft.item.BowItem           # 字节码（常量、调用点）
& $javap -v -p -cp build\libs\simple_tweaks-2.0.0.jar dev.firefly.simpletweaks.mixin.XxxMixin  # 注解实际取值
```

| 要看什么 | 位置 |
|---|---|
| 未 remap 的 class（验证源码改动） | `build/classes/{kotlin,java}/main/...` |
| refmap | `build/libs/simple_tweaks-2.0.0.jar` 内的 `simple_tweaks-refmap.json` |
| KSP 生成物 | `build/generated/ksp/main/{kotlin,resources}/` |
| 游戏日志 | 正式客户端 `<gameDir>\logs\latest.log`；开发 `run/logs/latest.log` |
| 离线查 Yarn 名称 | `tools/yarn-query.ps1` |
| 旧附魔表生成器（迁移时用） | `tools/gen-enchantments.ps1` |

**验证 jar 内容**（PowerShell，`-match` 默认不区分大小写，注意误命中）：

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path 'build\libs\simple_tweaks-2.0.0.jar'))
@($z.Entries | Where-Object { $_.FullName -like 'data/simple_tweaks/enchantment/*.json' }).Count
$z.Dispose()
```

---

## 9. 文档地图

| 文件 | 负责回答 |
|---|---|
| `DEV_GUIDE_1.21.1.md`（本文件） | **怎么做** —— 结构 / 构建 / 加附魔 / 写 Mixin / 铁律 / 坑 / 验收 |
| `MIGRATION.md` | **当初为什么** —— 阶段流水账、决策记录、踩坑经过、版本核实、§10 声明式 vs Kotlin 的判定表、§14 铁砧、§15 KSP。**append-only** |
| `docs/acceptance-checklist.md` | 客户端验收清单 + **已知未做**表（未做事项的唯一去处） |
| `docs/phase4-mixin-notes.md` | Mixin 目标选择的早期考古（含被主动删除的粒子抑制 mixin） |
| `docs/phase6-client-notes.md` | 阶段 6 客户端细节：飘字、图鉴、Starfall 世界地板、Multishot 无敌帧 |
| `docs/infinite-power-deferred.md` | InfinitePower 全记录，含**背包为何被舍弃**（§17） |
| `docs/phase3-{member-map,port-spec}.md` | 阶段 3 的成员映射与移植规格（历史） |

> 迁移已结束，功能状态：附魔台 / 弓箭三件套 + Starfall / CelestialBlessing / InfinitePower 核心 + 激光 **均已实测通过**；Velocity 与铁砧祛魔 **作者裁定不需要**；InfinitePower 背包 **8 次尝试后舍弃**。
