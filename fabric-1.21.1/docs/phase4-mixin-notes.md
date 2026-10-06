# 阶段 4 — Mixin 重定向笔记（细节档）

配套 `MIGRATION.md`（主文档只留索引与决策）。本档记录阶段 4 的实测结论、踩过的坑、以及未做项的判定依据。
本档只追加，不改写既有段落。

---

## 1. B-2a 收尾状态（附魔台「重附魔」）

| 项 | 状态 | 证据 |
|---|---|---|
| ① 已附魔物品能进附魔台 | ✅ 已实现并动态验证 | `gateVanilla=false` → `gateMixin=true`，`powers=[2,4,14]`，`redirectCalls=3` |
| ② 附魔书当书处理（`isOf(BOOK)` 重定向） | ✅ 已实现 | refmap: `isOf` → `class_1799.method_31574` |
| ③ 替换而非累加 | ✅ **已实现**（第三次尝试，锚点换成 `applyEnchantmentCosts`） | 见 §2.1；前两次失败记录保留在 §2 |
| ④ 附魔书能出报价 | ❌ 未做，根因已定 | `ENCHANTED_BOOK.getEnchantability()==0`，见 §3 |
| 验收 | **9/9 PASS**（`ReEnchant` 必判项 = ① + 负对照） | 干净服务器单轮 |

---

## 2. change(3)「替换」两次尝试都失败 → 撤回

1.12.2 在 `player.onEnchant(...)` 之后清 `ench` NBT（该调用早于写入），语义是**替换**。1.21 的等效尝试：

| 尝试 | 结果 |
|---|---|
| `@Inject HEAD` on `method_17410` 清空组件 | offset 23-30 是「列表空则 return」的早退点，清空执行了、写入没执行 → **物品被剥光** |
| `@Redirect` on `addEnchantment`，首次写入前清空 | 清空**确证执行**（已附魔剑丢了 `sharpness:3`+`mending:1`），但紧接着对**同一对象**的 `addEnchantment` 没留下任何附魔 |

第二版还留下一个**未解释**的状态：点击后 `enchantmentPower=[0,0,0]`，而槽位 0 里是普通下界合金剑（`isEnchantable()` 应为 true）。
**在有未解释路径的情况下不发布会销毁物品的钩子**，故撤回，保留 vanilla 累加语义。当前 `ReEnchant` 把 `replaceOk` 标为 INFO-ONLY。

> 上面这段是**撤回时**的记录，保留原样。第三次尝试已经成功，见 §2.1；§1 表格里 ③ 的状态已从 ❌ 改为 ✅。

---

## 2.1 change(3) 第三次尝试：成功（锚点 = `applyEnchantmentCosts`）

作者裁定「重复附魔应当覆盖而非叠加」后重做。这一次不是在前两个锚点之间猜，而是先取 `method_17410` 的完整字节码，再找**唯一**满足 1.12.2 语义窗口的位置。

### 锚点：offset 38（`applyEnchantmentCosts` 之后）

```
  23..30    if (list.isEmpty()) goto 240      ← 早退（ifne 240）
  33..38    player.applyEnchantmentCosts(local8, level)   ← 锚点在这里（shift=AFTER）
  41..49    local8.isOf(Items.BOOK)
  52..68    if BOOK { local8 = arg1.withItem(ENCHANTED_BOOK); inventory.setStack(0, local8); }
  73..119   for (entry : list) local8.addEnchantment(...)
```

为什么这个位置是**结构性**安全，而不是"这次大概行"：

| 论点 | 依据 |
|---|---|
| 清空后**不可能**留下空物品 | offset 33 被 offset 30 的 `ifne 240` 支配 ⇒ 回调只在 `list` **非空**时运行（这正是第一次尝试失败的模式） |
| 不可能重演第二次尝试的失败 | 写入循环 73..119 **完全没有被动过**，vanilla 写入路径原样 |
| 不会影响本次报价 | `generateEnchantments` 在 offset 18 运行，**早于**回调；清空动不了已生成的列表 |
| `local8 == arg1`（非书） | 清第一个参数＝清写入循环要写的那个对象；书的分支在回调之后才 `withItem`，复制出来的新对象继承已清空的组件 |

### 与第一次尝试记录的差异（诚实标注）

