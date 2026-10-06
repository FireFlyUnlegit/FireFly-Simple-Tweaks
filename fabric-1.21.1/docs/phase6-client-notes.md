# 阶段 6 — 客户端移植笔记（细节档）

配套 `MIGRATION.md`（主文档只留索引与决策）。本档为**侦察结论**，尚未开始实现。
本档只追加，不改写既有段落。

---

## 1. 范围与体量

1.12.2 客户端相关代码合计约 **2,600 行**，分布在 9 个区域：

| 区域 | 文件 | 行数 |
|---|---|---|
| 客户端 mixin | `MixinMinecraft` / `MixinEntityRenderer` / `MixinEntityPlayerSP` / `MixinNetworkManager` | 189 |
| 客户端事件管线 | `core/event/{Event,EventManager,EventDispatcher}` + `extraforgeapi/extraevents/SlowDownEvent` | 190 |
| Module 框架 | `core/{Module,ModuleManager}` + `core/configs/{Configurable,Value}` | 229 |
| 模块本体 | `modules/{NoFov,AutoSprint,Velocity}` | 113 |
| GUI | `gui/{ModGuiScreen,GuiEnchantInfo,Component}` | 673 |
| 伤害数字 | `damageindicator/` 5 文件 | 531 |
| client 包 | `client/{ClientManaPoolCache,particle/CelestialRingParticle,tooltips/ManaPoolToolTipHandler}` | 126 |
| 网络 | `network/NetworkManager` + 3 个 packet | 189 |
| InfinitePower 客户端 | `GuiInfiniteBag` + `ClientHandler` + `EnchantInfinitePower` | 282 |
| 杂项 | `util/ParticleUtil`、`ModEnchantments`（附魔名颜色） | 151 |

---

## 2. 1.21 移植版里**已经有的**（不要重做）

| 项 | 状态 |
|---|---|
| `ClientModInitializer` 入口 | ✅ `SimpleTweaksClient` + `fabric.mod.json` 的 `entrypoints.client` 已就位 |
| 客户端 tick 桥 | ✅ `compat/bridge/ClientEventBridge`（47 行）：`ClientTickEvents` → `TickEvent.ClientTickEvent` + 每玩家 `PlayerTickEvent` |
| `DamageIndicatorConfig`（9 项） | ✅ 阶段 1c 已完成 |
| 服务端粒子生成 | ✅ EchoShield / Starfall 等已用 `ServerWorld.spawnParticles`，服务端侧无需再移植 |
| 配置读写 | ✅ `SimpleTweaksConfig`（JSON），客户端可直接复用 |

---

## 3. 清单：旧 → 新目标

### 3.1 客户端 mixin（4 个，全部需先做 override 审计）

| 旧 | 1.21 目标 | 注入点 | 服务的功能 |
|---|---|---|---|
| `MixinMinecraft` | `MinecraftClient` | 3（`runTick` HEAD、`displayGuiScreen` HEAD cancel、`loadWorld` HEAD） | 客户端 tick、GUI 打开拦截、世界切换 |
| `MixinEntityRenderer` | `GameRenderer` | 3（2× 渲染钩子、`getFOVModifier` RETURN cancel） | Render2D/3D 事件 + **NoFov** |
| `MixinEntityPlayerSP` | `ClientPlayerEntity` | 8 | Motion/Jump/Attack/Sprint/Step 事件 + **SlowDownEvent** |
| `MixinNetworkManager` | `ClientConnection` | 2（`sendPacket` / `channelRead0` HEAD cancel） | PacketSend/Receive 事件 |

⚠️ 风险点：`MixinEntityPlayerSP` 用的 `onLivingUpdate` 在 1.21 已被合进 `tick()`；`attackEntity` / `setSprinting` / `getStepHeight` 需逐个核。

### 3.2 其余（按用途）

| 旧文件 | 1.21 落点 | 前置 |
|---|---|---|
| `modules/NoFov.kt`（7 行）+ `getFOVModifier` | `GameRenderer#getFov` | Module 框架（或改为直读配置） |
| `damageindicator/*` | `WorldRenderEvents` + `TextRenderer` | **一个自定义包**（见 §4.1） |
| `client/tooltips/ManaPoolToolTipHandler` | `ItemTooltipCallback` | `PacketManaPoolSync` + `ClientManaPoolCache` |
| `ModEnchantments`（`enabledEnchantmentColor`） | `ItemTooltipCallback`，改附魔名样式 | 无 |
| `gui/*` + `core/configs/*` | `Screen`/`DrawContext`/`ClickableWidget` | Module 框架；`GuiEnchantInfo` 还需 `PacketOpenEnchantInfo` |
| `modules/{AutoSprint,Velocity}` | 用 3.1 的事件 | Motion/Sprint/Jump 事件 |
| `GuiInfiniteBag` / `ClientHandler` | — | **InfinitePower 处理器本身未移植** |
| `CelestialRingParticle` | 自定义 `ParticleType`（客户端注册） | `PacketCelestialRing` + CelestialBlessing（阶段 5/6） |

---

## 4. 优先级与预估

### P0（用户直接感知）

| 项 | 1.12.2 体量 | 工作量 | 风险 | 前置 |
|---|---|---|---|---|
| **NoFov** | 7 行 | 极小（1 mixin） | 低 | Module 框架 **或** 改为配置字段 |
| **粒子收尾** | `ParticleUtil` 85 行 | 小 | 低 | 无（服务端已可用） |
| **伤害数字** | 531 行 | **大** | 中高 | **阶段 5 的一个包**（见 §4.1） |

### P1（重要）

| 项 | 体量 | 工作量 | 风险 | 前置 |
|---|---|---|---|---|
| 附魔名颜色 | 66 行 | 小中 | 中 | 无（1.21 附魔名数据驱动，颜色需在 tooltip 层改） |
| ManaPool tooltip | 33 行 | 小 | 低 | `PacketManaPoolSync` |
| 配置 GUI | 673 + 框架 229 行 | **大（重写）** | 高 | Module 框架 |
| GuiEnchantInfo | （含上面 317 行） | 大 | 高 | `PacketOpenEnchantInfo` + 框架 |

### P2（可缓）

| 项 | 体量 | 缓做理由 |
|---|---|---|
| InfinitePower 背包 GUI | 282 行 | 依赖未移植的 InfinitePower 处理器 |
| CelestialRing 粒子 | 75 行 | 依赖 CelestialBlessing（阶段 5/6） |
| AutoSprint / Velocity | 27 + 79 行 | 依赖 4 个客户端 mixin + 事件管线 |

---

## 5. ⚠️ 三个关键依赖发现

1. **"先客户端再网络层"对伤害数字不成立。** 伤害数字（P0）**必须先有一个自定义包**（`PacketDamageIndicator`），它是阶段 5 的第一块。二者是同一件事的两半，建议合并为一批。
2. **NoFov 与配置 GUI 共享前置**：`Module` / `ModuleManager` / `Configurable` / `Value`（229 行）。要么先移植框架（顺带解锁 P1 的 GUI），要么 NoFov 先特殊化为一个配置字段。
3. **4 个客户端 mixin 都要先过铁律二审计**，且 `ClientPlayerEntity` 的 `onLivingUpdate` 在 1.21 已被合并进 `tick()`，注入点需重新定位。

---

## 6. 建议批次（每批可独立验收）

| 批 | 内容 | 风险 |
|---|---|---|
| 1 | NoFov 配置字段化 → **已实现后又删除**（§11.1）。「粒子收尾」经逐符号核查是**死代码，无需做** | — |
| 2 | 附魔名颜色 + 粒子收尾（若批 1 未含） | 低 |
| 3 | **网络层最小切片 + 伤害数字**（阶段 5 与 6 的交汇） | 中高 |
| 4 | ManaPool tooltip + SlowDownEvent + AutoSprint/Velocity + 4 个客户端 mixin | 中 |
| 5 | ❌ **已砍**（§11.3）：配置 GUI + GuiEnchantInfo ≈900 行重写，性价比低 → 标为「已知未做，低优先级」 | — |
| 缓 | InfinitePower 背包、CelestialRing | — |

---

## 7. 待决定：Module 框架 vs Cloth Config

| 选项 | 代价 | 收益 |
|---|---|---|
| (a) 照搬 `Module` 框架 | 229 行 | 最保真；GUI 也要用它 |
| (b) NoFov 特殊化为配置字段 | 7 行 | 立刻拿到 P0；框架推迟 |
| (c) 改用 Cloth Config + ModMenu | 新依赖 | 省掉 673 行 GUI 重写 |

**建议 (b) 先做**，把 (a)/(c) 的取舍推到配置 GUI 那一批 —— NoFov 的价值是"立即可见的 P0"，不值得先扛 229 行框架；GUI 自研 vs Cloth Config 应在真正要写 GUI 时再定。

---

## 8. 验收策略（阶段 6 多数项只能客户端验）

**我能做的**：跑 `runClient`；**盯 `latest.log` 的 `joined the game`**；你连上后我用 `/say` / `/title` / `/tellraw` 把"下一步测什么"直接发到你屏幕上。
**每批交付时**会给一张逐项核对表（改了什么 → 怎么触发 → 期望看到什么）。

| 批 | 需要你测 |
|---|---|
| 1 | 配置里把 FOV 设为非 90，疾跑/瞄准时视角不再拉伸 |
| 2 | 带 mod 附魔的物品 tooltip 里附魔名有颜色；攻击时看到特殊粒子 |
| 3 | 攻击生物出现飘字（正负号 / 百分比模式 / 上浮时长）；配置关掉后消失 |
| 4 | 持弓 / 进食 / 潜行时的减速；疾跑锁定；击退表现 |
| 5 | 打开配置界面的按键；附魔信息界面的布局与滚动 |

---

## 9. 客户端验收清单 — 第一轮：阶段 1–4 的积压项

