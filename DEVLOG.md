# DEVLOG — FireFly's Simple Tweaks（1.21.1 / Fabric）

> **这份文件是"每一批改了什么、为什么"的流水**，回答「这一版和上一版差在哪」。
>
> 与另外三份的分工：
>
> | 文件 | 回答 |
> |---|---|
> | `MIGRATION.md` | **1.12.2 → 1.21.1 迁移**期间的阶段流水与决策记录（append-only，历史，不再改写） |
> | `DEV_GUIDE_1.21.1.md` | **动手怎么做**：结构 / 构建 / 加附魔（KSP）/ 写 Mixin / 四条铁律 / 坑 / 命令注册（§10） |
> | `docs/acceptance-checklist.md` | **逐条验收项**。本文件每条改动都指向那里的编号（O/P/Q/R 组） |
>
> 写法：每条按「**现象 → 证据 → 改法**」写，并给出验收编号。
> **没有动态证据的一律标「未验收」——不得写成「已实现」。**

---

## `build=cleanup21`（2026-10-11 · **未安装；本批含作者的 `[FST-*]` 重命名与 `golden_shield` 新增**）

### 提交摘要（可直接用作 commit message）

```
fabric-1.21.1: 修复 golden_shield（吸收护盾在 1.21.1 是空操作）+ 新增 4 个附魔 + 注解按分类派生

修复
- golden_shield：吸收值写入被 generic.max_absorption（默认 0）钳掉 → 该附魔从未生效过一帧，
  而 contribution 记的是"请求值"不是"落地值"，几十秒后还自认已满。改为自带临时 max_absorption
  修饰符 + 记落地差值 + 补 [FST-GoldenShield] 探针；卸下护甲时连修饰符一起撤

新增（作者）
- frostbite 冰霜（RARE）：叠层减速，5 层冻结 2 秒，冻结期间受伤 +20%×等级
- venom 毒液（RARE）：叠层真实伤害（无视护甲/抗性/保护），宿主死亡传染最近的敌人
- chain_lightning 连锁闪电（RARE）：充能 → 再命中触发，范围 3+等级、目标 1+等级/2、伤害 0.3+0.1×等级
- 新伤害类型 simple_tweaks:venom / simple_tweaks:chain_lightning（+ venom 进 3 个 bypass tag）
- （同批曾新增 damage_lottery 伤害大乐透，**作者随后以"有点超模"为由删除** —— 见 §2.9）

注解与 KSP
- weight / damagePerLevel 改为按 category 派生（1.12.2 的 10-rarity*2 与 0.2+rarity/20，仅 SWORD），
  默认值是哨兵；处理器新增"显式值 != tier 派生值"的漂移报告（warn）
- EnchantItemWeights 恢复 10/8/6/4/2/1（cleanup20 曾静默改成 20/15/10/6/3/1）
- 17 处 supportedItems 字面量 → EnchantSupportedItemTags.*；15 处 weight 字面量 → EnchantItemWeights.*；
  16 处冗余 damagePerLevel 删除；11 处有意覆写保留并列入报告
- reforge / infinite_power / celestial_blessing / heavenly_punishment 翻成生成式 JSON（删手写件）；
  只有 unbreakable 继续 jsonEmit=false（item_damage 不可表达）
- 修掉 heavenly_punishment 的 damagePerLevel 2.5→5.0、anvilCost 90→14（此前注解是惰性的）、
  以及两个 MYSTERY 的 weight = 0（构建失败）

文档
- DEV_GUIDE §6 新增"吸收护盾"坑；AGENTS §4.9 新增对应硬约束；本批另加 §4.10（注解派生与惰性字段）

新增（配置项）
- noHurtCam（stconfig 第 1 页，默认关）：受伤不抖动画面。**新选项、非移植**（1.12.2 没有）；
  接缝 GameRenderer#tiltViewWhenHurt（唯一按 hurtTime 旋转视角处），client mixin + refmap 已验证

工具
- runPlayerAttack：删掉两个在 1.21 无意义的参数（lastHitKeepDamagePlayerSource / ignoreArmorAndPotion），
  复现 triggerEvent = false（EventSeams 的抑制作用域，ThreadLocal 深度计数），新增 simulateCooldown
  （vanilla 的 `0.2 + charge²×0.8` + resetLastAttackedTicks）；6 个调用点语义未变
- kill_aura 接上 simulateCooldown：只在挥砍就绪（charge = 1.0）时攻击，命中即花掉充能

移除（作者）
- damage_lottery（伤害大乐透）：作者裁定"有点超模"，连同 4 个 lang 键一并删除，**不恢复**
```

规模：**66 个附魔**（KSP 生成 65 + 手写 1 = `unbreakable`）· handler 注册 **70**（4 手写 + 66
`@ModEnchantment`）· mixin 注册 **26**（22 common + 4 client）· 每语言 lang **196** 键 ·
产物 `simple_tweaks-2.1.0.jar`（634803 bytes / SHA256 `C6099475…D6F483`）。

---

### 1. 🔴 `golden_shield` 从未生效：吸收护盾在 1.21.1 需要先抬 `generic.max_absorption`

**现象**：代码审查（作者要求）时按字节码核，发现这个附魔**不可能有任何效果** —— 不是"偶尔不生效"，
是每一次写入都被丢掉。

**证据**（全部来自 `minecraft-merged-*.jar` 的 `javap`，非推断）：

| # | 事实 | 字节码 |
|---|---|---|
| 1 | `setAbsorptionAmount(float)` 是 `public final`，**第一步就是钳位** | `fload_1; fconst_0; invokevirtual getMaxAbsorption; invokestatic MathHelper.clamp(FFF)F; invokevirtual setAbsorptionAmountUnclamped(F)V` |
| 2 | `getMaxAbsorption()` = 属性 `generic.max_absorption`，**默认 0** | `ClampedEntityAttribute("attribute.name.generic.max_absorption", 0.0, 0.0, 2048.0)`；`LivingEntity.createLivingAttributes()` 把它按默认值加进去；`PlayerEntity` 全程不碰它 |
| 3 | 唯一抬高它的原版机制是 `absorption` 状态效果 | `StatusEffects.ABSORPTION` 构造时 `addAttributeModifier(GENERIC_MAX_ABSORPTION, "effect.absorption", 4.0, ADD_VALUE)`；`AbsorptionStatusEffect#onApplied` 再 `setAbsorptionAmount(max(当前, 4×(amp+1)))` |
| 4 | 玩家的吸收值**是**同步的（所以问题不在"客户端看不见"） | `PlayerEntity.ABSORPTION_AMOUNT` 是 `TrackedData<Float>`，`PlayerEntity` 重写了 `getAbsorptionAmount` / `setAbsorptionAmountUnclamped` 走 tracker |
| 5 | 吸收值会被存档 | `LivingEntity.writeCustomDataToNbt` 写 `"AbsorptionAmount"`，读回时用 `setAbsorptionAmountUnclamped`（**绕开**钳位），随后才读 `attributes` |

原实现是 `player.absorptionAmount += toAdd`（1.12.2 的直写），于是 `max_absorption = 0` ⇒ 钳成 0 ⇒
**每秒白算一次**，而 `contribution[id] = mine + toAdd` 照样前进 —— 几十秒后 handler 还会自认已到上限并
停止尝试。全程**零日志**，所以"没生效"与"没装"无法区分（正是 `AGENTS.md` §4.4 要防的那种形状）。

**改法**：
- 每次充能前 `ensureHeadroom(player, cap)`：给自己的**临时** `generic.max_absorption` 修饰符
  （`simple_tweaks:golden_shield_max_absorption`，`ADD_VALUE` = 上限）。`ADD_VALUE` 保证与原版吸收效果
  **叠加**而不是覆盖，所以"外部吸收值保留"这条承诺仍然成立；
- 记账改成**落地差值**：`applied = 写入后 - 写入前`，并把 `applied < requested` 显式记成
  `outcome=clamped` —— 以后再被谁钳住，日志会说话，而不是静默；
- 探针：`outcome=granted / at-cap / clamped / cleared`（`[FST-GoldenShield]`）；
- 修饰符**必须是 temporary**：它的解除期限在内存里（本文件的 map），持久化修饰符 + 内存期限正是
  `heavenly_punishment` 当初留下永久攻速压制的组合（`AGENTS.md` §4.2）；
- 卸甲 / 等级归零时 `clear()`：撤掉修饰符（属性变更会触发 `LivingEntity#onAttributeChanged` 把吸收值
  钳回新上限）⇒ 本附魔给的那部分消失，金苹果该得的部分保留。
  > **这条是设计决定，可被作者否决**：原实现是"忘掉记录、但吸收值留着"，等于白送护盾。

**同类问题（本批**未**改，等作者定）**：同一个模式还出现在两处 ——
`healing_blade` 的溢出转化（`attacker.absorptionAmount += added`）与 `death_protection` 的
`player.absorptionAmount = lvl * 20f`。两者在玩家没有 `absorption` 效果时**同样被钳成 0**。
修法就是把 `ensureHeadroom(...)` 抽成共用工具（本批刻意没动这两个文件，避免在作者的未提交改动上叠加）。

**验收**：§T 组（T1–T8）。

---

### 2. 新附魔四个：`damage_lottery` / `frostbite` / `venom` / `chain_lightning`

设计上只需记住三点；完整规则写在各自 KDoc 里。

**① 元素伤害必需自己的伤害类型。** 毒液与电弧都以**玩家为 attacker** 制造伤害（否则击杀不给经验、
死亡信息难看），而这正是本仓库所有战斗附魔判定的形状（`hunters_mark` 会吃掉标记、`charged_strike` 会
pool、`combat_master` 会叠连击、`execute`/`assassin` 会判阈值）。若不加以区分，**每秒一次的毒伤会喂给
它们全部**，还会重新进入三件套自己的 handler（无限叠层）。故新增
`simple_tweaks:venom` / `simple_tweaks:chain_lightning`，四个新 handler 一律 `isElementDamage()` 早退
（`util/damagesource/ElementDamageSources.kt`）。
"无视护甲/抗性"就用原版 tag 表达：`venom` 进 `bypasses_armor` + `bypasses_resistance` +
`bypasses_enchantments`（保护附魔属于玩家语感里的"护甲"）；`chain_lightning` **不进任何 tag** ——
作者的公式没说它无视什么。

