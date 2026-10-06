# InfinitePower —— 已移植（核心 + 光环 + 激光；背包已舍弃）

> 状态（2026-10-06 收尾）：**已交付**。§3 的「直接置血、绕过伤害管线」与 §5.4 的「不 cancel」方案
> 就是实现的直接依据，§5 的四个坑全部规避；实测通过的项目见 `acceptance-checklist.md` 的 L 组。
>
> | 部分 | 状态 |
> |---|---|
> | 秒杀 / 末影龙 / AOE / 掉落收集 / 粒子 | ✅ 已实现并实测通过（§7、§8） |
> | 无敌、免死、万能工具、飞行光环、灵魂绑定 | ✅ 已实现并实测通过 |
> | 激光（触发 + C2S 包） | ✅ 已实现并实测通过（§9） |
> | **背包** | ❌ **8 次尝试后舍弃**（§10 设计、§11 结论） |
> | 掉落翻倍（原 `DropHandler`） | ❌ 作者要求舍弃，源码已删 |
>
> 原始报告：拿带 `infinite_power` 的剑打**监守者打不死**。
>
> 文件名里的 `-deferred` 是历史原因（它最初是一份"缓做"记录）；内容已按收尾状态更新，
> 保留该文件名是为了不让 `MIGRATION.md` 与源码 KDoc 里的十来处引用失效。

## 1. 结论速览

| 问题 | 答案 |
|---|---|
| `EnchantInfinitePowerHandler` 移植了吗？ | **没有**。1.21.1 里只有名字颜色类 `InfinitePowerRainbow.kt` |
| 秒杀为什么失效？ | 1.21.1 里**根本没有这段逻辑**，与减伤/免疫无关 |
| `min_cost=65535` 有影响吗？ | 没有。它是原设计（正常玩法滚不出来），`/give` 直接给无影响；秒杀逻辑也不看它 |
| 缺背包 GUI 导致秒杀失效吗？ | **不是**。两者是同一附魔的**独立功能**，只是当初被一起缓做 |
| 监守者的减伤挡住了 `Int.MAX_VALUE` 吗？ | **方向相反** —— 原实现根本不走伤害管线 |

## 2. 体量

| | 1.12.2 |
|---|---|
| 主 handler | `enchantments/handlers/mythic/EnchantInfinitePowerHandler.kt`（**367 行**） |
| 子包 | `handlers/mythic/infinitepower/` **12 个文件** |
| 1.21.1 现状 | 以上**全部不存在**；`infinite_power.json` 的 `"effects": {}` 为空 |

子包清单：`ForgeHandler`、`ClientHandler`、`FlightHandler`、`ToolHandler`、`SoulBindHandler`、
`InfiniteContainerHandler`、`DropHandler`、`SaveHandler`、`PacketLaser`、`InfiniteBagInventory`、
`ContainerInfiniteBag`、`GuiInfiniteBag`。

缓做记录见 `core/EnchantmentManager.kt` 的 batch-4 KDoc。

## 3. 1.12.2 的秒杀语义：**直接置血，绕过整个伤害管线**

```kotlin
target.health = 0f
target.isDead = true
target.onDeath(DamageSource.causePlayerDamage(player))
```

**没有调用 `damage()`** —— 所以装甲、抗性、`damage_immunity` 一律碰不到它。这是监守者问题的关键：
**原设计已经绕过了减伤，不需要"额外绕过"。** 反过来说，如果移植时图省事写成"造成 `Int.MAX_VALUE` 伤害"，
那才会真正进入伤害管线、被监守者的减伤/免疫吃掉 —— 那才是错的做法。

同一函数还包含：

- **末影龙特例** `killDragon`：置血 + `deathTime=199` + `PhaseList.DYING` + `fightManager.processDragonDeath` + `removeEntityDangerously`
- **2 格内掉落自动拾取**（`DROP_COLLECT_RANGE = 2.0`，`player.inventory.addItemStackToInventory`）
- **AOE 连秒**：2×3×2 AABB 内除自己与目标外的所有 `EntityLivingBase`
- **死亡粒子**（END_ROD / DRAGON_BREATH / ENCHANTMENT_TABLE / FIREWORKS_SPARK / NOTE）+ `ENTITY_GENERIC_EXPLODE`