§2 说第一次尝试「清空执行了、写入没执行」——那句描述是准确的。但由此推出的「报价生成依赖已有附魔」这一**机制**从未被证实，本次也没有复现：回调在 offset 38，生成早已完成。因此本次不再依赖那个推测。

### 可观测性

每次替换打一行日志（`simple_tweaks/enchant_table`）：

```
[enchant-table] re-enchant on minecraft:netherite_sword: replaced 2 existing enchantment(s)
```

这个 seam 已经错了三次，"钩子没跑"和"钩子跑了但 vanilla 仍在累加"从玩家侧看起来一模一样。另外本 mixin 声明 `defaultRequire: 1`，锚点写错会在加载时硬失败，所以日志只需要覆盖"锚点解析了但分支没走到"这一种残留情况。

### 验收判据

| # | 期望 |
|---|---|
| J1.1 | 带 `sharpness:3`+`mending:1` 的剑重新附魔 → **原有附魔消失**，只剩新的一套 |
| J1.2 | 再附一次 → 仍然只有一套，不累积 |
| J1.3 | 日志出现上面那行 |
| J1.4 | 未附魔物品附魔 → 正常，且**没有**日志行（无旧附魔可替换） |

---

## 2.2 change(3) 第四次尝试成功 —— 根因是 `remove`，不是锚点

§2.1 声称"锚点选对就结构性安全"，**那个论断是错的**，已由作者实测推翻：第四次的结果与第 1、2 次**症状
完全相同**（附魔被删、且之后再也附不了）。既然锚点在窗口内、写入循环一行未动，共同因素就只剩"清空"本身。

`ItemStack#addEnchantment` → `EnchantmentHelper.apply(ItemStack, Consumer)`，其字节码第 14..21 行是：

```
component = stack.get(type);
if (component == null) return ItemEnchantmentsComponent.DEFAULT;   // 静默空操作
```

所以 **`remove()` 之后所有写入都被丢弃**。改为 `set(ItemEnchantmentsComponent.DEFAULT)`（组件存在但为空）
即恢复写入；组件类型需按 `getEnchantmentsComponentType` 镜像（附魔书用 `STORED_ENCHANTMENTS`）。

完整根因、字节码证据与三处修复见 `phase6-client-notes.md` §26。**教训：同一个症状在三个不同锚点上重复出现，
说明被怀疑的变量不是锚点。** §2.1 的表格保留原样作为记录。

---

## 3. 附魔书根因（未做，判定依据）

`Items.ENCHANTED_BOOK.getEnchantability() == 0`，而 `EnchantmentHelper.generateEnchantments` 的第一段就是：

```
 13: invokevirtual Item.getEnchantability:()I
 18: iload 6
 20: ifgt 26          ← <= 0 直接跳过
 23: aload 4
 25: areturn          ← 返回空列表
```

所以书的报价恒为 0，且 `calculateRequiredExperienceLevel` 读同一个值。1.12.2 用
`MixinEnchantmentHelper.firefly$bookEnchantability`（`getItemEnchantability` 重定向：`<=0 && ENCHANTED_BOOK → 15`）修它。
1.21 落点需先对 `Item.getEnchantability()` 做 override 审计（铁律二）。

---

## 4. ⚠️ `levels=-1` —— 我连续判断错两次，靠运行时快照才纠正

**现象**：`/sttest enchant` 每次报 `levels=[-1,-1,-1]`。
**第一次错**：当成「走了清零分支」（闸门失败）。
**第二次错**：当成「生成器返回空」的可靠信号（随后又被自己否定为「完全不是信号」）。

**真相**（分阶段运行时快照，非字节码推断）：

| 阶段 | `enchantmentPower` | `enchantmentId` | `enchantmentLevel` | seed |
|---|---|---|---|---|
| ctor | `[0,0,0]` | `[-1,-1,-1]` | `[-1,-1,-1]` | 2017543814 |
| setSlot0 | `[1,2,5]` | `[7,78,78]` | `[1,1,1]` | 2017543814 |
| contentChanged | `[1,2,5]` | `[7,78,78]` | `[1,1,1]` | 2017543814 |
| clicked | `[0,0,0]` | `[-1,-1,-1]` | `[-1,-1,-1]` | **-1377592018** |

**结论：`-1` 是「点击之后」的状态**，不是任何失败信号。`method_17410` 结尾会
`seed.set(player.getEnchantmentTableSeed())` 再调一次 `onContentChanged`，把三个数组整个重置。
而 `setSlot0` 阶段 `L[1,1,1] I[7,78,78] P[1,2,5]` 说明**报价生成一直正常**。

