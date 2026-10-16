# AGENTS.md —— 给 AI 代理的操作契约

> **这是代理的入口文件，不是人类教程。** 人类版（结构 / 原理 / 为什么）在
> [`DEV_GUIDE_1.21.1.md`](DEV_GUIDE_1.21.1.md)。
>
> 两份的分工是刻意的：**事实只在一处定义**，另一份只放指针，免得两份漂移。
> 本文件拥有：硬约束、确切命令、任务→文件查找表、完成判据、禁止事项、失败取证协议。
> `DEV_GUIDE` 拥有：项目结构、构建原理、KSP/Mixin 教程与例子、坑的来龙去脉。
> **两者冲突时以本文件为准**，并顺手修掉 `DEV_GUIDE`。

改动历史与"本批未验收清单"见 [`DEVLOG.md`](DEVLOG.md)；逐条验收项见
[`docs/acceptance-checklist.md`](docs/acceptance-checklist.md)。

---

## 0. 三条前置事实（先读）

1. **工程根就是本目录**（`fabric-1.21.1/`，独立 Gradle 工程，自带 wrapper）。
   上一级 `../` 是 1.12.2 Forge 原工程 —— **只读参考，绝不要修改**（它是对照物）。
2. **构建必须设两个环境变量。不设 = 失败，不是"变慢"**（详见 §1）。
3. **验收由作者本人在正式客户端做。代理不要跑 `runClient`。**
   `runServer` 只用于冒烟（mixin 是否绑上、数据包是否加载），**不构成行为验收**。

---

## 1. 构建与验证：确切命令

```powershell
$env:JAVA_HOME = "C:\Users\Administrator\.jdks\temurin-21.0.8"
$env:GRADLE_USER_HOME = "E:\gradle"
cd '<工程根>'
.\gradlew.bat build --console=plain
```

| 事实 | 值 |
|---|---|
| 产物 | `build/libs/simple_tweaks-2.1.0.jar`（版本取自 `gradle.properties`） |
| 构建标识 | 源码里一行 `build=cleanupN`（`SimpleTweaksClient`）；**改客户端可见行为就要推进它** |
| 当前标识 | `build=cleanup23` |

**硬规则**

- **删除了任何源文件之后必须 `clean build`。** Kotlin 增量编译**不会**删除已移除源文件对应的
  `.class`，而 `jar` 任务照打不误 —— 症状是"源码里删了，jar 里还在"。
- **判断编译失败只看 `^e:` 行。** 如果没有 `e:` 行却 `BUILD FAILED`，几乎总是环境变量问题
  （`JAVA_HOME` 指向 JDK 8）—— 去看 `build/reports/problems/problems-report.html`，**不要翻源码**。
- **要装的是 `build/libs/simple_tweaks-2.1.0.jar`（remap 后），绝不是 `build/devlibs/*-dev.jar`。**
  `-dev.jar` 未 remap：全 Yarn 名、`mixins.json` 里**没有** `refmap` 键。装进 `mods/` 的症状是
  「**每一个** `@Mixin target … was not found … from mod simple_tweaks` + 崩在
  `NoClassDefFoundError: net/minecraft/server/world/ServerWorld`」，**而入口类自己的日志照打**
  （`loading` / `Config loaded from`），极易误判成代码写坏了。取证看 `DEV_GUIDE` §2.5。
- **复制 jar 进 `mods/` 前必须完全关闭客户端**（Windows 会锁文件）。
- 本机沙箱若为 `workspace-write`，Gradle 会因为要写 `E:\gradle` 并 fork JVM 而**静默退出且零输出**。
  这不是代码问题；需要 `danger-full-access` 或请作者在终端跑。

---

## 2. 任务 → 要改哪些文件

