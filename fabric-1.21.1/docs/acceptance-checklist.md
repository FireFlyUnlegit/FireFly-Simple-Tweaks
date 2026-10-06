# 客户端验收清单（`simple_tweaks-2.0.0.jar`）

> 目标 jar：`fabric-1.21.1/build/libs/simple_tweaks-2.0.0.jar`
> SHA256 `09A1D99B9ACA7EF5D177FC3D5A3BB59B3BDDA1B9627202C4737566B682B30DDC`（541642 bytes）
> 启动标识：**`build=cleanup6`**
> 安装位置：`F:\.minecraft\versions\1.21.1-Fabric 0.19.3\mods\simple_tweaks-2.0.0.jar`
> 配置/日志：`<gameDir>\config\simple_tweaks.json`、`<gameDir>\logs\latest.log`
> 回退点：`simple_tweaks-2.0.0.jar.prev`（上一版 `build=cleanup5`）
>
> **前置**：单人世界 + 开作弊。
> ⚠️ **`[ST-*]` 调试日志默认关闭**（`build=cleanup3` 起，见 §M 与 `MIGRATION.md` §12）。本清单里凡
> "日志应出现 `[ST-...]`"的判据（B8、F3、K1.3、K2.1…），**都要先按 §M5 的方法打开开关**，
> 否则那些行根本不会出现 —— 这是预期，不是功能失效。
>
> ⚠️ `build=cleanup2` 只删掉了**开发期自测入口**：`/sttest` 指令、Carpet 假玩家依赖、`tools/acceptance.ps1`、
> `tools/rcon.ps1`（见 §M 与 `MIGRATION.md` §11）。**`/stconfig` 与 `/enchantinfo` 未受任何影响** ——
> 本清单各组照常按客户端做法测，E/F 等组本来就不依赖 `/sttest` 或假玩家。

---

## 0. 启动自检（先看这三条）

| # | 检查 | 期望 |
|---|---|---|
| 0.1 | 日志有 `client ready: damage indicator + /stconfig + /enchantinfo ... (build=cleanup2)` | 证明加载的是新 jar；**注意结尾的 build 标识**，不是 `cleanup2` 就是旧 jar |
| 0.2 | 日志 `Config loaded from ... (anvilLimit=..., damageIndicator=..., cancelVanillaDmgIndicator=..., noFov=false@90.0)` | 配置读取正常 |
| 0.3 | 全日志 `ERROR` / `Exception` = 0（`PDH Counter` 那两条是 Windows 噪音，忽略） | 无异常 |

---

## A. 附魔名颜色（批 2）

**一次性给全（可绕过附魔适用性）：**
```
/give @s minecraft:diamond_sword[enchantments={"simple_tweaks:soul_bound":1,"simple_tweaks:auto_smelt":1,"simple_tweaks:crit_damage":3,"simple_tweaks:celestial_blessing":1,"simple_tweaks:reforge":1,"simple_tweaks:infinite_power":1,"minecraft:sharpness":5}]
```

| # | 悬停看 | 期望 |
|---|---|---|
| A1 | `soul_bound`（LEGENDARY） | **金色** |
| A2 | `auto_smelt`（COMMON） | 灰色 |
| A3 | `crit_damage`（EPIC） | 淡紫 |
| A4 | `celestial_blessing`（MYSTERY） | 青色 |
| A5 | `reforge`（UNIQUE） | 白色 |
| A6 | **`infinite_power`** | **逐字符彩虹，约 3 秒流动循环** ← 唯一特例 |
| A7 | 原版 `sharpness` | **不受影响**，原版色 |
| A8 | `/stconfig` 第 1 页关「附魔名彩色」 | 全部回原版色（含彩虹失色） |

---

## B. 伤害飘字（批 3）

| # | 操作 | 期望 |
|---|---|---|
| B1 | 打任意生物 | 红色 `-N`，上浮淡出；第二行 `剩余/最大`；**绕圈都可见** |
| B2 | 一击超过剩余血量 | `-原始(实际)`，如 `-8.50(1)` |
| B3 | 治疗（金苹果 / 恢复效果） | **绿色 `+N`** |
| B4 | `/damage @s 5` | 自己的数字**更大**（1.3×）且**背对也不裁掉** |
| B5 | **背对**一只正在受伤的怪 | 飘字**不显示**（背面剔除） |
| B6 | 打生物时看原版粒子 | **原版红色伤害粒子消失** |
| B7 | **同一击看爆击** | **爆击星星仍在、附魔命中特效仍在** ← **窄版关键判据**；若这两个也消失，说明范围过宽，请立刻告知 |
| B8 | 日志 | 出现 `[ST-DamageIndicator] packet=received` 与 `seam=alive ... scaleSigns=+,-,+` |

**`/stconfig` 第 2 页，逐个切换应即时生效（不用重启）：**

| # | 开关 | 期望 |
|---|---|---|
| B9 | 伤害飘字 | 关 → 飘字完全消失 |
| B10 | 显示 +/- 符号 | 关 → 无 `+`/`-` |
| B11 | 按百分比显示 | 开 → `-30%` 形式 |
| B12 | 显示阴影 | 关 → 无阴影 |
| B13 | 存活帧数（默认 40，**单位是帧不是 tick**） | 改 120 → 停留更久 |
| B14 | 上升时长 / 可见距离 / 字号 / 起始高度系数 | 各自可感知（起始高度系数语义见 §M3） |