**教训**：探针读的是「我以为的那一刻」的值。**当观测值与模型矛盾且反复纠正不清时，改用分阶段运行时快照，停止字节码推断。**
顺带：`seed` 变化 + `lapis 3→2` 是「`method_17410` 跑到底」的可靠证据（两者都在 add 循环之后）。

---

## 5. 无 yarn 名字的私有方法照样能 mixin

`EnchantmentScreenHandler.method_17410` / `method_17411` 在 yarn 1.21.1+build.3 **没有名字**（javap 打印 intermediary）。
Mixin 仍可注入：refmap 步骤对未映射名**原样保留**，运行时类里就是这个名字。

```json
"method_17410(...)V": "Lnet/minecraft/class_1718;method_17410(...)V"
"generateEnchantments(...)": "Lnet/minecraft/class_1718;method_7637(...)"
```

写 target 时用完整签名（含描述符）即可，不需要 `@Shadow`。

---

## 6. 阶段 4 清单的依赖分类（🟢 标的其实是机制难度，不是就绪度）

| 旧 mixin | 服务的功能 | 1.21 状态 | 结论 |
|---|---|---|---|
| `MixinEntitySetDead` / `MixinWorldRemoveEntity` | InfinitePower | 未移植 | 🚫 做了是死代码 |
| `EntityArrowPiercingMixin` + `IPiercingArrow` | PiercingArrow | 在 `deferred/` | 🚫 同上 |
| 3 个 accessor | — | 1.21 已公开或无调用者 | ⚪ 无需移植 |
| `MixinEntityPlayer` | `attackCharge` 捕获 | 本阶段已覆盖 | ✅ 完成 |
| `MixinContainerEnchantment` | 重附魔 | ① ② 完成；③ 撤回；④ 待做 | 🟡 见 §1 |
| `MixinEnchantmentHelper` (B-2c) | 自定义权重 / 最小附魔条数 / 预览确定性 | 5 条注入里 **3 条要注入的方法在 1.21 不存在** | ⏸ **缓**，理由见 §7 |
| `MixinContainerEnchantmentPreview` | 预览确定性 | 依赖已删除的 `buildEnchantmentList` | ⏸ 随 B-2c |
| `MixinContainerRepair` / `MixinGuiRepair` | 祛魔台 | 依赖未移植的 `DisenchanterLogic` + 客户端 GUI | ⏸ 阶段 6 |
| `MixinEntityPlayerSP` / `MixinMinecraft` / `MixinEntityRenderer` / `MixinNetworkManager` | 客户端事件 | 依赖未移植的 `EventDispatcher` / `NoFov` | ⏸ 阶段 6 |

---

## 7. B-2c 缓做理由（数据层已支持核心功能）

- 生成的 JSON 已写 `max_cost = {base:65535, per_level_above_first:0}` + 每条附魔自己的 `weight`
- `tagEntries=87` = vanilla 35 + **mod 52** ⇒ `non_treasure.json` 的 `replace:false` 追加生效，**52 个 mod 附魔全部进入附魔台候选集**
- 所以「附魔台能出 mod 附魔」已由数据层达成；B-2c 只是补「自定义权重 / 最少附魔条数 / 预览确定性」的代码层，且要重写 3 处已消失的 API
- 成本估计 ≥ 一次完整切片，收益低于阶段 5/6

---

## 8. ⚠️ 一次被作废的验收（陈旧 JVM + 日志轮转）

**现象**：一轮 9 用例跑出 6/9，三个「失败」的伤害数字与通过时**完全一致**，只是 `[ST-*]` 抓不到。

**真因**：`job_kill` 停掉了 Gradle 任务，但**游戏 JVM 没死**。新服务器启动时被世界锁挡住：

```
Failed to start the minecraft server
java.io.IOException: 另一个程序已锁定文件的一部分，进程无法访问。
    at ...SessionLock.create ...
```

于是：① 验收脚本连上的是**旧 jar 的旧服务器**；② 新服务器启动失败时**轮转掉 `latest.log`**，正好落在套件中间，导致按字节偏移读取错位。

**已加两道守卫**：