| 任务 | 必改 | **不要碰** |
|---|---|---|
| **加一个附魔** | 新建 `enchantments/handlers/<category>/EnchantXxxHandler.kt`（`@ModEnchantment` + `Listenable`）<br>+ `lang/en_us.json` 与 `lang/zh_cn.json` 各 2 条（name + desc） | 不要手写 datapack JSON（与 KSP 生成物撞路径）<br>不要改 `EnchantManager.handlerList`（注册是自动的）<br>不要碰三个 tag（KSP 独家产出） |
| **加一个 Mixin** | 新建 `mixin/XxxMixin.java` + **注册进 `simple_tweaks.mixins.json`** | `required: true` + `defaultRequire: 1` → **目标方法名写错 = 启动崩溃**（这是好事，不静默） |
| **加一条命令** | 只改 `core/EnchantCommand.kt` 的 `tree()`（屏幕命令用 `core/ScreenCommands.kt`） | **绝不注册客户端命令** —— 见 §4.1 |
| **加一个配置项** | 字段 + JSON 读写 + 屏幕开关 + lang；**并写清"谁读它"** | 键存在 ≠ 有人读它（本仓库已发生 3 次） |
| **改客户端可见行为** | 推进 `build=cleanupN` + 同步 checklist 页首 SHA/字节数 | — |
| **加/改网络包** | `network/packets/Xxx.kt` + `NetworkManager.registerPackets()` + 客户端接收器 | S2C 必须 `PayloadTypeRegistry.playS2C()` 注册，否则运行期不报错但收不到<br>**空 body 的包必须单例**：`PacketCodec.unit` 的编码器会 `equals` 校验**注册时那一个实例**（本仓库的 payload 都不覆写 `equals`，即身份比较），发送时新建实例 = 服务端编码抛 `IllegalStateException`、玩家被踢（`cleanup22` 的 `PacketResetAttackCooldown` 就这么断连过，`cleanup23` 改成 `INSTANCE` + 私有构造） |
| **装到客户端** | `Copy-Item build\libs\simple_tweaks-2.1.0.jar "$mods\simple_tweaks-2.1.0.jar"`（旧的先存 `.prev`）；装完对 SHA256 | **`build/devlibs\…-dev.jar`**（未 remap，见 §1 硬规则）；客户端没关就复制 |

---

## 3. 「完成」的判据（**声明成功前必须跑**）

1. `.\gradlew.bat build` → `BUILD SUCCESSFUL` 且**无 `e:` 行**。
2. 若动了附魔：**lang 覆盖检查** —— 每个 `@ModEnchantment` 在两个 lang 文件里都有 name 与 desc。
3. 若动了 lang：**两文件键顺序一致** + **`%` 合规扫描**（值里只允许 `%%`、`%s`、`%N$s`；
   单个裸 `%` 会让 vanilla 抛异常后回退原串）。
4. 若动了 mixin：refmap 里有映射。**但这只是"构建关"，不等于接缝活着** —— 见 §5。
5. 文档：`DEVLOG.md` 加条目 / `docs/acceptance-checklist.md` 加或改验收项 / 页首 SHA 同步。
6. **报告时必须区分三类**：静态证据 / 构建证据 / **动态证据**。没有动态证据的写「未验收」，
   **不得写「已实现」**。

---

## 4. 硬约束（每条都有血债，逐条都是真踩过的）

### 4.1 命令**绝不能**注册在客户端 🔴

**Fabric 的客户端指令层会吞掉同名根下的服务端子命令。** 客户端先拿一个"只含客户端注册指令"的
dispatcher 试解析（`ClientCommandInternals#executeCommand`），而它只放行两种异常
（`#isIgnoredException`：`dispatcherUnknownCommand` / `dispatcherParseException`）。
共用根名时 `/root sub …` 匹配到客户端的根、只是子命令不认 → `dispatcherUnknownArgument`
→ **报错给玩家、`return true`、永不转发服务端**（源码里那句
`TODO: Check for server commands before executing` 就是承认这个缺口）。

**规则：所有命令都由 `CommandRegistrationCallback` 注册在服务端**；要开客户端界面就发包
（`PacketOpenModScreen`）。`requires` 写在**子节点**上（`enchant` 有 OP 2、屏幕命令没有）。

### 4.2 属性修饰符：`saved` ↔ persistent / temporary 的映射

| 1.12.2 写法 | 1.21 必须映射为 |
|---|---|
| `attr.applyModifier(mod)`（**默认会保存**） | `addPersistentModifier(mod)` |
| `mod.setSaved(false)` + `attr.applyModifier(mod)` | **`addTemporaryModifier(mod)`** |

- **"1.21 没有这个旗标"是错的。** `EntityAttributeInstance` 里有
  `private final Map<Identifier, EntityAttributeModifier> persistentModifiers` —— **逐修饰符**。