---

## C. 配置界面（阶段 7 回补）

| # | 操作 | 期望 |
|---|---|---|
| C1 | `/stconfig` | 界面出现 |
| C2 | 「下一页 ▶」循环翻 3 页 | 通用 / 伤害飘字 / 移动 |
| C3 | 任改一项后关界面，看 `config\simple_tweaks.json` | **改动已落盘**；且**不重启仍生效** |
| C4 | 第 1 页「铁砧最大花费」显示 | 满值时显示 **无限** |

---

## D. 附魔图鉴（阶段 7）

| # | 操作 | 期望 |
|---|---|---|
| D1 | `/enchantinfo`、`/ei`、`/enchinfo` | 三者**都能打开** |
| D2 | 分类版式 | **上行 5 个**：COMMON→UNCOMMON→RARE→EPIC→LEGENDARY<br>**下行 3 个**：UNIQUE / MYTHIC / MYSTERY，**两行各自居中** |
| D3 | **逐个**悬停并点击 8 个图标 | 每个都命中正确分类（重点是**下行那 3 个** —— 坐标只算一处，最易错位） |
| D4 | 悬停提示 | `分类名` / `N 个附魔` / `点击打开` |
| D5 | 点进某分类 | 9×5 **闪光**附魔书网格 |
| D6 | 悬停附魔书 | **名称（按分类上色）** / `分类:X` / `适用:剑` / `最大等级:N` / 描述（自动换行） |
| D7 | 点返回箭头 | 回到分类菜单 |
| D8 | 按 ESC | **先回分类菜单**，再按才关界面 |
| D9 | 鼠标滚轮 | 每类只有 ~7 个附魔、一页能放 45 个 ⇒ **页码不显示、翻不动是正常的** |

---

## E. 自动冲刺（批 4）

| # | 操作 | 期望 |
|---|---|---|
| E1 | `/stconfig` 第 3 页开「自动冲刺」，**关界面后**前进 | 持续冲刺（界面开着时会跳过，这是 1.12.2 的行为） |
| E2 | 蹲下前进 | **不冲刺** |
| E3 | 再开「全向冲刺」 | 蹲下 / 侧向也应冲刺 |
| E4 | 关掉「自动冲刺」 | **冲刺立刻停止**（下降沿行为，不是等下次输入变化） |

---

## F. 阶段 1–4 的积压项（服务端逻辑，可与上面一起测）

| # | 操作 | 期望 |
|---|---|---|
| F1 | 附 `auto_smelt` 的镐挖金矿 | 掉落直接变锭、获得熔炼经验、**耐久 -2**（挖 1 + 熔炼 1） |
| F2 | 精准采集的镐挖金矿 | **不熔炼**（反例，验证 F1 不是恒真） |
| F3 | `soul_bound` 剑 + 石头 → `/damage @s 1000 minecraft:generic_kill` | 重生后**剑回到背包**，石头掉在原地。**日志应有 `withheld-from-drops` + `restored-on-respawn` 成对出现** |
| F4 | 附 `damage_limiter` 的护甲，`/damage @s 20` | 被夹到 `(0.8-0.2×lvl)×20`；`/damage @s 61`（超过 maxLimit=60）**不夹、直接死** |
| F5 | Starfall（弓）命中 | 视觉：爆炸类粒子（不是白色烟雾） |
| F6 | EchoShield 受击 / 反弹 | 视觉与音效 |
| F7 | **粒子常量改名的视觉确认** | `CRIT_MAGIC→ENCHANTED_HIT`、`EXPLOSION_NORMAL→POOF`、`SPELL_WITCH→WITCH` —— 类型看起来对 |

> ⚠️ **F3 不要用 `/kill`**：`/kill` 对玩家走的是 `LivingEntity.kill()` → `damage(genericKill, MAX)`，虽也能触发，
> 但用 `/damage ... generic_kill` 更明确；且**创造模式玩家死亡不掉落**，测试前确认是生存模式。

---

## G. 回归检查（本轮大量内部改动，值得过一遍）

| # | 检查 | 期望 |
|---|---|---|
| G1 | 普通攻击 / 暴击 / 附魔台 / 铁砧 | 行为无异常 |
| G2 | `/stconfig` 与 `/enchantinfo` **交替打开**再关闭 | 不串屏、不残留（两者共用 `ClientScreenOpener`） |
| G3 | 打开界面时玩家是否暂停 | **不暂停**（与原版 GUI 一致） |

---

## H. 弓箭附魔（本轮新增：`multishot` / `tracking_arrow` / `piercing_arrow`）

```
/give @s minecraft:bow[enchantments={"simple_tweaks:multishot":3}]
/give @s minecraft:bow[enchantments={"simple_tweaks:tracking_arrow":3}]
/give @s minecraft:bow[enchantments={"simple_tweaks:piercing_arrow":3}]
/give @s minecraft:arrow 64
```

### H1 Multishot（`multishot:3` ⇒ 1+3 支箭）

| # | 期望 |
|---|---|
| H1.1 | 满蓄力射一箭 → **4 支箭呈 5° 扇形散开** |
| H1.2 | 全部命中同一目标 → **每支都造成伤害**（无敌帧被清零；否则只算 1 下） |
| H1.3 | 弹药消耗 **4 支**、弓耐久 **-1** |
| H1.4 | 快速点击（蓄力不足，`charge < 5`）→ **不多发**，走原版单发 |
| H1.5 | 带**无限**附魔 → 箭不消耗且**不可拾取**；创造模式 → 不消耗、不掉耐久 |
| H1.6 | **药箭** → 命中目标带**药水效果**（验证走的是原版箭矢工厂） |