**② 毒液不能挂在 `PlayerTickEvent` 上（纠正任务描述里的写法）。** `PlayerTickEvent` 只对玩家触发，
而毒液层数挂在**怪**身上 ⇒ 那样写等于"只有玩家中毒时才有 DoT"。改用
`LivingEvent.LivingUpdateEvent`（`EntityTickMixin` 提供的每实体 tick）。

**③ 每秒一跳的伤害会被无敌帧静默吞掉。** `LivingEntity#damage` 的门（字节码）是
`timeUntilRegen > 10 && !source.isIn(BYPASSES_COOLDOWN) && amount <= lastDamageTaken → return false`，
**且这道门在护甲处理之前**（`BYPASSES_ARMOR` 只在 `applyArmorToDamage` 里被读，位于门之后）。1 点/秒的
毒伤在玩家持续攻击时永远小于 `lastDamageTaken` ⇒ 一发都落不了地。`ElementDamageSources.damageElemental`
因此**观测**（而非预测：`lastDamageTaken` 是 `protected`）到 `false` 后清 `timeUntilRegen` 重试一次，并把
"吞掉后强制"与"一次就落地"记成不同 outcome。
> 声明式的替代是让类型进 `#minecraft:bypasses_cooldown`（零代码）。**没采用**的理由：那样每一跳都必定
> 落地，"差点被吞"变成不可观测，且会静默改变该类型的任何未来调用者。改回 tag 是一行数据改动。

**其余公式与刻意的设计决定**（都可一行改回）：

| 项 | 取值 | 备注 |
|---|---|---|
| 大乐透倍率 | `[0.5, 1.5]` 均匀，等级**只**延长窗口（`60+40×lvl` tick） | 作者只给了 50%~150%，等级无对应语义 |
| 大乐透触发方式 | **同一事件内：先结算旧赌局，再抽新赌局** | 否则隔次命中才生效，一半挥砍是"普通"的 |
| 冰霜层数 | 5 层冻结、2 秒、碎冰 `+20%×lvl` | 冻结 = 每 tick 归零速度 + `Slowness 6`（`-0.15`/级 `ADD_MULTIPLIED_TOTAL` ⇒ 等级 6 即 `-1.05`，属性自钳到 0）；玩家位置由客户端权威，只清速度定不住 |
| 冰霜的冻结 | 不阻止**原地跳跃** | 要拦住需负 `jump_strength` 修饰符 + 内存期限 = 重新打开 §4.2 那类泄漏，二者取其一 |
| 毒液 | 每层每秒 `0.2 × 等级` 真实伤害，上限 10 层，10 秒不命中则整层失效 | |
| 毒液传染 | 死亡时把 `ceil(层数/2)` 传给 8 格内最近的敌人（排除主人/队友/已驯服宠物） | 全额传染会让一把剑无限连坐 |
| 连锁 | 伤害 `0.3+0.1×lvl`（吃护甲）、范围 `3+lvl` 格、目标 `1+lvl/2`、窗口 `60+40×lvl` tick | 电弧**不**给受害者充能（否则是 `目标数^命中数` 的级联） |

**验收**：§U 组（三件套 U1–U11）、§V 组（大乐透 V1–V5）。

---

### 2.5 附魔注解统一 + `weight` / `damagePerLevel` 改为按分类派生

**起因**：作者问"`damagePerLevel` 好像不能自动检测生成？"，并说注解里的东西只统一了一部分。两件事其实
是一件事：**这两个字段在 1.12.2 本来就是从分类推出来的**，移植时被逐个写成了字面量；而 5 个
`jsonEmit = false` 的声明里，它们**根本不生效**（这正是"改了没反应"的原因）。

**证据**（1.12.2 原工程，只读参考 —— 注意下面第 3 条：**伤害那条曲线已被作者改掉**）：

```kotlin
// baseclass/EnchantmentCategories.kt
enum class EnchantmentCategories(val rarity: Int, ...) {
    UNIQUE(-1), COMMON(0), UNCOMMON(1), RARE(2), EPIC(3), LEGENDARY(4), MYTHIC(5), MYSTERY(6);
    val weight: Int get() = if (rarity < 0) 0 else (10 - rarity * 1.5.roundToInt()).coerceAtLeast(1)
}
// baseclass/ModEnchantments.kt
protected open fun itemExtraDamage(level: Int, creatureType: EnumCreatureAttribute): Number =
    if (this.modType == ModEnchantmentType.SWORD) (0.2 + (category.rarity / 20.0)) * level else 0.0
```

`1.5.roundToInt()` 是 **2**，所以权重曲线是 `10 - rarity × 2` → **10/8/6/4/2/1**（本批已按此恢复，见
第 5 条）；伤害在 1.12.2 是 `0.2 + rarity/20`，且**只有 SWORD 类型有**。1.12.2 里有 6 个附魔覆写了
`itemExtraDamage`（`combo` 0.15、`fire_master` 0.25、`healing_blade` 1.0、`celestial_blessing` 2.0、
`heavenly_punishment` 5.0，`infinite_power` 返回 `Int.MAX_VALUE` 当秒杀用）——**这 6 个覆写值本批全部
按原样保留**，它们才是"1.12.2 血统"的部分。

> ⚠️ **派生曲线（作者在 `cleanup21` 期间改定，以代码为准）**：`tierDamagePerLevel` 现在是
> `rarity × 1.5 / 20`，即 `UNIQUE 0 / COMMON 0 / UNCOMMON 0.075 / RARE 0.15 / EPIC 0.225 /
> LEGENDARY 0.3 / MYTHIC 0.375 / MYSTERY 0.45` —— **不是** 1.12.2 的 `0.2 + rarity/20`
> （COMMON 0.2 … MYTHIC 0.45）。这是一次有意重平衡：低档大幅缩水，COMMON 直接归零。同时作者把
> `venom` 的显式 `damagePerLevel = 0.0` 删掉（改为吃 RARE 派生的 0.15）。本文件与 KDoc 里凡是引用
> "1.12.2 公式"的地方都已改成指向**代码现状**并标明差异。

**改法**：

1. `@ModEnchantment.weight` / `damagePerLevel` 的默认值改成**哨兵**
   （`WEIGHT_FROM_CATEGORY = -1`、`DAMAGE_PER_LEVEL_FROM_CATEGORY = -1.0`），语义是"按分类派生"。
   **注解参数必须是编译期常量**，调用点不可能自己算 `10 - rarity×2` —— 派生只能在 KSP 里做，
   处理器持有那份表（`TIER_WEIGHTS` / `TIER_RARITY`）。
2. 处理器新增**漂移报告**（`warn`，默认可见）：显式值与分类派生值不一致就逐条列出。不是报错
   （覆写合法），目的是让**意外的**不一致藏不住；本批正是靠它看清了有哪些覆写。
   ⚠️ 一开始用了 `logger.info`，构建里**根本不显示**（KSP 的 info 要 `-i`）——已改 `warn`。
   派生公式也写成一次除法 `(4 + rarity) / 20.0` 而不是 `0.2 + rarity/20.0`：后者在 IEEE-754 下
   得到 `0.30000000000000004`，会原样写进 datapack JSON。
3. `EnchantItemWeights` **恢复成 10/8/6/4/2/1** 并补 `MYSTERY` —— 见下面第 5 条。
4. `jsonEmit = false` 的 5 个文件里有 4 个的 `effects` 其实**已经可表达**（`{}` 或 `minecraft:damage`），
   于是翻成生成式并删掉手写 JSON，注解成为唯一来源；只有 `unbreakable` 继续 `jsonEmit = false`
   （它用 `minecraft:item_damage`，处理器不产出该组件）。翻的依据：**原版 codec 的 `primary_items`
   默认取 `supported_items`**，而手写文件正是靠省略该键在跑 —— 生成式显式写出它，语义相同。
5. 源码清扫（脚本 + 逐项复核）：17 处 `supportedItems = "#…"` → `EnchantSupportedItemTags.*`；
   15 处 `weight = <字面量>` → `EnchantItemWeights.<分类>`；**删掉 16 处冗余 `damagePerLevel`**
   （值与派生值相同的）；保留 11 处**有意覆写**（1.12.2 的 6 个 + 4 个新增附魔的显式 `0.0`
   + `grievous_wounds`）。

**顺手修掉的三处真实漂移**（"注解说了不算"的静默错）：

| 项 | 注解里写的 | 实际生效的 | 处置 |
|---|---|---|---|
| `heavenly_punishment.damagePerLevel` | `2.5`（作者改过两轮） | 手写 JSON 的 **5** | 翻成生成式后注解才生效。我按已发布值对齐成 5.0，**作者随后又改回 2.5 —— 这次它是真生效的**（这正是翻成单源的目的） |
| `heavenly_punishment.anvilCost` | `90` | 手写 JSON 的 **14** | 对齐到 14（同上：改 90 现在才有效） |
| `celestial_blessing` / `heavenly_punishment` 的 `weight = 0` | `0` | JSON 的 `1`，且**构建直接失败**（处理器要求 `weight >= 1`） | 改用 `EnchantItemWeights.MYSTERY`（=1）；两个 tier 本来就被 `NOT_IN_ENCHANTING_TABLE_CATEGORIES` 排除在附魔台之外，权重只是描述性的 |

**`EnchantItemWeights` 曾被静默改过**（本批最该记住的一条）：

```
commit db3147c（引入）     COMMON=10 UNCOMMON=8  RARE=6 EPIC=4 LEGENDARY=2 MYTHIC=1
commit 146123e（cleanup20） COMMON=20 UNCOMMON=15 RARE=10 EPIC=6 LEGENDARY=3   ← 无 DEVLOG 记录
```

原值就是 `10 - rarity×2`。改过之后**不变量被破坏**：常量不再等于 tier 的规范权重，而当时已有 8 个声明
在用常量（那 8 个一旦重建就会静默改权重）；作者本轮又把 48 个字面量声明换成常量 → 若不恢复，
**48 个附魔的附魔台权重会一起上调**。恢复后这次统一是**保值**的。

**收益权重的净变化（vs HEAD 已发布值，逐 id 核对）**：**52 个不变，8 个变化** ——