阶段 6 尚未开工，所以这一轮测的是**服务端测不到、一直挂着"待客户端完整验收"的积压项**。
玩家的客户端安装：`F:\.minecraft\versions\1.21.1-Fabric 0.19.3\`（mod jar 时间戳需与 `build/libs` 一致）。

### 前置

单人世界 + **开作弊**（建世界时勾选，或 暂停 → 对局域网开放 → 允许作弊）。
`/give` 用 `enchantments` 组件可绕过附魔适用性校验，所以任意附魔都能装上。

### T1 配置层读写（⚠️ 原「crit 粒子开关」部分**已作废**）

> **订正**：T1 原本要求验证本阶段新增的 `PlayerEntityAttackParticleMixin` /
> `PlayerEntityCritParticleMixin` 真的抑制了原版暴击粒子。**这两个 mixin 已被删除**
> （选项语义搞错，见 `phase4-mixin-notes.md` §9），所以下面原来的两步**不再适用**，
> 粒子抑制也无事可验。`cancelVanillaDamageIndicator` 现在是空开关。

T1 只剩「配置文件能读写、改了会生效」这一件事要验，而它已被 §10.4 的 NoFov 测试完全覆盖
（改 `noFovEnabled` + 重启 → 日志 `outcome=forced` / 视角行为变化）。

所以：**T1 直接按 §10.4 走即可，不必单独测粒子。**

### T2 AutoSmelt 真实挖矿 + 精准采集反例

| 步骤 | 期望 |
|---|---|
| `give @s netherite_pickaxe[enchantments={"simple_tweaks:auto_smelt":1}]` 挖铁矿石 | 掉**铁锭**、耐久 -1、经验 + |
| 换成同时带 `minecraft:silk_touch:1` 的镐再挖 | 掉**原矿**（不熔炼） |

第二条是**服务端从未测过的反例**（`/sttest harvest` 只走了合成入口）。

### T3 Momentum 真实连续挖掘 + 相邻同类方块重置

| 步骤 | 期望 |
|---|---|
| `momentum:8` 镐，按住左键持续挖**同一块**（黑曜石/深板岩） | 速度逐渐变快 |
| 挖穿后继续挖**紧邻的同类方块** | 速度**重置**（验证 `pos` 精确性；按方块类型判断会误判为同一块） |

### T4 SoulBound 的「归还」那一半（**服务端根本无法测**）

| 步骤 | 期望 |
|---|---|
| `soul_bound:1` 钻石剑 + 5 个石头，`gamerule keepInventory false`，`kill @s` | 重生后**剑回到背包**，石头**掉在原地**不返还 |

Carpet 假玩家死亡后直接断线、不重生，`PlayerEvent.Clone` 不触发 —— 这半边只有真实玩家能验。

### T5 Starfall 视觉

`starfall:5` 弓 + 箭，射生物：应见 **3 颗星辰**从天而降、火焰/烟雾尾迹、命中爆炸粒子与音效；
自驯狼站在目标旁应**不受伤**（服务端已用 Owner 标签证明数值层，这里看视觉）。

### T6 EchoShield 视觉

4 件 `echo_shield` 护甲被怪打：缓冲期附魔粒子、吸收+反弹时爆炸粒子 + 雷声。
穿甲数值已由服务端证明（Δ24，若不穿甲约 1.2）。

### T7 粒子常量改名的视觉确认（**纯客户端，从未验过**）

`CRIT_MAGIC→ENCHANTED_HIT`、`EXPLOSION_NORMAL→POOF`、`SPELL_WITCH→WITCH`。
触发点：crit 三件套、Starfall 爆炸、GrievousWounds。期望粒子的**类型**看起来对
（例如 Starfall 爆炸是"爆炸"而不是白色烟雾）。测此项需先把 T1 的开关设回 `false`。

### ⚠️ T1 的粒子开关部分已作废（原「两个粒子 mixin」已删除）

本节原本警告「本阶段的两个粒子 mixin 是无条件抑制，要盯范围是否过宽」。
**`PlayerEntityAttackParticleMixin` 与 `PlayerEntityCritParticleMixin` 已按作者裁定删除**
（选项 `cancelVanillaDamageIndicator` 的本意是控制**本模组自己的伤害指示器**，不是原版粒子；
根因与证据见 `phase4-mixin-notes.md` §9）。该选项现在是**空开关**，留给本阶段伤害指示器接线。
**T1 只剩配置层本身要验**，不再有粒子抑制需要观察。

---

## 10. 批 1 完成记录（NoFov）— 粒子收尾是空任务

### 10.1 「粒子收尾」不需要做：那三个函数是死代码

§4/§6 把 `ParticleUtil`（85 行）列为 P0。逐符号核对 1.12.2 全树调用点后：

| 符号 | 1.12.2 调用点 |
|---|---|
| `spawnCritParticlesServer` | **0** |
| `spawnCritParticlesClient` | **0** |
| `World.spawnParticles`（扩展函数） | **0** |

全项目只有定义、没有任何调用 —— 是死代码。真正的粒子助手 `spawnRingParticles` **早已移植**
（`util/WorldExtensions.kt`）。所以批 1 的粒子部分**零工作量**，不要再去补。

### 10.2 NoFov 实现（§7 选项 (b)：先扁平化为配置字段）

| 项 | 1.12.2 | 1.21.1 |
|---|---|---|
| 开关 + 数值 | `NoFov.state` + `float("Fov",90f,30f,120f)` | `GeneralConfig.noFovEnabled=false` + `noFovValue=90f`（读入时 `coerceIn(30,120)`） |
| 生效点 | `EntityRenderer#getFOVModifier(FZ)F` RETURN | `GameRenderer#getFov(Camera,F,Z)D` RETURN |
| 强制方式 | `cir.setReturnValue(customFov)` 无条件 | 同（无条件），`double` 返回 |

**语义等价性有字节码依据**：1.12.2 的 `getFOVModifier` 返回的是**最终 FOV 角度**（`70.0F` 起，`useFovSetting` 时换成 `fovSettings.fovSetting`）；
1.21.1 的 `getFov` 形状完全一致（`if (renderingPanorama) return 90.0; double d = 70.0; if (changingFov)
{ d = options.getFov().getValue(); d *= lerp(tickDelta, lastFovMultiplier, fovMultiplier); } ... return d;`）。

**override 审计**：`getFov` 全 jar **仅 `GameRenderer` 声明**（`Camera` 无 `getFov`；
`AbstractClientPlayerEntity`/`ClientPlayerEntity` 只有 `getFovMultiplier`，是**另一个东西**）。
`GameRenderer` 在 vanilla 无子类 → 无顶层重写可绕过注入点。
疾跑拉伸位于 `fovMultiplier`（`updateFovMultiplier()` ← `AbstractClientPlayerEntity#getFovMultiplier()`，
只此一处声明、`ClientPlayerEntity` 不覆盖），被强制返回一并抹平 —— 正是本选项的意图。

**被否决的另一方案**：把 `fovMultiplier` 字段归零。否决理由：那会让**原版 FOV 设置**成为基准值，
使配置里的 `noFovValue` 失去意义，而配置值正是这个模块的用途。

**已知 1.21 独有的副作用（接受）**：强制值会**一并抹平潜望镜缩放**。1.12.2 无潜望镜，无「保真」可言；
且潜望镜缩放**不在 `GameRenderer` 里**（该类字节码零 spyglass 引用），它同样经由 `fovMultiplier` 进入投影。
接受而不特判的理由：这正是 "NoFOV" 的字面含义，且该选项**默认关闭**。

### 10.3 三关验证状态

| 关 | 证据 | 状态 |
|---|---|---|
| 静态（override 审计） | 上表，`javap` 依据 | ✅ |
| 构建（refmap） | `getFov(Lnet/minecraft/client/render/Camera;FZ)D` → `Lnet/minecraft/class_757;method_3196(Lnet/minecraft/class_4184;FZ)D` | ✅ |
| **动态** | `[ST-NoFov] seam=alive, ...` 一次性日志（**无论开关是否打开都会打**） | ⏳ 待客户端 |

探针**无条件**触发是有意的：若只在启用时打日志，「接缝是死的」与「开关没开」在日志里无法区分 ——
这正是铁律一要消灭的歧义。

### 10.4 批 1 客户端验收

1. 直接启动（配置默认 `noFovEnabled=false`）→ 日志应出现 `[ST-NoFov] seam=alive, enabled=false, vanillaFov=70.0, outcome=passthrough`
   → **这一步就证明接缝活着**，不需要改配置
2. 退出 → 编辑 `<gameDir>\config\simple_tweaks.json`：`"noFovEnabled": true`、`"noFovValue": 70.0` → 重启
   → 日志应出现 `enabled=true, ..., forcedFov=70.0, outcome=forced`
3. 游戏内：疾跑、瞄准、拉弓、水下 —— 视角**完全不变**；潜望镜也**不再缩放**（见 10.2，预期内）

---

## 11. 批 1 撤销 + 批 2 完成（附魔名颜色）+ 批 5 砍掉

### 11.1 NoFov 已删除（§10 的实现 **已作废**，方法论保留）

批 1 的 NoFov 实现通过了静态 + 构建两关（§10），**随后被作者删除**：

1. 1.12.2 的 NoFov 本来就不是核心功能；
2. 强制 FOV 会一并抹平**潜望镜缩放** —— 说明它与 1.21 的 FOV 模型存在**语义冲突**，不是"补个移植"能解决的。

| 处置 | 内容 |
|---|---|
| 删除 | `mixin/GameRendererFovMixin.java` + `mixins.json` 的 `client` 条目（`client` 恢复为空） |
| 保留 key | `GeneralConfig.noFovEnabled` / `noFovValue` 标注**已废弃、无实现**（删 key 会破坏已有配置文件） |
| 构建 | 通过，无残留引用 |

> §10 与 §9 T1 中凡指向 NoFov 实测的段落**均已作废**；§10 保留只为记录三关验证的方法论。
> 副产品：本批确认了「潜望镜缩放经由 `fovMultiplier`」这一事实，将来重做 FOV 功能可直接复用。

### 11.2 批 2 完成：附魔名颜色

| 项 | 1.12.2 | 1.21.1 |
|---|---|---|
| 生效点 | `ModEnchantments.getTranslatedName(level)` → `decorateName` | `Enchantment.getName(RegistryEntry,int)`（**public static**）RETURN |
| 普通附魔 | 前缀 `category.color` 的 `§` 码 | `Style.withColor(Formatting)`，作用于整行（含等级后缀） |
| 颜色表 | `EnchantmentCategories.color` 枚举 | **生成** `enchantments/EnchantmentNameColors.kt`（56 条，id→Formatting） |
| 开关 | `GeneralConfig.enabledEnchantmentColor` | 同，运行时判断 |

**为什么颜色不能烤进 JSON**：1.21 附魔数据驱动，把颜色写进 `description` 文本组件确实能零代码着色 ——
但该功能带**运行时开关**，静态组件无法被关掉，等于把 `enabledEnchantmentColor` 变成空开关
（与刚删掉的粒子 mixin 属**同一类错误**）。故走代码路线。

**⚠️ 保真特例（差点漏掉）**：1.12.2 的 `EnchantInfinitePower` **覆盖了 `decorateName`** 做**逐字符动画彩虹**
（8 色 RED/GOLD/YELLOW/GREEN/AQUA/BLUE/LIGHT_PURPLE/DARK_PURPLE，索引 `((tick+i*0.08)*8).toInt()%8`，
`colorTick` 每 tick +1、60 tick 循环）。已完整移植 → `enchantments/InfinitePowerRainbow.kt`，
tick 驱动接在 `ClientEventBridge` 的 `END_CLIENT_TICK`。**不做的后果是这个效果静默消失。**
生成表中它的 `DARK_RED`（MYTHIC）保留但不会被使用，目的是让表保持为 `EnchantmentCategories.color` 的忠实副本。

**另一个 override 已核对**：`EnchantHealer` 显式传 `textColor = GREEN`，但其分类 UNCOMMON 本就是 GREEN
→ **无差异，不需特例**（查过，不是假设）。

**副作用**：无。`Text.getString()` 会丢弃样式，故服务端日志与 `/sttest` 诊断**不受影响**。

**三关**：静态（`getName` 全 jar 仅此一处、static）✅ / 构建（refmap `getName(...)Text;` → `class_1887;method_8179`）✅ / 动态 ⏳ 待 tooltip 目视。

### 11.3 批 5（配置 GUI）已砍

配置 GUI + `GuiEnchantInfo` ≈900 行重写（自研 `Module` 框架 229 + GUI 673），**决定不做**，
记为「**已知未做，低优先级**」。将来若要做，优先评估 Cloth Config + ModMenu，而非复刻自研框架。

### 11.4 顺带修掉两个生成器 bug（都会污染生成产物）

| bug | 症状 | 修法 |
|---|---|---|
| `` `tools/... `` 出现在双引号 here-string 里 | `` `t `` 被解析为 **TAB**，生成物成为 `by ools/...` | 去掉反引号 |
| `.ps1` 缺 UTF-8 BOM | **PS 5.1** 按 GBK 解析脚本，输出串中的 `—` 变成 `鈥?` 写进生成文件 | 加 BOM；输出串中的 `—` 一律改 `--` |