- **额外规则**：若解除某个修饰符依赖**内存**状态（`WeakHashMap`、计时器等），**必须用 temporary**。
  否则重载后"期限没了、修饰符还在存档里"，就是**永久残留**（本仓库在 `heavenly_punishment` 上踩过：
  `-100` 攻速压制让受害者永久无法出手）。

### 4.3 Mixin：三关缺一不可

`@Mixin` 只改写**它标注的那个类**的方法体，**不会传播给子类的重写**。1.21 里不少基类方法被
**完整重写且不调 `super`**，注入永不执行 —— 本仓库曾因此让**整个伤害层是死的**，而第一轮验收
2/2 通过（用例恰好绕开了）。

1. **静态关**：用 `javap -c` 做 override 审计，确认实际执行的类就是注入目标。
   `javap` 按 ~100 列折行会把 `owner.name` 从中间劈开 —— **正则前先 `-join '' -replace '\s',''`**。
2. **构建关**：refmap 有映射（**最容易过、最不能说明问题**）。
3. **动态关**：真实入口驱动一次，看到 `[FST-*]` 日志。

### 4.4 禁止静默 no-op / 空实现

- 改写一个方法前先问"**还有谁在读它**"（`javap` 扫调用点）。
  `markDirty()` 写成空实现 ⇒ 服务端改了内容却从不通知客户端；
  `getItemUseTimeLeft()` 看起来只是 getter，实际同时喂给拉弓动画与射箭威力。
- 处理器里**不许留没有任何日志的提前返回分支**（否则"没生效"无法定位）。需要探针时用 `FSTLog`。
- **"这次没生效"和"这次没触发"必须能区分** —— 分不清就说明缺观测手段。

### 4.5 不要用 `id = "…"` 正则扫描 `@ModEnchantment`

作者会用**位置参数**写法：

```kotlin
@ModEnchantment(
    "armor_enhance",                 // ← id 是位置参数，不是 id = "…"
    EnchantCategory.MYTHIC, ...
)
```

按 `id\s*=\s*"…"` 扫描会**静默漏掉**这类声明，然后得出"lang 没缺"的假结论。
**正确做法**：取注解之后**第一个字符串字面量**作为 id。
（写新附魔时请沿用 `DEV_GUIDE` §3 的**具名**写法。）

### 4.6 日志与探针

- 统一走 `compat/FSTLog`：`FSTLog.log("Name") { "k=v, …" }`（**用 lambda 重载**，日志关闭时不构造字符串）。
- **默认关闭**，启动开关：`-Dsimpletweaks.debug=true` 或环境变量 `SIMPLETWEAKS_DEBUG=true`。
- 前缀 **`[FST-*]`**；抓取：`Select-String '\[FST-'`。
  ⚠️ `MIGRATION.md` 是 append-only 历史，正文仍是 `[ST-*]`，**不要照它 grep**。
- **每 tick 的事件只在状态跃迁时打点**，否则刷爆日志（多个处理器挂在 `PlayerTickEvent` 上）。
- 无条件输出（不该被开关吞掉）：`loading` / `load complete` / `client ready: … (build=…)` /
  `Config loaded from …` / `Registered N enchantment handler(s)`，以及全部 `warn` / `error`。

### 4.7 不要改的东西

- `../`（1.12.2 原工程）
- `build/generated/ksp/main/**`（KSP 生成物 —— 改它等于没改，下次编译就没了）
- `mixin/disabled/`、`client/disabled/`、`.../infinitepower/disabled/` 下的未启用文件（**它们不在构建路径上**）

### 4.8 不要跑 `runClient`

需要真实客户端 + 人工判读手感/画面。由作者验收。需要冒烟时用 `runServer`，并只报
「注册数」与「有无报错」两项。

### 4.9 写实体状态字段前，先查 1.21 有没有给它加 clamp 🟡

**"方法名一样、语法一样"的字段写入，可能在 1.21 被一层看不见的钳位吃掉 —— 编译不报错，运行也没有日志。**

实例（`build=cleanup21`）：`player.absorptionAmount = x` 在 1.21.1 会走
`setAbsorptionAmount(float)`，而它是 `public final`，第一步就是
`MathHelper.clamp(x, 0.0f, getMaxAbsorption())`；`getMaxAbsorption()` 读的属性
`generic.max_absorption` **默认 0**，唯一抬高它的是 `absorption` 状态效果。于是
`golden_shield` 的吸收护盾**从未生效过一帧**，而且因为记账记的是"请求值"而不是"落地值"，
handler 还自我认证"已满"并停止尝试 —— 零日志，无从发现。