| id | HEAD | 现在 | 原因 |
|---|---|---|---|
| `extra_armor` | 6 | 4 | 之前用漂移后的 `EPIC=6`；EPIC 的 1.12.2 值是 4 |
| `adopted_armor` / `blessing_extension` / `curse_resistance` / `grievous_wounds` / `multishot` | 3 | 2 | 同上，`LEGENDARY` 3→2 |
| `clear_sight` | 10 | 6 | 同上，`RARE` 10→6 |
| `fast_bow` | 5 | 4 | HEAD 是**故意**的字面量 5；统一时被换成 `EPIC` 常量，恢复后 EPIC=4。**想保住 5 就写 `weight = 5`** |

> 即：这 7 个是把漂移值**改回** 1.12.2 的值，只有 `fast_bow` 丢了它原本的覆写。

**新附魔的伤害**：`frostbite` / `chain_lightning` 两个 SWORD 写了显式 `0.0`（按派生会白得平砍伤害），并在
漂移报告里留痕；**`venom` 与 `damage_lottery` 之后由作者改成吃派生**（RARE 0.15 / LEGENDARY 0.3），
本文件不再把它们列为覆写。想让另两个也与其它 SWORD 附魔一致，删掉那两行即可。

**验收**：§W 组。构建期证据：`w: [ksp] ModEnchantment: 9 explicit value(s) differ from the tier
default (deliberate overrides) -> …`（默认可见，逐条列出覆写者；数量随作者后续调整而变）。

---

### 2.6 附魔的平砍伤害加成现在会出现在 tooltip 上

**现象**（作者截图）：同一把钻石剑，`minecraft:attributes` 带来的 `+20% 横扫伤害比率` **有**显示，
而附魔的 `damagePerLevel` 加成的伤害**一个字都没有**。

**证据（这解释了为什么"本来就不该有"）**：

- `@ModEnchantment.damagePerLevel` 生成的是 `minecraft:damage` **附魔效果**，原版在**命中时**通过
  `EnchantmentHelper#getDamage` 应用它（把 `MutableFloat` 喂给每个 DAMAGE entry 的
  `EnchantmentValueEffect#apply`）——它**不是属性修饰符**。
- 原版 tooltip 的附魔区只做两件事：按 `#minecraft:tooltip_order` 渲染附魔**名字**
  （`ItemEnchantmentsComponent#appendTooltip` → `Enchantment.getName`），以及把**属性**类效果并进
  "在主手时"块。伤害效果两边都不进 —— 所以截图里那个对比是预期行为，不是这次的 bug。
- 1.12.2 同样不显示：那边也是 `calcDamageByCreature`（命中时），不是属性。

**改法**：新增 `client/tooltips/EnchantDamageToolTipHandler.kt`（Fabric `ItemTooltipCallback`，
与 `ManaPoolToolTipHandler` 同一接缝），**把原版那条总伤行就地改写成"含附魔"的数字**（作者要求
的不是单开一行）：

```text
在主手时：
44.02 攻击伤害      ← 原版那行（原本 7），被改写为 7 + 37.02
1.6 攻击速度
```

- **只改"总伤"那一行**：判定条件是 `attribute.modifier.equals.0`（**平铺**总值的键，不是百分比的
  `.1/.2`）+ 参数里出现攻击力属性键 ⇒ 一定是"7 攻击伤害"，而不是附魔块里的 `+20% 攻击伤害`。
- **不重算原版的算术**：把那一行已经渲染出来的数字**读回来**，只加上附魔的份额，再用**同一个翻译键**
  重渲染并替换该行（保留原样式）。数字参数按"哪个参数能解析成平铺数字"取值，不假设顺序。
- **值从实时注册表读**：`Enchantment#effects().get(EnchantmentEffectComponentTypes.DAMAGE)` →
  `EnchantmentLevelBasedValue#getValue(level)`（输入取 `0`，对处理器唯一产出的
  `minecraft:add`/`linear` 形状即"增量"）。**没有在客户端再抄一份 `damagePerLevel` 表** —— 作者改
  派生曲线，这个数字自动跟着变。
- **兜底（绝不丢信息、也绝不显示错的数字）**：无法改写时**改为新增一行** `+X 攻击伤害`
  —— ①没有平铺总伤行（创造模式搜索/配方书这类不显示属性；或该物品的总值以百分比呈现）；
  ②数字参数解析不出来（**带千位分隔符的 `1,000` 不会被当成 `1`**，而是判定为不可解析并走兜底）。
  兜底行的文案用原版键 `attribute.modifier.plus.0` + 从注册表读的攻击力属性键，颜色 `DARK_GREEN`
  （`ItemStack` 字节码里该块用的色），落点：紧跟攻击力行 → 块末尾 → tooltip 末尾。
  ⚠️ 属性键**不能手写**：1.21.1 里是 `attribute.name.generic.attack_damage`，少个 `generic.` 会直接
  显示裸键名。
- **判定一律按翻译键，不按渲染后的字符串**（`attribute.modifier.*` / `item.modifiers.*` 跨语言稳定）；
  `EnchantmentNameColorMixin` 的 KDoc 已经记过这条教训。
- 🔴 **必须走完整的文本树（`content` + `siblings`）—— 只看 `line.content` 会找不到那一行。**
  第一版就是这么失败的：作者客户端上 fold 没触发、退了兜底，日志里只有成功路径的探针，什么都没说。
  从字节码里挖出的真相是 `ItemStack#appendAttributeModifierTooltip` 把整行构造成
  **`ScreenTexts.space().append(Text.translatable("attribute.modifier.equals." + operationId, DECIMAL_FORMAT.format(value), Text.translatable(attr.getTranslationKey())))`**
  —— 也就是说**行的自身 `content` 是一个空格字面量，可翻译文本是它的 sibling**。所以现在
  识别与改写都在"本行 + 一级 siblings"上进行（`findAttackDamageTotal` / `translatableNodes`），
  命中 sibling 时替换那个 sibling（保留外层包装与其 `DARK_GREEN`）。
  顺带解释了上一版为什么落点"看起来对"：附魔属性行是**没有包装的**纯可翻译文本，所以那时能匹配上。
- **失败也不再静默**：走兜底时会把 tooltip 前 24 行的结构 dump 进 `[FST-EnchantDamageTooltip]`
  （形如 `3:literal+[attribute.modifier.equals.0]`），一次悬停就够定位 —— 这正是第一版缺的东西。
- **只对本模组命名空间、且只对 `requirements` 为空的 entry**：原版 Sharpness/Smite/Bane 带生物类型
  条件，tooltip 没有 `LootContext` 可判，宁可什么都不显示也不显示一个"看起来无条件"的数字。
- 探针 `[FST-EnchantDamageTooltip]`（debug 关闭时静默）：`outcome=folded-into-vanilla-total` 表示改写
  成功；**一行探针都没有** = 客户端注册表没给 `minecraft:damage` 组件（本文件唯一的假设）。

> **诚实标注语义**：被改写后的数字是"**对一个生物造成多少**"，而不是"物品的属性合计"——附魔伤害在命中时
> 才加上去，所以要打方块、或打被 `requirements` 排除的目标时，这个数字偏乐观。这是作者明确要的效果；
> 若哪天想两个数并存（原版总数 + 附魔增量），把兜底那行改成始终追加即可。

**未采用的备选（一行可切换，故记录在此）**：把伤害从 `damagePerLevel` 改成
`attributes = [AttributeSpec("minecraft:generic.attack_damage", perLevel, add_value)]` —— 那样它会
**零客户端代码**地出现在"在主手时"块里（和截图里那行横扫一样）。代价是改变了伤害的施加机制
（属性修饰符 vs 附魔伤害效果，会与属性上限/其它修饰符交互），而 1.12.2 走的是
`minecraft:damage` 路径。**没有**擅自切换。

**验收**：§X 组。

---

### 2.7 新配置项 `noHurtCam`（受伤不抖动画面）

**需求**（作者）：`stconfig` 里加一项，打开后受伤不再导致屏幕抖动。

**证据**：

- **1.12.2 工程没有这个模块**（查过它的 `GeneralConfig` 与所有 `hurtTime` 用法，只出现在 Velocity 模块里）
  ⇒ 这是**新选项**，不是移植，因此默认值取 **false（可选开启）**，而不是照抄某个注解默认。
- **接缝**（字节码）：`GameRenderer#tiltViewWhenHurt(MatrixStack, float)` 是**唯一**按
  `LivingEntity#hurtTime` 旋转视角的地方 —— 它取相机实体、算 `hurtTime - tickDelta`、绕
  `RotationAxis.POSITIVE_Z` 转 `40 * f / 8000`，另有一条 `deathTime`（上限 20）的死亡倾斜分支；
  `GameRenderer` 里没有第二处读 `hurtTime`。HEAD 处 `cancel()` 正好只去掉抖动，红色受伤overlay、
  受伤音效、手部摆动、击退与无敌帧都在别的路径上，不受影响。

**改法**（按 `AGENTS.md` §2 的"加一个配置项"清单，四件套一个不落 + 一个 mixin）：

| 位置 | 改动 |
|---|---|
| `GeneralConfig` | `noHurtCam: Boolean = false` + KDoc（为什么不是移植、为什么默认 false、接缝在哪） |
| `SimpleTweaksConfig` | `readGeneral` 读 `bool("noHurtCam", false)`；`write` 回写 |
| `SimpleTweaksConfigScreen` | 第 1 页加 `boolRow("noHurtCam", …)`（`boolRow` 自带 `SimpleTweaksConfig.save()`，改动即时落盘） |
| lang | `gui.simple_tweaks.config.noHurtCam`（en `No hurt cam shake` / zh `受伤不抖动画面`） |
| `mixin/NoHurtCamMixin.java` | `@Inject(method = "tiltViewWhenHurt(...)", at = HEAD, cancellable)` → `GeneralConfig.getNoHurtCam()` 时 `ci.cancel()`；**注册进 `simple_tweaks.mixins.json` 的 `client` 列表**（`GameRenderer` 在专用服务器上不存在） |

**构建期验证**：refmap 里已生成
`NoHurtCamMixin { "tiltViewWhenHurt(Lnet/minecraft/client/util/math/MatrixStack;F)V": "Lnet/minecraft/class_757;method_3198(...)" }`
—— 目标解析成功，所以 `required: true` + `defaultRequire: 1` 不会在启动时炸。refmap 里现在 26 个 mixin 类
（25 + 本项）。

**验收**：§Y 组。

---

### 2.8 `runPlayerAttack` 的参数清理 + `simulateCooldown`