**触发点**：`MixinEntityPlayerAttack` 在 `attackTargetEntityWithCurrentItem` 的 **HEAD**、`cancellable`。
条件：`!world.isRemote` + 主手该附魔 `level > 0` + `target is EntityLivingBase`
→ `handleAttack(...)` 然后 **`ci.cancel()`**。

> 好消息：这个接缝在 1.21.1 **已经存在**（`PlayerEntityAttackMixin` 就是同一位置），所以最小切片的接缝成本为 0。

## 4. `Int.MAX_VALUE` 是**另一条路径**，且对玩家攻击本来就不生效

`Int.MAX_VALUE` 出现在 `EnchantInfinitePower.itemExtraDamage()` → `calcDamageByCreature(level, creatureType)`。
Forge 的 `calcDamageByCreature` 走 `EnchantmentHelper.getModifierForCreature`，那是**生物持械攻击**的加成通道；
**玩家攻击走 `getDamageBonus`**。

因此 1.12.2 里玩家拿这把剑**并不会**因为 `Int.MAX_VALUE` 而秒杀，真正生效的只有 `handleAttack` 的置血。
这也说明 `infinite_power.json` 的 `"effects": {}` **是保真的，不是漏生成**。

## 5. ⚠️ 移植时必须处理的四个坑（本轮已查明，均未实现）

1. **`ci.cancel()` 会跳过攻击冷却重置。**
   `PlayerEntity.attack` 是在 HEAD **之后**才重置攻击冷却计时的；在 HEAD 取消 ⇒ 冷却永不重置
   ⇒ 每刀都按满蓄力结算 ⇒ 等于无限连击。1.12.2 同样如此，属于**原版遗留缺陷**，不是可以照抄的部分。

2. **`ci.cancel()` 会泄漏暴击接缝状态。**
   `PlayerEntityAttackMixin` 的 `simpletweaks$endCrit` 挂在 `@At("RETURN")`；方法被 cancel 后**不会执行**
   ⇒ `EventSeams.critActive()` 永远为真 ⇒ **后续所有攻击的暴击结算被污染**。
   若要移植，**必须在 `ci.cancel()` 之前手动 `EventSeams.endCrit()`**。

3. **若同时给 JSON 补 `minecraft:damage` 巨量加成，会与手动秒杀重复致死。**
   顺序：管线先按附魔算伤害 → 目标已死、掉落已生成 → 手动 `onDeath` **再来一次** ⇒ 双倍掉落 / 重复死亡事件。
   注意 1.12.2 **没有**这个问题，因为那条路径对玩家攻击从未生效 —— 把它补进 JSON 等于
   **新引入一条 1.12.2 里从未生效的路径**，属于本项目反复记录的「看着对、语义错」。

4. **一个规避全部坑的做法（倾向方案，需实测确认）**：**不 cancel**。
   在 HEAD 做手动秒杀，但**保留 `isAlive` 守卫**；目标死后 `LivingEntity.damage` 对 `!isAlive` 直接
   `return false`（无害空操作），攻击冷却正常重置，暴击接缝的 RETURN 也照常执行。
   代价：偏离 1.12.2 的 `cancel` 语义（其余攻击附魔会在"已死"目标上空跑一次）。
   `DAMAGE_INDICATOR`/音效等副作用需实机确认。

## 6. 可选范围（用户曾在 (B) 与 deferred 之间改判，最终选 **deferred**）

| 选项 | 范围 | 体量 | 依赖 |
|---|---|---|---|
| (A) 最小切片 | 秒杀 + 末影龙 + 掉落收集 + AOE + 粒子/音效 | ~230 行，1 handler | 无（接缝已有） |
| (B) 全量 | A + 激光 + 飞行/工具/灵魂绑定/容器/存档 + 背包 GUI | 367 行 + 12 文件 | 激光→**阶段 5**；GUI→**客户端**（批 6 已砍） |
| (C) 不做 / deferred | — | 0 | — |

**本次决定：deferred。** 将来重启时建议从 (A) 开始，并优先采用 §5.4 的"不 cancel"方案。

---

## 7. 2026-10-06 实际移植结果（`build=infpower1`）