> **根因教训**：`tools/gen-enchantments.ps1` 文件头推荐的调用方式是
> `Invoke-Expression (Get-Content .\...ps1 -Raw)` —— 而 **PS 5.1 的 `Get-Content` 不带 `-Encoding` 就是 GBK**，
> 这正是 zh_cn 乱码的最初来源。加 BOM 后这条路径也变正确。
> 另：**本 harness 自身就是 Windows PowerShell 5.1，没有 `pwsh`** —— 不要假设能用 PS7。
> 脚本是**函数定义文件**（`Invoke-GenEnchantments -SrcRoot ... -OutRoot ...`），直接 `& script.ps1` 只会定义函数、什么都不生成。

### 11.5 批 2 客户端验收（前置：单人 + 开作弊，`/give` 后可绕过附魔适用性）

用 `enchantments` 组件给物品装上附魔后**悬停**看名字颜色：

| # | 附魔（分类） | 期望颜色 |
|---|---|---|
| 1 | `soul_bound`（LEGENDARY） | **金色** |
| 2 | `auto_smelt`（COMMON） | 灰色 |
| 3 | `crit_damage`（EPIC） | 淡紫 |
| 4 | `crit`（LEGENDARY） | 金色 |
| 5 | `celestial_blessing`（MYSTERY） | 青色 |
| 6 | `reforge`（UNIQUE） | 白色 |
| 7 | **`infinite_power`（特例）** | **逐字符彩虹**，约 3 秒循环**流动** |
| 8 | 改 `config\simple_tweaks.json` → `"enabledEnchantmentColor": false` 重启后再悬停 | 全部**回到原版颜色**，彩虹同样失色 |
| 9 | 顺带：原版附魔（如 `sharpness`） | **不受影响**，保持原版颜色 |

---

## 12. 批 3 完成：网络骨架 + 伤害数字（+ `cancelVanillaDamageIndicator` 删除）

### 12.1 `cancelVanillaDamageIndicator` 已**直接删除**（作者裁定）

之前的分析在「按名字接（抑制原版粒子）」与「按作者早前裁定接（控制自己的指示器）」之间卡住，
因为后者会与已有的 `DamageIndicatorConfig.enabled` **语义重复**。作者最终裁定：**这个选项本就该删掉**。

| 处置 | 位置 |
|---|---|
| 删字段 | `GeneralConfig.cancelVanillaDamageIndicator` |
| 删读/写 | `SimpleTweaksConfig` 的 `readGeneral` / `write` |

**不会破坏已有配置文件**：加载器是**按 key 显式读取**的（`s.bool("name", default)`），
JSON 里残留的 `cancelVanillaDamageIndicator` 会被直接忽略；下次保存时 `write()` 不再输出该 key，自然消失。
与其他「保留 key 只为兼容」的项（`noFovEnabled`/`noFovValue`）不同，这里作者明确要求删除。

### 12.2 一个保真发现：`lastOriginalDamage` 不能接 `LivingHurtEvent`

1.12.2 的伤害指示器挂在 Forge `LivingHurtEvent` 上。Forge 1.12.2 的该事件**在护甲与抗性计算之后、吸收之前**。
而移植版的两个候选接缝**不在同一位置**：

| 移植版接缝 | 实际位置 | 是否等价 |
|---|---|---|
| `compat.event.LivingHurtEvent` | `Entity#damage` HEAD = **护甲前** | ❌ 会让每个"原始伤害"被护甲系数放大 |
| **`compat.event.LivingDamageEvent`** | `LivingEntity#applyDamage` = 护甲+抗性后、吸收前 | ✅ **位置完全对应** |

所以批 3 用的是 `LivingDamageEvent`。这属于本项目反复记录的那类错误（看着像的映射 ≠ 语义正确），
如果照名字接，数字会**静默偏大**且很难被发现。

### 12.3 实现清单

| 文件 | 内容 |
|---|---|
| `network/NetworkManager.kt` | Fabric `PayloadTypeRegistry` 取代 Forge 判别号模型；只登记已存在功能的包 |
| `network/packets/PacketDamageIndicator.kt` | 9 字段**线格式与 1.12.2 逐字节一致**；`IMessage`→`CustomPayload`+`PacketCodec` |
| `damageindicator/DamageIndicatorHandler.kt` | 服务端**血量差分**（非事件上报）+ 64 格广播 |
| `damageindicator/DamageNumber.kt` | 上升缓动/淡出/缩放/正负号/百分比/超杀双行，**公式原样** |
| `damageindicator/DamageIndicatorManager.kt` | 生命周期队列（1.12.2 无需改动） |
| `damageindicator/DamageIndicatorRenderer.kt` | `WorldRenderEvents.LAST`；`GlStateManager` 已不存在，深度→`TextLayerType.SEE_THROUGH`，颜色→ARGB 参数 |
| `SimpleTweaks` / `SimpleTweaksClient` | 接线：登记 payload + handler；客户端接收器 + 渲染注册 |

**保留的 1.12.2 怪癖（看着像 bug 但是原行为）**：
- `DamageIndicatorManager.update()` 每**帧**调用而非每 tick ⇒ `duration` 实际按**帧**计
- 超杀渲染成 `原始(实际)`，第二个数比第一个小
- `formatFloat` 对整数省略小数（`5.0` → `5`，不是 `5.00`）

**刻意保留的手绘阴影**：1.12.2 自己在 `(+1,+1)` 画一份 `alpha*0.6` 的黑色文本，并给原版 `drawString` 传 `shadow=false`。
移植版**没有**改成 `shadow=true`，而是照抄手绘，这样视觉才一致而非"近似"。

### 12.4 三关状态

| 关 | 证据 | 状态 |
|---|---|---|
| 静态 | 接缝位置对照表（§12.2） | ✅ |
| 构建 | 编译通过；6 个类均在 jar 内 | ✅ |
| **动态** | 两条一次性探针：`[ST-DamageIndicator] packet=received, ...` 与 `[ST-DamageIndicator] seam=alive, drawn=1, ...` | ⏳ 待客户端 |

### 12.5 批 3 客户端验收

配置里 `damageIndicator` 默认 `true`，所以**直接启动就能看到**。

| # | 操作 | 期望 |
|---|---|---|
| 1 | 打任何生物 | 冒出**红色 `-伤害`**，数字上浮并淡出；第二行是 `剩余/最大` 血量 |
| 2 | 一次打超过它剩余血量（超杀） | 显示 `-原始(实际)`，如 `-20(5)` |
| 3 | 治疗（如金苹果、持续恢复） | **绿色 `+数值`** |
| 4 | 打自己（`/damage @s 5`） | 自己的数字**更大**（`isSelf` 缩放 1.3）且**不会因背对而被裁掉** |
| 5 | 背对一只正在受伤的怪 | 数字**被裁掉不显示**（背面上剔除 `dot < -0.1`） |
| 6 | 改配置 `percentageMode: true` 重启 | 变成 `-30%` 形式 |
| 7 | 改配置 `symbol: false` 重启 | 不再有 `+`/`-` 号 |
| 8 | 改配置 `enabled: false` 重启 | 完全不显示 |
| 9 | 日志 | 应出现两条 `[ST-DamageIndicator]`（`packet=received` 与 `drawn=1`） |

---

## 13. 批 3 修正：渲染不显示的真因 + `cancelVanillaDamageIndicator` 恢复（窄版）

### 13.1 定位：链路是活的，问题在渲染层

日志两条探针**都打出来了，参数正常**：

```
[ST-DamageIndicator] packet=received, entity=监守者, actual=13.649999, original=13.65, ... outcome=received
[ST-DamageIndicator] seam=alive, drawn=1, entity=监守者, text=-13.65, second=358.57/500, alpha=1.00, dist=3.8, ... outcome=drawn
```

⇒ 服务端差分 → 包 → 客户端接收 → `TextRenderer.draw` 全部执行，**问题只在渲染状态/矩阵层**。

### 13.2 两个真因（其中一个是本移植自己引入的）

| # | 问题 | 修法 |
|---|---|---|
| 1 | 用了 `WorldRenderEvents.LAST`。`LAST` 在 `WorldRenderer.render` **返回之后**触发，矩阵栈已不再带相机的世界变换，而 `translate(dx,dy,dz)` 传的是**相机相对**坐标 ⇒ 文字被画到屏幕外 | 改用 **`AFTER_ENTITIES`** |
| 2 | **本移植的偏差**：写成 `scale(-s,-s,s)`（Z 为正）。1.12.2 是 `GlStateManager.scale(-s,-s,-s)`，**三个轴全负**。更糟的是我把这个偏差**当成"原行为"写进了 KDoc** | 改为 `scale(-s,-s,-s)`，KDoc 同步更正 |

> ⚠️ **教训**：**把自己的猜测写进 KDoc，会让它伪装成"已核实的事实"**。第 2 条若不回头对照原码，
> 会一直躺在文档里被当作依据 —— 这比代码里的 bug 更危险，因为它会误导后来的每一次判断。

### 13.3 §12.1 更正：`cancelVanillaDamageIndicator` 已恢复，但**范围收窄**

作者先要求删除该字段，随后改判需要恢复 —— 但**只恢复一部分**。

| 项 | 内容 |
|---|---|
| 字段 | `GeneralConfig.cancelVanillaDamageIndicator`（已恢复） |
| 新 mixin | `PlayerEntityDamageIndicatorParticleMixin`（mixin 总数 13 → **14**） |
| 作用范围 | **只关原版 `DAMAGE_INDICATOR` 粒子** |
| 明确**不做** | 不碰爆击 `CRIT`、不碰附魔命中 `ENCHANTED_HIT`（1.12.2 的未过滤 redirect 会一起关，本移植按作者裁定不做） |

**为什么这个调用点本身就是窄的**：`PlayerEntity#attack` 里**只有一处** `spawnParticles`，生成的就是
`DAMAGE_INDICATOR`。代码中的类型判断是**防御性**的 —— 万一原版将来在同一调用点加了别的粒子，
未过滤的 `@Redirect` 会静默把新的也吞掉。

**与 `DamageIndicatorConfig.enabled` 语义不重复**：
`enabled` = 本模组**要不要画数字**；`cancelVanillaDamageIndicator` = **原版要不要画它那个粒子**。

**构建关（refmap）**：
```
Lnet/minecraft/server/world/ServerWorld;spawnParticles(...)I -> Lnet/minecraft/class_3218;method_14199(...)I
attack(Lnet/minecraft/entity/Entity;)V                      -> Lnet/minecraft/class_1657;method_7324(...)V
```

### 13.4 复测要点

1. 打生物 → 飘字应**可见**（红色 `-伤害`，上浮淡出，第二行 `剩余/最大`）
2. 打生物时**不应再看到原版那个红色伤害粒子**
3. **爆击星星仍在**、**附魔命中特效仍在** ← **窄版的关键判据**；这两个若也消失，说明范围过宽
4. 若飘字**仍不可见** → 真因在更底层（`Immediate` 缓冲/图层），下一步改用 `context.consumers()`
   或"两种图层各画一遍"来定位，不再靠猜

---

## 14. 批 3 收工：飘字不可见的根因（**本移植自己引入**）+ 两条排查手法

> ⚠️ **本节 §14.1 的结论已被推翻，见 §14.5。** 保留原文是为了记录"我是怎么把线索误读成结论的"——
> 那本身比结论更值得记。**当前仍未定案**，最新进展见 §14.5。

### 14.1 ~~根因：负行列式缩放 → 被面剔除~~（**此结论已被推翻**）

`scale(-s, -s, -s)` 在 1.21 里 = **负行列式** → 四边形被镜像、**绕序翻转** → 剔除 → 不可见。
1.12.2 能用三轴全负，是因为当时字体渲染器在全局 GL 状态里**关掉了面剔除**（`GlStateManager`），
而那个状态在 1.21 已不存在。正确值是 `-s, -s, +s`（正行列式，1.21 的标准 billboard 缩放）。