**需求**（作者）：审查 `util/PlayerUtils.runPlayerAttack` 是不是有很多参数"无效"；**能复现的就复现、
不能的就删**；另外加一个 `simulateCooldown`（攻击目标后像玩家手动打一样触发/消耗 atkCharge，
charge 不满则伤害按比例减少）。

**调用点审计**（判定依据 —— "参数存在 ≠ 有人读它"，函数参数同理）：

| 调用点 | 实际传的 |
|---|---|
| `EnchantKillAuraHandler` | `(p, dmg, forceHit = true)` |
| `EnchantAoeAttackHandler` | `(attacker, perTarget, true)` |
| `EnchantCelestialBlessingHandler` | `(owner, proj.damage, true, CelestialDamageSources.dealDamage(owner))` |
| `EnchantEchoShotHandler` | `attacker/rawDamage/forceHit/source/triggerEvent=true/allowCrit=false` |
| `EnchantStarfallHandler` | 同上（`triggerEvent = true`、`allowCrit = false`） |
| `EnchantPiercingArrowHandler` | `+ source = source`（真正的自定义源） |

⇒ 从未被传成非默认值的是 **`lastHitKeepDamagePlayerSource`**、**`ignoreArmorAndPotion`**、
**`damageCreativePlayer`**；`triggerEvent` 只被传过 `true`（等于默认）。

**逐个判定**：

| 参数 | 1.12.2 语义 | 判定 |
|---|---|---|
| `attacker` / `rawDamage` / `forceHit` / `source` / `allowCrit` | 生效 | **保留**（`allowCrit = false` 有 3 个调用点在用） |
| `damageCreativePlayer` | 生效：`if (this is PlayerEntity && !damageCreativePlayer) … isCreative` | **保留**（有效，只是暂无调用点 —— 记在这里，不删能力） |
| `lastHitKeepDamagePlayerSource` | 只喂给 1.12.2 手写的 `applyDamage`，在那里决定**死亡源**：`if (keepPlayerSource) causePlayerDamage(attacker) else source`（原工程 `PlayerUtils.kt:236`） | **删除**：1.21 里"用哪个源打的"就是"死亡记在谁头上"，开关无事可做 —— 想改归属就传对应的 `source` |
| `ignoreArmorAndPotion` | 跳过 `applyArmorCalculations` + `applyPotionDamageCalculations`（1.12.2 把 Protection 折在护甲步里，故一并跳过） | **删除**：1.21 跳过护甲/抗性是**伤害类型标签**（`#minecraft:bypasses_armor` / `bypasses_resistance` / `bypasses_enchantments`）的属性，**不能由调用点开关**；做成布尔参数只能去**覆盖调用方的 `source`**，等于同一件事两种说法。能力没丢：数据层那条路已存在（`util/damagesource/ModDamageTypes.kt`，其中 `damageBypassingArmor` 就是 Echo Shield 用的那套） |
| `triggerEvent` | `false` 时不 post `AttackEntityEvent` / `LivingAttackEvent` / `LivingHurtEvent` / `LivingDamageEvent` | ✅ **复现**：新增 `EventSeams.pushSuppressDamageEvents()` / `popSuppressDamageEvents()` 作用域；被抑制时 `hurt()` / `damage()` **不 post**，直接返回"未取消、amount 原样"的事件（正是"没有任何处理器跑过"时注入器会看到的东西）。`runPlayerAttack` 用 `try/finally` 成对开合 |
| `simulateCooldown` | —（1.12.2 没有；只有 vanilla 自己的攻击路径有） | ✅ **新增**，见下 |

**`triggerEvent` 复现的三个设计点**（KDoc 同款）：① **深度计数而非布尔** —— 嵌套调用（AoE 命中后目标
死亡又波及别人）不能让内层收尾替外层恢复 post；② **`ThreadLocal`** —— 单机下客户端与内置服务端同 JVM
两线程，绝不能互相影响；③ **只覆盖两个伤害 seam** —— 1.12.2 那四个事件里 `AttackEntityEvent` 在 1.21
属于 **vanilla 攻击流程**（`PlayerEntityAttackMixin`）而不属于这个 helper，`LivingDeathEvent` 刻意不抑制
（1.12.2 的 `triggerEvent` 也不管死亡）。

**`simulateCooldown` 的依据**（`PlayerEntity#attack` 字节码，一次读出全部）：

```
67: ldc 0.5f
71: invokevirtual getAttackCooldownProgress:(F)F        ← charge
76: fload_2 / ldc 0.2f / fload 6 / fload 6 / fmul / ldc 0.8f / fmul / fadd / fmul
90: → damage *= 0.2f + charge * charge * 0.8f
99: aload_0 / invokevirtual resetLastAttackedTicks:()V   ← 消耗冷却
```

即 `charge = getAttackCooldownProgress(0.5f)`（就是 `attackCharge` 那个扩展属性），伤害乘
`0.2 + charge² × 0.8`，随后 `resetLastAttackedTicks()`。
⚠️ **唯一刻意的顺序差异**：vanilla 在暴击判定**之前** reset，这里放在判定**之后** —— 因为
`EventSeams.forgeCriticalHit` 会**实时读** charge 喂给 `CriticalHitEvent`，而 `EventSeams` 的 KDoc 已记录
"reset 之后再读只会拿到 ≈0.5，会让所有 `charge >= 0.848` 的门失效"。放在判定之后既保住事件里的真值，
又照样在伤害落地前把冷却消耗掉。

**调用点语义未变**（逐个核对位置参数）：`EnchantAoeAttackHandler` 传 3 个、`CelestialBlessing` 传 4 个，
都落在**未被删除**的位置上（删掉的是第 5、第 7 个参数）⇒ 没有任何调用点需要改动、也没有行为变化。
**`triggerEvent = false` 与 `simulateCooldown = true` 目前都还没有调用点**（见下面的未验收条目）。

**验收**：§Z 组。

---

### 2.9 `damage_lottery` 移除（作者）+ `simulateCooldown` 接上 kill_aura

**① `damage_lottery` 已删除，且不恢复。** 作者裁定它"有点超模"（每次命中都让目标下一次受伤
×0.5~1.5，配合多段伤害的附魔会滚成一串乘算）。清理范围：

| 项 | 处置 |
|---|---|
| `EnchantDamageLotteryHandler.kt` | 作者删除 |
| 4 个 lang 键（`enchantment.simple_tweaks.damage_lottery` / `.desc` × 中英） | **已删**（孤儿键 —— 项目规矩：不留无消费者的键） |
| 启动行 build 摘要里的名字 | 已改（改记为 frostbite / venom / chain_lightning） |
| 生成物 | KSP 重建后 `GeneratedEnchantments`、`KEYS`、datapack JSON 都不再包含它 ⇒ 附魔 **67 → 66**、handler **71 → 70**、lang **198 → 196** |
| 文档 | checklist §V 整组**退役**（编号保留，组内写明原因）；§W1/§W2 的期望值改为当前 9 条覆写 |

**② `simulateCooldown` 接上 kill_aura：保留"是否冷却完毕"的判断，然后花掉这次挥砍。**
作者的意图是 VI 级 kill_aura 不再靠自己的 tick 计数"每 2 tick 打一下"，而是**问武器冷却是否就绪**。
现有代码正好支持这个读法：`maxCooldown(p, lvl) = ceil(20 / attackSpeed × (7 - lvl))`，**VI 级系数为 1，
即恰好一个挥砍周期** —— 高档位本来就"想"按挥砍速度打，只是用 tick 计数近似，会与真实冷却漂移
（掉帧、中途手动挥砍、`onPlayerAttack` 重置计数）。改动：

- `onPlayerTick`：tick 冷却判定之后加 **`if (p.attackCharge < 1.0f) return`** —— charge 是"这次挥砍是否
  就绪"的权威答案；`charge = 1.0` 时伤害系数是 `0.2 + 1²×0.8 = 1.0` ⇒ **伤害不缩水**，变的只是节流与
  "这一下算你的挥砍"。
- 命中改为 `runPlayerAttack(..., simulateCooldown = true)` ⇒ 光环**花掉**这次充能，于是它不再是
  "免费多打一下"，随后手动那一刀要从 0 蓄力。
- **低档位（I–V）这道门从不拒绝**：tick 冷却是挥砍周期的 2–6 倍，远长于充能回满所需 ⇒ 行为不变。
- 成功路径的日志加了 `charge=`，验收时可直接核对。

**验收**：§Z1–§Z3（现在是真可测的：kill_aura 已经接上）。`triggerEvent = false` 仍无调用点（§Z4）。

---

### 3. 本批的「未验收」状态

**整批（cleanup21）尚未安装到客户端。** 代理侧的动态证据只有 `runServer` 冒烟，且**跑了两次**：
§2 那批之后一次，§2.5 的注解/派生重构之后又一次 ——

- 服务器启动无异常，`Loading 45 mods` 含 `simple_tweaks 2.1.0`，两次都 `Done (…)! For help`；
- `Registered 71 enchantment handler(s) (4 hand-listed + 67 @ModEnchantment)`，其中
  `EnchantDamageLotteryHandler` 2 / `EnchantFrostbiteHandler` 2 / `EnchantVenomHandler` 3 /
  `EnchantChainLightningHandler` 2 / `EnchantGoldenShieldHandler` 3 个监听器；
- 新增的 2 个 damage_type JSON 与 3 个 tag 修改**加载无报错**（数据包解析失败会在这里炸）；
- 重构后的第二次冒烟额外确认：**4 个新翻成生成式的定义**（`reforge` / `infinite_power` /
  `celestial_blessing` / `heavenly_punishment`）与 66 个生成 JSON **全部加载无报错**、无 tag 悬空引用。

`runServer` **不构成行为验收**。因此下列全部条目**未验收**：§T1–T8、§U1–U11、§V1–V5、§W1–W5、
§X1–X7、§Y1–Y5。

> §X/§Y 是**纯客户端**功能（tooltip 渲染、受伤视角），连 `runServer` 都碰不到它们 —— 这两组的动态
> 证据只能来自作者的正式客户端。（§X 的第一版正是这样暴露的：作者截图 + `latest.log` 里的
> `[FST-EnchantDamageTooltip]` 行一起，才定位到"sibling 包装"那个根因。）

---

### 4. 本批的三条教训