作者改判为 **(B) 全量**，但实际交付是 **(A) 超集 + (B) 的一部分**，因为 (B) 里有两块各自卡在一个
**尚不存在的接缝**上。已交付：

| 文件 | 1.12.2 | 说明 |
|---|---|---|
| `mythic/EnchantInfinitePowerHandler.kt` | 367 | 秒杀 / 末影龙 / 2 格掉落收集 / AOE / 5 圈死亡粒子 / `fireLaser` 全文（含激光粒子） |
| `infinitepower/ForgeHandler.kt` | 51 | 无敌 + 免死 + 伤害归零 |
| `infinitepower/ToolHandler.kt` | 28 | 万物可采 + 瞬间挖掘 |
| `infinitepower/FlightHandler.kt` | 64 | 飞行 / 夜视 / 饱食 / 耐久 / 攻速 |
| `infinitepower/SoulBindHandler.kt` | 61 | 灵魂绑定 |
| ~~`infinitepower/DropHandler.kt`~~ | ~~71~~ | **作者要求舍弃，源码已删** |

**四个坑的处置**（§5）：① 与 ② 由「不 cancel」一并消除 —— 击杀挂在 `PlayerEntityAttackMixin` 的 HEAD，
但**不取消**，所以攻击冷却照常重置、`@At("RETURN")` 的 `EventSeams.endCrit()` 照常执行。
③ 从未引入：JSON 里的 `"effects": {}` 保持为空，没有补 `minecraft:damage`。④ 即本次采用的方案。

**与 §3 的两处有意偏离**：
- 灵魂绑定**不再序列化 NBT**。`PlayerDropsEvent` 在死亡掉落算完之后、实体丢弃之前触发，而
  `ServerPlayerEntity#copyFrom` 会把背包带进重生后的玩家 —— 所以把物品放回 `inventory` 就够了，
  比 1.12.2 少一整套存档机制。
- 末影龙**不再显式调用** `fightManager.processDragonDeath`。1.21 里那个字段不是公开的，也不需要：
  `EnderDragonFight#tick` 自己会检测 `dragon.isDead()` 并走出口传送门流程；1.12.2 显式调用是在绕开
  `removeEntityDangerously` 的竞态。

**仍未做的两块，各自缺的接缝**：

| 缺块 | 卡在哪 |
|---|---|
| 背包（`InfiniteBagInventory` / `ContainerInfiniteBag` / `GuiInfiniteBag` / `InfiniteContainerHandler` / `SaveHandler`，共 483 行） | 需要注册 `ScreenHandlerType`、一个 `ExtendedScreenHandlerFactory`（客户端要能构造 handler）、以及玩家级持久化。三处都是新接缝，不是改名能过的 |
| 激光触发 + 包（`ClientHandler` + `PacketLaser`，共 73 行） | 需要 `PlayerInteractEvent.LeftClickEmpty`（`compat/event/PlayerInteractEvent.kt` 自己注明"尚未提供"）与一个 C2S 包。`fireLaser` 本体已移植完整，接缝一到位就能接上 |

**未验证的风险点**：`ForgeHandler` 的免死依赖取消 `LivingDeathEvent`，因为 1.21 的死亡标志位是
**protected** 的 `LivingEntity.dead`，没有公开 setter —— 1.12.2 的 `isDead = false` 无处可写。
若实测发现"取消后仍然死亡"，需要给该字段加一个 accessor mixin。

---

## 8. 首次实测结果（`build=infpower1`）+ 末影龙修复（`build=infpower2`）

作者实测：**击杀特效 ✅、秒杀 ✅、免疫伤害 ✅、挖掘 ✅、灵魂绑定 ✅**，唯一失败项是**末影龙无法秒杀**。

### 8.1 根因：注入点在原版"解包龙部位"**之前**

日志与字节码互相印证。日志里 `[ST-InfinitePower] … outcome=one-shot` 对每一只普通怪都出现，
**对龙一行都没有** —— 说明 `handleAttack` 对龙从未被调用，不是"调用了但没打死"。

`PlayerEntity.attack(Entity)` 在 offsets **1052..1062** 才做解包：

```
1052: instanceof  EnderDragonPart
1059: checkcast   EnderDragonPart
1062: getfield    EnderDragonPart.owner:Lnet/minecraft/entity/boss/dragon/EnderDragonEntity;
```