**这是我的错，而且犯了两次**：

1. 最初写的是 `-s, -s, +s`（**对**），然后为了"保真"改成 `-s, -s, -s`（**错**）—— 我把对改成了错；
2. 改的同时还把这个偏差**当成"原行为"写进 KDoc**，让文档为错误背书。

> **教训**：保真的对象是**可观察行为**，不是"周边状态已经变了"的某个调用的算术字面。
> 照抄 `GlStateManager.scale(-s,-s,-s)` 比 `-s,-s,s` **更不**保真。

### 14.2 三个对照的结果（一次运行定位）

| 标记 | 配置 | 结果 | 证明了什么 |
|---|---|---|---|
| D1 | `NORMAL` + 不 billboard | **只朝北可见** | `AFTER_ENTITIES` 的矩阵栈**世界对齐**，不含相机旋转 |
| D2 | `NORMAL` + **billboard** | **全方向可见** | `MatrixStack#multiply(Camera#getRotation())` **正确** |
| D3 | **`SEE_THROUGH`** + 不 billboard | 只朝北可见 | `SEE_THROUGH` 图层**能正常渲染** |
| 数字 | `SEE_THROUGH` + billboard + **`-s,-s,-s`** | **不可见** | 三个标记都是**正行列式**，只有它是负 |

**唯一差异 = 缩放的行列式符号。** 图层与 billboard 都被逐个排除。

### 14.3 两条排查手法（比结论更值钱）

1. **「绕圈看」** —— 让玩家绕着标记转一圈。**朝向依赖**会直接暴露"这个面根本没 billboard"。
   这是作者自己想出来的，比"你看到几个字母"有效得多：它把问题从"可见/不可见"变成"**朝向依赖**"，
   而朝向依赖直接指向变换矩阵。
2. **一次运行 + 单变量对照** —— 每个标记只改一个变量，避免多轮重启。

### 14.4 日志的边界（要记住）

探针能证明"**调用发生了、参数正确、坐标合理**"；它**原理上无法证明"屏幕上有像素"**。
所以到达某一步后必须交给人眼。**明确说出这条边界，比假装日志能判定更有用** ——
否则会像我前两轮那样，用"探针都打了"去解释"为什么看不见"，方向完全错。

### 14.5 ⚠️ §14.1 的结论被推翻：真因**仍未定案**

§14.1 断言"负行列式导致剔除"是根因，**这个断言没有依据**，作者实测把它否掉了：

- `CC9773B2` / `4FD2B060` / `85F9ACDE` 三版里，数字**已经**是正行列式 `-s,-s,s`，而作者报告
  **数字依然不可见**；
- 三个诊断标记也都是正行列式，**可见**。

⇒ **正/负行列式不能区分可见与不可见** ⇒ §14.1 是错的。

**我是怎么把线索误读成结论的**（这才是要记的部分）：

1. 我观察到"标记全部正行列式 → 可见；数字负行列式 → 不可见"，就把相关性当成因果；
2. 我**没有做那个能否证它的实验**：把数字改成正行列式单独验证。而实际上 `CC9773B2` 起它就已经是
   正行列式了 —— 也就是说**反证材料一直在手边，我没去用**；
3. 更糟的是我把这个未验证的推断立刻写进了 KDoc 和文档，让它看起来像"已证实的事实"。

> **教训（本项目第 N 次同类）**：*相关性不是因果，尤其当"相关"恰好符合我想要的解释时。*
> 下结论前先问：**有没有一个实验能直接否证它？** 如果有，先做那个实验，再写文档。

**仍未覆盖的组合**：D1/D2/D3 三者的**两两交叉**刚好漏掉了数字实际使用的
`SEE_THROUGH + billboard`。已据此做了**逐层二分**：数字第 1 行用 `NORMAL`（D2 已证可见），
第 2 行用 `SEE_THROUGH`（嫌疑），一次运行即可判定"是图层与 billboard 的交互"还是"`drawLine` 自身"。

### 14.6 ✅ 真正根因：**X 轴缩放符号必须为正**（依据是原版自身的代码）

作者提示"去看原版 1.21.1 怎么写" —— 这是本轮最关键的一步，一次定位。原版
`EntityRenderer#renderLabelIfPresent` 的矩阵指令：

```
120: ldc   #28    // float 0.025f
123: ldc_w #436   // float -0.025f
126: ldc   #28    // float 0.025f
128: invokevirtual MatrixStack.scale:(FFF)V
```

**原版名牌缩放 = `(0.025, -0.025, 0.025)`，X 是正的。** 本移植从第一版起就假设成
`(-0.025, -0.025, 0.025)`，于是**一直**带着 `-s` 在 X 上。

| 配置 | 行列式符号 | 结果 |
|---|---|---|
| 三个可见标记 `(0.08, -0.08, 0.08)` | **负** | ✅ 可见 |
| 高度扫描 `(-0.05, -0.05, 0.05)` | 正 | ❌ 不可见 |
| 数字 `(-0.03, -0.03, 0.03)` | 正 | ❌ 不可见 |
| **原版名牌 `(0.025, -0.025, 0.025)`** | **负** | ✅ 可见 |

**文字四边形只在行列式为负时不被剔除**（`(+)(-)(+) < 0`）。所以正确写法是 `scale(s, -s, s)`。

**这把此前全部观察一次性解释完**：数字**从第一版起就没显示过**，与作者每一次的报告完全一致；
而所有能显示的标记恰好都是 X 为正。

**关于 §14.1 的错误，还有一层值得记**：§14.1 不仅断言了一个未验证的相关性，**而且把行列式的
符号说反了**（它说"负行列式被剔除"，真相是"正行列式被剔除"）。这意味着我当时那个"修复"
（把 `-s,-s,-s` 改成 `-s,-s,s`）不仅没修好，**还把一个本来正确的行列式符号改成了错误的**。

> **教训升级**：错误结论不只是"没帮上忙"，它还会**主动把对的东西改坏**。
> 而本轮真正解决问题的不是更多的假设，而是**去读权威实现**（原版自己的代码）。

### 14.7 顺带读到、但**刻意没有照抄**的原版做法

`renderLabelIfPresent` 画**两遍**：先用 `SEE_THROUGH` + 低透明度 `0x20FFFFFF` 画一层柔和衬底，
再用 `NORMAL` 不透明画一遍；并且传了 `backgroundColour = textBackgroundOpacity << 24`。

本移植保留 1.12.2 的"单次不透明 + 手绘黑色阴影"，这样可以贴合**原模组**的观感而不是现代原版名牌。
但**矩阵运算顺序与 `draw` 重载完全照原版** —— 那才是必须对齐的部分。

### 14.8 批 3 收工状态 + 两个后续决定

**三关**：静态 ✅ / 构建 ✅ / **动态 ✅（作者确认飘字正常显示、位置与朝向正确）**。批次 3 完成。

**阴影改为不透明黑**（作者选 A）：

| | |
|---|---|
| 依据 | 1.12.2 阴影色是 `0x000000`，alpha 位为 0，其 `FontRenderer` 强制 `if ((color & 0xFC000000) == 0) color |= 0xFF000000;`，**覆盖了**同一段里那句 `GlStateManager.color(0f, 0f, 0f, alpha * 0.6f)` |
| 结论 | 1.12.2 实际画的是**不透明纯黑**，那个 `0.6` **从未生效**；移植版照字面实现成 60% 半透明，才导致"发灰、发糊" |
| 一处刻意偏离 | 阴影仍**跟随文字淡出**。严格保真会得到"不随淡出"的纯黑，动画末尾留下黑色残影 —— 那是 alpha 被忽略的**副产物**，不是特性 |
| 待定 | 若要严格版（阴影不淡出），说一声即可改 |

**⚠️ 一条被证伪的 bug 报告（记下来避免重复排查）**

作者曾报告"**附魔额外加的基础伤害不计入伤害显示**"，经确认为**误解**，无需修改：

- 他是按**物品提示框里的「攻击伤害」**去核对的原伤害；
- 该数字来自**物品的属性修饰符**，而 1.21 的附魔 `minecraft:damage` 效果在**命中时**计算，**不进属性修饰符** ⇒ 提示框天然不含附魔伤害；
- **原版锋利（Sharpness）完全一样**；1.12.2 的机制亦同（提示框走 `getAttributeModifiers`），且该模组 1.12.2 本身也没有往提示框加"附魔伤害"行；
- 而**飘字是血量差**，必然包含一切真实扣血。

⇒ **结论：飘字与提示框的差异是原版机制，不是移植缺陷。** 若将来希望提示框显示附魔伤害，那属于**新增功能**（需 `ItemTooltipCallback` + 汇总 `minecraft:damage` 效果），应作为扩张明确立项，而不是当 bug 修。

---

## 15. 配置 GUI（`/stconfig`）—— §11.3「批 5 已砍」的**部分回补**

§11.3 把批 5（配置 GUI + `GuiEnchantInfo`，≈900 行）砍掉了。作者随后改判：**需要一个可配置的界面**，
并且要**由指令唤起**（而不是先前提议的文本命令 `/stconfig <key> <value>`）。

| 项 | 内容 |
|---|---|
| 指令 | `/stconfig` —— **客户端指令**（Fabric `ClientCommandRegistrationCallback`）→ 打开界面 |
| 界面 | `client/SimpleTweaksConfigScreen.kt`：**原版 `Screen` + 原版 `ButtonWidget`**，**无 `Module` 框架、无 Cloth Config / ModMenu 依赖** |
| 布局 | **两页**（通用 / 伤害飘字），行高 20 px ⇒ 9 行页 + 页眉页脚 < 240 px 逻辑高度，任何 GUI 缩放都放得下；**不用自定义滚动列表** |
| 持久化 | 每次点击**立即** `SimpleTweaksConfig.save()`（新增的公开方法；`load()` 内部也改为复用它） |
| 覆盖 | 17 个活配置项（通用 8、飘字 9）；`noFovEnabled`/`noFovValue` 已废弃故不出现 |

**为什么是「客户端指令」而不是「服务端指令 + 包」**：1.12.2 需要
`NetworkRegistry.INSTANCE.registerGuiHandler`（双侧都要注册）。Fabric 的客户端指令直接在客户端 JVM
以 `FabricClientCommandSource` 执行，可以调用 `MinecraftClient.setScreen`，**不需要任何网络包**，
而且在**任意服务器**上都能用 —— 对"只改本地配置"这件事是正确的作用域。

界面通过 `client.execute { }` 打开而非在指令回调里直接调，因为回调可能跑在网络线程 ——
这与 1.12.2 数据包处理器需要 `Minecraft.getMinecraft().addScheduledTask` 是同一个理由。

**已知简省（有意，需后续补）**：标签是**硬编码中文**，未走 lang 文件。1.12.2 的 GUI 使用翻译键；
补约 25 个 key 到 `en_us.json` / `zh_cn.json` 是机械工作，为控制本次改动规模**暂未做**。

**仍未做**：`GuiEnchantInfo`（附魔信息界面）—— 依赖 `PacketOpenEnchantInfo`，价值未经确认。

**验证状态**：构建 ✅；**动态 ✅ —— 作者确认 `/stconfig` 正常打开、翻页正常、改动即时生效**。

---

## 16. ⚠️ `/stconfig` 打不开：一次**完全没有异常**的失败

现象：键入 `/stconfig`，界面不出现，**日志零异常**。

先排除"跑的是旧 jar"（上一轮刚因此吃过亏）：GUI 版 jar 写入 mods 是 `02:35:23`，客户端进程启动于
`02:36:22` ⇒ **确实加载了 GUI 版**。所以是真的坏了。