### H2 TrackingArrow（`tracking_arrow:3`）

| # | 期望 |
|---|---|
| H2.1 | 朝一只**偏离准星**的怪射箭 → 箭会**明显转向追过去** |
| H2.2 | 每 2 tick 有 **END_ROD 粒子**尾迹 |
| H2.3 | 等级越高转向越快（3 级 ⇒ 60°/tick 上限） |
| H2.4 | 射向**空处** → 不转向（无目标），正常落地 |
| H2.5 | 箭落地（`inGround`）后 → **停止转向**（accessor 那条判据） |
| H2.6 | 不会追自己的**同队**玩家/宠物 |

### H3 PiercingArrow（`piercing_arrow:3`）

| # | 期望 |
|---|---|
| H3.1 | 一排 3 只怪 → 一箭**依次穿过并各造成伤害** |
| H3.2 | **同一只怪可以被同一支箭打中多次**（最多 3 次 = `maxPerTarget`）← 这是与**原版穿透**的关键差异，重点验 |
| H3.3 | 穿透次数用尽后 → 箭**正常停在最后那个目标身上** |
| H3.4 | 射向空处 → 正常插地（`onBlockHit` 未受影响） |
| H3.5 | 火箭（穿过火焰）→ 命中目标**着火 5 秒** |
| H3.6 | 爆击箭 → 伤害有随机加成（`isCritical` 分支） |

### H4 组合与回归

| # | 期望 |
|---|---|
| H4.1 | `multishot` + `piercing_arrow` 同时装 → 齐射 + 穿透互不干扰 |
| H4.2 | `tracking_arrow` + `piercing_arrow` → 追踪时**优先选未命中过的目标** |
| H4.3 | 普通弓（无这些附魔）→ 与原版**完全一致**（这是最重要的反例） |

---

## I. CelestialBlessing（本轮新增，全套基础设施）

```
/give @s minecraft:netherite_sword[enchantments={"simple_tweaks:celestial_blessing":1}]
/give @s minecraft:stone_sword[enchantments={"simple_tweaks:celestial_blessing":1}]
```

**法力池从 0 开始** —— 必须先用这把剑打怪才开始充能。剑的 tooltip 会实时显示法力值。

### I1 充能

| # | 期望 |
|---|---|
| I1.1 | 拿**空池**剑打怪 → 造成伤害，tooltip 法力值**上升** |
| I1.2 | 打怪造成的伤害越高 → 充能越多 |
| I1.3 | 打死怪物 → 也有充能（不因击杀流程丢失） |
| I1.4 | 打**自己/宠物/队友** → 不充能（友好目标被排除） |
| I1.5 | 用**弓/弓箭**（不持剑）→ 不充能（只有持该剑的攻击才算） |
| I1.6 | 多个玩家各持一把 → 法力池**互不串**（缓存按玩家 UUID 键控） |

### I2 自动消耗（法力够时**自动**触发，无按键）

| # | 期望 |
|---|---|
| I2.1 | 血量不满 + 有法力 → 自动回血，法力下降 |
| I2.2 | 饥饿值不满 → 自动回复饱食度 |
| I2.3 | 手持的**该剑耐久不满** → 自动修复，法力下降 |
| I2.4 | 法力不足 → **什么都不做**（不半途消耗） |
| I2.5 | 法力为 0 时 → 完全无副作用 |

### I3 光环粒子（`PacketCelestialRing`）

| # | 期望 |
|---|---|
| I3.1 | 法力 > 0 时 → 玩家周围出现**环绕的粒子环** |
| I3.2 | 每 4 tick 更新一次位置 |
| I3.3 | 第二个玩家在 64 格外 → **看不到**该环（范围包，不是全服广播） |
| I3.4 | 走进 64 格内 → 环出现 |
| I3.5 | 法力归 0 → 环**消失** |

### I4 自动攻击链（`targetQueue` + 弹射物）

先用剑打怪进入战斗，然后**站着不动**观察：

| # | 期望 |
|---|---|
| I4.1 | 会自动向**附近的敌对怪**发射弹射物 |
| I4.2 | 命中后弹射物**转向链上的下一个目标**（`findChainTarget`） |
| I4.3 | 有**冷却**，不是每 tick 狂发 |
| I4.4 | 附近只有动物/村民/队友 → **不发射**（`Monster` 判据） |
| I4.5 | 弹射物造成的伤害**无视护甲/护盾**（`bypasses_armor` + `bypasses_shield`） |
| I4.6 | 弹射物**不伤害自己**和队友 |
| I4.7 | 打**女巫** → 伤害被削减（`witch_resistant_to`，这个 tag 生效即说明伤害类型注册对了） |
| I4.8 | 打**守卫者** → **不反弹荆棘**（`avoids_guardian_thorns`） |

### I5 命中目标的减益（`celestial_shred`）

| # | 期望 |
|---|---|
| I5.1 | 被命中的怪 → **最大生命值降低**（看血量条变短） |
| I5.2 | 效果**约 60 秒**后失效，最大生命值恢复 |
| I5.3 | 重复命中**刷新**时长而不是无限叠加 |