而击杀钩子挂在 **HEAD**，即解包**之前**。`EnderDragonPart extends Entity`（`javap` 已确认），
**不是** `LivingEntity`，所以 `target instanceof LivingEntity` 守卫直接 return。**与激光无关。**

**这在 1.12.2 里也是同一个 bug**：那里的 `EntityDragonPart` 同样不是 `EntityLivingBase`。所以
`killDragon` 当年虽然存在，近战却永远走不到它 —— 只有激光（沿路径扫描 `EntityLivingBase`）能碰到龙本体。
因此本次是**修 bug**，不是偏离。

修法：在 HEAD 里先按原版同样的方式解包 `EnderDragonPart → owner`，再进守卫。

### 8.2 连带修改：不再立刻移除龙实体

1.12.2 在置血之后做了四件事：`deathTime = 199`、`PhaseList.DYING`、
`fightManager.processDragonDeath(dragon)`、`removeEntityDangerously(dragon)` ——
它是**手动**开出口传送门，然后才移除实体。

本次不调用 `processDragonDeath`（1.21 里该字段不公开，也不需要：`EnderDragonFight#tick` 自己检测死亡
并走传送门流程）。**正因为如此，立刻 `remove` 必须一起去掉** —— 留着它就会跳过龙本体的死亡后 tick，
而没有任何别的东西会去开传送门。现在只留 血量 0 + `phase = DYING`，其余交给原版。

**jar**：`build=infpower2`。

---

## 9. 激光（`build=laser1`）

`infinitepower/PacketLaser.kt` + 新增 `client/InfinitePowerLaserClient.kt`。`fireLaser` 本体早在 §7 就已移植，
这次补的是**触发**与**通路**。

**这是本项目的第一个 C2S 包**（其余三个都是 S2C）。有一处**有意删掉的字段**：1.12.2 的线格式是
`int playerId` + 3 个 double。三个 double 原序保留，但 `playerId` **不保留** —— Forge 的 `MessageContext`
不携带服务端玩家，所以原版只能把射手的 `entityId` 发上来再用 `getEntityByID` 解析，这同时意味着
**客户端可以冒充任意 entityId**。Fabric 的 `ServerPlayNetworking` 上下文直接给出权威的发送者，
所以那个字段既冗余又是伪造入口。

**`LeftClickEmpty` 的复现方式值得记一笔。** Forge 的 `LeftClickEmpty` 在本项目里标注为"没有 Fabric 等价物"，
所以这个文件自己复现它。看似显然的做法 —— `attackKey.wasPressed()` —— **不可用**：同一次 tick 里
`MinecraftClient#handleInputEvents` 会用 `while (attackKey.wasPressed()) doAttack()` 把它**抽干**，
到 `END_CLIENT_TICK` 时计数已经归零。所以改用 `isPressed()` + 前一 tick 状态做边沿检测。
准星条件是 `MISS`（或没有命中）—— 敲方块也会被排除，与 Forge 一致。

服务端收到后**重新校验**附魔并自行重算射线，且显式 `server.execute {}` 派发到服务端线程
（`fireLaser` 要杀实体、生成粒子），不依赖 Fabric"处理器已在游戏线程"的保证。

---

## 10. 背包（`build=bag2`）

| 文件 | 1.12.2 | 说明 |
|---|---|---|
| `infinitepower/InfiniteBagInventory.kt` | 147 | `IInventory` → 1.21 `Inventory`；**另有 `InfiniteBagStore`** |
| `infinitepower/ContainerInfiniteBag.kt` | 103 | `Container` → `ScreenHandler`；含 `ScreenHandlerType` 注册与开启 |
| `infinitepower/InfiniteContainerHandler.kt` | 93 | 潜行右键开启 + 跨重生复制 |
| `client/GuiInfiniteBag.kt` | 200 | `GuiContainer` → `HandledScreen` |
| `mixin/PlayerEntityBagDataMixin.java` | — | **新增**：NBT 持久化 |

### 10.1 持久化：1.21 没有 `getEntityData()`，所以换了一种机制