### 真因：`ChatScreen` 会自己把界面关掉

客户端指令是在 `ChatScreen` **仍在处理 Enter 键**的流程中**同步执行**的，而 `ChatScreen` 之后会调用
`client.setScreen(null)` 关闭自己：

```
ChatScreen 发出指令
  → 我们的回调 client.setScreen(配置界面)
ChatScreen 继续执行 → client.setScreen(null)      ← 配置界面被丢弃
```

界面只存在不到一帧，玩家什么都看不到，**且不抛任何异常**。

修法：把打开界面**推迟到下一个 `END_CLIENT_TICK`**，等聊天栏先关完。

> `client.execute { }` **不能**解决这个问题 —— 它在本就在客户端线程时是**立即执行**的，
> 这也正是上一版"看起来最自然"的写法仍然失败的原因。

### 新增探针，让三种失败可区分

| 日志行 | 含义 |
|---|---|
| `... client ready: damage indicator + /stconfig registered (build=config-gui)` | 新构建已加载 |
| `[ST-Config] command=stconfig, outcome=screen-queued` | **指令被找到了**并执行 |
| `[ST-Config] command=stconfig, outcome=screen-opened` | 界面真的被设置 |

之前这三种情况在日志里**完全无法区分** —— 因为 GUI 那版我没有加任何可辨识的启动行。

### 两条教训

1. **「没有日志」和「没有异常」不等于「没有发生」。** 一个只存活不到一帧的界面，日志干净得像成功一样。
   这类**无声失败**只能靠**机制推理**（问"还有谁会碰 `setScreen`？"）+ **可分辨的探针**定位，
   而不是"再读一遍日志"。
2. **每一个改变客户端行为的构建，都必须留下一条可辨识的启动行。** 否则"功能没生效"与"客户端还在跑旧 jar"
   在证据上完全等价 —— 本轮为此白跑了一轮。

---

## 17. 批 4 范围审计：4 个客户端 mixin（20 个注入点）→ **实际只需 1 个**

审计方法不是"看 1.12.2 有哪些 mixin"，而是**反查每个被触发的事件到底有没有消费者**。
结论比预期干净得多。

### 17.1 触发者 vs 消费者（1.12.2 全树）

| mixin | 触发的事件 | 内部消费者 |
|---|---|---|
| `MixinMinecraft` | `onTick` / `onGuiOpen` / `onWorldChange` | **0** |
| `MixinEntityRenderer` | `Render2DEvent` / `Render3DEvent` | **0** |
| `MixinEntityPlayerSP` | **`PlayerUpdateEvent`** | **AutoSprint、Velocity** |
| | `MotionEvent` / `JumpEvent` / `AttackEvent` / `SprintEvent` / `StepEvent` | **0** |
| | `SlowDownEvent` | **0**（见 §17.3） |
| `MixinNetworkManager` | `PacketSendEvent` | **0** |
| | **`PacketReceiveEvent`** | **Velocity** |

⇒ **20 个注入点里只有 2 个服务于真实功能**，且都指向同一个模块（Velocity）+ AutoSprint。

### 17.2 1.21 落点

| 需要的能力 | 1.21 落点 | 需要新 mixin？ |
|---|---|---|
| 客户端逐 tick 玩家钩子（AutoSprint） | **已有**：`ClientEventBridge` 已在派发 `TickEvent.PlayerTickEvent`（`ClientTickEvents.START/END_WORLD_TICK`） | **否** |
| 拦截/改写收到的实体速度包（Velocity 的 `Modify` 与检测） | `ClientPlayNetworkHandler#onEntityVelocityUpdate(EntityVelocityUpdateS2CPacket)` | **是（唯一 1 个）** |
| 其余 18 个注入点 | —— | **否**（无消费者） |

Fabric **没有**"任意包收/发"事件（`ClientPlayNetworking` 只覆盖自定义载荷），所以速度包那一个 mixin 确实必需。

### 17.3 `SlowDownEvent`：不是死代码，但**没有内部消费者** —— 需要作者裁定

它位于 `extraforgeapi/extraevents/`，包名表明这是**给第三方模组用的 API 扩展**。
所以"0 内部消费者"**不等于**"可以删"：它可能是对外公共 API，也可能是一个没做完的功能。
**这是要作者决定的事，我不擅自判定。**

### 17.4 审计副产物：两个必须先核的风险点

- `Velocity` 的 `Attack` 模式调用 `p.attackTargetEntityWithCurrentItem(target)` —— 即**由客户端发起攻击**。
  1.21 的 `ClientPlayerEntity` 是否还能这样直接攻击，需要单独核实（不能照抄）。
- `Velocity` 的 `JumpReset` / `Attack` 依赖 `p.hurtTime == 9` —— 该值在 1.21 的语义需确认。

### 17.5 批 4 的新范围

`PlayerUpdateEvent` 等价物（复用现有客户端 tick）+ **`AutoSprint`** + **`Velocity`**（含唯一那个速度包 mixin）。
原本 §3.1 列的 4 个 mixin / 20 个注入点中，**19 个被证明确实无事可做**。
ManaPool tooltip 仍待 `PacketManaPoolSync`（阶段 5 剩余包）。

### 17.6 批 4 最终范围：**只做 AutoSprint，0 个新 mixin**

作者裁定 **Velocity 不移植**。于是 `PacketReceiveEvent` 的唯一消费者也没了 ⇒ §17.2 里那唯一一个必需的
速度包 mixin **也不再需要**。

| 项 | 处置 |
|---|---|
| **AutoSprint** | ✅ **已移植** → `client/AutoSprintHandler.kt`，**复用现有客户端 tick，0 个新 mixin** |
| Velocity | ❌ 作者裁定不移植 |
| `SlowDownEvent` + `MixinEntityPlayerSP` 注入 | ⏸ **待作者裁定**：它位于 `extraforgeapi/extraevents/`，包名像是给第三方模组用的公共 API，"0 内部消费者"不等于可删 |
| `MixinMinecraft` / `MixinEntityRenderer` / `MixinNetworkManager` | ❌ 不需要（其事件无消费者） |
| ManaPool tooltip | ⏸ 仍待 `PacketManaPoolSync` |

**新增配置项**：`autoSprintEnabled`（默认 `false`，对应 1.12.2 `Module.state`）、
`autoSprintOmniSprint`（默认 `false`）。已接入配置 GUI **第 3 页「移动」**。

**保真细节**：`isSprinting = true` 的条件按 1.12.2 的**运算符优先级**原样写
（`(moveForward > 0.1f && !isSneaking) || omniSprint`）；`Module.onDisable()` 的
`isSprinting = false` 用**下降沿**复现（扁平化配置没有 `onDisable` 钩子）；只取 `Phase.END`，
因为 1.12.2 的 `PlayerUpdateEvent` 每 tick 只触发一次而本移植两相都发。

> 结果：**批 4 从"4 个 mixin / 20 个注入点"缩到"1 个 handler、0 个 mixin"** ——
> 全部来自"反查消费者"这一步，而不是"照 1.12.2 逐个搬"。

---

## 18. `SlowDownEvent` 已移植（公共 API），以及一个必须说清的限制

作者裁定要移植。它是 **`extraforgeapi/extraevents/` 里的公共扩展点** —— 本模组自己一个监听者都没有，
存在意义是让**第三方模组**能观察/覆盖潜行·格挡·进食·拉弓时的移动减速。

| 项 | 内容 |
|---|---|
| 事件 | `compat/event/SlowDownEvent.kt` —— 字段、`speedFactor` 算术、`Type` 全部逐行照搬，只有基类从 Forge 的换成 [Event] |
| 注入 | `mixin/InputSlowDownMixin.java` —— `@Mixin(Input.class)`，`tick(ZF)V` **RETURN** |
| 列表 | **`client`**（`net.minecraft.client.input.Input` 是客户端类） |
| refmap | `tick(ZF)V` → `class_744;method_3129(ZF)V` ✅ |

**落点为什么变了，以及我怎么补偿的**：1.21 把"读键位"和"施加 0.3 惩罚"合并进了
`Input#tick(boolean, float)` 一个方法（已从 jar 核实签名）。1.12.2 是在惩罚**之前**抛事件的，所以：

- 抛事件**前**先把输入**除以** `slowDownFactor` ⇒ 监听者看到的 `forward`/`strafe` 仍是"惩罚前的原始输入"，语义与 1.12.2 一致；
- 写回时**乘回** `factor` ⇒ 监听者设 `speedFactor = s` 后实际移动速度就是 `s` 倍，与 1.12.2 的预补偿**净结果相同**。

选 RETURN 而非"在惩罚前插入"，是因为产生原始值的那几次字段写入和消费它们的乘法**在同一个方法里、
中间没有可锚定的调用**。

**override 审计**：`Input` 是无子类的具体类（vanilla jar 内），没有可绕过的重写；且只有本地玩家的
`input.tick` 会被调用（`ClientPlayerEntity#tickMovement`），身份检查是双保险。

### ⚠️ 一个必须说清的限制：第三方**目前拿不到**这个事件

1.12.2 的 `MinecraftForge.EVENT_BUS` 是**全局共享**的，所以任何模组都能监听。
本移植的 [ForgeEventBus] 是**本模组自己的**总线 —— 第三方模组除非**依赖本模组并调用
`ForgeEventBus.register(owner)`**，否则收不到 `SlowDownEvent`。

也就是说：**事件本身已移植且行为正确，但"公共 API"这层目前是名义上的。**
干净的做法是加一个稳定的门面（例如 `SimpleTweaksApi.registerSlowDownListener(...)`，
把内部总线藏起来），而不是让第三方去碰 `compat` 内部类。**这属于新增设计，需作者决定是否做。**

---

## 19. `/enchantinfo` + 附魔图鉴（批 5 的最后一项）

1.12.2 `gui/GuiEnchantInfo.kt`（317 行）→ 用原版 `Screen` 重实现，不经 1.12.2 的 GUI 框架。

**布局照搬**（含 1.12.2 那个略显古怪的 `guiLeft + 8 + 18 + col * 18 + 18`）：

| 视图 | 内容 |
|---|---|
| 分类菜单 | 4 列彩色羊毛图标；悬停显示 分类名 / 数量 / 点击提示 |
| 附魔列表 | 9×5 闪光附魔书；滚轮翻页；返回箭头；悬停显示 名称/分类/适用/最大等级/描述 |

**映射表**

| 1.12.2 | 这里 |
|---|---|
| `EnchantmentCategories` 枚举 | 生成的 `EnchantmentTiers.CATEGORY` |
| `ModEnchantmentType` 枚举 | 生成的 `EnchantmentTiers.TYPE` |
| `ench.getMaxLevel()` | 生成的 `EnchantmentTiers.MAX_LEVEL` |
| 遍历 `Enchantment.REGISTRY` | 遍历 `ModEnchantmentKeys.ALL` |
| `ItemStack(Blocks.WOOL, 1, meta)` | 对应的 1.21 `Items.*_WOOL`（元数据 0/7/13/11/2/4/14/3 → 白/灰/绿/蓝/品红/黄/红/淡蓝） |
| `ItemEnchantedBook.addEnchantment(book, ...)` | `DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE` |
| `drawTexturedModalRect(generic_54.png)` | `DrawContext.fill` 面板 |

**为什么三张表都靠生成**：1.21 的附魔是数据驱动的，这三个概念在代码里都不存在；而从客户端注册表读回来
会给"注册表是否已同步"增加一个失败面。**整个图鉴 = 静态数据 + 渲染，没有注册表查询。**

### 三处有意的保真偏离（均已知）