### I6 自身增益（`celestial_knockback` / `celestial_attack_speed`）

| # | 期望 |
|---|---|
| I6.1 | 法力 > 0 时 → **抗击退**明显变强 |
| I6.2 | 法力 > 0 时 → **攻击速度**变快（连点手感差异明显） |
| I6.3 | 法力归 0 → 两个增益**立即移除**（这是 `ManaPoolSync` 的另一半作用） |

### I7 同步与登出（`PacketManaPoolSync`）

| # | 期望 |
|---|---|
| I7.1 | 服务端扣法力 → 客户端 tooltip **立刻**反映新值（不是延迟几秒） |
| I7.2 | 退出到主菜单再进来 → tooltip **不显示旧的法力残留**（登出清缓存） |
| I7.3 | 死亡后 → 法力归 0 |

### I8 服务端兼容

| # | 期望 |
|---|---|
| I8.1 | **专用服务端启动无崩溃**，日志无 `NoClassDefFoundError: …client…`（这条是本轮改动最大的风险点：`onClientDisconnect` 被有意移出 common 类） |

---

## J. 附魔台覆盖语义 + 天赐无视抗性（本轮两项修改）

> **先确认启动日志里是 `build=enchantfix`。** 如果还是 `build=celestial`，说明加载的是旧 jar，
> J 组的结果全部无效（不要报 bug，先换 jar）。

### J1 附魔台：重复附魔**覆盖**而非叠加 —— ✅ **实测通过**

```
/give @s minecraft:netherite_sword[enchantments={"minecraft:sharpness":3,"minecraft:mending":1}]
```
放进附魔台，附一次魔。

| # | 期望 |
|---|---|
| J1.1 | 附完后 → **`sharpness:3` 与 `mending:1` 消失**，只剩新生成的那一套（这就是"覆盖"） |
| J1.2 | 再附一次 → 仍然只有一套，**不累积**（这是本次要修的 bug 本身） |
| J1.3 | 日志出现 `[enchant-table] re-enchant on minecraft:netherite_sword: replaced 2 existing enchantment(s)` |
| J1.4 | 拿一把**未附魔**武器附魔 → 正常出附魔，且**没有**上面那行日志（无旧附魔可替换） |
| J1.5 | 附魔**不会消耗掉物品**、不会变成空手（前几次尝试的失败模式是物品被剥光/消失，重点回归） |
| J1.6 | 覆盖之后**立刻再附一次仍然可以附魔**（修复前：清空后组件消失，三个报价全变 0，再也附不了） |
| J1.7 | 日志出现 `[enchant-table] re-enchant on …` 且数量等于覆盖前的附魔数 |

> J1.1 的"原有附魔消失"是**预期**，不是 bug：1.12.2 就是「清空旧附魔 → 写入本次生成的列表」。
> 但"消失后什么也没有、还附不了"是 bug，J1.6 就是针对它。

### J2 天赐伤害无视抗性提升药水 —— ✅ **实测通过**

先给自己或测试目标上抗性提升，再用天赐（`celestial_blessing`）的自动弹射物打它。

| # | 期望 |
|---|---|
| J2.1 | 目标有**抗性提升**时 → 天赐弹射物伤害**仍按原值扣除**（不被 ×0.2 之类削减） |
| J2.2 | 对照：`/damage <target> 10 minecraft:magic` → **仍然被抗性削减**（证明改的是自定义类型，不是全局关掉了抗性效果） |
| J2.3 | 目标穿**保护 IV** 装备 → 天赐伤害**仍被保护削减**（这条是本次**故意没做**的：没用 `bypasses_effects`） |
| J2.4 | 女巫减伤（`witch_resistant_to`）与守卫者不荆棘（`avoids_guardian_thorns`）行为**不变** |
| J2.5 | 天赐伤害仍然**无视护甲与护盾**（原有行为，回归） |

---

## K. Starfall 地板修复 + Multishot 无敌帧 —— ✅ **两项均实测通过**

> 修复与机制见 `phase6-client-notes.md` §25。2026-10-06 实测日志的判据：
> - **K1**：`outcome=star-impact` 里 `life=1` 从 **90/90 降到 0**；`starY` 从全负变为 **14.3–69.3**；
>   并出现了 `life=10/13`（**追踪飞行后命中**，修复前从未出现）与 `life=103`（目标已死、撞 100 tick 上限）。
> - **K2**：76 次箭矢命中中 **75 次 `flagged=true`**（唯一的 `false` 是普通箭，正好是负对照）；
>   第 2 支箭起 `regenBefore=20 → regenAfter=0`，且每支箭伤害各不相同（13/10/10/11）⇒ **四支箭各自结算**。
>   当时的临时逐箭探针已删除，只保留每轮一次的 `outcome=volley-spawned`。
>
> 下面两张表保留作为**回归清单**。

### K1 Starfall（**必须在 y ≤ 0 的地方测**，这是本轮修的那个 bug）

```
/give @s minecraft:bow[enchantments={"simple_tweaks:starfall":5}]
```

站到 **y ≤ 0** 的位置（洞穴 / 深板岩层都行），射一只怪。