1. **"1.12.2 直写字段"在 1.21 可能被一层看不见的钳位吃掉。** `absorptionAmount` 是私有字段 + 公开
   setter，Kotlin 语法上完全合法、编译毫无怨言，运行时却因为一个**默认 0 的属性**而全部作废。
   与 §4.2（`setSaved(false)`）同源：**跨版本改写的不是语法，是语义**。判据：任何"写实体状态"的代码，
   先确认 1.21 有没有给它加了 clamp / 上下限 / 追踪层。
2. **记"请求值"而不是"落地值"的记账，会在出错时把错误放大成永久状态。** 原 handler 因为记的是请求值，
   在写入全被丢弃的同时还自我认证"已满"。凡是"写入 + 记账成对出现"的地方，差值必须从**写入后重新读**。
3. **"能写"不等于"生效"：注解参数是编译期常量，派生只能在 KSP 里发生；而 `jsonEmit = false` 会让
   注解里几乎所有 datapack 字段变成惰性文档。** 三处真实漂移（2.5 vs 5、90 vs 14、weight 0 直接构建
   失败）都是这一条的后代。对策不是"记得同步"（人记不住），而是**让机器报出来**：默认可见的漂移
   报告 + 让注解成为唯一来源。另外：`logger.info` 在普通 `gradlew build` 里**根本看不到**（KSP 的 info
   需要 `-i`），一个看不见的报告等于没有报告 —— 这条本身也是踩过才知道的。

---

## `build=cleanup20`（2026-10-10 · **未安装；本批全部改动并号于此**）

### 提交摘要（可直接用作 commit message）

```
fabric-1.21.1: 修命令树 / 反伤循环 / 属性持久化，日志前缀改 FST，清理惰性配置

修复
- 命令：客户端与服务端共用根名 → 服务端所有子命令不可达。全部命令改服务端注册，
  开界面改走 PacketOpenModScreen（复活 1.12.2 的 PacketOpenEnchantInfo）
- echo_shield：双方都带时反伤无限循环（onDamage 几何级联约百层；onHurt 储值相等时栈溢出）
- echo_shield × fast_bow：multishot 取消射击后 @ModifyArg 永不执行 → fast_bow 倍率被丢弃
- heavenly_punishment：攻速压制误用 addPersistentModifier（1.12.2 为 setSaved(false)），重载后永久压制
- blessing_extension / curse_resistance：原地改 duration 未通知客户端 → 两边时长静默分叉
- multishot：齐射被剩余箭数封顶，且一次松手收 1+等级 支箭

调整
- 日志前缀 [ST-*] → [FST-*]
- 清晰视界：夜视一次给 30 分钟（原 20 秒一次、10 秒续期）
- 命令树：/simple_tweaks <enchant|stconfig|enchantinfo> + /fst 简写；等级 0 = 移除附魔
- 删除零消费者的配置键：anvilDisenchant / noFovEnabled / noFovValue
- ForgeEventBus：分派按事件类索引

新增（作者）
- armor_enhance（MYTHIC / ARMOR）+ 双语 lang
```

规模：**62 个附魔** · kotlin 142 / java 28 · handler 文件 70 · mixin 注册 25 · 每语言 lang 187 键。

---

### 1. 命令系统：共享根名让服务端所有子命令不可达 🔴

**现象**（作者客户端日志原文）：

```
Syntax exception for client-sided command 'fst enchant @s'
CommandSyntaxException: 错误的命令参数 at position 4: fst <--[HERE]
```

而 `/fst` **裸执行**时服务端打出了用法行 —— 说明服务端树里 `fst → enchant` 是好的，坏的是"客户端到不了它"。

**证据**（Fabric 源码 `ClientCommandInternals`，本地 sources jar）：

```java
// TODO: Check for server commands before executing.
activeDispatcher.execute(command, commandSource);   // 只含「客户端注册的指令」
return true;

private static boolean isIgnoredException(CommandExceptionType type) {
    // Only ignore unknown commands and node parse exceptions.
    return type == builtins.dispatcherUnknownCommand() || type == builtins.dispatcherParseException();
}
```

客户端**先**拿只含客户端指令的 dispatcher 试解析：只有 `dispatcherUnknownCommand` /
`dispatcherParseException` 会放行给服务端。共用根名时 `/fst enchant …` **匹配到了客户端的 `fst` 根**、
只是子命令不认 → `dispatcherUnknownArgument` → 命中"非忽略"分支 → **报错给玩家并 `return true`，永不转发**。
裸 `/fst` 恰好是 `dispatcherUnknownCommand`，所以它反而能到服务端。

> **判定式**：一个根名下只要有**任何**客户端注册，该根名下**所有服务端子命令都不可达**。

**改法**：**全部命令改为服务端注册**（只有一棵树），开客户端界面走新包
`PacketOpenModScreen`（= 复活 1.12.2 被砍掉的 `PacketOpenEnchantInfo`）。

| 子命令 | 权限 | 位置 |
|---|---|---|
| `<root> enchant <目标> <附魔> <等级> [槽位]` | OP 等级 2 | `core/EnchantCommand.kt` |
| `<root> stconfig` | 无 | `core/ScreenCommands.kt` → `PacketOpenModScreen(CONFIG)` |
| `<root> enchantinfo` / `ei` / `enchinfo` | 无 | 同上 → `PacketOpenModScreen(ENCHANT_INDEX)` |

- 删除 `client/ConfigCommand.kt`、`client/EnchantInfoCommand.kt`；客户端接收器改在 `SimpleTweaksClient`
- 顺带修好两处：裸 `/simple_tweaks` 打印**根用法**、裸 `/simple_tweaks enchant` 打印 **enchant 语法**（原来是解析错误）
- 新增 lang 键 `commands.simple_tweaks.usage`

**代价**：两个开界面的命令变成服务端命令 → 在**没装本模组服务端**的服务器上不可用（以前客户端指令任意服务器可用）。作者已确认可接受。

**验收**：§P12（`/fst enchant` 必须可用 —— 就是本轮修的）、§P13（两个开界面命令 + `[FST-Screen]` 往返日志）、§P14（TAB 补全）、§P15（用法输出）。

---

### 2. 反伤循环：双方都带 echo_shield 时无限递归 🔴

**现象**：双方都带回响之盾时互相反伤，停不下来。

**证据**（结构性，不是偶发）：

- `damageBypassingArmor` 用 `DamageSources#create(REFLECTION, attacker)` 造伤害源，而这个**两参**构造器把同一个
  entity **同时**写进 `source` 与 `attacker`（字节码：`DamageSource(RegistryEntry, Entity)` 转发
  `(type, entity, entity)`，三参构造器把第 3 个参数 `putfield attacker`）→ 反弹伤害到达对方时
  **`getAttacker()` 非空**，而本 handler 恰好只认这个字段；
- 该 helper 里还有 `this.timeUntilRegen = 0`（强制命中）→ 无敌帧**也不会**截断链。

于是有**两条**路径（都只在双方都带时成立）：

| 路径 | 机制 | 后果 |
|---|---|---|
| `onDamage`（每次落地命中） | 把 `0.03×lvl` 立即弹给攻击者，对方再弹 `ratio²`（满级 0.36）回来 | 数值衰减但**深度不衰减** → 一个 tick 内约百层伤害事件 |
| `onHurt`（储值 ≥ 本次伤害） | 弹的是**整个储值**，且储值原本在嵌套调用**之后**才清 | 每层金额完全相同 → 两储值相等时**无限递归 → StackOverflow** |

**改法**（按作者要求"降级"而不是"整段跳过"）：

- 反伤伤害**仍然吃基础倍率减免**（`0.03 × lvl`，满级 20 → 免 60%、实吃 40%），但**不回弹**、**不进储值**、
  **不被完全免疫**（`onHurt` 对反伤整段 `return`，所以"储值 ≥ 本次伤害 → 取消 + 全额反弹"那条分支够不到），
  `markNoHeal` 也跳过（那是盾的反击动作）；
- **储值在反弹之前就扣掉**（它本来就是被这次反弹花掉的）；
- 判定用 `source.typeRegistryEntry.matchesKey(ModDamageTypes.REFLECTION)` —— **无状态**，不引入 ThreadLocal。

"不进储值"是刻意的一条：否则**由反伤攒出来的储值**日后还能被弹出去，等于绕个圈子把反伤反弹了。

**验收**：§Q6（双方都带 → A 只吃 40%、不回弹、日志 `outcome=soaked-reflection`）、§Q7（只有单侧时行为不变）。

---

### 3. echo_shield × fast_bow：倍率被静默丢弃 🟠

**现象**：同时附 multishot 与 fast_bow 时，**拉弓动画变快了、箭却是软的**。

**证据**：`fast_bow` 的倍率靠 `BowItemArrowLooseMixin` 里 `getPullProgress` 上的 `@ModifyArg`（**offset 44**）送达，
而 `multishot` 在 **HEAD** 就 `ci.cancel()` → 方法在 offset 0 返回，那个注入器**永不执行**，倍率被写进
`ChargeBoost` 却无人读取。客户端那侧 `multishot` 提前返回、**不取消**，所以动画一直是好的。

**改法**：`multishot` 自己调 `EnchantFastBowHandler.chargeBoost(bow)` 重算 —— 与客户端动画 mixin **同一个函数**，
并按该注入器的合约**把蓄力夹到满值**（可以补足，不能超过）。

> 不能用 `ChargeBoost.value`：`multishot` 声明 `order = 28`、`fast_bow` 用默认 `Int.MAX_VALUE`，
> 所以 fast_bow 的监听器排在它**之后**，读到只会是刚被重置的 `1.0f`。

**验收**：§O3.5（正例）、§O3.6（反例：不带 fast_bow 时必须仍是半威力）。

---

### 4. 清晰视界：夜视时长 🟠

**现象**：夜视每隔约 10 秒被重发一次，每次重发都让客户端重新评估光照 → 周期性闪烁。

**证据**：原值 `NIGHT_VISION_DURATION = 400`（20 秒）、`RENEW_THRESHOLD = 200`（剩 10 秒续）。

**改法**：一次给 **30 分钟**（`36_000` tick），续期仍只在最后 200 tick、每 tick 检查（所以不会断档）→
从"每 10 秒一次"变成"每半小时一次"。

**验收**：§Q4、§Q5。

---

### 5. multishot 的箭矢消耗 🟠

**现象**：快没箭时齐射会缩水，且一次松手收 `1+等级` 支箭。