1. **面板用纯色填充而非原版箱子贴图。** 1.21 的 `DrawContext.drawTexture` 签名比 1.12.2 的
   `drawTexturedModalRect` 复杂得多，而面板只是装饰。
2. **附魔书的闪光用 `ENCHANTMENT_GLINT_OVERRIDE`，不是真的 `STORED_ENCHANTMENTS` 组件。**
   因为 `ItemEnchantmentsComponent.Builder` 唯一的构造函数要传入一个**已有组件**，而且要
   `RegistryEntry<Enchantment>` —— `Registry` 在 1.21.1 **没有** `getEntry(RegistryKey)`
   （已从 jar 核实，只有 `getEntry(int)` / `getEntryInfo` / `getEntryCodec`）。
   **后果：这些不是真正的附魔书**，将来若有逻辑去读它的附魔组件会落空。
3. **提示框名称用 lang 键 + 生成的颜色，而非 `Enchantment.getName`**（同样为了不碰注册表）。
   可见后果：`infinite_power` 在这里显示分类色，而不是它的流动彩虹名。

### `/enchantinfo` 的实现偏离（作者已同意）

1.12.2 是**服务端命令 + 空包**：`PacketOpenEnchantInfo` **没有任何载荷**，存在的唯一目的就是让客户端
开界面。这里做成**客户端命令**（`/enchantinfo` + 别名 `/ei`、`/enchinfo`，三个独立字面量而不是
Brigadier redirect，因为 1.12.2 里它们就是三个平级名字），**不存在也不需要那个包** ——
界面内容全部是客户端渲染信息。

**顺带**：§16 那个 `ChatScreen` 坑的修法（延后一 tick 开界面）抽成了 `client/ClientScreenOpener.kt`，
`/stconfig` 与 `/enchantinfo` 共用，避免两份拷贝各自演化。

**验证状态**：构建 ✅；动态 ⏳ 待作者实测。

---

## 20. 阶段 7 收尾状态

### 已完成

| 项 | 结果 |
|---|---|
| `/enchantinfo` + 附魔图鉴 | ✅ 见 §19；分类版式按作者要求改成两行（§19 的 KDoc 已标注为有意偏离） |
| **配置 GUI lang 键** | ✅ 30 个键加进**生成器的 `$extraLang`**，而不是手改生成产物（下次重跑会覆盖）。带**冲突检查**：与 1.12.2 已有键撞名直接 `throw`，因为静默覆盖会在一份"看起来正常"的 JSON 里藏掉一边的文案。lang **140 → 170** 键，中英同步、无乱码 |
| 打包审计 | ✅ **`fabric.mod.json` 无需改动**。1.12.2 `mcmod.info` 的 `url` / `credits` / `logoFile` **三者全为空字符串** ⇒ 本来就没有图标，`contact: {}` 是忠实的；`description` 与 `authorList` 已逐字保留；`required-after:mixinbooter` 是 Forge 专有依赖（Fabric 由 loader 直接施加 mixin，无对应物） |
| `/attribute`（146 行） | ❌ **不移植** —— **1.21 原版自带 `/attribute`** 且更强。1.12.2 没有才需要自己写，移植只会与原生重复或冲突 |
| `util/ItemUtil.kt`、`util/ForgeEventUtils.kt` | ❌ **不移植** —— 全树**零引用**（死代码；`ForgeEventUtils` 只在一处 KDoc 里被提到） |

### ⚠️ 仍属阶段 7 的一个真实项：`CommandEnchantHandler`（未做）

`enchantments/others/CommandEnchantHandler.kt`（56 行）提供 **`/enchant <目标> <附魔> 0` = 移除附魔**：
它订阅 Forge 的 `CommandEvent`，命令名为 `enchant`、参数足够、且**等级为 0** 时，
取消原版命令并从目标**主手**移除该附魔，再给玩家发一条提示。
MIGRATION.md:1126 正是把 `CommandEvent` 归入阶段 7。

**1.21 复现它在结构上更难，原因值得单独记：**

| | 1.12.2 | 1.21 |
|---|---|---|
| 等级参数 | 无下限的 `int` ⇒ **`0` 能通过解析** | `IntegerArgumentType.integer(1)` ⇒ **`0` 在解析阶段就被拒** |
| 事件时机 | Forge `CommandEvent` 在**参数解析之后、执行之前** | Brigadier 的 `execute` **根本不会跑到** |

也就是说：**在 1.21 里照搬"挂一个命令事件"会得到一个永远不会触发的注入。**
要做到同样的事必须**两步**：

1. 用 `@ModifyArg` 把 `EnchantCommand.register` 里 `IntegerArgumentType.integer(1)` 的 `1` 改成 `0`（让 `0` 可解析）；
2. 在 `EnchantCommand.execute` 头部 `@Inject`，当 `level == 0` 时自行移除附魔、回消息、并 cancel。

需要的 API（`EnchantmentArgumentType.getEnchantment`、`ItemEnchantmentsComponent` 的空实例、
参数名 `targets` / `enchantment` / `level`）**尚未核实**，所以这是一轮独立工作。

> **这是一条通用教训，不限于本项**：`CommandEvent` 与 Brigadier 的差异说明
> **"接缝位置相同" ≠ "可拦截点相同"** —— 参数校验发生的时间点变了，原接缝就可能落在解析之后。
> 与 §14.6（缩放符号）属同一类：**名字对得上不代表行为对得上**。

### 决定：**不移植**（作者裁定）

`/enchant ... 0` 移除附魔 **不移植**，记为已知未做。已有的替代路径：
1.21 原版 `/enchant` 无法移除，但**铁砧祛魔**（`GeneralConfig.anvilDisenchant`，已移植）本来就是这个用途的
面向玩家的正规入口；为它引入两个 mixin（改参数下限 + 拦截 execute）性价比不足。

**阶段 7 到此结束。**

---

## 21. 弓箭三附魔全部保真移植（`multishot` / `tracking_arrow` / `piercing_arrow`）

原本在 `deferred/`。MIGRATION.md 记的四个阻塞项里，**`PlayerUtils.runPlayerAttack`（需 `CriticalHitEvent`）已由批 3 解除**，其余三项在本节解决。

### 新增基础设施（5 个文件 / 4 个 mixin）

| 文件 | 作用 |
|---|---|
| `compat/event/ArrowLooseEvent.kt` + `mixin/BowItemArrowLooseMixin` | Forge `ArrowLooseEvent` 的替代：`BowItem#onStoppedUsing` **HEAD**、cancellable。`getMaxUseTime` 用 `@Shadow`，所以**改过拉弓时长的模组弓也正确** |
| `interfaces/SimpleTweaksArrow.java` | 箭矢状态接口。**写成 Java** —— 方法名带字面 `$`，Kotlin 属性会生成不匹配的 `getSimpletweaks$...` |
| `mixin/PersistentProjectileEntityStateMixin` | 穿透三元组用**同步** `TrackedData`（照 1.12.2）；齐射标记与追踪等级是纯服务端字段 |
| `mixin/PersistentProjectileEntityAccessor` | 取 `protected inGround`（1.12.2 的 `EntityArrowAccessor`） |
| `mixin/PersistentProjectileEntityPiercingMixin` | `onEntityHit` HEAD cancel —— 见下 |

### 三个改变了实现方式的发现

1. **1.21 原版本来就有 Multishot 与 Piercing，而且是数据驱动的**（`minecraft:projectile_count` /
   `projectile_spread` / `projectile_piercing`；原版 `multishot.json` / `piercing.json` 就是范例）。
   作者选择**不用**声明式，理由是保真：声明式**无法表达** `charge < 5` 门槛，也无法表达 piercing 的
   `maxPerTarget`（同一目标可被同一支箭多次命中）。
   > ⚠️ **我上一轮报告"`projectile_spread` 只在弩上用、弓会失去扇形"是错的，此处更正。**
   > `BowItem#onStoppedUsing` 正是调用 `RangedWeaponItem#shootAll`，而 `shootAll` 会读散布并做
   > **确定性扇形**（`yaw_i = ±((i+1)/2) * 2·spread/(size-1)`）。那次错误结论**影响了作者的取舍判断**，
   > 是"没读权威实现就下结论"的又一次代价。
2. **`onEntityHit` 不能简单 HEAD cancel。** 1.21 把"造成伤害"和"停止飞行"写在**同一个方法体**里，
   blanket cancel 会得到一支**穿透但完全不造成伤害**的箭 —— 又一个"名字对得上、语义对不上"。
   正确做法是 cancel + **自己复现伤害**（`runPlayerAttack`）。
3. **`Entity.timeUntilRegen` 在 1.21 是 `public` 且位于 `Entity`**（不在 `LivingEntity`），
   所以齐射无敌帧清零**不需要 accessor/mixin**（1.12.2 用的是 `hurtResistantTime`）。
   而且本移植的 `LivingHurtEvent` 正好在 `LivingEntity#damage` **头部**、即无敌帧检查**之前**，位置刚好。

### 保真映射要点

- 箭矢用**原版工厂**构造：`ArrowItem#createArrow(World, 弹药栈, 射手, 武器栈)`。药箭/光灵箭/模组箭
  **自动正确**；1.12.2 是 `new EntityTippedArrow` + 特判药箭再 `setPotionEffect`。
- `st_pierce_hits` 的打包字符串 → **每箭一个 `Map<Int,Int>` 字段**：同样是"每目标已命中次数"，
  省掉 1.12.2 每次碰撞的 `split`/`join`。
- **"最后一次命中会不会双重伤害"**：本处理器以 `forceHit = true` 调用 `runPlayerAttack`，它先清零
  `timeUntilRegen`，而命中成功后该值被置为 20，因此原版紧随其后的那次伤害**被无敌帧吞掉**。
  可观察结果是"一次有效伤害 + 箭停下"，正是意图。这条特意记录，因为控制流看起来像 bug。
- `Entity#isTeammate` 在 1.21 位于 **`Entity`**（不在 `LivingEntity`），已按实际位置调用。

### ⚠️ 顺带查出、**未修改**的生成器既有偏差

三个 BOW 附魔的 JSON 里都有 `minecraft:damage`（`add 0.3/级`）。但 1.12.2 的 `itemExtraDamage()`：

```kotlin
return if (this.modType == ModEnchantmentType.SWORD) (0.2 + category.rarity / 20.0) * level else 0.0
```

**只有 SWORD 返回非零**，BOW / BREAKABLE / ARMOR 等都返回 0。生成器似乎没有实现这个类型判断 ——
`multishot`（BOW）、`soul_bound`（BREAKABLE）都拿到了伤害效果，属**相对 1.12.2 的行为扩张**。
**需作者裁定是否修**（本节按"不改生成器"处理，以免与弓箭改动混在一起）。

---

## 22. 🎯 找到 1.12.2 的反编译产物 —— 以及它直接解决的一个悬案

### 22.1 资源位置（此后所有 1.12.2 语义问题都应先查这里，而不是推理）