**规则**：
1. 要写实体的状态字段（护盾/属性/速度…），先用 `javap -p -c` 看 setter 有没有 clamp / 上下限 /
   追踪层；有 clamp 就**先自己抬上限**（`addTemporaryModifier`，见 §4.2 的持久化规则）。
2. **写入 + 记账成对出现时，账要记落地差值**（写入后重新读），不要记请求值。
3. 这类写入必须留下探针，且把"落地值 < 请求值"单独记成一个 outcome，否则失败不可观测。

同类未修（等作者定）：`healing_blade` 的溢出吸收、`death_protection` 的 `lvl × 20` 吸收。

### 4.10 注解参数只能是编译期常量 ⇒ 派生在 KSP；`jsonEmit = false` 会让字段变惰性 🟡

1. **"派生"不可能写在调用点。** `@ModEnchantment.weight` / `damagePerLevel` 的默认值是**哨兵**
   （`WEIGHT_FROM_CATEGORY` / `DAMAGE_PER_LEVEL_FROM_CATEGORY`），真正的派生在
   `ModEnchantmentProcessor` 的表里（`TIER_WEIGHTS` = 1.12.2 的 `10 - rarity×2`；
   `tierDamagePerLevel` = `rarity × 1.5 / 20`，**仅 SWORD** —— 注意伤害那条**不是** 1.12.2 的
   `0.2 + rarity/20`，是作者 `build=cleanup21` 的有意重平衡，**以代码为准**）。注解参数必须是编译期
   常量 —— 想加派生规则就加在处理器里。派生式要写成**一次除法/乘法**（`(4 + rarity) / 20.0` 这类）：
   `0.2 + rarity/20.0` 在 IEEE-754 下得到 `0.30000000000000004`，会原样写进 datapack JSON。
2. **`jsonEmit = false` 时，注解里的 `weight` / `anvilCost` / `minCostBase` / `damagePerLevel` /
   `supportedItems` / `slots` 全是惰性的** —— 生效的是那份手写 JSON。血债：
   `heavenly_punishment` 注解写 `damagePerLevel = 2.5`、`anvilCost = 90`，而实际发布的是 JSON 的
   `5` 与 `14`；作者改了注解，游戏里毫无反应。**这个旗标只能留给处理器真的表达不了的组件**
   （目前仅 `unbreakable` 的 `minecraft:item_damage`）。
3. **`EnchantItemWeights`（注解侧常量）与处理器表是两份，必须同一次提交一起改。** 唯一的自动护栏是
   处理器每次构建输出的一行 `warn`（逐条列出"显式值 != tier 派生值"）。⚠️ 别用 `logger.info` 报这种事：
   普通 `gradlew build` **看不见** KSP 的 info 输出（要 `-i`），看不见的报告等于没有报告。

### 4.11 每侧各自维护的状态：写入成功只完成了一半 🟡

`PlayerEntity#lastAttackedTicks`（攻击充能）这类字段是**每侧各自一份**的普通 int：不是 tracked 字段、
没有任何同步包。服务端调 `resetLastAttackedTicks()` 只改**服务端**那份，而 `InGameHud` 画攻击指示条读的是
**客户端**那份（`ClientPlayerEntity#getAttackCooldownProgress`，字节码核实）。血债（`build=cleanup21`）：
kill_aura 的 `simulateCooldown` 只落了服务端一半 —— 指示条一直满格，玩家却打出 `0.2 + charge² × 0.8`
的伤害；`build=cleanup22` 才补上 S2C 的 `PacketResetAttackCooldown`（详见 `DEVLOG.md`）。

**规则**：迁移一句"重置 / 写入某个状态"之前先答三问 ——

1. **另一侧是谁在读它**（HUD、动画、客户端预测都算）？
2. **另一侧是谁在写它**（是不是只有"客户端自己的输入路径"才会写）？
3. **有没有同步包**？没有就自己发一个（S2C 必须 `playS2C()` 注册），并留一条探针把**落地值**记出来。

同类嫌疑：`itemCooldownManager`、handler 里的各种 `WeakHashMap` 计时器、`ClientManaPoolCache` 这类客户端缓存。

