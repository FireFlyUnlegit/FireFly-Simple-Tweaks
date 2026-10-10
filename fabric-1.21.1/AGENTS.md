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
| 当前标识 | `build=cleanup20` |

**硬规则**

- **删除了任何源文件之后必须 `clean build`。** Kotlin 增量编译**不会**删除已移除源文件对应的
  `.class`，而 `jar` 任务照打不误 —— 症状是"源码里删了，jar 里还在"。
- **判断编译失败只看 `^e:` 行。** 如果没有 `e:` 行却 `BUILD FAILED`，几乎总是环境变量问题
  （`JAVA_HOME` 指向 JDK 8）—— 去看 `build/reports/problems/problems-report.html`，**不要翻源码**。
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
| **加/改网络包** | `network/packets/Xxx.kt` + `NetworkManager.registerPackets()` + 客户端接收器 | S2C 必须 `PayloadTypeRegistry.playS2C()` 注册，否则运行期不报错但收不到 |

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
- 处理器里**不许留没有任何日志的提前返回分支**（否则"没生效"无法定位）。需要探针时用 `STLog`。
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

- 统一走 `compat/STLog`：`STLog.log("Name") { "k=v, …" }`（**用 lambda 重载**，日志关闭时不构造字符串）。
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