```
F:\Codes\Mod\FireFly's Simple Tweaks-1.12.2\build\rfg\recompiled_minecraft-1.12.2.jar
```
这是**反编译且去混淆的 1.12.2 Minecraft**，可以直接 `javap`。
另外 `E:\gradle\caches\minecraft\net\minecraftforge\forge\1.12.2-14.23.5.2847\unpacked\`
下有 Forge 的 `classes.jar`、`sources.zip`（**只有 Forge 自己的类，不含 MC**）与 `src/`。

> **方法**：`javap -p -c` 之后**合并折行**，再扫 `invokevirtual ...Method <owner>.<name>`；
> 若要"**谁引用了它**"，直接**扫描 jar 内每个 `.class` 的常量池字节**最快 —— 调用方必然在常量池里含该字符串。
> 本轮就是靠这一步把悬案一次性锁死的。

### 22.2 悬案：`isMagicDamage()` 到底影响什么

上一轮我写"`isMagicDamage()` 只作用于 `applyPotionDamageCalculations`（抗性药水那一步），无法核实，故不映射"。
**这句话是错的** —— 该方法根本不调用它。而且我当时是**凭印象**说的。

**实际做法**：常量池扫描整个 1.12.2 jar，引用 `isMagicDamage` 的类**只有 4 个**：

| 类 | 作用 |
|---|---|
| `DamageSource` | 声明它 |
| `DamageSourcePredicate` | 进度判据 |
| **`EntityWitch`** | `applyPotionDamageCalculations` 里 `damage *= 0.15` —— 女巫只承受 15% 魔法伤害 |
| **`EntityGuardian`** | `attackEntityFrom` 里 `!source.isMagicDamage()` 才反伤 |

而 `applyArmorCalculations` / `applyPotionDamageCalculations` / `EnchantmentProtection` **一次都没调用它**
⇒ **魔法伤害并不豁免抗性药水，也不豁免保护附魔** ⇒ 这个类型**不能**加 `bypasses_resistance` / `bypasses_enchantments`。

**另一侧同样核对过**（1.21.1 原版标签内容）：

| 1.21 标签 | 原版内容 |
|---|---|
| `#minecraft:witch_resistant_to` | `magic`、`indirect_magic`、`sonic_boom`、`thorns` |
| `#minecraft:avoids_guardian_thorns` | `magic`、`thorns`、`#is_explosion` |

两者正好就是 1.12.2 那两个消费点所依据的"魔法伤害"分组 ⇒ **两侧都确认，不再是单向推断。**

### 22.3 `isUnblockable()` 的映射（有据，且已确认）

常量池扫描只找到 **2 个消费点**：

| 消费点 | 1.12.2 行为 | 1.21 标签 |
|---|---|---|
| `EntityLivingBase#canBlockDamageSource` | 盾牌挡不住 | `#minecraft:bypasses_shield` |
| `EntityLivingBase#applyArmorCalculations` | 整个护甲步（含 `damageArmor` 与 `getDamageAfterAbsorb`）被跳过 | `#minecraft:bypasses_armor` |

### 22.4 最终映射与一处**刻意保留的冗余**

`simple_tweaks:celestial_particle` 加入 **4 个原版标签**：
`bypasses_armor`、`bypasses_shield`、`witch_resistant_to`、`avoids_guardian_thorns`。

原版的 `bypasses_shield` **已经包含 `#bypasses_armor`**，所以 `bypasses_shield` 那条是**传递冗余**的；
保留它是为了让意图在本模组自己的数据里可读，而不是隐式依赖原版标签的组成。

**jar**：`0FFC7864AF64007F` / 456407 bytes。

### 22.5 教训

「无法核实」**不等于**「无法核实」—— 本轮之前我没有去找 1.12.2 的产物就下了"不可核实"的结论，
而它其实一直躺在同一个仓库的 `build/rfg/` 下。**在宣布某件事不可验证之前，先确认自己找过所有可能的证据源。**

---

## 23. CelestialBlessing 完成 —— **`deferred/` 已空**

`EnchantCelestialBlessingHandler`（1.12.2 534 行）是本项目最大的单个处理器。本轮连同它依赖的全部基础设施一起移植完毕：

| 组件 | 文件 |
|---|---|
| 伤害类型 + 4 个原版标签 | `data/simple_tweaks/damage_type/celestial_particle.json` 等 |
| 伤害源 | `util/damagesource/CelestialDamageSources.kt` |
| 两个包 | `PacketCelestialRing` / `PacketManaPoolSync`（线格式逐字节一致） |
| 客户端缓存 | `client/ClientManaPoolCache.kt` |
| 粒子 | `particle/ModParticles.kt` + `client/particle/CelestialRingParticle.kt` + `CelestialRingParticles.kt` + `assets/simple_tweaks/particles/celestial_ring.json` |
| 提示行 | `client/tooltips/ManaPoolToolTipHandler.kt` |
| 主处理器 | `enchantments/handlers/mystery/EnchantCelestialBlessingHandler.kt` |
| `sendToAllAround` | 补进 `NetworkManager`（1.12.2 有，之前未写） |

### 23.1 需要"判断"而不是"查表"的映射

| 1.12.2 | 这里 | 理由 |
|---|---|---|
| `isCreatureType(EnumCreatureType.MONSTER, false)` | `this is Monster` | 1.21 删除了 `EnumCreatureType`，且 35 个 `entity_type` 标签里**没有** monster 标签（已核实）。`Monster` 是 1.21 惯用法（作者选定），但**不是同一个集合** |
| `EntityLiving.attackTarget` | `MobEntity.target` | 直接改名 |
| `EntityCreature` / `EntityAnimal` / `EntityVillager` | `MobEntity` / `AnimalEntity` / `VillagerEntity` | **检查顺序原样保留**（先动物/村民、再通用生物），否则分类会变 |
| `UUID.nameUUIDFromBytes(...)` ×3 | `Identifier.of(MOD_ID, …)` | 1.21 的 `AttributeModifier` 以 `Identifier` 为键 |
| `applyModifier(...)` + `setSaved(false)` | `addTemporaryModifier(...)` | 1.21 把"跨重载保存"与"临时"分开了 |
| `world.getEntities(Class) { uuid 匹配 }` | `ServerWorld#getEntity(UUID)` | 1.21 有直接按 UUID 查的 API，线性扫描整套已加载实体不再需要 |
| `world.canPositionSee(x,y,z,entity)` | `World#hasLineOfSight(Vec3d, Vec3d)` | 端口的实体重载本来就有；点重载从未移植 |
| `world.getEntities(...)` 全量扫描（`findChainTarget`） | 先按 64 半径建 `Box` 再查 | **同一谓词**先被包围盒限定，等价且更快 |

### 23.2 一处**有意不移植**的方法

`onClientDisconnect` 原本清空客户端法力缓存。把它留在这个 **common** 类里会迫使
`client/ClientManaPoolCache` 被专用服务端加载 —— 这是本项目一开始就定下的边界。
该清理改由 `SimpleTweaksClient` 在 `ClientPlayConnectionEvents.DISCONNECT` 上完成，位置也更正确。

### 23.3 构建期发现的两个符号名错误（已修正）

| 写的 | 实际的 1.21 yarn 名 |
|---|---|
| `AttributeModifier` | **`EntityAttributeModifier`**（1.21 改名） |
| `Tameable.isTamed()` | `Tameable` 接口**只有** `getOwnerUuid/getWorld/getOwner`；`isTamed()` 在**具体类** `TameableEntity` 上 |

### 23.4 状态

**jar**：`1715502FF6C0CA9B` / 484432 bytes。
**`deferred/` 目录已空** —— 除作者裁定的 **InfinitePower**（见 `infinite-power-deferred.md`）外，
再无缓做项。

---

## 24. 附魔台「覆盖而非叠加」+ 天赐无视抗性

### 24.1 附魔台 change(3)：第三次尝试成功

判据来自 `method_17410` 的完整字节码（不是推断）。1.12.2 的语义窗口是「报价生成之后、写入之前」，
1.21.1 里唯一满足它的位置是 **offset 38（`applyEnchantmentCosts` 之后、`isOf(Items.BOOK)` 之前）**。

之前两次尝试都锚在窗口**之外**：`@Inject HEAD`（早于 offset 18 的生成）与 `@Redirect addEnchantment`。
第三次能成立的理由是**结构性**的：offset 33 被 offset 30 的 `ifne 240` 支配，所以回调只在报价列表非空时运行
——「清空后物品被剥光」这个失败模式不再只是"不太可能"，而是不可能。写入循环本身一行未动。

细节、字节码布局与验收表见 `phase4-mixin-notes.md` §2.1。每次替换打一行日志：
`[enchant-table] re-enchant on <item>: replaced N existing enchantment(s)`。

**未做**：④ 附魔书能出报价（`ENCHANTED_BOOK.getEnchantability()==0`，见同档 §3）。

### 24.2 `#minecraft:bypasses_resistance`：一处**故意偏离 1.12.2** 的改动

1.12.2 的 `isMagicDamage()` 只有两个消费者（女巫减伤、守卫者不荆棘），**没有任何一个**让天赐伤害免疫
抗性提升药水；1.21.1 的两个候选 tag 也**不是同义词**：

| tag | 效果 | 原版成员 |
|---|---|---|
| `#minecraft:bypasses_resistance` | 只跳过抗性那一步 | `out_of_world`、`generic_kill` |
| `#minecraft:bypasses_effects` | 在 `modifyAppliedDamage` 开头就 return，**连 Protection 附魔一起跳过** | 只有 `starve` |

按作者要求加的是前者：它表达的正好是「无视抗性提升药水」，不做多余的事。
`bypasses_effects` 更宽，会让天赐伤害连**保护 IV** 一起无视 —— 那不是被要求的行为。
`bypasses_enchantments` 仍未添加。同类先例：本 mod 自己的 `simple_tweaks:reflection` 早就用 `bypasses_resistance`。

**jar**：`31FAD8896BA6B847` / 485394 bytes。启动行从 `build=celestial` 改为 **`build=enchantfix`**
（本轮的改动是服务端 mixin + 数据标签，客户端看不到别的迹象，只能靠这一行区分新旧 jar）。

---

## 25. Starfall 世界地板 bug（已修）+ Multishot 无敌帧（未确诊）

### 25.1 Starfall：`y < 0` 在 1.12.2 是"掉出世界"，在 1.21.1 是"半张地图"

作者的复现日志（`logs/2026-10-06-7.log.gz`，04:00）给出了判决性证据：

| 观测量 | 值 | 含义 |
|---|---|---|
| `outcome=star-impact` 且 `life=` | **90 / 90 都是 `life=1`** | 每颗星在**第一 tick** 就爆炸 |
| `starY` 范围 | **-48.45 … -46** | 全部为负 |

根因：1.12.2 的世界地板是 y=0，所以 `if (star.y < 0.0) impact = true` 等价于"掉出世界"。
1.21.1 主世界地板是 **y=-64**，于是这颗星在**生成点**（`target.y + 15`，即目标上方约 15 格、
在自己 5.5 半径之外）立刻判定落地 → 空中原地爆炸、谁也打不到。

`findValidSpawnY` 里的 `while (y > 1.0)` 是同一个假设的第二处（y≤1 时探针**根本不跑**）。
两处都改成 `world.bottomY`。

**这个 bug 在 y>0 的地面上不存在** —— 这正是它通过了上一轮验收的原因。

### 25.2 Multishot：机制是对的，所以缺的是一个观测量

`LivingEntity#damage` 的无敌帧守卫在 offsets 228..237：
`timeUntilRegen > 10 && !isIn(BYPASSES_COOLDOWN)` → 走"已受伤"分支（伤害 ≤ 上次则直接 return false）。
`EntityDamageMixin` 注入在 **HEAD**，早于该守卫 → **只要归零真的执行了，就必然生效**。

所以失败前提只可能是两者之一：①`onHurt` 根本没被调用，②落到目标上的箭 `isMultishotArrow()` 为 false。
两者从玩家侧完全一样。已在 `onHurt` 加两行 `STLog`：**第一行在 flag 检查之前**（所以"完全没有这一行"
即证明钩子没被调用），第二行在归零之后。

**不能用 tag 绕过**：`#minecraft:bypasses_cooldown` 被 `LivingEntity#damage` 引用，但
**1.21.1 原版数据里没有这个 tag 文件**（解析为空），而把 `minecraft:arrow` 塞进去会让全游戏的箭都无视无敌帧。