**证据**：`actual = minOf(1 + lvl, ammo.count)` 之后 `ammo.decrement(actual)` —— 背包只剩 1 支箭时射 1 支
（**与"附魔没生效"无法区分**），而且箭越少附魔越亏。

**改法**：齐射**恒为 `1 + 等级`**（不再按剩余箭数封顶），一次松手**只收 1 支**（原版的价钱），扇形以准星为中心。

**验收**：§O3.8。

---

### 6. 属性修饰符持久化：`heavenly_punishment` 的永久攻速压制 🔴

**现象**：被天罚命中的生物/玩家可能**永久无法出手**。

**证据**（双重回归；1.12.2 有两道独立保险，两边都被拆掉）：

| | 1.12.2 | 移植后（修前） |
|---|---|---|
| 修饰符本身 | `modifier.setSaved(false)` → **不落盘** | `addPersistentModifier` → **落盘** |
| 解除期限 | 存在**持久化**的 per-entity NBT（`getEntityData()`） | 存在**内存** `WeakHashMap`（DEVIATION 1） |

解除逻辑只有一处、靠那个内存期限。一旦受害者的 `entityData` 条目消失（区块卸载/重载、世界重开、玩家重登）
→ `weakUntil` 读 0 → 清理分支**永远不再满足** → 落盘的 `-100 ADD_VALUE` 攻速压制**再也无人移除**。

**同时纠正一句错误论断**：KDoc 原本写"1.21 在 `AttributeContainer` 层记 `saved`、没有逐修饰符的对应物" ——
**字节码否证**：`EntityAttributeInstance` 里就是一个
`private final Map<Identifier, EntityAttributeModifier> persistentModifiers`，逐修饰符，
而 `addPersistentModifier` / `addTemporaryModifier` 正是那两个旗标（同门的 `EnchantCelestialBlessingHandler`
面对**完全相同**的 `setSaved(false)` 就用对了）。

**改法**：`addPersistentModifier` → **`addTemporaryModifier`**（= 恢复 1.12.2 的 `setSaved(false)` 语义）。
这样即使解除期限丢失，落盘的也只剩"没有"，故障自愈。两处 KDoc 论断一并更正。

**触发窗口较窄**（诚实记录）：压制只在冻结期间施加、`等级 × 10` tick 后解除（I 级 0.5 秒 / IV 级 2 秒），
所以必须在**这 0.5–2 秒内**让受害者对象消失才会泄漏。

**验收**：§R1（NBT 里不应出现该修饰符 id）、§R2（正常到期解除）、§R3（2 秒内重登不留后果）。

**其余低危偏差按作者决定不动**：`CelestialBlessing` 的抗击退/攻速、`FlightHandler` 的攻速在 1.12.2 会保存、
现在是 temporary —— 但它们**逐 tick 重施 → 自愈、不泄漏**，只影响重登瞬间的保真度。

---

### 7. 效果同步：`blessing_extension` / `curse_resistance` 与客户端静默分叉 🟠

**现象**：作者怀疑"没跟客户端同步"——**属实**。

**证据**：两个 handler 都**原地改** `StatusEffectInstance.duration`（`StatusEffectInstanceAccessor`），
而 vanilla 只在 `addStatusEffect` 那条路上通知客户端，链路的终点是
**`ServerPlayerEntity` 重写 `onStatusEffectApplied/Upgraded`，给"本人的连接"发 `EntityStatusEffectS2CPacket`**
（基类只通过 `sendEffectToControllingPlayer` 通知**乘客**）。绕开 `addStatusEffect` 就等于绕开了那个包
→ 客户端一直按它**最后收到的**时长倒计时：增益在客户端提前结束、减益在客户端滞留。

**为什么不能用 `addStatusEffect` 修**：它会把新实例送进 `StatusEffectInstance#upgrade`，而 `upgrade` 在同等级下
**只接受更长的时长** → 碰巧能表达 `blessing_extension`（+1），但**永远表达不了** `curse_resistance`（−1）。

**改法**：保留原地改，收集动过的实例后补发 `EntityStatusEffectS2CPacket(id, effect, keepFading = false)`
（与 `ServerPlayerEntity#onStatusEffectUpgraded` 同款参数：已可见的效果不重放淡入）。**移除**那条路不用管 ——
`removeStatusEffect` 自己会发包。只发给**本人**（别人的粒子颜色走 `POTION_SWIRLS` 追踪数据，与时长无关）。

**验收**：§Q1（增益计时走得慢）、§Q2（减益计时走得快且归零即消失）、§Q3（卸甲立即停止）。

---

### 8. 日志前缀 `[ST-*]` → `[FST-*]`

`STLog` 输出、EchoShield 的 4 处 `[FST-NaN]`、以及 4 个文件注释里的引用全部更新；
`DEV_GUIDE` 与 `acceptance-checklist` 的抓取命令改为 `Select-String '\[FST-'`。
**`MIGRATION.md` 是 append-only 的历史，正文仍是 `[ST-*]`，不要照它 grep。**

---

### 9. 配置清理：三个零消费者的键

按项目自己的规矩（"配置键存在 ≠ 有代码读它"，已发生 3 次）逐个查消费者：

| 键 | 消费者 | 处置 |
|---|---|---|
| `anvilDisenchant` | **0** | 删（1.21 的砂轮已替代铁砧祛魔） |
| `noFovEnabled` / `noFovValue` | **0** | 删（NoFOV 与 1.21 的 FOV 模型冲突：会一起压平望远镜变焦） |
| 其余 9 个 | 都有真实消费者 | 保留 |

删除范围：字段、JSON 读写、屏幕开关、`anvilDisenchant` 的 lang 键 ×2、加载日志里的 `noFov={}@{}` 段。
**历史保留在 `GeneralConfig` 的注释里**；`docs/acceptance-checklist.md` 的「已知未做」表同步。
现有配置文件不会出事：加载器忽略未知键并在加载时回写。

---

### 10. 事件总线：分派按事件类索引

**量测**（先量再改）：监听器方法 **118** 个；单事件最多 24 个（`LivingHurtEvent`）；
最热发布点是 `LivingUpdateEvent`（**每实体每 tick**，来自 `EntityTickMixin`）。

**改法**：`byEventClass` 缓存"参数类型可接受该具体事件类"的子表，`post` 只碰可能命中的监听器；
`CopyOnWriteArrayList` → `@Volatile List` + 下标循环（去掉每次 post 的迭代器分配）。
**与线性扫严格等价**：对具体实例 `isInstance` ⇔ `isAssignableFrom(instance.getClass())`，顺序也保持
（过滤自已排序表），所以 `@ModEnchantment.order` 依赖的同优先级稳定顺序没动。

**诚实结论（已写进 KDoc）**：300 实体下约 6000 次/秒 → 约 **1–1.5 ms/秒（0.1% 一个核）**，**这是个小改进**。
真正的大头在 18 个 `PlayerTickEvent` 监听器内部每 tick 重读附魔组件，**不在总线**。

**未做**：作者选择的"方向 2（无附魔玩家早退）"在实现前被审计否决 —— 三个独立阻碍：
① `EnchantExtraArmorHandler` / `EnchantVitalityHandler` 用 `addPersistentModifier` 挂属性修饰符，
**必须在附魔缺席时也跑才能拆除**，早退会留下**永久 +生命/+护甲**；
② 判定谓词必须扫全背包（`FlightHandler` / `ReForgeHandler` / `VoidProtectionHandler` 就是整包扫描），
不缓存则不比省下的便宜；③ `AutoSprintHandler` 是**配置**驱动而非附魔驱动，一刀切会关掉自动冲刺。
"去掉 START 相位"也被否决（契约变更不值）。

---

### 11. 新附魔 `armor_enhance`（作者新增）

`mythic/EnchantArmorEnhanceHandler.kt` —— MYTHIC / ARMOR，`maxLevel 8`，`slots [armor]`。
1.12.2 工程里**没有**，是全新附魔。KSP 已正确生成 `GeneratedEnchantments.ARMOR_ENHANCE` 与
`data/simple_tweaks/enchantment/armor_enhance.json`。

> ⚠️ **它用的是位置参数写法**（`@ModEnchantment("armor_enhance", EnchantCategory.MYTHIC, …)`）。
> 这本身合法（Kotlin 1.4+ 允许位置参数跟在具名参数后，只要位置正确），但**会让按 `id = "…"` 写的探测脚本漏掉它** ——
> 本文件的统计脚本已改为"注解后第一个字符串字面量"，`DEV_GUIDE` 的 §3 例子仍是具名写法，建议新附魔沿用。

**本轮补的 lang**（两语言各 2 键）：

| 键 | en_us | zh_cn |
|---|---|---|
| `…armor_enhance` | Armor Enhance | 护甲强化 |
| `…armor_enhance.desc` | 按护甲值与盔甲韧性的平方根之和 × 等级减免受到的伤害，护甲越好减伤越多但收益递减 | 同义 |

**两处留待作者确认**（未改代码）：

1. 变量名叫 `armorLoss` / `armorToughnessLoss`，但代码是把值**从 `e.amount` 里减掉**（减伤），名字与实际语义相反；
2. 量级：护甲 20 / 韧性 12 / 满级 8 时减免约 **1.0** 点；即使护甲 30 / 韧性 20 也只有约 **1.26** 点 —— 对一个 MYTHIC 附魔偏弱，
   若 `0.005` / `0.008` 的系数是为更高护甲环境（模组护甲）准备的，注释里值得写明。

---

## 本批的「未验收」状态

**整批（cleanup20）尚未安装到客户端**，因此下列全部条目**未验收**：

- §P12–§P15（命令树 / `/fst` / 开界面 / TAB 补全）—— **TAB 补全尤其需要作者反馈**：补全不写日志，
  代理无法从日志判断症状；去掉全部客户端注册是唯一能同时解释"执行失败"与"补全异常"的机制，但**未证实**。
- §Q1–§Q7（效果同步 / 清晰视界 / 反伤降级）
- §R1–§R3（属性修饰符持久化）
- §O3.5–§O3.8（multishot × fast_bow / 门槛 / 箭矢）

`runServer` 冒烟（mixin 是否绑上、数据包是否加载）**不构成行为验收**。

---

## 本批的三条教训