| # | 期望 |
|---|---|
| K1.1 | 星**从目标上方落下并追向目标**，不是生成瞬间就炸 |
| K1.2 | 落点命中目标 → **掉血** |
| K1.3 | 日志 `outcome=star-impact` 的 `life=` **明显大于 1**（修复前 90/90 都是 1） |
| K1.4 | 同一位置、**地面上（y>0）** 再射一次 → 行为与修复前一致（不该回归） |
| K1.5 | lvl≥5 → 3 颗星；lvl 3-4 → 2 颗；lvl<3 → 1 颗 |
| K1.6 | 不会打到自己和自己的宠物 |

### K2 Multishot 无敌帧（需要日志，见 `phase6-client-notes.md` §25.2）

近距离对一只怪射一箭（`multishot:3` ⇒ 4 支箭）。

| # | 期望 |
|---|---|
| K2.1 | 把 `logs/latest.log` 里所有 `[ST-Multishot]` 行发我（这轮就是要这个数据） |
| K2.2 | 若出现 `outcome=hurt-hook-ran` 且 `flagged=false` → 问题在箭上的标记位 |
| K2.3 | 若**完全没有** `outcome=hurt-hook-ran` → 问题在事件钩子没被调用 |
| K2.4 | 若出现 `outcome=iframe-cleared` 且 `regenAfter=0` 但仍只掉一次血 → 问题在别处，需要新的观测量 |

> **编号说明**：原来的 **J3**（"附魔书附魔力放行"）已删除 —— 它当时被标为"实测仍失败"，
> 与后来真正解决该问题的 **J6** 矛盾。J 组编号保留空缺，不再重排，以免破坏别处的引用。

### J4 最低附魔条数保底（本轮新增）

| # | 期望 |
|---|---|
| J4.1 | 用约 **15–29 级**的报价附魔 → 结果**至少有 1 条附魔**（`level / 15 = 1`） |
| J4.2 | 用约 **30 级**的报价 → **至少有 2 条**（`level / 15 = 2`） |
| J4.3 | 报价 **< 15 级** → 不受影响（保底数为 0，走原版） |
| J4.4 | 后补的附魔**不重复**、且与已有附魔**不冲突**（不会出现锋利+节肢杀手这种互斥组合） |
| J4.5 | 保底只在"生成条数不足"时补；本来就有足够多条时**不额外增加** |

> 注意：原版对**普通书**有一条「多于 1 条时随机去掉 1 条」的规则
> （`EnchantmentScreenHandler.generateEnchantments` offsets 73..117），1.12.2 也如此。
> 所以**书**的最终条数可能比保底数少 1 —— 那是原版行为，不是保底失效。J4 用装备测。

### J5 附魔台等级上限 —— ✅ **实测通过**（`disableEnchantmentTableLimit` / `maxEnchantmentPower`）

> 这两个配置项**之前是死的**（写得进配置文件，但没有任何代码读它）。现在生效。
> 注意 `maxEnchantmentPower` 默认就是 **15**（= 原版），所以**必须调大**才看得出区别。

用 `/stconfig` 改（配置界面直接写 `GeneralConfig`，不需要重启），改完**把物品从附魔台槽位拿出来再放回去**
（报价在 `onContentChanged` 里算，不重算就还是旧值）。

| # | 期望 |
|---|---|
| J5.1 | 附魔台周围摆 **超过 15 个**书架（例如 30 个）→ 记下三个报价数字 |
| J5.2 | `maxEnchantmentPower` 从 15 改成 **30** → 重算后报价数字**变大** |
| J5.3 | `disableEnchantmentTableLimit` 关掉 → 回到原版（等同 15），即使 `maxEnchantmentPower` 还是 30 |
| J5.4 | 书架 **≤15 个**时改这个值**没有区别**（上限根本没触发）—— 这是预期的，不是 bug |
| J5.5 | 书架的**上限之外**再多摆书架也不涨（只有这个配置能抬高上限） |

### J6 附魔书 —— ✅ **实测通过**

真实原因（`build=enchanttable4` 修复）：`getPossibleEntries` 里 `isBook = stack.isOf(Items.BOOK)`
对附魔书为 **false**，导致候选集为空、`method_17410` 在 `if (list.isEmpty())` 处静默早退。
详见 `phase6-client-notes.md` §26.7。

**实测日志（2026-10-06）**：`book in slot 0: type=stored_enchantments, present=true, oldCount=3`
与 `re-enchant on minecraft:enchanted_book: replaced 3 existing enchantment(s)` —— 组件类型正确、
写入成功、3 条旧附魔被覆盖。（这两行诊断日志已随收尾删除。）

下表保留作为**回归清单**。

```
/give @s minecraft:enchanted_book[stored_enchantments={"minecraft:sharpness":2}]
```

| # | 期望 |
|---|---|
| J6.1 | 放进去 → 报价正常亮起（`[14,34,64]` 这类） |
| J6.2 | 点击 → **这次有反应**：书上的附魔被**覆盖**成新的一套 |
| J6.3 | 日志出现 `book in slot 0: type=stored_enchantments, present=true, oldCount=N` —— 上一轮它一行都没有 |
| J6.4 | 附魔写在 **`stored_enchantments`** 里，不是 `enchantments` |
| J6.5 | 普通书（`minecraft:book`）附魔仍然正常，不回归 |

---

## L. InfinitePower（本轮新增：核心 + 光环，`build=infpower1`）

```
/give @s minecraft:netherite_sword[enchantments={"simple_tweaks:infinite_power":1}]
```