---

## 5. 失败取证协议（**先看日志，再加探针，最后才猜**）

1. **先要客户端的 `logs/latest.log`。** 已经不止一次是"日志里一句话就定位了"。
   高效信号：*哪些事件的处理器出现在日志里、哪些没有* —— 例如
   `LivingHurtEvent` 有而 `LivingDamageEvent` 没有 ⇒ 问题必在 `applyDamage` 那一层。
2. **日志干净但行为不对 → 先加探针再改代码。** 没有观测量时的改动等于猜。
3. 每条接缝过 §4.3 的三关。
4. **同一失败形状出现两次就换方法** —— 不要第三种方式重试同一种假设。
5. 观测值与模型反复矛盾 → 停止字节码推断，改用运行时快照/日志探针。

---

## 6. 反模式清单（本仓库真实踩过，看到就停手）

| 反模式 | 实例 |
|---|---|
| 把**静态**结论当成**运行时**结论 | 用 `CommandNode#addChild` 字节码论证"同名根可共享" → 命令树整体不可用 |
| 改了"看起来对"的地方却没效果 | 只改 `getPullProgress`（威力），而动画读的是 `getItemUseTimeLeft` |
| 判定谓词比它省下的活还贵 | "无附魔就早退"必须扫全背包，而不缓存时比 18 个处理器加起来还贵 |
| 分类给出"目标状态"，只落地一半 | 判定"声明式即可"后删了处理器**却没写 JSON 组件** → 静默失效 |
| 键/表存在 ≠ 有人读它 | 配置键已 3 次、生成表已 2 次 |
| 同名 API 跨版本换含义 | `setSaved(false)` vs persistent/temporary 映射反了 → 永久残留 |

---

## 7. 写代码的约定（简短）

- **包结构**：handler 放 `enchantments/handlers/<category>/`，文件名 `EnchantXxxHandler.kt`；
  通用工具放 `util/`；Forge 兼容层放 `compat/`。
- **处理器形状**：`object XxxHandler : Listenable { @SubscribeEvent fun onYyy(e: SomeEvent) { … } }`；
  事件类型在 `compat/event/`。**没有现成接缝时去加 Mixin，不要在处理器里做反射或轮询**。
- **读附魔等级**：用 `util/EnchantmentsUtil.kt` 的 `getItemSpecificEnchantLevel(stack, GeneratedEnchantments.X)`
  （按组件匹配，不需要世界/注册表）。
- **注释语言**：代码 KDoc / 注释用**英文**；`MIGRATION.md`、`DEV_GUIDE`、`DEVLOG`、
  `acceptance-checklist` 等文档用**中文**。
- **不要新增依赖**，除非作者明确要求。
- **`order` 字段有语义**：同 `EventPriority` 的监听器按注册顺序派发，`@ModEnchantment.order` 决定它。
  从 `handlerList` 迁出的处理器**必须带上原位置**，否则会静默挪到总线末尾。

---

## 8. 文档义务与地图

改完**必须**：

1. `DEVLOG.md` 加一条（现象 → 证据 → 改法 → 验收编号），并更新「本批未验收」清单；
2. `docs/acceptance-checklist.md` 加/改验收项（**每条都要可判定**，尽量配反例）+ 同步页首 SHA/字节数；
3. 动了客户端可见行为 → 推进 `build=cleanupN`；
4. 发现本文件或 `DEV_GUIDE` 有错 → **就地改掉**（文档漂移是本仓库的常见坑）。

| 文件 | 拥有什么 |
|---|---|
| **`AGENTS.md`**（本文件） | 代理契约：硬约束 / 命令 / 查找表 / 完成判据 / 禁止事项 / 取证协议 |
| `DEV_GUIDE_1.21.1.md` | 人类版：结构 / 构建原理 / KSP 与 Mixin 教程 / 铁律详解 / 坑 / 诊断命令 / 命令注册 §10 |
| `DEVLOG.md` | 逐批流水：改了什么、为什么、**哪些未验收** |
| `MIGRATION.md` | 迁移期阶段流水与决策（**append-only**，不要改写） |
| `docs/acceptance-checklist.md` | 逐条验收项 + 「已知未做」表 |
| `docs/phase*.md`、`docs/infinite-power-deferred.md` | 各阶段专项考古（历史） |
