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