> **先确认启动日志是 `build=infpower1`。**
> 核心实现**不走伤害管线**：直接置血 + `onDeath`，所以护甲/抗性/保护附魔/生物减伤全都不参与。
> 这正是"监守者打不死"那个原始问题的解法 —— 若你看到监守者被打死时掉血数字很小，那是原版在扣护甲显示，不是失败。

### L1 秒杀（最初报告的问题）

| # | 期望 |
|---|---|
| L1.1 | 一刀砍死**监守者** |
| L1.2 | 一刀砍死普通怪，**掉落正常**（2 格内的掉落物会被自动收进背包） |
| L1.3 | 落点 2×3×2 范围内的**其它怪一起被秒**（AOE） |
| L1.4 | ✅ **实测通过**（`build=infpower2`）：末影龙一刀秒杀、DYING 阶段、出口传送门正常。`build=infpower1` 时此項**失败**：龙一行 `[ST-InfinitePower]` 日志都没有 —— 击杀钩子在原版解包 `EnderDragonPart` 之前，而部位实体不是 `LivingEntity`（1.12.2 有同一个 bug）。详见 `infinite-power-deferred.md` §8 |
| L1.5 | 死亡时 5 圈粒子 + 爆炸音效 |
| L1.6 | **攻击冷却照常**（本移植**故意不取消攻击**）：连砍的节奏与普通剑一致，不是无限连击 |
| L1.7 | 砍完一刀再砍别的怪 → **暴击判定正常**，不会"永远暴击"（这是 1.12.2 取消攻击会引入的缺陷） |

### L2 自身不死

| # | 期望 |
|---|---|
| L2.1 | 手持该剑受到伤害 → 无伤，且血量被补满 |
| L2.2 | 手持该剑**不会死亡**（致死伤害被取消）⚠️ 这一条风险最高：1.21 没有公开的"死亡标志位"可写，只能靠取消事件。若仍然死亡，请告诉我，需要加一个 accessor mixin |

### L3 每 tick 光环

| # | 期望 |
|---|---|
| L3.1 | 允许飞行；**换成别的物品后飞行收回** |
| L3.2 | 夜视（**环境型** —— 不应与信标/药水的夜视冲突） |
| L3.3 | 饱食度与饱和度满 |
| L3.4 | 手持物**耐久不掉**且被修复为满 |
| L3.5 | 攻速大幅提升；换下剑后加成**移除** |

### L4 万能工具

| # | 期望 |
|---|---|
| L4.1 | 任何方块都判定为"可采集"（含通常需要正确工具的方块） |
| L4.2 | 挖掘**瞬间完成** |

### L5 灵魂绑定

| # | 期望 |
|---|---|
| L5.1 | 死亡时带该附魔的物品**不掉落**，复活后仍在背包里 |

### L6 InfinitePower 的取舍记录

| 项 | 状态 |
|---|---|
| **激光** | ✅ **已实现并实测通过**（见 L7） |
| **末影龙** | ✅ **已修复并实测通过**（见 L1.4） |
| **掉落翻倍**（原 `DropHandler`） | ❌ 作者要求舍弃，源码已删 |
| **背包**（GUI / 右键开启 / 存档） | ❌ 8 次尝试后舍弃（见 L8），源码保留在 `src/**/disabled/` |

### L7 激光（`build=laser1`）—— ✅ **实测通过**

手持带 `infinite_power` 的物品，**对着空处**左键（不要对着方块或怪）。

| # | 期望 |
|---|---|
| L7.1 | 沿准星方向出现一条粒子光柱（彩色 `EFFECT` + `END_ROD` + `FIREWORK` + `ENCHANT`） |
| L7.2 | 光柱**打穿路径上的一切生物**，全部秒杀（含末影龙 —— 见 L1.4） |
| L7.3 | 光柱被方块挡住（射线打到方块就停） |
| L7.4 | 命中生物处额外爆一圈粒子 + 爆炸音效 |
| L7.5 | **对着方块或怪左键不会触发**（这是 `LeftClickEmpty` 的语义：只有打空才触发） |
| L7.6 | 光柱最远 128 格 |

### L8 背包 —— ❌ **已舍弃（`build=infpower3`）**

8 次尝试后仍无法让客户端与服务端内容稳定一致，按作者指示**整体舍弃**。
源码移到 `disabled/` 保留，全部设计与排查结论保留在 `infinite-power-deferred.md` §10–§17。

**现在不再有背包功能**：潜行右键带 `infinite_power` 的物品**不会**打开任何界面（这是预期）。
原先的 L8.1–L8.11 验收项**全部作废**，其内容已随收尾删除 —— 不要当 bug 报。

**同时移除的两处全局改动**（净收益）：`SlotPositionMixin`（剥掉 `Slot.x`/`y` 的 final）与
`ScreenHandlerClickProbeMixin`（`ScreenHandler#internalOnSlotClick` 的 HEAD 探针）。

---

## M. 收尾改动验证（`build=cleanup3`）—— 本轮只验这 7 条

> 本轮删了测试指令 + Carpet 附属，把 `yStartFactor` 的乘数从「世界高度」改成「实体高度」，
> 并把 `[ST-*]` 调试日志改成**默认关闭**（含 `[enchant-table] re-enchant`）。其余功能**代码未动**，
> 无需重测前面各组。