1. **静态上"能合并" ≠ 运行时"能用"。** 我用 Brigadier `CommandNode#addChild` 的字节码论证
   "同名根可以安全共享" —— 那条字节码结论本身没错（按名合并、唯一异常是 RootCommandNode、不比较 requirements），
   但**执行根本走不到合并后的树**：Fabric 的客户端指令层先解析、而它的"忽略白名单"只放行两种异常。
   **教训**：涉及"谁能被执行"的判断，必须过动态关（铁律①的形状又出现了一次，这次在命令层）。
2. **一个误导性的前提会放过一个真 bug。** `heavenly_punishment` 那句"1.21 没有 `saved` 旗标"让
   `addPersistentModifier` 显得理所当然；而**同一批的另一个 handler 面对完全相同的 1.12.2 模式就用对了** ——
   内部不一致本身就是信号。**教训**：写"1.21 没有对应物"之前，先在同类 handler 里搜一遍。
3. **被否决的优化也要留档。** "方向 2" 的否决理由（拆除职责 / 谓词成本 / 配置驱动的监听器）比实施本身更有价值，
   否则下一轮还会有人重新提出它。

---

## 文档结构变更：DEV_GUIDE 拆成「人类版 + 代理版」（同批，非代码）

**现象**：`DEV_GUIDE_1.21.1.md` 同时承担"给人类的叙事"与"给代理的约束"两种职责。后果是约束散落在长文里，
而代理最需要的「必须/绝不 + 跑什么命令才算完成」没有集中处 —— 本批已经因此重犯过一次同类错误
（把静态结论当运行时结论，§命令树那条）。

**改法**：拆成两份，**事实只在一处定义**，另一份只放指针：

| 文件 | 拥有 |
|---|---|
| **`AGENTS.md`**（新增，代理入口） | 8 条硬约束（每条附血债）/ 确切构建命令 / 任务→文件查找表 / **「完成」判据** / 禁止事项 / 失败取证协议 / 反模式清单 / 代码约定 / 文档义务 |
| `DEV_GUIDE_1.21.1.md`（人类版） | 结构与原理：项目布局、构建为什么这么配、KSP 与 Mixin 教程与例子、四条铁律的**来龙去脉**、坑的经过、诊断命令、命令注册 §10 |

- 两份**互相加了指针**（`DEV_GUIDE` §5 / §10 → `AGENTS.md` §4；`AGENTS.md` 顶部 → `DEV_GUIDE`），
  并写明：**冲突时以 `AGENTS.md` 为准，并顺手修掉 `DEV_GUIDE`**（把"文档漂移"变成一次主动检查）。
- `DEV_GUIDE` §9 文档地图新增 `AGENTS.md` 与 `DEVLOG.md` 两行。
- `AGENTS.md` 的 8 条硬约束**全部来自本仓库真实踩过的坑**，其中三条是本批新增的教训：
  §4.1 命令必须服务端注册（客户端注册会吞掉同名根下的服务端子命令）；
  §4.2 `setSaved(false)` ↔ persistent/temporary 的映射规则（映射反了会永久残留）；
  §4.5 作者会用**位置参数**写 `@ModEnchantment`，按 `id = "…"` 正则扫描会**静默漏扫**。

> **命名说明**：用 `AGENTS.md` 是刻意的 —— 这是跨 harness 的约定文件名，多数代理工具会把它自动读进上下文
> （本次已实测：文件写出后立刻被当作工作区指令载入）。若某工具链认 `CLAUDE.md` / `.cursorrules` 之类名字，
> 做一个指向本文件的软链即可，**不要复制**（复制就会漂移）。
>
> **未做**：`AGENTS.md` 目前是中文。若交接给不读中文的代理，需要一份英文版 —— 但同样应当是**软链/单源**，
> 而不是两份各自维护的翻译。

---

## `build=cleanup22`（2026-10-17 · 一次部署事故 + kill_aura 的充能只花在服务端）

本批两件事，性质完全不同：

| # | 现象 | 性质 |
|---|---|---|
| 1 | 客户端起不来：满屏 `@Mixin target … was not found … from mod simple_tweaks`，最后崩在 `NoClassDefFoundError: net/minecraft/server/world/ServerWorld` | **部署事故**（把 dev jar 装进了 mods），不是代码问题 |
| 2 | kill_aura 自动攻击后，玩家**看不到**自己的攻击充能被清零（准星旁指示条一直满格） | 真 bug：充能是**每侧各自一份**的状态，服务端花了、客户端没花 |

### 1. 事故：`build/devlibs/*-dev.jar` 被当成产物装进了 mods

**现象**（`logs/latest.log`，2026-10-17 00:05:48 起）：启动期逐个报 `Error loading class: net/minecraft/block/AbstractBlock`
+ `@Mixin target net.minecraft.block.AbstractBlock was not found simple_tweaks.mixins.json:… from mod simple_tweaks`，
**25 个注册的 mixin 一个都没绑上**；随后 `ServerEventBridge.register(ServerEventBridge.kt:56)` 抛
`NoClassDefFoundError: net/minecraft/server/world/ServerWorld`，整个 `main` entrypoint 阶段失败。
注意**误导项**：入口类自己的日志照打（`FireFly's Simple Tweaks v2.1.0 loading`、`Config loaded from …`），
因为纯 Kotlin 路径不碰 MC 类 —— 看起来像"某段代码写坏了"。

**证据**（三条独立通道，全部指向"装错文件"）：

1. **哈希相等**：`mods\simple_tweaks-2.1.0-dev.jar` 的 SHA256 `78F3BB82…DEFE` == `build\devlibs\simple_tweaks-2.1.0-dev.jar`；
   而文档指定的产物 `build\libs\simple_tweaks-2.1.0.jar` 是 `C6099475…`（当时页首记的正是它）。
2. **命名空间扫描**：dev jar 里 **0** 个类含 `net/minecraft/class_`（intermediary），**87** 个类含 Yarn 名
   `net/minecraft/entity/LivingEntity`；`build/libs` 那一份反之（`ServerEventBridge.class` 里是 `net/minecraft/class_3218`）。
   `simple_tweaks.mixins.json` 在 dev jar 里**没有** `"refmap"` 键（`remapJar` 才加上）—— 所以生产环境里 mixin
   既拿不到映射、目标名又是 Yarn 名，必然全数 not found。
3. **同环境对照**：`2026-10-16-1.log.gz`（18:22）在同一台机、同一套 mods 上加载 `build=cleanup21` **正常**；
   事故只出现在换 jar 之后的 10-17 00:03 / 00:04 / 00:05 三次启动。

**改法**：装 `build\libs\simple_tweaks-2.1.0.jar`（装完对 SHA256）。文档侧不留活口：

- `DEV_GUIDE` §2.5 重写：三行表格（`libs` / `devlibs` / `sources` 各是什么、能不能装）+ 完整症状清单与取证命令；
- `AGENTS.md` §1 硬规则、§2 查找表各加一条（产物路径）；
- `docs/acceptance-checklist.md` 新增 **0.0**：先核对已安装 jar 的 SHA + 文件名里没有 `-dev`。

**验收**：checklist §0.0（可判定：哈希比对）。

### 2. kill_aura：充能在服务端被花掉，客户端根本不知道

**现象**：`build=cleanup21` 给 kill_aura 接上了 `simulateCooldown`（光环只在挥砍就绪时出手，命中即花掉充能）。
服务端确实花了 —— 但玩家盯着的**攻击指示条始终满格**，于是"指示条说可以打"与"这一刀只有 `0.2 + charge² × 0.8` 的伤害"
同时成立。§Z3 当初就是这么写的（"手动那一刀从 0 蓄力"），只是**画面侧从来没成立过**。

**静态证据（`javap -c`，`minecraft-merged-1.21.1-net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2`）**：

| 字节码 | 结论 |
|---|---|
| `PlayerEntity#resetLastAttackedTicks()V` = `iconst_0 / putfield lastAttackedTicks` | 复位就是把**该对象自己**的 int 置 0 |
| `PlayerEntity#attack(Entity)V`：`getAttackCooldownProgress(0.5F)` → `0.2F + c²×0.8F` | 伤害按各自那份充能算 |
| `MinecraftClient#doAttack()Z`(235)、`ClientPlayerInteractionManager#attackEntity`(35)、`#cancelBlockBreaking`(113) → `resetLastAttackedTicks()` | **客户端那份**只在客户端自己的攻击路径里被清 |
| `InGameHud`(293/388) → `ClientPlayerEntity#getAttackCooldownProgress(F)` | 指示条读的是**客户端那份** |

`lastAttackedTicks` 既不是 tracked 字段也没有同步包，所以"服务端打了这一刀"对客户端**不可见**。
而 `EnchantKillAuraHandler.kt:70` 在客户端直接 `return`（光环只在服务端挥砍）—— 两侧从此分叉。

**改法**（`build=cleanup22`）：

- 新增 S2C 空包 `network/packets/PacketResetAttackCooldown.kt`（`simple_tweaks:reset_attack_cooldown`，
  `PacketCodec.unit`，无 body）；
- `NetworkManager.registerPackets()` 里 `playS2C().register(...)`（漏了这一步运行期**静默收不到**）；
- `util/PlayerUtils.kt` 的 `runPlayerAttack`：**紧挨着**服务端那次 `resetLastAttackedTicks()` 发给自己
  （`if (attacker is ServerPlayerEntity)`）—— 花掉与通知成对出现；六个调用点里只有 kill_aura 传
  `simulateCooldown = true`，其余调用点语义未变；
- `SimpleTweaksClient` 加接收器：清本地玩家充能 + 探针 `[FST-KillAura] outcome=client-charge-spent,
  chargeBefore=…, chargeAfter=…`（`chargeAfter` 不是 0 而是 ≈0.04 —— 公式里那 `+0.5F` 的基准时间还在）。

**构建证据**：`gradlew build` → `BUILD SUCCESSFUL`，**0 行 `e:`**；
`build/libs/simple_tweaks-2.1.0.jar` = `FA1FB85B…F823`（638116 bytes），jar 内
`PacketResetAttackCooldown.class` 已 remap 成 intermediary、`SimpleTweaksClient.class` 里是 `cleanup22`；
已按 §2.5 安装并对过哈希（mods 里只剩一个可加载的 `simple_tweaks-2.1.0.jar`）。

**动态证据**：**无**。代理不跑客户端 —— 见下面的验收编号。
（→ 该 jar 后来被证明**会断连**：作者真机一进图就被踢，见下一节 `build=cleanup23`。）