**jar**：`B9F28022E0694591` / 486618 bytes，启动行 `build=starfallfix`。

---

## 26. 附魔台三处修复（第 4 次尝试：这次有根因实证）

### 26.1 真正的根因：**删掉组件**，不是注入点

作者回报「重附魔后附魔被删除且无法再次附魔」——与第 1、2 次尝试**症状完全相同**。共同因素既然不是
锚点，就只能是"清空"这个动作本身。`ItemStack#addEnchantment` 实际是：

```
EnchantmentHelper.apply(stack, consumer):
     0: type = getEnchantmentsComponentType(stack)   // ENCHANTED_BOOK ? STORED_ENCHANTMENTS : ENCHANTMENTS
     5: c = stack.get(type)
    14: if (c == null) return ItemEnchantmentsComponent.DEFAULT;   ← 静默空操作
    22: builder = new Builder(c); consumer.accept(builder);
    51: stack.set(type, builder.build())
```

`remove()` 之后 `get()` 返回 null → **之后每一次 `addEnchantment` 都被丢弃**。清空执行了、写入被吞了、
物品被剥光；而且组件此时**不存在**，`onContentChanged` 再也算不出报价 → 「无法再次附魔」。

**修法：`set(DEFAULT)` 而不是 `remove`**，组件保持"存在但为空"，写入路径恢复。
组件类型按 `getEnchantmentsComponentType` 镜像 —— 附魔书存的是 `STORED_ENCHANTMENTS`，清错那个等于没清。

| 尝试 | 锚点 | 清空方式 | 结果 |
|---|---|---|---|
| 1 | HEAD | remove | 剥光 |
| 2 | `@Redirect addEnchantment` | remove | 剥光 |
| 3 | `applyEnchantmentCosts` 之后 | remove | 剥光 |
| **4** | `applyEnchantmentCosts` 之后 | **set(DEFAULT)** | — |

锚点仍然有用（它在 `ifne 240` 早退之后，只在有东西可写时才触发），但**锚点从来不是充分条件** ——
§24.1 与 `phase4-mixin-notes.md` §2.1 都曾错误地宣称它是。

### 26.2 附魔书：要同时放行**两处** `getEnchantability()`

> ⚠️ **实测结果（2026-10-06，`build=enchanttable2`）：装备重附魔 ✅ 成功；附魔书重附魔 ❌ 仍失败。**
> 下面两处 `@Redirect` 已确认被 refmap 解析（`calculateRequiredExperienceLevel` /
> `getEnchantability` 都在映射表里），所以失败点不在这两处"有没有生效"，
> 而在下面两个候选中**之一**，二者用**一次**测量即可区分：
>
> | 候选 | 一次就能区分它的观测量 |
> |---|---|
> | A. 报价侧仍然为 0（书的 `enchantmentPower` 仍算不出来，所以按钮不可点，`method_17410` 从未进入） | 把书放进附魔台时打印 `enchantmentPower` 三个值 |
> | B. 报价有了，但写入落到别的组件上（`withItem` 复制后的 `local8` 上 `STORED_ENCHANTMENTS` 为 null，`apply` 静默丢弃） | 在 §26.1 那条 re-enchant 日志旁再打一行：进入 `method_17410` 时的 `inventory.getStack(0)` 组件类型与 `get(type) == null` |
>
> **不要在没有这两行日志之前再改代码。** 这个 seam 已经错了四次，四次的共同教训就是"没有观测量时改动等于猜"。

`ENCHANTED_BOOK` 的附魔力是 0，1.21.1 里这会挡住两个地方，**只修一个不够**：

| 方法 | 早退 | 不修的后果 |
|---|---|---|
| `calculateRequiredExperienceLevel` | offset 13..19 `i <= 0 → return 0` | 报价恒为 0，`method_17410` 在 `enchantmentPower[id] <= 0` 处直接返回 |
| `generateEnchantments` | offset 13..25 `i <= 0 → 空列表` | 就算有报价也生成不出附魔 |

1.12.2 也正是分了两处（`MixinContainerEnchantment.firefly$forceEnchantability` + `MixinEnchantmentHelper`）。
1.21 的 getter 去掉了 stack 参数，但 `@Redirect` 的**接收者就是那个 Item**，所以不需要 stack。

### 26.3 最低附魔条数：等价位置是 `EnchantmentHelper.generateEnchantments`

1.12.2 在 `buildEnchantmentList` RETURN 注入，`minEnchants = level / 15`，不足则最多 30 次随机补齐。
1.21 的对应物是 `EnchantmentHelper.generateEnchantments(Random, ItemStack, int, Stream)`。

差异在于候选集来源：1.12.2 遍历 `Enchantment.REGISTRY`，1.21 走 `#minecraft:in_enchanting_table` 标签。
因此用 ThreadLocal 在 `getPossibleEntries` 的 RETURN 捕获候选列表 —— 那是**已经**按等级、
`isPrimaryItem(stack)` 与标签过滤过的集合，比重新推导更贴近原意（宝藏附魔不在标签里，正好替代了
1.12.2 的 `allowTreasure` 判断）。权重取 `Enchantment#getWeight()`（即 JSON 的 weight）。

**jar**：`B3F122C04FE9F4F0` / 490309 bytes，启动行 `build=enchanttable2`。

### 26.4 附魔台等级上限：配置项此前"持久化了但没人读"

`SimpleTweaksConfig` 一直在读写 `maxEnchantmentPower` / `disableEnchantmentTableLimit`，配置界面也暴露了开关，
但**没有任何代码消费这个值**。1.12.2 的对应物是 `MixinEnchantmentHelper#modifyMaxPower`
（`@ModifyConstant(intValue = 15)` on `calcItemStackEnchantability`），1.21.1 的等价位置是
`EnchantmentHelper.calculateRequiredExperienceLevel`。

该方法里 `15` 只出现两次，而且必须**同时**替换才成立：

```
20: iload_2      (bookshelfCount)
21: bipush 15    ← 比较操作数
23: if_icmple 29
26: bipush 15    ← 截断值
28: istore_2
```

两个都换成配置值，得到的正是 `if (bookshelfCount > max) bookshelfCount = max;`。只换一个会得到一个自相矛盾的
分支，所以这里**故意不加 `ordinal`**，匹配全部两处。配置值经 `EnchantTableGate.maxEnchantmentPower()` 读取
（`@JvmStatic`，与既有的 `canEnchant` 同一套 Java↔Kotlin 调用方式），`coerceAtLeast(1)` 也在那里再做一次。

注意 `maxEnchantmentPower` 的**默认值就是 15**（等于原版），所以默认配置下这个钩子是**设计上的空操作** ——
只有把它调大、且书架数超过 15 时才看得出区别。

### 26.5 附魔书的诊断日志（本轮构建的目的就是取这两个数）

`build=enchanttable3` 加了两行，跑一次即可区分 §26.2 表格里的 A / B：

| 日志行 | 读法 |
|---|---|
| `[enchant-table] book offer power = [a, b, c], vanillaEnchantable=…` | 出现在 `onContentChanged` 末尾。**全是 0** ⇒ 候选 A（报价侧仍被挡，`method_17410` 从未进入） |
| `[enchant-table] book in slot 0: type=…, present=…, oldCount=…` | 出现在 `method_17410` 里。**它出现且 power 非 0** ⇒ 候选 B（写入被丢弃）；`present=false` 直接指出组件类型取错了 |

### 26.6 附魔书"失败"的实测结论：**不是附魔书的问题，是点击被门槛挡掉了**

`build=enchanttable3` 的实测日志（`latest.log` 10:35）给出了判决：

```
[Server thread] [enchant-table] book offer power = [14, 34, 64], vanillaEnchantable=false
```

**报价侧完全正常**（§26.2 的两处 `getEnchantability` 放行生效）。而
`[enchant-table] book in slot 0: …` **一行都没有** —— 也就是说 `method_17410` **从未被进入**，
附魔书的写入代码路径**根本没跑过**。所以失败点在点击那一层。

`EnchantmentScreenHandler.onButtonClick`（offsets 86..159）的全部守卫，**没有一条与附魔书有关**：

| offset | 条件 | 不满足时 |
|---|---|---|
| 92 | `enchantmentPower[id] > 0` | return false |
| 99 | `!itemStack.isEmpty()` | return false |
| 102..121 | `experienceLevel >= id+1` **且** `experienceLevel >= enchantmentPower[id]` | 落到 124 |
| 124..131 | 上面不满足时，只有创造模式才放行 | return false |

剑与书走的是同一条路径，剑能成功 ⇒ 路径没问题 ⇒ **是这次点击的门槛没过**。

**门槛从哪来的**：`[14, 34, 64]` 里第三项 = `max(j, power*2)`，反推出 `maxEnchantmentPower = 32`。
所以 §26.4 那个配置**确实生效了**（用户也观察到"EnchantPower 提升通过"），但它的副作用是
**报价即所需等级** —— 三个按钮要 14 / 34 / 64 级。而 `applyEnchantmentCosts` 只扣 **1/2/3 级**：
`enchantmentPower` 是**门槛**，不是花费。1.12.2 的上限是书架 15（`power*2 = 30`），提到 32 之后
高门槛报价出现得更频繁。

**门槛假设被推翻（作者补充：他就在创造模式下）。** 创造模式下 `onButtonClick` 的全部守卫都被跳过，
所以 `method_17410` **必然**被进入、日志**必然**出现。它没出现 ⇒ 唯一的解释是
`method_17410` 在 offset 30 就早退了：`if (list.isEmpty()) goto 240`。而探针在 offset 38（早退**之后**），
所以看不到。**报价正常，但生成出来的附魔列表是空的。**

### 26.7 真正的根因：附魔书在 `getPossibleEntries` 里不算"书"

1.12.2 的 `MixinEnchantmentHelper#firefly$getEnchantmentDatas` 整段改写 `getEnchantmentDatas`，
对附魔书改用 `isAllowedOnBooks()` 而不是 `canApply(stack)` —— 当时只被当成"附魔书附魔力为 0"的补丁，
其实是**同一个问题的另一半**。1.21.1 里它对应对 `getPossibleEntries` 的候选过滤器：

```
 4: aload_1  (stack)
 5: getstatic  Items.BOOK
 8: invokevirtual ItemStack.isOf
11: istore 4                 isBook
13: stream.filter(lambda(stack, isBook))
```

`Items.ENCHANTED_BOOK.isOf(Items.BOOK)` 是 **false**（两个不同的物品）⇒ 附魔书走"非书"分支，
用 `isPrimaryItem(stack)` 过滤；而**没有任何附魔把附魔书当成 primary item** ⇒ 候选集为空 ⇒
列表为空 ⇒ 静默早退。

**修法**：在 `getPossibleEntries` 上再加一个 `isOf` 重定向（与 `method_17410` 里那个同形）。
"附魔书是书"这件事需要**两半**：`getPossibleEntries` 那一半让报价能生成出来，
`method_17410` 那一半让报价能写进去。之前只补了后一半，所以报价能显示、点击却毫无反应。

**这个 bug 之所以完全静默**：报价的 **power** 由 `calculateRequiredExperienceLevel` 算（那一处已修好），
所以按钮正常亮起、显示 `[14,34,64]` 这样合法的数字；玩家点击后什么都没发生，因为此时列表已经是空的。
日志里没有异常、没有堆栈 —— 与 §16 记的"≤1 帧的失败看起来和成功一样"是同一类。

**jar**：`D944DF4DAEABC5D4` / 491348 bytes，启动行 `build=enchanttable4`。




