| 守卫 | 位置 | 作用 |
|---|---|---|
| 日志轮转检测 | `Get-NewStLines`：`latest.log` 长度 < 请求偏移时 **throw** | 不再静默读到别的用例的行 |
| 服务器新鲜度检测 | 跑套件前断言 `Done (` 行时间 ≥ 启动时间 | 避免连到陈旧服务器 |

**操作规则**：每次杀完服务器**必须确认 `Get-Process java` 为空**再启动下一个。

## 9. 撤回两个「原版粒子抑制」mixin（选项语义搞错了）

**做了什么**：为 `GeneralConfig.cancelVanillaDamageIndicator` 忠实移植了 1.12.2 `MixinEntityPlayer` 里那段
`@Redirect WorldServer#spawnParticle`，拆成两个 mixin：

| mixin | 钩子 | 状态 |
|---|---|---|
| `PlayerEntityAttackParticleMixin` | `@Redirect` `PlayerEntity#attack` 内的 `ServerWorld#spawnParticles`（DAMAGE_INDICATOR） | **已删** |
| `PlayerEntityCritParticleMixin` | `@Inject HEAD cancel` `addCritParticles` / `addEnchantedHitParticles`（`PlayerEntity` + `ServerPlayerEntity`） | **已删** |

两个都**通过了静态+构建+动态三关**（refmap 解析成功、注入生效）。**问题不在实现，在语义。**

**真因**：作者澄清该选项要控制的是**本模组自己的伤害指示器**，不是原版粒子。而 1.12.2 全树粒子清单证明：

| 候选 | 事实 |
|---|---|
| `damageindicator/*`（5 文件 531 行） | **不生成任何粒子**（`particle=False, packet=True`）——`onLivingHurt` 只记录伤害并 send `PacketDamageIndicator`，`DamageIndicatorRenderer` 用 `font.drawString` 画**飘字数字** |
| `EnchantDelayedRecoveryHandler:89` | **全项目唯一** `EnumParticleTypes.HEART` —— 是延迟回血的**治疗**反馈 |
| `MixinEntityPlayer` 的重定向 | 在 `attackTargetEntityWithCurrentItem` 内 → 关的确实是**原版**攻击粒子 |

所以「按掉血量生成爱心粒子」这件事**在 1.12.2 代码里不存在**；作者记的是**预期行为**，而 1.12.2 的实现
做成了「关原版粒子」。移植版按代码做，反而是错的。

**处置**：删两个 mixin、从 `mixins.json` 移除（15 → 13）、`GeneralConfig` 保留该 key 但**标注为空开关**，
留给阶段 6 的伤害指示器移植接线（删 key 会破坏已有配置文件）。选项现在**只被配置读写引用**，无其他消费者。

**教训（与本项目已记录的同类失败第三次复发）**：*「映射看起来对、语义是错的」*。前两次是 mixin 目标覆盖、
同优先级监听器顺序；这次是 `alive`/`wasDeath` 反转与选项语义。**代码读得再对，也不能替代问一句「这个选项本来要干什么」。**

> **第 4 次（同一轮内紧接着发生）**：我把 `Entity#kill()` 的字节码结论直接套到 `/kill <player>` 上，
> 但 **`LivingEntity#kill()` 是顶层重写**，走完整的伤害→`onDeath`→掉落 管线。详见 `MIGRATION.md` 陷阱 8。
> 结论：**override 审计不是 mixin 专属动作** —— 只要结论依赖"某个方法在某个类上的行为"，
> 就必须先确认实际 dispatch 到哪个类。

## 10. ⚠️ Kotlin KDoc 里的 `/*` 会吞掉整个文件（第 2 次踩）

给 `GeneralConfig` 写 KDoc 时写了 `` `damageindicator/*` ``，其中 `/*` **开启了一个嵌套块注释**
（Kotlin 块注释可嵌套，与 Java 不同），永不闭合 → 整个 `object GeneralConfig` 被吞掉，
报错是 **9 个字段全部 `Unresolved reference`**（`cancelVanillaDamageIndicator`、`maxAnvilCost`…），
看起来像"配置类没编译"，实际是注释语法。

**第 1 次**是 `enchantment/*.json` 写进 KDoc，导致约 30 个 `Unresolved reference 'ModEnchantmentKeys'` 级联。

**判据**：一个文件里**大面积、跨字段**的 `Unresolved reference`，优先怀疑**注释未闭合**，而不是依赖或符号问题。