| # | 操作 | 期望 |
|---|---|---|
| M1 | 启动看日志 | `client ready: ... (build=cleanup5)` ← **每次改动都会推进这个标识**，认准页首写的那一个 |
| M2 | 输入 `/sttest` | **指令不存在**（原版"未知指令"红字）—— **这是预期**，测试指令已删除 |
| M3 | `/stconfig`、`/enchantinfo` | 都照常打开、照常可用（确认删掉的只是测试指令，正式功能未受影响） |
| M4 | **不加任何启动参数**，正常打怪 / 附魔玩几分钟，再 `Select-String '\[ST-\|enchant-table' logs\latest.log` | **一行都没有** ← 本轮的默认行为 |
| M5 | 按下面加 `-Dsimpletweaks.debug=true` 重进，再打怪 / 附魔 | `[ST-...]` 日志**重新出现**（开关有效） |
| M6 | 把 `config\simple_tweaks.json` 里 `yStartFactor` 改成 **2.0** 并重启，打一只怪 | 飘字**仍然显示**，位置明显高于实体；**改前这个值会让飘字彻底消失** |
| M7 | 改回 **1.0** 并重启，再打一只怪 | 飘字紧贴实体头顶 —— 与 1.12.2 默认位置一致（默认值行为**未变**） |

> M6/M7 的判据是**同一次构建内改配置即可对比**：2.0 比 1.0 高，而不是 2.0 消失。
> 若 M6 仍然完全不显示飘字，请把 `config\simple_tweaks.json` 发我 —— 那就说明问题不在公式。

### M5 的启动参数加在哪

| 你从哪启动 | 怎么加 |
|---|---|
| 正式客户端（官方启动器 / HMCL） | 该版本 profile 的 **JVM 参数**里加 `-Dsimpletweaks.debug=true` |
| IDEA `runClient` | Run Configuration → **VM options** 填 `-Dsimpletweaks.debug=true` |
| 命令行 `gradlew runClient` | 先 `$env:SIMPLETWEAKS_DEBUG='true'` 再执行（等价的**环境变量**写法，不用改 `build.gradle`） |

> 开关是**启动时读一次**的，改完必须重启客户端才生效（运行时改没用）。

---

## N. 铁砧等级上限（`build=cleanup4` 新修复）—— **本轮重点**

> 这个选项之前**只有配置项、没有代码**（`disableAnvilCostLimit` / `maxAnvilCost` 谁都没读），
> 所以"改了没用"是必然的，不是你的操作问题。现在补了服务端闸门 + 客户端红字两个 mixin。
>
> ⚠️ **必须在生存模式测**，并且先把等级刷够（`/xp add @s 100 levels`）。原版这个闸门里本来就带
> `!creative` —— **创造模式原版也不拦**，所以在创造模式下改前改后完全一样，看不出任何区别。
> 另外注意：只取消**上限**，不取消**价格**，代价 60 就真的要 60 级。

| # | 操作 | 期望 |
|---|---|---|
| N1 | 把铁砧代价推到 **40 以上**（给剑打一堆附魔后用同种剑合并，或反复合并同件物品） | 产物**不再显示红色「过于昂贵！」**，而是正常显示代价 `Cost: N`（N ≥ 40） |
| N2 | 等级 ≥ N 时点产物 | **能取出**，等级正常扣除 |
| N3 | 等级 < N 时点产物 | 仍然**取不出**（红字）—— 这是**对的**：只取消上限，不取消价格 |
| N4 | `/stconfig` 关掉「移除铁砧『过于昂贵』」 | 立刻恢复原版：≥ 40 就红字「过于昂贵」 |
| N5 | 「铁砧最大花费」改成 **100**（开关保持开启），把物品拿开再放回去重算 | 代价 40–99 正常可取出；**≥ 100 才**变红「过于昂贵」 |
| N6 | 用**一叠材料**（如 2 个铁锭修护甲）在铁砧上修 | 代价**仍是原版那个小数字**，不会变成天文数字 |

> ⚠️ **N6 是本次最容易踩的坑，也是最重要的一条。** `AnvilScreenHandler.updateResult()` 里有**三处**
> `40`：第一处是"材料堆叠 > 1 时的花费 40"，第二处是创造模式显示钳位，**第三处才是上限闸门**。
> 只改了第三处（`@Constant(intValue = 40, ordinal = 2)`）；如果 N6 变成"修复要 40 级"或"代价 21 亿"，
> 说明 ordinal 选错了，**请立刻告知**，不要继续测别的。

---

## O. FastBow（KSP 试点，`build=cleanup6`）

> 这是**第一个用 `@ModEnchantment` 声明的附魔**：数据包 JSON、注册表键、图鉴元数据、handler 注册
> 全部由 KSP 在编译期生成（见 `MIGRATION.md` §15）。本组同时验证"KSP 生成的 JSON 能不能被游戏吃下"
> —— 因为这份 `fast_bow.json` 已经不是手写的了。
>
> **`cleanup5` 上报的两个问题都已修**：颜色灰（= COMMON 的颜色）见 O1.1，拉弓看不出快见 O2。

### O1 存在性、图鉴与**颜色**

```
/give @s minecraft:bow[enchantments={"simple_tweaks:fast_bow":3}]
/give @s minecraft:arrow 64
```