1.12.2 把背包写进 Forge 的 `player.getEntityData()`（公开的持久化 per-entity NBT）。1.21 没有等价物 ——
玩家数据只在 NBT 回调内部可达。所以数据存在 `InfiniteBagStore`（`WeakHashMap<PlayerEntity, …>`，
弱键所以不需要清理），由 `PlayerEntityBagDataMixin` 在
`writeCustomDataToNbt` / `readCustomDataFromNbt` 的 **TAIL** 拷进拷出。

`javap` 已确认这两个方法**由 `PlayerEntity` 自己声明且是 public**（§5 那套 override 审计在这里没有歧义）。
TAIL 而非 HEAD，因为 `InfiniteBagStore` 解析注册表项需要 `player.world.registryManager` 已就绪。

**这顺带让 1.12.2 的 `SaveHandler` 整个消失** —— 它唯一的职责就是 `PlayerEvent.SaveToFile` 时 `markDirty()`。

**物品序列化换了格式**：1.12.2 手写 `Slot`/`Id`/`Count`/`Damage`/`tag`；1.21 的物品是组件化的，
那四个字段**表达不了组件**，所以每个堆叠由 `ItemStack.CODEC` 编码成一个 `Item` 元素。`Slot` 保留 ——
背包是**稀疏**的，只写非空槽正是 54 行背包在近乎空的时候依然便宜的原因。
必须用 `RegistryOps`：附魔/药水/属性修饰符等组件会解析注册表项，用裸 `NbtOps` 会**静默失败**。

### 10.2 容器：两个 1.12.2 覆写**有意不移植**

1. **`mergeItemStack`**：1.12.2 手写它是因为 Forge 默认忽略自定义槽位上限。1.21 的
   `ScreenHandler#insertItem` 已经会查 `Slot#getMaxItemCount(stack)`，再抄一遍等于制造一个会漂移的第二份实现。
2. **`getSlotUnderMouse`**（GUI，35 行）：它重写命中测试**只是**为了让被搜索过滤掉的槽位不可悬停。
   这里那些槽位被停在 `(-1000,-1000)`，而 `HandledScreen#getSlotAt` 本来就做边界检查并跳过它们。
   同理 `renderHoveredToolTip` 也不需要。

开启界面不需要 `ExtendedScreenHandlerFactory`：两侧都从**玩家**构造 handler
（客户端 `playerInventory.player` 就是客户端玩家，其背包槽位由原版 screen-handler 同步填充）——
这正是 1.12.2 的 `IGuiHandler` 的做法。

### 10.3 ⚠️ 两个已知风险，请重点实测

| 风险 | 说明 |
|---|---|
| **"无限堆叠"可能存不下来** | 槽位上限是 `Int.MAX_VALUE`，但 1.21 的 `ItemStack.CODEC` 对 `count` 有 99 的约束。若成立，>99 的堆叠在**保存/重载后会缩到 99**。这是该功能的核心卖点，取样时请专门试一个 1000 个的堆叠，然后退出重进 |
| **同步开销** | handler 有 486+36+9 = **531 个槽位**，而 1.21 的 `ScreenHandler#syncState` 把整张槽位表打成**一个** `InventoryS2CPacket`。1.12.2 用的是逐槽更新。对一个近乎空的背包来说这是真实的更新成本退步；接受它是因为虚拟化槽位表会改变槽位**索引**，而那正是这个容器两边必须一致的东西 |

### 10.4 未移植

1.12.2 的 `InfiniteContainerHandler.onLivingDrops`（击杀掉落直接进背包）需要 `LivingDropsEvent`，
本项目没有该接缝（`compat/EventUtils.kt` 自己列出）。加上 `DropHandler` 已按作者要求舍弃，
**整个移植里现在不存在任何掉落拦截** —— 所以这里没做的都是"缺功能"，不是"坏了"。

**jar**：`8DDD5DC26ADEED67` / 535616 bytes，启动行 `build=bag2`。

---


## 11. 背包被舍弃（原 §11–§17 的逐次排查已按作者要求删除，以下为摘要）

作者在 **8 次尝试**后裁定**整体舍弃背包**（`build=infpower3`）。源码保留在
`src/main/kotlin/**/infinitepower/disabled/`、`src/main/kotlin/**/client/disabled/`、
`src/main/java/**/mixin/disabled/`，但**不注册、不应用** —— 等于从构建中移除。
玩家存档里的 `InfiniteBagItems` NBT 保持不动（读写它的 mixin 已注销，只是一段惰性数据）。