### 3. 本批「未验收」

| 项 | 为什么未验收 |
|---|---|
| **checklist §AA1–§AA6**（kill_aura 的充能到达客户端） | 需要真实客户端 + 看指示条"清零→重新蓄力"；日志侧只需 `[FST-KillAura]` 两行成对（§M5 开调试开关） |
| checklist §Z1–§Z4 | 本批不改其状态；注意 **§Z3 的"画面侧"语义到本批才第一次成立**（此前只有服务端那一半） |
| checklist §Z5 | 仍无调用点（`triggerEvent = false`） |
| checklist §0.0 | 可判定项，但要在**下一次安装**时执行 |

### 4. 本批的教训

1. **"崩在 `NoClassDefFoundError: net/minecraft/…` + 所有 mixin 目标 not found" 是部署判据，不是代码判据。**
   分水岭在**入口类自己的日志有没有打**：打了 ⇒ 类加载器与模组元数据都没问题，找不到的只是 MC 名
   ⇒ 先查装的是哪个 jar（§0.0），别先翻源码。
2. **每侧各自维护的状态，"写入成功"只完成了一半。** `resetLastAttackedTicks()` 在服务端调用成功
   ≠ 玩家的充能被花掉：客户端读的是**它自己那份** `lastAttackedTicks`。凡是遇到"一个 int / 计时器 / 冷却"的
   影子字段，都要问一句：**另一侧是谁在读、谁在写、有没有同步包**（已写进 `AGENTS.md` §4.11）。
3. **"像玩家手动挥砍"在 1.21 里是两侧的事**（客户端预测 + 服务端判定）。`simulateCooldown` 落地时只写了
   服务端那一半，正是反模式清单里"给出目标状态、只落地一半"的又一例 —— 而它连编译告警都没有，
   只有玩家看得见。

---

## `build=cleanup23`（2026-10-17 · 修 cleanup22 的断连：`PacketCodec.unit` 要单例）

**现象**（作者真机，`logs\latest.log` 00:19:18）：装上 `cleanup22` 后进单人世界，**光环第一次命中**时直接断连：

```
[Netty Server IO #1/ERROR]: Error sending packet clientbound/minecraft:custom_payload
io.netty.handler.codec.EncoderException: Failed to encode packet
    'clientbound/minecraft:custom_payload' (simple_tweaks:reset_attack_cooldown)
Caused by: java.lang.IllegalStateException: Can't encode
    'dev.firefly.simpletweaks.network.packets.PacketResetAttackCooldown@1dde5184',
    expected 'dev.firefly.simpletweaks.network.packets.PacketResetAttackCooldown@306b7db2'
	at knot/net.minecraft.class_9139$7.encode(class_9139.java:57)   ← PacketCodec.unit(value) 的编码器
```

**根因（字节码）**：`PacketCodec.unit(V)` 的编码器**不是"什么都不写"**：

```java
// PacketCodec$7.encode(B, V)
if (!value.equals(this.field_48586)) throw new IllegalStateException("Can't encode '" + value + "', expected '…'");
```

它只接受**注册编解码器时的那一个实例**（本类没覆写 `equals`，所以等价于身份比较）。cleanup22 写的是
`CODEC = PacketCodec.unit(PacketResetAttackCooldown())`，而发送处 `runPlayerAttack` 里是
`PacketResetAttackCooldown()` —— **两个不同实例**，于是编码期抛异常、玩家被踢。

**改法**：`val INSTANCE = PacketResetAttackCooldown()` + **私有构造**，`CODEC = unit(INSTANCE)`，发送用 `INSTANCE`。
私有构造把"又发一个新实例"变成**编译错误**，而不是又一次断连。

**静态证据（javap）**：`PacketResetAttackCooldown.<clinit>` = `new` → `putstatic INSTANCE` → `getstatic INSTANCE`
→ `PacketCodec.unit` → `putstatic CODEC`；`PlayerUtilsKt.runPlayerAttack` 里是 `getstatic Companion` →
`invokevirtual getINSTANCE`（不再是 `new`）。

**构建证据**：`BUILD SUCCESSFUL`、**0 行 `e:`**；`build/libs/simple_tweaks-2.1.0.jar` =
`E0D72D5A…2F5C`（638328 bytes），jar 内标识 `cleanup23`；已安装（mods 里只剩一个可加载的 simple_tweaks）。

**回退点修正**：cleanup22 那份 jar 会断连，**不能**当回退点。已把它改名
`simple_tweaks-2.1.0-cleanup22-BROKEN.jar.disabled`，并把 10-11 那份 cleanup21（640635 bytes，
2026-10-16 18:22 实测正常加载）放回 `.prev`。

**动态证据**：**无**（代理不跑客户端）。验收：checklist **§AA0**（进图不断连 + 连续 ≥15 次发送不断连）
加 §AA1–§AA6。

### 本批的教训

1. **`PacketCodec.unit` 是"单例编解码器"，不是"空编码器"。** 它在编码期做值校验，防止同一个空包存在两个实例；
   所以空 body 的 payload 必须与注册时**同一个对象**（本仓库用 `INSTANCE` + 私有构造把这个约束固化下来）。
   **"空包 = 无状态"是错的推论：无状态 ≠ 无身份。**
2. **这是"三关"的教科书案例**：编译与构建全绿（第一、二关），第三关（动态）一按就断连 —— 而且比静默失效更响。
   凡新增 `CustomPayload`，第一次真机出现必须当必测项（§AA0 就是它的固化形式）。

---

## 仓库布局迁移（2026-10-17 · 非代码改动）

**背景**：`fabric-1.21.1` 分支一直是"在 1.12.2 仓库里塞了个子目录"——
`git diff --shortstat origin/master origin/fabric-1.21.1` 原本是 `212 files changed, 24983 insertions(+), 1 deletion(-)`，
即**分支根目录就是 `master` 的 1.12.2 树**（191 个 `src/` 文件 + `build.gradle` / `gradle.properties` /
`tags.properties` …），Fabric 工程在 `fabric-1.21.1/` 子目录里。后果：在 GitHub 上浏览该分支会看到 1.12.2 源码；
CI（`.github/workflows/deploy.yml`，1.12.2/Forge 模板，跑根 `gradlew`）也在构建 1.12.2。

**改法**：`git filter-branch --subdirectory-filter fabric-1.21.1` **重写该分支历史** —— 14 个提交，
每个提交树里都不存在 1.12.2 文件（逐提交扫 `tags.properties` 等标记，命中 0）；再补一个提交放回
`LICENSE` 与 `README.md`（它们原本在仓库根，被 subdirectory-filter 丢掉了），并把 README 里
指向 `./fabric-1.21.1/MIGRATION.md` 的链接改回 `./MIGRATION.md`。**`master` 与所有 tag 未动**；
旧 tip `146123e` 仍由 tag `v2.1.0` 引用着，需要时可取回。

**本地工作副本**：另建 `FireFly's Simple Tweaks-fabric-1.21.1/`（GitHub 分支的克隆，根 = 工程），
把原 `fabric-1.21.1/` 里那 **105 项未提交改动**搬过去（101 复制 / 5 删除：`STLog.kt` 重命名的旧名 +
4 个已交给 KSP 生成的附魔 JSON），提交为 `9c78ddd`；旧的 `FireFly's Simple Tweaks-1.12.2/` 目录
**原样保留当备份**（那 105 项改动仍在里面）。

**迁移的校验方式（可复用）**：不比字节，比"**会存进对象库的内容**"——
`git hash-object`（应用 `autocrlf` 等过滤器）逐路径比对源/目标（101/101 一致），
再用两侧 `git status --porcelain` 的**路径集合**核对（106 = 106，无遗漏无多余）。
中途曾因源路径前缀拼重（`…\fabric-1.21.1\fabric-1.21.1\…`）导致 101 个文件一个都没复制成功，
正是这步校验当场发现 —— **"复制成功"的日志不算证据，哈希一致才算**。

**布局迁移连带的三件事**（都已随本批改掉）：

1. **文档路径**：`AGENTS.md` §0/§4.7、`DEV_GUIDE` §0 与 §1 目录树、《清单》页首 jar 路径里的
   `fabric-1.21.1/` 前缀全部去掉；1.12.2 对照物改为"同仓库 `master` 分支（`git show master:<path>`）"。
   `docs/phase3-port-spec.md` 属专项考古，只在开头加了一行路径说明，正文不改写（历史保真）。
2. **jar 多带一个 LICENSE**：根目录现在就是工程，`build.gradle` 里那句 `from('LICENSE')`
   **第一次真的找到了文件**（旧布局里 LICENSE 在上一层，被静默跳过）⇒ jar 638328 → **639095 bytes**
   （`9C26255D…`，代码完全相同）。《清单》页首 SHA / 字节数已按新布局更新，并把这笔差异写进解释。
3. **`.gitignore` 覆盖变窄**：`run/`、`.idea/`、`*.iml`、`out/` 以前靠 1.12.2 那份根 `.gitignore` 忽略，
   重写后只剩工程自己那份（`.gradle/`、`build/`、`MIGRATION.md`）⇒ 已补回 IDE / run 的覆盖
   （否则在新目录跑一次客户端，`run/` 就会变成未跟踪文件，有误提交世界存档的风险）。

**踩到的坑（已记入 `DEV_GUIDE` §6 #26）**：把旧 `.idea` 整份拷进新布局后，IDEA 弹
「**检测到未注册的 VCS 根**」—— `vcs.xml` 里的 `$PROJECT_DIR$/..`（旧布局下等于仓库根）在新布局里
指向一个**非 git 仓库**的父目录（实测 `F:\Codes\Mod` 不是仓库），IDEA 于是把父目录注册成 Git 根、
又报告工程目录"未注册"（日志：`VcsRootProblemNotifier` + `VcsRootError{UNREGISTERED_ROOT - …}`）；
同时 `.idea/runConfigurations/Minecraft_{Client,Server}.xml` 里写死了旧目录的绝对路径
（`-Dfabric.dli.config=…\1.12.2\fabric-1.21.1\.gradle\loom-cache\launch.cfg`，点"运行"会去跑旧工程）。
**结论：布局变了就别拷 `.idea`，让它自己重建。**

**状态**：本节的文档 / `.gitignore` 改动先**留在本地**，攒够再 push；`9c78ddd`（WIP 迁移提交）同样未推送。