| # | 操作 | 期望 |
|---|---|---|
| O1.1 | 用上面命令拿到弓，悬停看提示 | 附魔名**蓝色**「快速拉弓」，且有描述行 ← **`cleanup5` 时是灰色，本轮已修** |
| O1.2 | 打开 `/enchantinfo` → 点 **RARE** 分类 | 列表里**有 Fast Bow** ← KSP 生成的图鉴元数据（旧 bug 就是它不在 `ALL` 里所以看不见） |
| O1.3 | 悬停它 | 分类 RARE / 适用**弓** / **最大等级 3** / 名字蓝色 / 描述正常 |
| O1.4 | 用附魔台或铁砧给弓刷附魔 | **能刷出 `fast_bow`**（说明生成的 datapack JSON 被正常加载） |
| O1.5 | 逐个看 56 个老附魔的名字颜色 | **全部照旧**（本轮把颜色查询改成走门面 + 回落旧表，理论上零变化，但值得抽查） |

### O2 拉弓变快 —— **这次看动画，不是看伤害**

> `cleanup5` 只改了"松手时的威力"，**拉弓动画仍是原版 20 tick**（客户端渲染读的是另一个方法），
> 所以当时"看不出哪里 fast"。现在客户端的拉弓进度也被加速，**手上的弓会明显更早到达满蓄力姿态**。

| # | 操作 | 期望 |
|---|---|---|
| O2.1 | **无附魔**弓：按住右键，盯着看它何时"拉满"（箭完全后拉 + 音效到位） | 约 **20 tick（1 秒）** ← 基准线 |
| O2.2 | `fast_bow:3` 的弓：同样按住盯着看 | 约 **12 tick（0.6 秒）** 就到同样的满蓄力姿态，**肉眼可辨** |
| O2.3 | `fast_bow:1` 的弓 | 约 **16 tick** —— 三级之间有梯度 |
| O2.4 | `fast_bow:3` **拉满之后**再放，与无附魔弓拉满后放，对比箭的落点/伤害 | **完全一致** ← 加速只补足蓄力，**不能超过满蓄力** |
| O2.5 | `fast_bow:3` 在约 0.6 秒（动画已显示拉满）时松手 | 射出的箭**满威满速** —— 动画与威力**一致**，不该"看着满了却射得软" |
| O2.6 | 射完**立刻**换一把无附魔弓射 | 那把弓**不受影响** |

> **O2.4 与 O2.5 是两条关键判据。**
> O2.4 证明没有突破上限；O2.5 证明客户端动画与服务端威力**用的是同一个倍率** ——
> 单机下客户端与内置服务端共享同一份类，如果两侧都乘一次会变成 `1.75²`，
> 那时 O2.5 会表现为"动画还没满、威力却已经满"，O2.2 的动画也会和实际脱节。

### O3 回归（本轮改的是多个弓附魔共用的注入点）

| # | 期望 |
|---|---|
| O3.1 | 普通弓手感与原版**完全一致** |
| O3.2 | `multishot` / `tracking_arrow` / `piercing_arrow` / `starfall` **全部照旧** ← 风险最高处，四个都过一遍 |
| O3.3 | 快速点击（蓄力不足 `charge < 5`）→ 与之前一样不多发、不异常 |
| O3.4 | **其它带使用进度的物品**：吃食物、喝药水、举盾、拉弩 → 时长与动画**都没变**（本轮动了 `getItemUseTimeLeft`，这是共用 getter） |

---

## 已知未做（**不要当 bug 报**）

| 项 | 原因 |
|---|---|
| **InfinitePower 背包**（GUI / 右键开启 / 存档） | **已舍弃**；源码保留在 `src/**/disabled/`，结论见 `infinite-power-deferred.md` §17 |
| **Velocity** | 作者裁定不移植 |
| `/attribute` | 1.21 原版自带且更强，不移植 |
| `/enchant ... 0` 移除附魔 | 作者裁定不移植（1.21 参数在**解析阶段**就拒绝 0，需两个 mixin，性价比不足） |
| **铁砧祛魔**（`anvilDisenchant`） | **作者裁定不需要**（`build=cleanup4` 核实：从未移植）。配置键/界面/lang 仍在但**无任何实现**，属惰性键（同 `noFovEnabled` 的处理），**不要当 bug 报** |
| **附魔成本倍率**（稀有度越高越贵） | **作者裁定不需要**（同上，从未移植）。1.12.2 靠 Forge 的 `AnvilUpdateEvent`，Fabric 无对应事件 |
| `SlowDownEvent` 的第三方注册门面 | **事件本身已移植且行为正确**，但只挂在本模组总线上；要对外需再加一个公开门面 |
| 原版箱子贴图式的界面底板 | 图鉴面板用纯色填充而非 `generic_54.png` 贴图 |
| 图鉴里的附魔书是"假"的 | 只用 `ENCHANTMENT_GLINT_OVERRIDE` 加闪光，**没有真的 `STORED_ENCHANTMENTS` 组件** |
| `infinite_power` 在图鉴里的名字 | 显示分类色而非流动彩虹（图鉴不读注册表） |
| `noFovEnabled` / `noFovValue` 配置键 | NoFov 实现后又删掉了（见 `MIGRATION.md`），两个键保留但**惰性** |
| `cancelVanillaDamageIndicator` 范围窄 | 只取消 `DAMAGE_INDICATOR`，不含暴击/附魔命中粒子 |

**已完成的项**（保留在此以防误报）：4 个箭矢处理器 ✅（H 组）、CelestialBlessing ✅（I 组）、
ManaPool tooltip ✅（I1/I7）、InfinitePower 核心与激光 ✅（L 组）、Starfall 与 Multishot ✅（K 组）。