### 11.1 实现了什么

483 行、基于 1.21 `ScreenHandler` + 槽位同步的可滚动/可搜索容器：

| 文件 | 作用 |
|---|---|
| `InfiniteBagInventory` + `InfiniteBagStore` | `Inventory` 实现；`WeakHashMap<PlayerEntity, MutableList<ItemStack>>` 作为运行期存储，`ItemStack.CODEC` + `RegistryOps` 做序列化 |
| `ContainerInfiniteBag` | 486 个背包槽 + 玩家 36 槽；`ScreenHandlerType` 注册与 `openHandledScreen` 开启 |
| `GuiInfiniteBag` | `HandledScreen`：6 行窗口滚动、搜索框、`Slot.x/y` 每帧重定位 |
| `InfiniteContainerHandler` | 潜行右键开启 + 跨重生复制 |
| `PlayerEntityBagDataMixin` | 在 `PlayerEntity#read/writeCustomDataToNbt` 的 TAIL 读写 NBT |

**滚动、渲染、堆叠上限、NBT 持久化这四项都验证通过**；失败的是下面的同步一致性。

### 11.2 无法解决的一环

**客户端与服务端的背包内容始终无法稳定一致。** 最终观测到的形态：

- **槽位错位**：服务端的背包由 NBT 还原（低位槽已被占用），客户端从空开始，
  于是同一批物品被放进客户端的 slot 0 与服务端的 slot 1；
- 客户端点"取出"时**服务端那一格是空的**，预测被回滚 —— 玩家看到的是**物品凭空消失**；
- 反复放入看起来像**复制**（64 → 128 → 256），因为客户端把自己那套预测与服务端那套各留了一份；
- 服务端的格子会**自己变空**（64 → 空），而服务端对该格子的两个写入者
  （`removeStack` 与 `setStack`）**都没有留下日志** —— 这一条到最后仍未解释。

修过并验证**无效**的方向（每一项本身都是真实修复，但没有一项解决分歧）：

| 尝试 | 结果 |
|---|---|
| 给 `removeStack` / `markDirty` 补探针 | 确认"服务端没取出"，但没解释为什么 |
| `markDirty()` 恢复同步通知职责（listener → `sendContentUpdates`） | 增量同步开始工作，分歧照旧 |
| 改为每次变化重发**完整** `InventoryS2CPacket` | 包发出去了（字节码已证），客户端没应用 |
| 修掉"每次交互开两次背包"（`RightClickItem` 每只手各触发一次 → `MAIN_HAND` 守卫） | 症状不变 |

### 11.3 四条教训（本项目第 4 次同形状）

1. **"看起来只做持久化"的方法往往同时承担状态同步的触发职责。**
   `markDirty()` 被写成空实现，丢掉的不是写盘（那由 mixin 负责），而是**通知 `ScreenHandler` 去同步**。
   空实现是危险的移植选择：它不会报错，只会让状态不同步。
2. **容器型功能不是改名就能移植的。** 1.12.2 的 `Container` / `IInventory` 由 Forge 托管同步；
   1.21 要求以**服务端为唯一真相**，并逐项验证"客户端预测 → 服务端权威 → 回包修正"这条链。
3. **增量同步有一个隐含前提：两边起点一致。** `sendContentUpdates()` 只发**变化**的槽，所以
   **客户端自己的幻觉槽永远不会被修正** —— 它在服务端从未变化过。
4. **没有观测量时的改动等于猜。** 这个子系统上每一次"看起来显然"的修复都需要日志证明，
   而日志本身要先被设计出来（`bag click` / `bag take` / `bag setSlot` / `bag quickMove` 四类探针）。

### 11.4 若将来要重做

从"服务端为唯一真相"重新设计，而不是修补现有结构：不要让客户端持有独立的背包状态
（客户端只渲染服务端发来的内容），并**先**把"开界面时的全量同步是否被应用"验成一个可观测事实，
再往上叠 GUI 与滚动。相关材料：`MIGRATION.md` 的 seam 审计表、
`phase6-client-notes.md` §26（附魔台上的同类教训：先有观测量，再改代码）。
