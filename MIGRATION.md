# FireFly's Simple Tweaks — 1.21.1 Fabric 迁移

从 `../`（Minecraft 1.12.2 / Forge / RetroFuturaGradle）迁移到 Minecraft 1.21.1 / Fabric / Yarn。
1.12.2 源码原样保留在上一级目录，可随时对照。

**阶段 0（垂直切片）已完成并编译通过**：附魔 `acid_attack` 从 JSON 定义 → 注册键 → 伤害事件接缝 → 处理器，全链路代码已就位，`gradlew build` 成功产出 jar。

---

# ⛔ 铁律一：refmap 有映射 ≠ 接缝活着

**这是整个迁移最重要的一条教训。** 编译产物里的 `simple_tweaks-refmap.json` 只要有映射，
只说明 Mixin 注解处理器**成功解析了那个方法名**；它**完全没有**说明这个方法在运行时会不会被执行。

Mixin 只改写它 `@Mixin` 的那个类的方法体，**不会**把注入传播给子类的 override。
1.21 里不少基类方法被**完整重写**（不是 wrapper、**不调 `super`**），于是注入永远不执行。
本项目因此有两个接缝**一次都没生效过**，整个伤害层是死的，而**第一轮验收却是 2/2 通过** ——
因为那两个用例恰好走了没有 override 子类的方法。

**所以每一个新接缝都必须过下面这三关，缺一不可：**

1. **静态关（override 审计）**：用下面「铁律二」的方法，找出实际执行的那个类，确认它就是
   `@Mixin` 的目标（或在其 super 链上）。
2. **构建关**：refmap 里有映射。**这一关最容易过，也最不能说明问题。**
3. **动态关**：真的启动服务器，用真实入口驱动一次，看到该接缝的 `[ST-*]` 日志。
   **没有动态证据的接缝一律记为「未验证」，不得写成「已实现」。**

---

# ⛔ 铁律二：Mixin 目标必须用字节码核实

不要靠"子类应该会调 super 吧"的直觉。可靠的做法：

```powershell
# javap 会按 ~100 列折行，折行会把 Method owner.name 从中间劈开，
# 所以必须先把所有空白去掉再正则，否则会漏报（我就因此得出过
# "35 个类全都不调 super" 的荒谬结论）。
$flat = ((& javap -cp $jar -p -c $CLASS) -join '') -replace '\s',''
[regex]::Matches($flat, 'invokespecial#\d+//Method([\w/$]+)\.METHOD:DESC')
```

判定规则：

- 某个类**声明**了该方法且**没有任何** `invokespecial <super>.METHOD` ⇒ 它是"顶层重写"，
  **必须**成为 `@Mixin` 的目标（除非它的实例永远不是处理器的目标）。
- 声明了且调了 super ⇒ 会被上层的目标覆盖，不要重复 mixin（否则可能触发两次）。
- `@Mixin({A.class, B.class})` 是多目标写法，适合"A 是顶层重写、B 也是顶层重写"的情况。
- **不存在**"既是 target 又调 super"的类时，多目标不会重复触发。

---

# 📋 每个接缝的 override 清单（新接缝照此模板核对）

下表是**已核实**的结果（1.21.1 yarn jar 字节码扫描），新接缝请按同样格式补一行再动手。
"顶层重写"= 声明该方法且不调 `super`。

| 接缝 | 方法 | 顶层重写（必须 mixin） | 调 super（经上层覆盖） | 最终 `@Mixin` 目标 |
|---|---|---|---|---|
| `LivingHurtEvent` / `LivingAttackEvent` | `damage(DamageSource,F)Z` | **`LivingEntity`**、`ArmorStandEntity`、（`ClientPlayerEntity` / `OtherClientPlayerEntity` 仅客户端，故意不覆盖） | `PlayerEntity`、`ServerPlayerEntity`、`ZombieEntity`、`WolfEntity`、`WitherEntity`、`EndermanEntity`、`WardenEntity`、`RaiderEntity`、`IronGolemEntity`、`HoglinEntity`、`EnderDragonEntity` … | `{LivingEntity, ArmorStandEntity}` |
| `LivingDamageEvent` | `applyDamage(DamageSource,F)V` | **`LivingEntity`**、**`PlayerEntity`** | `WolfEntity`（→`TameableEntity`） | `{LivingEntity, PlayerEntity}` |
| `LivingDeathEvent` | `onDeath(DamageSource)V` | **`LivingEntity`**、**`ServerPlayerEntity`** | `PlayerEntity`、`WolfEntity`、`IronGolemEntity`、`RaiderEntity` | `{LivingEntity, ServerPlayerEntity}` |
| `LivingHealEvent` | `heal(F)V` | **`LivingEntity`**（全 jar 仅此一个） | — | `LivingEntity` |
| `LivingEvent.LivingUpdateEvent` | `tick()V` | `Entity`（顶层），但**链路完整**：`LivingEntity.tick`→`super.tick`、`ServerPlayerEntity.tick`→`PlayerEntity.tick`→`LivingEntity.tick`→`Entity.tick` | — | `Entity` |
| `PlayerEvent.BreakSpeed` | `getBlockBreakingSpeed(BlockState)F` | **`PlayerEntity`** | — | `PlayerEntity` |
| `LivingFallEvent` | `handleFallDamage(FFLDamageSource)Z` | `Entity`（顶层）；`LivingEntity`/`PlayerEntity` 都调 super 回到它（`AbstractHorseEntity` 不调，但马不是目标） | — | `Entity` |
| `BlockEvent.HarvestDropsEvent` | `Block#dropStacks` / `dropStack` | 静态方法，无 override 问题 | — | `Block` |
| `PlayerEvent.BreakSpeed.pos` 供应 | `AbstractBlock#calcBlockBreakingDelta` | 静态方法 | — | `AbstractBlock` |
| **`CriticalHitEvent`**（新增） | `attack(Entity)V` | **`PlayerEntity`** | `ServerPlayerEntity` | `PlayerEntity` |
| **`PlayerDropsEvent`**（新增） | `dropItem(ItemStack,ZZ)ItemEntity` | **`PlayerEntity`**（全 jar 仅此一个） | `ServerPlayerEntity` | `PlayerEntity` |
| **`PlayerDropsEvent`**（新增） | `ServerPlayerEntity#onDeath` | **`ServerPlayerEntity`** —— 1.21.1 的玩家死亡掉落**直接调 `PlayerEntity.dropItem`**，既不经过 `PlayerEntity.dropInventory()`，也不经过 `PlayerInventory.dropAll()`（后者把返回值 `pop` 掉了） | — | `ServerPlayerEntity` |

### 顺带核实到的两件事（别再去试）

- `ServerPlayerEntity#applyDamage` **不存在**，它继承 `PlayerEntity` 的顶层重写。
- `EnchantDamageLimiterHandler` 在护甲等级 ≥ 5 时 `limit = (0.8 − 0.2·lvl)·maxHealth ≤ 0`，
  会把伤害夹到负数然后照样记日志 —— 是行为问题，不是日志门控问题。

---

## 1. 怎么构建

```powershell
cd fabric-1.21.1
.\gradlew.bat build          # 编译 + remap，已实测通过
.\gradlew.bat runServer      # 快速验证 mixin / 数据包（run/ 下已放好 eula.txt）
.\gradlew.bat runClient      # 客户端实测
```

产物：`fabric-1.21.1/build/libs/simple_tweaks-2.0.0.jar`

> **沙箱限制提醒**：Gradle 需要写 `GRADLE_USER_HOME=E:\gradle`（在工作区之外）并 fork JVM。
> 在 `workspace-write` 策略下这两件事都被拒绝，`gradlew` 会**静默退出且不输出任何内容**
> （不是报错，是退出码 0 加零输出）。要在这里跑构建，必须先把 DSH 文件策略切到
> `danger-full-access`；否则请在本机终端执行上面的命令。

### 已核实的版本坐标（不要再改）

全部对照 `https://maven.fabricmc.net/.../maven-metadata.xml` 核实过，构建已跑通：

| 属性 | 值 | 备注 |
|---|---|---|
| `minecraft_version` | `1.21.1` | |
| `yarn_mappings` | `1.21.1+build.3` | 本地 Loom 缓存里也有 |
| `loader_version` | `0.16.14` | 0.16 线最后一个 |
| `loom_version` | `1.9-SNAPSHOT` | 实际解析为 **Fabric Loom 1.9.2** |
| `fabric_version` | `0.116.17+1.21.1` | 1.21.1 线最后一个发布版（共 37 个） |
| `kotlin_version` | `2.0.21` | |
| `fabric_kotlin_version` | `1.12.3+kotlin.2.0.21` | **踩过的坑**：`1.12.1+kotlin.2.0.21` 不存在；1.12.1/1.12.2 配的是 kotlin.2.0.20，2.0.21 从 1.12.3 开始 |

`fabric-language-kotlin` 与 MC 版本无关（它是语言适配器），只跟 `kotlin_version` 绑定。

### 构建结果（已实测）

```
BUILD SUCCESSFUL in 8m 18s
:compileKotlin → :compileJava → :jar → :remapJar → :build  全部通过
产物 build/libs/simple_tweaks-2.0.0.jar  (31.1 KB)
```

首次构建需下载 MC 1.21.1 / Yarn / Fabric API / Kotlin，约 8 分钟；之后增量构建是秒级。

> Gradle 发行包走的是 `gradle-wrapper.properties` 里配的腾讯镜像，可用。
> 注意那只是 **Gradle 自身**的镜像；Maven 依赖仍从 `maven.fabricmc.net` 拉取（实测可用）。
> 若哪天 maven.fabricmc.net 不可达，需要改的是 `settings.gradle` / `build.gradle` 的
> `repositories {}` 块，而不是 wrapper 的 distributionUrl。

### 还需要跑一次游戏才能确认的部分

编译 + remap 已经证明了绝大部分东西，但下面 4 项只有真正启动游戏才能验证：

```powershell
.\gradlew.bat runServer     # 已放好 run/eula.txt 与超平坦 server.properties，启动很快
```

启动后检查 `run/logs/latest.log`：

| 要确认什么 | 日志里找什么 |
|---|---|
| mixin 是否真的注入成功 | 不应出现 `Mixin apply failed`／`EntityDamageMixin` 相关报错 |
| 附魔 JSON 是否通过数据包校验 | 不应出现 `Couldn't parse enchantment simple_tweaks:acid_attack` |
| 标签文件是否合并成功 | 不应出现 `Unknown entry 'simple_tweaks:acid_attack'` |
| 入口与事件总线是否工作 | `Registered 1 event handler(s) from EnchantAcidAttackHandler` |
| 服务器是否起来 | `Done (Xs)! For help, type "help"` |

客户端验证则用 `.\gradlew.bat runClient`，然后：

```
/give @s diamond_sword
/enchant @s simple_tweaks:acid_attack 3
```

攻击生物，目标主手武器应随机掉耐久（1/2/3 级 → 15%/20%/25%/30% 概率，每次掉对应点数）。

### ⚠️ refmap 只能证明"目标解析成功"，**不能**证明注入会被执行

编译产物里的 `simple_tweaks-refmap.json` 是 Mixin 注解处理器生成的，只有目标方法解析成功才会有内容：

```json
"damage(Lnet/minecraft/entity/damage/DamageSource;F)Z":
    "Lnet/minecraft/class_1297;method_5643(Lnet/minecraft/class_1282;F)Z"
```

`class_1297` = `Entity`、`method_5643` = `damage(DamageSource,float)` —— 描述符确实是对的。

**但我当时把这当成了"接缝已被静态证明"，这是错的。** refmap 里有这条记录，
只说明 `Entity.damage` 这个名字解析成功；它**完全没有**说明这个方法在运行时会不会被执行。
事实是 `LivingEntity#damage` 完整重写了它，注入一次都没跑过，整个伤害层是死的。
教训见下面的「致命 Mixin 目标错误」一节：**refmap 有映射 ≠ 接缝活着，必须动态验证。**

### 验证切片是否成功

进入游戏后：

```
/give @s diamond_sword
/enchant @s simple_tweaks:acid_attack 3
```

攻击任意生物，目标主手武器应随机掉耐久（15%/20%/25%/30% 概率，每次掉 1/2/3 级对应点数）。
日志里应有 `Registered 1 event handler(s) from EnchantAcidAttackHandler`。

---

## 2. 离线 API 名称校验工具

本机**无法** `javap` / 编译，但 Yarn 映射已缓存在本地，可离线查出任意类/方法/字段的准确 Yarn 名：

```powershell
Invoke-Expression (Get-Content .\tools\yarn-query.ps1 -Raw)   # .ps1 被执行策略拦截，必须这样加载
Get-YarnClass -Class 'net/minecraft/entity/LivingEntity' -Filter '\t(damage|heal)$'
Get-YarnClass -Class 'net/minecraft/item/ItemStack' -Version '1.21.4'
Get-YarnFind  -Pattern '^\tm\t\(Lbrk;F\)Z\t'
```

支持版本：`1.21.1`（默认）、`1.21.4`、`1.21.11`（均已缓存）。

> 注意：映射文件里混有 javadoc 行，形如 `c<TAB>一段带空格的说明`。
> 工具用 `^c\t\S+\t\S+\t\S+$` 判定真正的类声明行，不要退化成 `^c\t`。

---

## 3. 已核实的 Yarn 1.21.1 ground truth

全部通过 `tools/yarn-query.ps1` 从缓存映射核实，**不是猜的**。

| 用途 | Yarn 名 | intermediary |
|---|---|---|
| 伤害入口（**接缝在这里**） | `Entity.damage(DamageSource, float): boolean` | `method_5643` |
| 护甲后伤害钩子 | `LivingEntity.applyDamage(DamageSource, float): void` | `method_6074` |
| 死亡 | `LivingEntity.onDeath(DamageSource): void` | `method_6078` |
| 治疗 | `LivingEntity.heal(float): void` | `method_6025` |
| 主手 | `LivingEntity.getMainHandStack(): ItemStack` | `method_6047` |
| 按槽取物 | `LivingEntity.getEquippedStack(EquipmentSlot): ItemStack` | `method_6118` |
| 扣耐久 | `ItemStack.damage(int, LivingEntity, EquipmentSlot): void` | `method_7970` |
| 可损坏判定 | `ItemStack.isDamageable(): boolean` | `method_7963` |
| 攻击者 | `DamageSource.getAttacker(): Entity` | `method_5529` |
| 读附魔表 | `EnchantmentHelper.getEnchantments(ItemStack): ItemEnchantmentsComponent` | `method_57532` |
| 读等级 | `ItemEnchantmentsComponent.getLevel(RegistryEntry): int` | `method_57536` |
| 键比对 | `RegistryEntry.matchesKey(RegistryKey): boolean` | `method_40225` |
| 注册键 | `RegistryKey.of(RegistryKey, Identifier)` | `method_29179` |
| 附魔注册表键 | `RegistryKeys.ENCHANTMENT` | `field_41265` |
| 客户端判定 | `World.isClient`（**字段**，非方法） | `field_9236` |
| 移除实体 | `Entity.remove(Entity.RemovalReason): void` | `method_5650` |
| 已移除判定 | `Entity.isRemoved(): boolean` | `method_31481` |
| 攻击 | `PlayerEntity.attack(Entity): void` | `method_7324` |
| 攻击冷却 | `PlayerEntity.getAttackCooldownProgress(float): float` | `method_7261` |
| 取属性实例 | `LivingEntity.getAttributeInstance(RegistryEntry): EntityAttributeInstance` | `method_5996` |
| 读属性值 | `LivingEntity.getAttributeValue(RegistryEntry): double` | `method_45325` |
| 击退 | `LivingEntity.takeKnockback(double, double, double): void` | `method_6005` |

`EntityAttributes` 在 1.21.1 仍用 `GENERIC_` 前缀（`GENERIC_MAX_HEALTH` = `field_23716`、
`GENERIC_MOVEMENT_SPEED` = `field_23719`、`GENERIC_ATTACK_DAMAGE` = `field_23721` 等），
且类型是 `RegistryEntry<EntityAttribute>` 而非裸 `IAttribute`。
（更晚的版本才把前缀去掉，迁移 1.21.1 时不要照抄新文档。）

### Fabric API 类名（本地实测，0.116.17+1.21.1）

不要凭记忆写 Fabric API 类名 —— 直接从缓存里的 jar 列出来。注意下面两条**与直觉不符**：

| 需要的东西 | 真实类名 |
|---|---|
| 玩家换维度 | `net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChange**Events**` |
| | ⚠️ **不是** `ServerEntityLevelChangeEvents`（Yarn 把 World 改成 Level 了，但 Fabric 这个类名没跟着改） |
| 玩家登录/登出 | `ServerPlayerEvents.JOIN` / `.LEAVE`（直接给 `ServerPlayerEntity`） |
| | 比 `ServerPlayConnectionEvents.JOIN`/`DISCONNECT` 更好用（后者给的是网络 handler） |

其余已核实：`ServerTickEvents`、`ClientTickEvents`、`ServerLifecycleEvents`、
`AttackEntityCallback`、`ItemTooltipCallback`、`WorldRenderEvents`、`PayloadTypeRegistry`、
`ServerPlayNetworking`、`ClientPlayNetworking`、`PlayerLookup`、`KeyBindingHelper`、
`CommandRegistrationCallback`、`PlayerBlockBreakEvents`、`ServerEntityEvents`、
`UseBlockCallback`/`UseEntityCallback`/`UseItemCallback`、`ServerLivingEntityEvents`。

**查法**：Fabric API 的 **sources jar 也缓存在本地**（152 个），可以直接读权威签名，别再猜：

```powershell
# 列出某个 sources jar 里的目标接口声明
Add-Type -AssemblyName System.IO.Compression.FileSystem
$j = Get-ChildItem "E:\gradle\caches\modules-2\files-2.1\net.fabricmc.fabric-api" -Recurse -Filter 'fabric-entity-events-v1*sources.jar' | Select-Object -First 1
$z = [System.IO.Compression.ZipFile]::OpenRead($j.FullName)
$e = $z.Entries | Where-Object { $_.FullName -match 'ServerPlayerEvents\.java$' }
$sr = New-Object System.IO.StreamReader($e.Open()); $t = $sr.ReadToEnd(); $sr.Close(); $z.Dispose()
$t -split "`n" | Where-Object { $_ -match 'interface |void |Event<' }
```

sources jar 里是 intermediary 名（`class_3222` = `ServerPlayerEntity`、`class_3218` = `ServerWorld`、
`class_310` = `MinecraftClient`、`class_638` = `ClientWorld`、`class_3244` = `ServerPlayNetworkHandler`），
Loom 会自动重映射，写 Kotlin 时用 Yarn 名即可。

### 实测统计：没有处理器用 `Phase.START`

26 处 tick 处理器**全部**过滤 `Phase.END`。这让 tick 桥接可以零 mixin 实现：
`ServerTickEvents` / `ClientTickEvents` 直接桥接，玩家 tick 由 world tick 遍历该世界玩家列表得出
（Forge 原本是在 `EntityPlayerMP#onUpdate` 里逐玩家触发）。
`Phase.START` 仍保留在事件类里以兼容源码，但只是出于忠实度而触发。

### 两个必须记住的坑

1. **`LivingEntity` 不覆写 `damage`。** 全游戏只有一个 `damage(DamageSource, float): boolean`，
   声明在 `Entity` 上（`LivingEntity` 的 272 个方法里没有它）。
   所以接缝 mixin 必须 `@Mixin(Entity.class)`，再用 `instanceof LivingEntity` 过滤。
2. **`World.isClient` 是字段，同时 `WorldView` 有 `isClient()` 方法。**
   Java 里 `world.isClient` 无歧义，Kotlin 里可能有歧义 →
   统一走 `compat/WorldSide.java` 的静态方法，不直接在 Kotlin 里访问。

---

## 4. 架构决策：为什么做 Forge 事件兼容层

1.12.2 代码库有 **144 个 `@SubscribeEvent` 方法分布在 76 个文件**，依赖 **27 种 Forge 事件**。
其中用量最大的两个在 Fabric API 里**没有对应物**：

| Forge 事件 | 用量 | Fabric API 对应 |
|---|---|---|
| `LivingHurtEvent`（amount 可变、可取消） | 27 | ❌ 无 |
| `LivingDamageEvent` | 16 | ❌ 无 |
| `CriticalHitEvent` | 5 | ❌ 无 |
| `TickEvent` | 26 | ✅ `ServerTickEvents` / `ClientTickEvents` |
| `PlayerEvent` | 21 | ✅ `ServerPlayerEvents` |
| `AttackEntityEvent` | 4 | ✅ `AttackEntityCallback` |
| `LivingDeathEvent` | 4 | ✅ `ServerLivingEntityEvents` |
| `ItemTooltipEvent` | 1 | ✅ `ItemTooltipCallback` |
| `RenderWorldLastEvent` | 1 | ✅ `WorldRenderEvents` |
| `BlockEvent` | 3 | ✅ `PlayerBlockBreakEvents` |
| `EntityJoinWorldEvent` | 2 | ✅ `ServerEntityEvents` |
| `PlayerInteractEvent` | 2 | ✅ `UseBlock/UseEntity/UseItemCallback` |

**若不这么做**，43 个依赖可变伤害值的处理器要逐个重新设计，55 个文件各写一遍，token 成本和出错率都会翻好几倍。

**采用的做法**：保留 Forge 事件*形状*，用**约 10 个 mixin 接缝**重建它们。
于是迁移规则退化成机械操作：

### 伤害接缝的关键设计（`compat/EventSeams.java`）

这是整个迁移最容易做错的地方，理由用实测数据说话：

- **25 处处理器改写伤害值**（`e.amount *= ...` / `+= ...` / `= ...`）
- **15 处直接设 `isCanceled = true`**，绕过了会顺带把 amount 归零的 `ForgeEventUtils.cancel()`
  —— 其中包含 `EnchantImmortalHandler`、`EnchantDeathProtectionHandler` 这类"防止死亡"的附魔

所以「把取消当成 amount=0」是**错的**：那些只设 `isCanceled` 的处理器会失效，玩家该死还是死。
真正的抑制必须走 `@Inject(cancellable = true)`。

但 Mixin 里这两个能力分属不同注入器：取消要 `@Inject(at = HEAD, cancellable = true)`，
改**方法参数**要 `@ModifyVariable(at = HEAD, argsOnly = true)`。两个注入器都在 HEAD，
而 **Mixin 并不保证它们的相对顺序** —— 若各自独立发事件就会重复触发
（`amount *=` 类处理器会叠加两次伤害），若靠 ThreadLocal 传递又可能读到空值而丢失改写。

`EventSeams` 的解法是**身份校验式 get-or-fire**：缓存只在「同一实体 + 同一 `DamageSource` 实例」
时才复用，否则重新发事件。于是：

- 谁先跑都只发一次事件，另一个注入器读到同一份结果（改写与取消都生效）
- 万一缓存跨调用残留（例如取消路径提前 return，`@At("RETURN")` 没清到），
  身份不匹配会强制重发，**残留绝不会静默吞掉一次处理器调用**

`LivingDeathEvent` 的取消还必须 `setHealth(1.0f)`（Forge 当年就是这么做的），
否则实体虽被取消死亡但仍会在下一 tick 被移除。

```diff
-import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
+import dev.firefly.simpletweaks.compat.event.SubscribeEvent
-import net.minecraftforge.event.entity.living.LivingHurtEvent
+import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
-import net.minecraft.entity.EntityLivingBase
+import net.minecraft.entity.LivingEntity
```

处理器**方法体基本不用动** —— 因为 `invalid` / `attacker` / `target` / `cancel()` / `chance()`
这些扩展在 `compat/EventUtils.kt` 里保留了同名实现。

`ForgeEventBus` 用反射扫描 `@SubscribeEvent`，与 Forge 行为一致（按 `EventPriority.ordinal` 升序派发、
已取消事件默认跳过、事件层级刻意保持扁平以保证"按精确类型派发"）。

### 已知限制（阶段 1 处理）

`EntityDamageMixin` 用 `@Inject(at = HEAD)`，只能**观察和取消**伤害，**不能改写 `amount` 参数**。
阶段 0 的 `acid_attack` 不需要改写，所以够用。
需要改写 `LivingHurtEvent.amount` 的处理器，阶段 1 会追加一个
`@ModifyVariable(argsOnly = true)` 注入器补上这一点。届时会先统计到底有多少个处理器真的改 `amount`。

---

## 5. 附魔定义：1.21 已改为数据驱动

**这是本次迁移最大的方案变化。** 自 1.21 起附魔定义写在数据包里，不再写代码：

- 55 个 `object EnchantX : ModEnchantments(...)` → 55 个 `data/simple_tweaks/enchantment/*.json`
- `ModEnchantments`（基类）、`ModEnchantmentType`（包装已删除的 `EnumEnchantmentType`）、
  `EnchantmentCategories` 全部溶解，由 `ModEnchantmentKeys`（`RegistryKey` 常量表）+ JSON 取代
- `MixinEnchantmentHelper` 里"按分类覆写权重"的逻辑作废（权重现在是 JSON 的 `weight` 字段）
- 基类提供的 SWORD 附加伤害 `(0.2 + rarity/20) * level` → JSON 的 `minecraft:damage` 效果组件

JSON 结构已用**原版文件**核实（从缓存的 `minecraft-merged.jar` 抽出 `sharpness.json`），
不是照文档猜的。`acid_attack.json` 的字段与 `sharpness.json` 完全同构。

### `ModEnchantmentType` → 物品标签映射（阶段 2 全量用）

| 原类型 | `supported_items` | 槽位 |
|---|---|---|
| SWORD | `#minecraft:enchantable/sword` | `mainhand` |
| WEAPON | `#minecraft:enchantable/weapon` | `mainhand` |
| HELD | `#minecraft:enchantable/weapon` | `mainhand`, `offhand` |
| ARMOR | `#minecraft:enchantable/armor` | `armor` |
| HELMET | `#minecraft:enchantable/head_armor` | `head` |
| CHESTPLATE | `#minecraft:enchantable/chest_armor` | `chest` |
| LEGGINGS | `#minecraft:enchantable/leg_armor` | `legs` |
| BOOTS | `#minecraft:enchantable/foot_armor` | `feet` |
| BREAKABLE | `#minecraft:enchantable/durability` | `any` |
| TOOL | `#minecraft:enchantable/mining` | `mainhand` |
| BOW | `#minecraft:enchantable/bow` | `mainhand`, `offhand` |

### 进附魔台必须加标签

1.21 里附魔要出现在附魔台，必须同时在 `#minecraft:in_enchanting_table` 里，
而该标签默认是 `#minecraft:non_treasure`。所以非宝藏附魔要追加进：

```
src/main/resources/data/minecraft/tags/enchantment/non_treasure.json
```

原代码用 `category.rarity >= 6` 判定宝藏（只有 MYSTERY 是），对应地 **MYSTERY 类附魔不要加进这个标签**。

---

## 6. 阶段状态

| 阶段 | 内容 | 状态 |
|---|---|---|
| 0 | 骨架 + `acid_attack` 垂直切片 | ✅ **编译通过 + 服务器启动实测通过**（日志已确认） |
| 1a | 生命体伤害接缝：`LivingAttack`/`LivingHurt`/`LivingDamage`/`LivingHeal`/`LivingDeath` | ✅ **编译通过，refmap 四条映射全部命中**（覆盖原代码 51 处事件调用） |
| 1b-1 | `TickEvent` 全家族 + `PlayerEvent` 全家族（Fabric 回调桥接，0 个新 mixin） | ✅ 代码完成（覆盖原代码 47 处事件调用），待编译 |
| 1b-2a | `AttackEntity`/`EntityJoinWorld`/`BlockEvent.BreakEvent`/`PlayerInteractEvent.RightClickItem`（Fabric 桥接）+ `LivingUpdateEvent`/`LivingFallEvent`（2 个 mixin） | ✅ 代码完成（覆盖原代码 12 处事件调用），待编译 |
| 1b-2b | 掉落类接缝：`LivingDropsEvent` / `PlayerDropsEvent`（需缓冲式 mixin，见下） | ⏳ |
| 1b-3 | 见「暂缓的接缝」表 | ⏳ |
| 1c | 配置（`Configurable`/`GeneralConfig`）+ `RegistryKey` 全表 | ⏳ |
| 2.5 | 55→**实际 56** 个处理器分类：声明式 JSON vs 保留 Kotlin | ✅ 完成，见 `docs/phase2.5-SUMMARY.md` |
| 2 | **56 个附魔 JSON + 非宝藏标签 + 140 键 lang JSON** | ✅ **已生成并本地校验通过**（`tools/gen-enchantments.ps1` 可重跑），待 `runServer` 验证数据包 |
| 3 | 处理器移植：4 个删代码、12 个 PARTIAL、约 47 个 Kotlin（分 4 批） | ⏳ 进行中 |
| 3-b1 | **common + uncommon：12 个处理器已移植**（另删 2 个声明式、隔离 1 个） | ✅ **编译 + `runServer` 实测通过**（12 个处理器全部注册，无任何报错） |
| 3-b2 | rare + epic | ✅ **15 个移植完成，构建通过（0 报错）**；另隔离 4 个 |

### 阶段 3 第二批：为什么是 15 而不是 17

计划 17 个，实际移植 15 个。除按计划隔离的 2 个，**另外 2 个箭头处理器实际也被阻塞**
（`EnchantPiercingArrowHandler`、`EnchantTrackingArrowHandler`）——
阶段 2.5 的逐处理器分类**漏掉了它们的跨文件/跨阶段依赖**：

| 依赖 | 状态 |
|---|---|
| `IPiercingArrow` 接口 + `EntityArrowPiercingMixin` | **阶段 4 的既有 mixin 重定向**，尚未移植 |
| `PlayerUtils.runPlayerAttack` | 需要 `CriticalHitEvent`（crit 接缝） |
| `arrow.entityData`（`st_pierce_hits` / `st_tracking_level` 每投射物 NBT） | 1.21 无对应物，需 Fabric attachment 或新 mixin |
| `EntityArrowAccessor.inGround` | 1.21 里 `PersistentProjectileEntity.inGround` 是 `protected` 且无公开 getter |

即：**投射物相关的接缝本来就排在阶段 4**，这两个处理器天然属于 hard 组。
`deferred/` 现共 5 个文件。

### 第二批值得复核的语义改动

- **`EnchantExtraArmorHandler` / `EnchantVitalityHandler`**：`UUID.nameUUIDFromBytes` → `Identifier`；
  `applyModifier` → **`addPersistentModifier`**（1.12.2 的 `applyModifier` 会写 NBT；
  用 `addTemporaryModifier` 会在重载后静默丢加成）。`EntityEquipmentSlot.Type.ARMOR` →
  `EquipmentSlot.Type.HUMANOID_ARMOR`。另 `getAttributeInstance` 在 1.21 是 `@Nullable`，
  清理修饰符需 `?.removeModifier`。
- **`PlayerUtils.syncAttributes()` 现在与 vanilla 冗余** —— 1.21.1 的 `EntityTrackerEntry`
  已经会推送被追踪的属性。按 1:1 保留并注释，而非删除。
- **两处粒子常量是"按名推断"而非机械替换**：
  `CRIT_MAGIC` → `ENCHANTED_HIT`、`EXPLOSION_NORMAL` → `POOF`（1.13 扁平化改名）。
  常量确实存在且语义对应，但值得实测确认视觉正常。
- `EnchantTunnelingHandler`：三段式破坏（`playEvent`/`setBlockToAir`/`harvestBlock`）改为
  `syncWorldEvent` / `removeBlock` / `Block.dropStacks` —— **掉落与经验现在走 1.21 的掉落表**。
- `EnchantHuntersMarkHandler`：`EntityArrow` → `PersistentProjectileEntity`（箭与光灵箭的共同基类），
  `immediateSource` → `getSource()`，`shootingEntity` → `owner`，`ticksExisted` → `age`。
| 3-b3 | legendary | ✅ **5 个移植完成，构建通过（0 报错）**；另隔离 3 个（crit ×2、soulbound） |

### ⚠️ 我自己造成的一个 bug（已修）

阶段 2.5 判定 4 个附魔"可完全声明式"（`anti_knockback`、`swift_sneak`、`unbreakable`、
`prismatic_blessing`）。我在阶段 3 只执行了**"删掉处理器代码"这一半**，
**没有把替代的效果组件写进 JSON** —— 于是：

- `anti_knockback`、`swift_sneak` 的处理器已被删，而 JSON 里**只有 `minecraft:damage`**
  → 这两个附魔当时**完全不生效**
- `unbreakable`、`prismatic_blessing` 的处理器还没轮到移植，属于"尚未生效"而非"坏了"，
  但若按原计划在 mythic 批次移植它们，就会与缺失的 JSON 组件错配

已修：生成器现在为这 4 个输出声明式组件
（`anti_knockback` → `minecraft:attributes`+`generic.knockback_resistance`；
`swift_sneak` → `attributes`+`player.sneaking_speed`；
`unbreakable` → `minecraft:item_damage` set 0；
`prismatic_blessing` → `attributes` 6 条 `add_multiplied_base`），56 个 JSON 重新校验通过。

**教训**：分类给出的是"目标状态"，执行时必须**成对**落地（删代码 + 写 JSON），
否则中间态是静默失效。同理，**PARTIAL 的"声明式那一半"目前并未写进 JSON**，
所以已移植的 PARTIAL 处理器按 1:1 保留了全部逻辑 —— 这是当前**刻意**的一致状态
（避免双重生效），不是遗漏。

### 第三批的残留行为差异（子代理标记 + 我复核）

- **`runPlayerAttack` 是部分移植，不是 1:1。** 4 项未复现：crit 分支（`allowCrit` 参数失效）、
  `triggerEvent = false` 无法抑制接缝、`ignoreArmorAndPotion = true` 无法跳过护甲、
  返回值语义（返回护甲前 `rawDamage` 而非实际伤害）。
  **我复核了唯一调用点（`EnchantEchoShotHandler`）**：原签名 `ignoreArmorAndPotion = false`、
  调用点显式传 `allowCrit = false`，所以这两项在该处**确实惰性** —— 子代理的判断成立。
- **但我发现一项子代理没提到的差异**：`triggerEvent = true` 时，1.12.2 的 `runPlayerAttack`
  会**手动 post `AttackEntityEvent`**，而 1.21 的 `target.damage(...)` 不会触发
  `AttackEntityCallback`（那只在玩家真实攻击动作时触发）。
  即**回声伤害不再触发 `AttackEntityEvent`** → 依赖它的 `EnchantDoubleStrikeHandler`、
  `EnchantCelestialBlessingHandler`、`EnchantKillAuraHandler` 在回声上不再联动。
  1.12.2 里回声**会**触发它们（可能是有意的连锁，也可能是副作用）。需要你判断是否接受。
- **`attackCharge` 的顺序风险（影响 `EnchantDoubleStrikeHandler`）**：
  1.12.2 用 accessor mixin 读攻击蓄力值；1.21 改用公开的
  `PlayerEntity.getAttackCooldownProgress(0.5F)`（`method_7261`）。
  但 `ServerPlayerEntity#swingHand` 也会调 `resetLastAttackedTicks()`，
  若服务器先处理挥击包再处理攻击包，读到的值会偏小、导致 DoubleStrike 永不触发。
  **需实测确认**，离线无法判定。
| 3-b4 | mythic：6 个移植完成 | ✅ **构建通过（0 报错）** |
### 三个行为修复（已实施，构建通过）

**① EchoShield 穿甲问题 —— 用方案 A（自定义伤害类型）修好，未动共享 helper。**

核实结论：`DamageTypeTags.BYPASSES_ARMOR` 在 1.21.1 存在（`field_42241`）。但**没有任何原版类型能同时绕过
护甲+抗性+保护**：`magic`/`generic` 只绕护甲；`bypasses_resistance` 里只有 `generic_kill` 和
`out_of_world`，它们还会绕过无敌帧（对反弹太强）。所以走"自定义类型 + 加入原版标签"：

| 文件 | 作用 |
|---|---|
| `data/simple_tweaks/damage_type/reflection.json` | 自定义伤害类型，`message_id: thorns`（原版"因试图伤害他人而死"的死亡消息正好贴切，无需新翻译键） |
| `data/minecraft/tags/damage_type/bypasses_armor.json` | 复现 `ignoreArmorAndPotion` 的护甲部分 |
| `.../bypasses_resistance.json` | 药水抗性部分 |
| `.../bypasses_enchantments.json` | 1.12.2 把保护附魔算进护甲计算，所以也应跳过 |

新增 `util/damagesource/ModDamageTypes.kt`，其中 `LivingEntity.damageBypassingArmor(attacker, dmg)`
**是独立的顶层的扩展函数，不改动 `util/PlayerUtils.runPlayerAttack`**（该 helper 的
`ignoreArmorAndPotion` 仍标注为惰性，其他调用方不受影响）。EchoShield 两个反弹调用点已改走这条路径。

> 坑：扩展函数写在 `object` **内部**会成为"成员扩展"，外部**无法 import**（必须 `with(obj){}` 才能用）。
> 编译报 `Unresolved reference`。已改为顶层函数。

**② Starfall 打驯服宠物 —— 已修。** 在 `isTeammate` 判断后补一层
`entity is TameableEntity && entity.isOwner(owner)`（`TameableEntity#isOwner(LivingEntity)` = `method_6171`）。
1.21 的 `isTeammate` 只看记分板队伍，而 1.12.2 的 `EntityLivingBase#isOnSameTeam` 还包含"已驯服宠物归主人"。

**③ KillAura 伤害"超集"之说 —— 已核实为等价，KDoc 已更正。**
子代理原判断（"`getModifierForCreature` 只看到原版三件套，所以 `getDamage` 是超集"）**是错的**：
1.12.2 的 `getModifierForCreature` 会对物品上**每一个**附魔调用 `calcDamageByCreature`，
而本模组的 `ModEnchantments` **覆写了该方法**（返回 `(0.2 + rarity/20) * level` 且忽略生物类型）——
所以模组自己的伤害附魔**本来就被计入**。1.21 的 `getDamage` 同样评估所有 `minecraft:damage` 效果，
而生成的每个附魔 JSON 用的正是同一条 linear 公式且无 `requirements`。
原版 Sharpness/Smite/Bane 两边都靠条件筛选（1.21 用 `EntityType` 标签谓词）。
**数值一致，不需要回退到"只算原版三件套"。**


| 3-b5 | mystery + unique：2 个移植完成，1 个隔离 | ✅ **构建通过（0 报错）**，注册 **40** |
### 阶段 3 第六批：AutoSmelt（新接缝 + 完整保真）

**我原先建议的 `LootTableEvents.MODIFY` 不成立** —— 读 Fabric API 源码确认它是**掉落表构造事件**
（`key, tableBuilder, source, registries`），在表加载时触发、**没有逐次掉落的上下文**（拿不到工具/玩家）。
而且就算绕过去，loot function 是纯物品变换，**给不了熔炼经验、也扣不了工具耐久**。
经确认后改用**方案 D：写一个等价于 `HarvestDropsEvent` 的缓冲式接缝**，处理器 1:1 移植。

| 新增 | 内容 |
|---|---|
| `compat/event/BlockEvent.HarvestDropsEvent` | `world`/`pos`/`state`/`harvester`/`drops: MutableList<ItemStack>`（与 Forge 同名成员） |
| `mixin/BlockHarvestDropsMixin` | 打 `Block#dropStacks(...,Entity,ItemStack)`（`method_9511`，**唯一同时带收获者与工具的重载**）+ 三个 `dropStack` 重载 |
| `compat/EventSeams` 的 harvest 缓冲 | 带嵌套深度的 ThreadLocal；`dropStacks` HEAD 开缓冲、`dropStack` HEAD 捕获并取消生成、`dropStacks` RETURN 发事件后只生成存活项 |

**设计要点**：不改动原版掉落生成（时运/精准采集/掉落表照常跑），只把**生成步骤延后** ——
把本该生成的堆收集起来交给事件，再生成存活的部分。

**三个 `dropStack` 重载全部注入**是有意的：取消最外层会阻止其向内层委托，
所以无论 `dropStacks` 实际调用哪个重载，**每次掉落恰好捕获一次** ✔

**踩到的坑**：`dropStack(World, BlockPos, Lji;, ItemStack)` 里的 `Lji;` 我一开始当成 `BlockEntity`
（那是 `Ldqh;`），实际是 **`Direction`**（`class_2350`）。Mixin AP 报了
`Cannot find target method`，refmap 才暴露出问题。

**当时的状态**（阶段 2 快照，已被后续阶段超越）：`41` 个处理器注册，`7` 个 mixin，`deferred/` 9 个。
> **当前（阶段 4 收尾）**：`47` 个处理器注册，`13` 个 mixin，`deferred/` 4 个。
> mixin 数在阶段 4 期间是 `15`，其中 2 个「原版粒子抑制」mixin **已被主动删除**（选项语义搞错，
> 见 `docs/phase4-mixin-notes.md` §9），故为 `13`。
**仍未处理 3 个**：`Momentum`（BreakSpeed 缺 `pos`）、`InfinitePower`（hard 组）、
`AutoSmelt` 已完成；余下 `AntiKnockback`/`SwiftSneak`/`Unbreakable`/`PrismaticBlessing` 为声明式（无需处理器）。

> ⚠️ **AutoSmelt 的接缝只做了静态验证**（refmap 四条映射全部命中），
> **逐掉落的捕获行为需要实机验证**：给镐附上 `simple_tweaks:auto_smelt` 挖矿石，
> 应看到掉落直接变成锭、工具掉 1 点耐久、并获得熔炼经验。
> 若捕获未生效，表现是"掉落照常但不熔炼"（安全降级，不会丢物品或崩服）。

### 阶段 3 第五批：`CelestialBlessing` 被隔离（我之前的说法有误）

我在排第五批时说「三者的接缝都已实现」—— **事件接缝确实都实现了，但这个判断不完整**。
`CelestialBlessing` 还依赖 5 个属于**后续阶段**的模块（我私自查了它的 import 与调用点确认）：

| 缺失符号 | 1.12.2 行号 | 归属阶段 |
|---|---|---|
| `network.NetworkManager` | 255, 301, 360, 512 | 阶段 5（网络） |
| `network.packets.PacketCelestialRing` | 256 | 阶段 5 |
| `network.packets.PacketManaPoolSync` | 302, 361, 513 | 阶段 5 |
| `client.ClientManaPoolCache` | 532 | 阶段 6（客户端） |
| `core.config.GeneralConfig.enabledSpecialParticles` | 254 | 阶段 1c（配置） |
| `FMLNetworkEvent.ClientDisconnectionFromServerEvent` | 531 | 需 Fabric 客户端连接回调 |
| `util.damagesource.CelestialDamageSources` | 425 | 需 `simple_tweaks:celestial_particle` 伤害类型 —— **模式已有**，照 `ModDamageTypes.REFLECTION` 抄即可（唯一已解决的一项） |

**这是第三次发现「逐处理器分类看不到跨模块依赖」**（前两次：`AoeAttack`→`runPlayerAttack`；
箭头处理器→阶段 4 的投射物 mixin）。教训：分类只看了**事件**维度，
应同时看 **import 维度** —— 凡 import 了本工程尚未移植模块的处理器，都应标为「间接阻塞」。

它的状态机清单我**逐条核对过源文件，与子代理报告完全一致**（11 个状态字段：
`manaPool`(public，客户端缓存/tooltip 要读)、`targetQueue`、`autoAttackCooldown`、`projectiles`、
3 个属性修饰符 UUID、`lastSyncedMana`、`lastSyncTick`、`SHRED_DURATION_TICKS`、`shredStates`），
`.disabled` 文件保留 1.12.2 原文并附阻塞表，未发明任何 API。

### 阶段 3 第七批：Momentum（并补上两个从未接线的接缝）

**先报一个我之前埋的坑**：`PlayerEvent.BreakSpeed` / `HarvestCheck` 这两个事件类我在阶段 1b-2a
就写好了、文档里也写了"接缝目标已核实"，但**从来没有真正写 mixin 去触发它们** ——
也就是"声明了却永远不触发"的状态，正是我自己说过要避免的那种。做 Momentum 时才发现。
现已补全 BreakSpeed（`HarvestCheck` 待 `ToolHandler` 那批再接线）。

**更好的消息：`pos` 不需要近似，可以精确拿到。** 我原以为只能拿"玩家视线方块"糊过去，
查字节码后发现调用链是：

```
AbstractBlockState.calcBlockBreakingDelta(player, world, pos)   ← 有坐标，但只是委托
    → AbstractBlock.calcBlockBreakingDelta(state, player, world, pos)  ← 有坐标，且调用 getBlockBreakingSpeed
        → PlayerEntity.getBlockBreakingSpeed(state)                ← 只有 BlockState
```

证据：`AbstractBlock.class` 同时引用了 `getBlockBreakingSpeed` 与 `canHarvest`，
而 `AbstractBlock$AbstractBlockState.class` 只引用 `calcBlockBreakingDelta`（说明是委托）。

所以在 `AbstractBlock#calcBlockBreakingDelta`（`method_9594`）的 HEAD/RETURN 挂一个 ThreadLocal
记录坐标，`getBlockBreakingSpeed`（`method_7351`）触发 `BreakSpeed` 时读回即可 ——
**真实坐标，不是近似**。这很重要：Momentum 靠坐标区分"还在挖同一块"与"换了一块"，
只看 `BlockState` 会把一片相邻的石头当成同一块、导致加成错误累积。

**只挂 `PlayerEntity`、没挂 `PlayerInventory`**：挖掘路径走前者；两个都挂会让事件每 tick 触发两次、
把乘法加成叠加两遍。

新增 `mixin/AbstractBlockBreakingDeltaMixin`、`mixin/PlayerEntityBreakSpeedMixin`，
`compat/EventSeams` 加 `MINING_POS` ThreadLocal。

---

## 🖥️ runServer 运行流程（自定流程，以后照此执行）

**不再每次询问用户**，改为先自查内存再决定：

```powershell
$os = Get-CimInstance Win32_OperatingSystem
$freeGB = [math]::Round($os.FreePhysicalMemory / 1MB, 1)
$totalGB = [math]::Round($os.TotalVisibleMemorySize / 1MB, 1)
"Free: $freeGB GB / Total: $totalGB GB"
```

| 空闲内存 | 动作 |
|---|---|
| **> 4 GB** | 直接启动，无需询问 |
| **2 – 4 GB** | 先 `.\gradlew.bat --stop` 停掉 Gradle Daemon，再启动 |
| **< 2 GB** | **停下来询问用户** |

启动时**只报「注册数」和「有无报错」两项，跑完立刻停**，不贴完整日志。

依据：一次 `runServer` 约需 2–3 GB。实测在执行 `--stop` 后空闲内存从 2.1 GB 回到 4.4 GB。

---

## 🖥️ 服务端验收基础设施

### 开发期假玩家：Fabric Carpet

> ⚠️ **本节已作废（`build=cleanup2`）：Carpet 依赖与相关脚本已全部删除，见 §11。**
> 以下内容是**当时的记录**，保留不改写 —— 但不要照它去加依赖。

`build.gradle` 已加（**仅开发期，不进 jar**）：

```gradle
repositories { maven { name = 'Modrinth'; url = 'https://api.modrinth.com/maven' } }
dependencies { modRuntimeOnly 'maven.modrinth:carpet:1.4.147' }
```

**版本已核实**：Carpet `1.4.147` 是**唯一**支持 MC 1.21.1 的发布
（Modrinth API `/v2/project/carpet/version?game_versions=["1.21.1"]` 只返回这一条；
之后的 Carpet 版本面向 1.21.2+）。依赖解析已验证通过。

提供 `/player <name> ...` 假玩家命令，用于在**专用服务器**上驱动验收，无需开客户端。

> ⚠️ Carpet 各子命令的准确写法请以游戏内 `/player help` 为准 ——
> 下面清单里的命令是按常见用法写的，**未在 1.21.1 + 1.4.147 上实测过**。

### 统一调试日志

`compat/STLog.kt`：`STLog.log("EnchantName", "key=value, ...")`，
输出形如 `[ST-AcidAttack] attacker=Zombie, lvl=3, rate=0.55, procced=true`。

- **默认关闭**（正常游玩一行 `[ST-*]` 都不写）。做验收时用启动开关打开：
  `-Dsimpletweaks.debug=true`（JVM 参数）或环境变量 `SIMPLETWEAKS_DEBUG=true`（等价 `=1`）—— 见 §12。
  开关在**类初始化时读一次**，是启动开关，不是运行时开关
- 日志器名 `simple_tweaks/debug`，前缀 `[ST-...]` 便于 `Select-String '\[ST-'` 抓取
- 只在**附魔真正生效的分支**打点；`PlayerTickEvent`/`LivingUpdateEvent` 这类每 tick 的处理器
  **只在状态跃迁时**打点，否则会刷爆日志并拖慢服务器

### 自动化驱动：RCON 验收脚手架（已搭好）

> ⚠️ **本节已作废（`build=cleanup2`）：`tools/acceptance.ps1` 与 `tools/rcon.ps1` 已删除，见 §11。**

**只用一条命令就能跑完整服务端验收，不需要客户端。**

| 文件 | 作用 |
|---|---|
| `tools/rcon.ps1` | 极简 RCON 客户端（TCP；协议 `[4字节LE长度][请求id][类型][正文][2字节0]`；类型 3=登录 / 2=命令 / 0=响应；登录失败返回 id=-1）。提供 `Connect-Rcon` / `Invoke-RconCommand` / `Disconnect-Rcon` |
| `tools/acceptance.ps1` | 验收驱动：生成假玩家 → 构造场景 → 发命令 → **只读取用例执行期间新增的日志字节**并断言 `[ST-*]` → 打印 PASS/FAIL |

两个脚本都被执行策略拦 `.ps1`，必须**按表达式加载**：

```powershell
cd fabric-1.21.1
Invoke-Expression (Get-Content .\tools\rcon.ps1 -Raw)
Invoke-Expression (Get-Content .\tools\acceptance.ps1 -Raw)
Invoke-AllAcceptance                 # 跑 AutoSmelt + Momentum
Invoke-AcceptanceCase -Name AutoSmelt
```

**日志只读增量**（记录用例前的文件长度，只解析之后追加的字节），所以前一个用例不可能造成后一个的假通过。

#### ⚠️ RCON 是开发期配置，发布时不要带

- 开关与密码在 **`run/server.properties`** —— `run/` 是 Loom 的运行目录，**不进 jar、不随模组发布**
- 密码用的是本地测试值 **`devtest`**（不是任何真实密码），仅监听本地回环
- 若要临时关掉：把 `enable-rcon` 改回 `false`，或清空 `rcon.password`
- Carpet 同理：`modRuntimeOnly` 只进开发期 runtime classpath，**绝不会被打进发布 jar**

---

## 🔬 服务端验收清单

命令均以**假玩家 `Bot`** 驱动。`<pos>` 请替换为固定坐标（建议 `~ ~ ~` 附近空地）。
`/give` 用 1.21 的**物品组件**语法直接带附魔，比 `/enchant` 更可靠。

### 1. AutoSmelt（缓冲式掉落接缝）

| 步骤 | 命令 |
|---|---|
| 造矿 | `/setblock <pos> minecraft:iron_ore` |
| 给镐 | `/give Bot minecraft:diamond_pickaxe[enchantments={"simple_tweaks:auto_smelt":1}]` |
| 生成假玩家 | `/player Bot spawn at <x> <y> <z> facing 0 0` |
| 看向矿石 | `/player Bot look at <pos>` |
| 挖 | `/player Bot mine once` |

**期望日志**：`[ST-AutoSmelt] ...`，含等级、被熔炼的掉落数、总经验、工具耐久消耗
**期望现象**（需开客户端或查背包）：掉落是**铁锭**而非铁矿石；工具耐久 **-1**；获得经验
**反例**：再附 `silk_touch` 后重做，应**无** `[ST-AutoSmelt]` 打点、掉落为原矿

### 2. Momentum（break-speed 接缝 + 坐标 ThreadLocal）

| 步骤 | 命令 |
|---|---|
| 给镐 | `/give Bot minecraft:diamond_pickaxe[enchantments={"simple_tweaks:momentum":3}]` |
| 放硬方块 | `/setblock <pos> minecraft:obsidian` |
| 持续挖同一块 | `/player Bot mine continuous` |
| 换相邻同类方块 | `/setblock <pos2> minecraft:obsidian` 后让 Bot 转向并继续 |

**期望日志**：`[ST-Momentum]` 在**换块时**出现 `pos` 变化与加成重置；同一块持续挖时加成递增
（只在跃迁打点，不是每 tick）

### 3. EchoShield 穿甲（自定义伤害类型）

| 步骤 | 命令 |
|---|---|
| 给甲 | `/give Bot minecraft:diamond_chestplate[enchantments={"simple_tweaks:echo_shield":3}]` |
| 打 Bot | `/damage Bot 10 minecraft:player_attack` |

**期望日志**：`[ST-EchoShield]` 含吸收量/反弹量
**关键断言**：反弹伤害**不应**被攻击者护甲减免 —— 需要对比不同护甲下的数值

### 4. Starfall 驯服宠物

| 步骤 | 命令 |
|---|---|
| 给弓 | `/give Bot minecraft:bow[enchantments={"simple_tweaks:starfall":3}]` |
| 驯狼 | `/summon minecraft:wolf <pos> {Owner:<Bot的UUID>,Tame:1b}` |
| 朝狼射 | `/player Bot look at <wolf>` → `/player Bot use once` |

**期望日志**：`[ST-Starfall]` 的候选列表**不含**该狼（被 `isOwner` 排除）

### 5. crit 三件套（`CriticalHitEvent` 接缝，尚未实现）

`EnchantCrit` / `EnchantDoubleCrit` / `EnchantCritDamage` 目前在 `deferred/`，
接缝做完后按同样方式验收：`/player Bot attack once` + `[ST-Crit]` 打点含
`vanillaCrit=` 与 `damageModifier=`。

### 待客户端验收（服务端无法覆盖）

| 项 | 原因 |
|---|---|
| 粒子常量替换的视觉效果 | 粒子只在客户端渲染 |
| GUI：`GuiEnchantInfo`、`ModGuiScreen`、`InfiniteBag` | 纯客户端 |
| 网络同步：`PacketManaPoolSync`、`PacketCelestialRing`、`PacketDamageIndicator` | 需客户端接收 |
| 伤害数字渲染 `DamageIndicatorRenderer` | `WorldRenderEvents`，客户端 |
| `EnchantCelestialBlessingHandler`（已隔离） | 依赖阶段 5/6 的网络与客户端缓存 |

---

## 🚨 验收暴露的两个**致命 Mixin 目标错误**（已修复）

这一轮验收最大的收获不是 6/6，而是**发现整个伤害层此前是死的**。两次都是同一个陷阱，
两次的静态"核实"都写下了自信但错误的结论。

### 陷阱本身：Mixin 只改写它 @Mixin 的那个类的方法体

Mixin **不会**把注入传播给子类的 override。所以 `@Mixin(X.class)` 注入 `m()` 时，
只有当**实际执行的方法就是 `X.m`** 才生效。1.21 里不少基类方法被**完整重写**（不是 wrapper，
**不调 `super`**），于是注入永远不执行。

**必须用字节码核实，不能靠"子类应该会调 super 吧"的直觉。** 可靠的核实方式：
`javap -c` 之后把**所有空白去掉**再正则搜
`invokespecial#\d+//Method<owner>\.<m>:<desc>`。
（我第一次的 grep 因为 javap 按 100 列折行而漏报，得出了"35 个类全都不调 super"的荒谬结论。）

### 错误 1：`LivingHurtEvent` 整个是死的

`EntityDamageMixin` 原本 `@Mixin(Entity.class)`，KDoc 里写着
*"LivingEntity does not override damage in 1.21"* —— **完全错误**。
`LivingEntity#damage` 是完整重写，字节码里**没有一个** `Entity.damage` 调用。

后果：活体受伤都走 `LivingEntity#damage`，注入永不触发 →
`LivingAttackEvent` / `LivingHurtEvent` **一次都没发过**，
**约 40 个基于伤害事件的处理器全是死的**。

为什么第一轮没发现：AutoSmelt 走 `Block#dropStacks`、Momentum 走 `getBlockBreakingSpeed`，
这两个方法都没有 override 子类，所以是好的 —— 2/2 的用例恰好绕开了整个伤害层。

**修复**：`@Mixin({LivingEntity.class, ArmorStandEntity.class})`。
`ArmorStandEntity#damage` 同样是完整重写，需单独列出；纯客户端类
（`ClientPlayerEntity` / `OtherClientPlayerEntity`）故意不列，因为该接缝服务端专用。
其余（`PlayerEntity`、`ServerPlayerEntity`、`ZombieEntity`、`WolfEntity`、`WitherEntity`、
`EndermanEntity`、`WardenEntity`、`RaiderEntity`、`IronGolemEntity`、`HoglinEntity`、
`EnderDragonEntity` …）都调 `super.damage`，经 `LivingEntity` 覆盖。
不存在"既是 target 又调 super"的类，所以不会重复触发。

### 错误 2：`LivingDamageEvent` 对玩家是死的

`LivingEntityApplyDamageMixin` 原本 `@Mixin(LivingEntity.class)`。全 jar 只有三个类声明
`applyDamage(DamageSource,float)`：`LivingEntity`、`PlayerEntity`、`WolfEntity`。
**`PlayerEntity#applyDamage` 是完整重写且不调 super**（自己做了 armor/absorption/setHealth
和玩家统计），于是玩家的 `LivingDamageEvent` 从未触发。

症状极具指示性：**`LivingHurtEvent` 的处理器在日志里出现，`LivingDamageEvent` 的一个都没有**。
表现就是 EchoShield / Resilience / DelayedRecovery 全都没反应。

**修复**：`@Mixin({LivingEntity.class, PlayerEntity.class})`。
`ServerPlayerEntity` 自己不声明，继承 `PlayerEntity` 的 override，所以覆盖 `PlayerEntity` 就够。

### 错误 3（预防性）：`LivingDeathEvent` 对服务端玩家是死的

按同一维度重扫其余接缝，发现 `ServerPlayerEntity#onDeath` 也是完整重写且不调 super →
`EnchantDeathProtectionHandler` / `EnchantImmortalHandler` 在专用服务器上永远不会触发。
**修复**：`LivingEntityDeathMixin` 改成 `@Mixin({LivingEntity.class, ServerPlayerEntity.class})`。

其余接缝经同一维度核实**是好的**，记录在案以免以后重复怀疑：

| 接缝 | 被注入的方法 | 结论 |
|---|---|---|
| `LivingEntityHealMixin` | `heal(F)V` | 全 jar 只有 `LivingEntity` 声明 → 安全 |
| `EntityTickMixin` | `Entity.tick()V` | `LivingEntity.tick` 调 `super.tick`；`ServerPlayerEntity.tick` → `PlayerEntity.tick` → … → `Entity.tick` 链路完整 |
| `PlayerEntityBreakSpeedMixin` | `getBlockBreakingSpeed(BlockState)F` | 只有 `PlayerEntity` 声明 |
| `EntityFallDamageMixin` | `handleFallDamage(FFLDamageSource)Z` | `LivingEntity` / `PlayerEntity` 都调 super 回到 `Entity`（`AbstractHorseEntity` 不调，但马不是任何处理器的目标） |
| `BlockHarvestDropsMixin` / `AbstractBlockBreakingDeltaMixin` | `Block#dropStacks` / `AbstractBlock#calcBlockBreakingDelta` | 静态方法，无 override 问题 |

---

## ✅ 逻辑层级验收结果（RCON，无需客户端）

**最终实测（2026-10-05，干净服务器单轮）：8/8 通过。**
（第一轮 6 条走的是已有接缝；第二轮补上 `CriticalHitEvent` 与 `PlayerDropsEvent` 两个新接缝后，
新增 2 条用例，一次跑到 8/8。）

### 第二轮：两个新接缝的验收证据

| 用例 | 结果 | 关键证据 |
|---|---|---|
| **CritEnchants** | 逻辑验证通过，待客户端完整验收 | `Crit=47 CritDamage=47 DoubleCrit=5 AoeAttack=12`。同一次挥击的三段叠加在日志里连续可见：`[ST-Crit] vanillaCritical=false, damageModifier=1.5, outcome=forced-crit` → `[ST-CritDamage] damageModifier=1.5->3.0` → `[ST-DoubleCrit] charge=1.0, roll=0.298, critDMG=1.5, damageModifier=3.0->5.0`；`[ST-AoeAttack] lvl=3, others=4, originalAmount=91.25, perTarget=54.75, targets=Foe,Brute,CritA,CritB` |
| **SoulBound** | 逻辑验证通过，待客户端完整验收 | `[ST-SoulBound] player=Bot, drops=1, saved=1, items=minecraft:diamond_sword, outcome=withheld-from-drops`；地面上的普通石头 `=True`（可证伪守卫），地面上的 soul_bound 剑 `=False` |

**Crit 那条的关键点**：Bot 站在地面上挥击 ⇒ `vanillaCritical=false`，所以走的是
`crit` **强制**暴击的支路 —— 这正是 `setCrit(true)` 要顺带 `damageModifier += 0.5f` 的原因
（否则强制暴击只会有普通伤害）。三段 `1.5 → 3.0 → 5.0` 是同一个事件被三个处理器依次加乘的结果。

**SoulBound 那条只断言了一半，而且这是刻意的。** Carpet 假玩家死亡后**直接断线、不会自动重生**，
所以 `PlayerEvent.Clone` 不会触发，`restored-on-respawn` 在服务端无法观察。
逻辑层只断言「扣留」（`withheld=1` + 剑不在掉落物里 + 石头**在**掉落物里），
「归还」留给客户端/真实玩家验收。石头那个断言是**可证伪守卫**：如果石头也没掉，
说明整个掉落流程根本没跑，那"剑没掉"就是假通过。


| 用例 | 结果 | 关键证据 |
|---|---|---|
| **AutoSmelt** | 逻辑验证通过，待客户端完整验收 | `/sttest harvest`（金矿石）→ `drops=[minecraft:gold_ingot x1]`、`toolDamage=0->1`、`xp=177->178`；`[ST-AutoSmelt] lvl=1, smelted=1/1, exp=1.0` |
| **Momentum** | 逻辑验证通过，待客户端完整验收 | `/sttest breakspeed`（黑曜石）→ `delta1=0.00533 → delta2=0.00693`、`rampRatio=1.30`；`[ST-Momentum] pos=BlockPos{x=0, y=97, z=0}, lvl=3, ticks=2, bonus=0.3, speed=8.0->10.4` |
| **EchoShield** | 逻辑验证通过，待客户端完整验收 | **Bot 掉 8.0（期望 8）、Foe 掉 24.0（期望 24）**；`[ST-EchoShield] lvl=20, ratio=0.59999996, buffer=5.9999995 → 11.999999 → outcome=absorbed-and-reflected, reflected=11.999999` |
| **Starfall** | 逻辑验证通过，待客户端完整验收 | `stars=3`、`star-impact=3`、Prey 掉 62.47；**驯服狼 Buddy 掉 0**，且它距最近爆心 **0.789 格（半径 5.5）** |
| **AttackEnchants** | 逻辑验证通过，待客户端完整验收 | 12/12 必需标签全部命中，共 **16** 个：`BloodLust, MotionBonus, SuperKnockback, Execute, FireMaster, TrueDamage, Combo, HealingBlade, GrievousWounds, HeavenlyPunishment, CombatMaster, Healer` + 额外 `AcidAttack, EffectBonus, ExperienceStealer, ChargedStrike` |
| **DefenseEnchants** | 逻辑验证通过，待客户端完整验收 | `DamageReduction, Resilience, DelayedRecovery` 全中；Bot 实受 **14.96/40**；`[ST-DelayedRecovery] outcome=payout` 连续 6 条显示分期回血 183.2→188.4 |

**Momentum 那条同时证明了 `pos` 精确性** —— 日志里出现的是真实坐标 `{x=0, y=97, z=0}`，
说明 `AbstractBlock#calcBlockBreakingDelta` → ThreadLocal → `BreakSpeed.pos` 这条链路真的通了
（这正是我最初担心拿不到、后来靠字节码分析解决的那个点）。

### EchoShield 那条是**可证伪**的穿甲证明

Foe 是 500 血僵尸，**全套下界合金（20 护甲 + 12 韧性）+ 抗性 IV（80% 减伤）**。
若 `simple_tweaks:reflection` 没进 `bypasses_armor` / `bypasses_resistance`，
同样 24 点原始伤害落地只会剩 **约 1.2**。实测正好 **24.0** —— 所以"穿甲"是实测不是推断。

测试伤害类型选 `minecraft:magic` 也有依据：它**在** `#minecraft:bypasses_armor` 里
（从服务端 jar 抽出 `data/minecraft/tags/damage_type/bypasses_armor.json` 核对过），
所以 `LivingHurtEvent` 的 pre-armor 值与 `LivingDamageEvent` 的 post-armor 值相等，
缓冲算术才精确成立；同时它**不在** `bypasses_enchantments` 里，所以护甲上不能带保护附魔。

### Starfall 那条把"狼没被打"从巧合变成了证据

只断言"狼血量不变"是不够的 —— 狼没掉血也可能只是因为它**根本不在爆炸范围内**。
脚本现在会从 `star-impact` 日志解析 `starX/starY/starZ/radius`，算出狼到最近爆心的距离
并断言 `< radius`：实测 **0.789 < 5.5**，所以"没被打"只能归因于
`TameableEntity#isOwner` 那条补回来的子句（1.21 的 `isTeammate` 只认计分板队伍）。

### 驱动过程中发现的坑（都不是 mod bug，但会误导验收）

1. **Carpet 的假玩家默认是创造模式** —— 创造模式会**静默跳过物品耐久消耗**，
   导致 AutoSmelt 的耐久断言恒为 `0->0`；创造模式还会让 `PlayerEntity#damage` 提前 return，
   伤害类用例全死。必须先 `gamemode survival Bot`。
2. **单块铁矿石的经验是 0.7，`totalExp.toInt()` 截断后为 0** ——
   原逻辑是 `val exp = totalExp.toInt(); if (exp > 0) player.addExperience(exp)`，
   所以铁矿石**正确地**不给经验。最初我把它当失败，是我的断言写错了。
   验收改用**金矿石（1.0 经验）**，`xp=0->1` 才能证明经验链路真的通了。
3. **Carpet 允许同名假玩家共存，代价是灾难性的。** 一旦有 3 个 `Bot` 在线，
   `data get entity Bot ...` 返回 `No entity was found`（名字有歧义），
   而 `player Bot stop` 在这种状态下是**静默空操作**、`/kick` 会被 Carpet 立刻重连回来。
   这一条**伪装成"bot 生成失败"，一次带偏了 4 个用例**。
   唯一可靠做法：生成前确认在线数为 0、生成后确认恰好为 1，否则**大声失败**。
   真正能删掉假玩家的是 `kill @e[type=player,name=Bot]`（实测有效，
   即使只有 1 个在线时 `player Bot stop` 也依然是空操作）。
4. **`/damage` 不会绕过无敌帧。** 1.21.1 的 vanilla 数据包里**没有** `bypasses_cooldown` 标签，
   所以 20 tick 内的第二击在事件之前就被丢弃。多次打击的用例必须间隔 > 20 tick（脚本用 1300–1600 ms）。
5. **发现错误 2 的方式值得记住**：不要靠猜 —— 直接看
   "哪些事件的处理器出现在日志里、哪些没有"，这个信号把范围锁定得非常准
   （`LivingHurtEvent` 有、`LivingDamageEvent` 没有 ⇒ 问题必然在 `applyDamage` 那一层）。
6. **同优先级的注册顺序是**有语义的**，而按 tier 分组会破坏它。**
   `ForgeEventBus.sortBy` 是**稳定排序**，所以同 `EventPriority` 的监听器按 `handlerList` 顺序跑。
   1.12.2 的列表是一个**扁平且刻意**的顺序（`Crit` 在 96、`CritDamage` 在 97、`DoubleCrit` 在 100），
   而我最初按 common/uncommon/rare/epic/legendary 分组重排，结果 `CritDamage`（epic 块）跑到了
   `Crit`（legendary 块）**前面** —— `CritDamage` 的 `if (lvl > 0 && e.isCrit)` 永远看到 `isCrit=false`，
   强制暴击时**静默失效**。修法是把 `EnchantCritDamageHandler` 挪回 `EnchantCritHandler` 紧跟其后，
   并在列表里写明原因。**凡是两个处理器监听同一事件且优先级相同，就要回去对照 1.12.2 的顺序。**
7. **`attackCharge` 必须在挥击开始时捕获，不能在事件里活读。**
   1.12.2 用一个 mixin `@Redirect` 抓住 `getCooledAttackStrength(0.5F)` 的返回值存进
   `AttackChargeAccessor`；1.21 移植版把它简化成"活读 `getAttackCooldownProgress(0.5F)`" ——
   对 `AttackEntityEvent` 那个调用点是对的，但 `CriticalHitEvent` 发生在 `PlayerEntity#attack`
   **重置冷却计时器之后**，活读只有 ≈0.5，于是 `DoubleCrit` 的 `charge >= 0.848` 门**每一刀都被拒**。
   现在充电值在 `PlayerEntity#attack` 的 HEAD 捕获并挂在 `CriticalHitEvent.attackCharge` 上。
   症状很好认：日志里 `DoubleCrit` **一次都不出现**。
8. **`/kill` 的行为取决于目标是不是 `LivingEntity`** —— 我一开始只看了 `Entity#kill()` 就下结论，**错了**。
   1.21.1 yarn 字节码事实：
   - `Entity#kill()` → `remove(RemovalReason.KILLED)`，**不走**伤害管线
   - **`LivingEntity#kill()` 是顶层重写** → `this.damage(this.getDamageSources().genericKill(), Float.MAX_VALUE)`
     —— **完整**走 伤害 → `onDeath` → 掉落 管线

   玩家和 Carpet 假玩家都是 `LivingEntity`，所以 `/kill <player>` **等价于**
   `/damage <player> 1000 minecraft:generic_kill`（用的就是同一个 `genericKill` 伤害类型）。
   实机日志证据：`/kill` 触发了 `[ST-SoulBound] drops=4, withheld-from-drops` 与 `restored-on-respawn`。

   **当初观察到"`/kill` 不掉落"的真因，大概率是假玩家当时处于创造模式**（创造模式玩家死亡不掉落），
   而这一点已由 `gamemode survival Bot` 单独修好。`/damage generic_kill` 仍然可用，但**理由不是原来那条**。

   > 教训（同一失败类第 4 次）：**override 审计不只适用于 mixin 目标**。读某个方法的字节码之前，
   > 必须先确认"实际 dispatch 到哪个类"，否则读得再对，结论也是错的。
   > 前三次：mixin 目标覆盖、同优先级监听器顺序、`alive`/`wasDeath` 反转。
9. **`@e[type=player]` 选不中 Carpet 假玩家**（`@a[limit=1]` 能），
   而 `player <name> stop` 在假玩家确实在线时也会回 "Can only manipulate existing players"。
   移除假玩家唯一实测有效的是 `kill @a[name=<name>]`。

### 分层结论（按要求区分）

- **逻辑验证（服务端，真实原版入口驱动）**：上面 6 个用例**全部通过**
- **完整验收（客户端）**：**尚未做** —— 见下方清单

### ⚠️ 加载验收脚本必须显式指定 UTF-8（或带 BOM）

Windows PowerShell 5.1 的 `Get-Content -Raw` 按 **ANSI/GBK** 读取，
会把脚本里的中文注释变成乱码并**直接导致语法解析失败**（实测报 `UnexpectedToken`）。
现在 `tools/*.ps1` 已写成**带 UTF-8 BOM**，所以 `Get-Content` 也能正确读；
但显式写法仍然最稳妥：

```powershell
Invoke-Expression ([System.IO.File]::ReadAllText((Resolve-Path ".\tools\acceptance.ps1"), [System.Text.Encoding]::UTF8))
```

**另外两个 PowerShell 陷阱**（都实际踩过）：

- **双引号字符串里 `""` 不是转义**。PowerShell 用反引号（`` `" ``）；`""` 是**单引号**字符串的规则。
  在双引号串里写 `""simple_tweaks:x""` 会提前结束字符串并把后面的内容当代码。
- **`-f` 的优先级高于 `+`**，且会把 SNBT 自己的 `{` `}` 当成格式项。
  所以 `'{...}' + '{0}' -f $x` 既因为只格式化最后一段而报
  *"Input string was not in a correct format"*，又需要把每个字面花括号写成 `{{`。
  最终改成用 `[char]34` / `[char]39` 拼 SNBT，彻底绕开。

**Momentum 那条同时证明了 `pos` 精确性** —— 日志里出现的是真实坐标 `{x=0, y=98, z=0}`，
说明 `AbstractBlock#calcBlockBreakingDelta` → ThreadLocal → `BreakSpeed.pos` 这条链路真的通了
（这正是我最初担心拿不到、后来靠字节码分析解决的那个点）。

---

## 🔬 完整验收清单（客户端，最后一次性做）

客户端验收：`.\gradlew.bat runClient`。
以下都是**静态验证已通过、运行时行为未验证**的项。

### AutoSmelt（新的缓冲式接缝 `BlockHarvestDropsMixin`）

1. 给镐附魔：`/enchant @s simple_tweaks:auto_smelt 1`
2. 挖**矿石**（铁矿石/金矿石等）
3. 检查三点：
   - **掉落是否直接变成锭**（而非原矿）—— 验证逐掉落捕获生效
   - **工具耐久是否 -1**（每熔炼一个掉落扣 1）—— 验证耐久代价保留
   - **是否获得熔炼经验** —— 验证经验保留
4. 反例：再附上**精准采集**后挖，应**不**熔炼（原逻辑有排除）
5. 安全降级：若第 3 步"掉落没变锭"，说明捕获未生效，但**不会丢物品、不会崩服**
   （事件收到空列表 → 处理器直接返回，原版掉落已正常生成）

> 这条同时验证 `PlayerDropsEvent` 要复用的**同一套缓冲模式** ——
> 若模式有问题，修一处能同时修两个。

### Momentum（新的 break-speed 接缝 + 坐标 ThreadLocal）

1. 给镐附魔：`/enchant @s simple_tweaks:momentum 3`
2. **持续挖同一块**（选硬度高的：黑曜石/深板岩），观察速度是否**逐渐变快**
3. 换一块**相邻同类**方块，速度应**重置**（回到无加成）—— 这是验证 `pos` 精确性的关键用例
4. 反例：不带该附魔时速度不变

### 更早批次的待验项

- **`EchoShield` 穿甲修复**：带该附魔的护甲被攻击时，反弹伤害**不应**被攻击者护甲减免
  （新自定义伤害类型 `simple_tweaks:reflection`，其 4 个数据包文件已静态验证加载正常）
- **`Starfall` 驯服宠物**：射出的星辰**不应**伤害自己的驯服狼
- **粒子常量替换的视觉确认**：`CRIT_MAGIC→ENCHANTED_HIT`、`EXPLOSION_NORMAL→POOF`、
  `SPELL_WITCH→WITCH` 等（1.13 扁平化改名，语义对应但未实测视觉效果）

### 当前精确状态

- **已注册处理器：40**
- **`deferred/`：9**（AoeAttack、CelestialBlessing、CritDamage、Crit、DoubleCrit、Multishot、PiercingArrow、SoulBound、TrackingArrow）
- **仍未处理：3** —— `AutoSmelt`（掉落表改造）、`Momentum`（BreakSpeed 缺 `pos`）、`InfinitePower`（hard 组）
- **声明式无需处理器：4** —— `AntiKnockback`、`SwiftSneak`、`Unbreakable`、`PrismaticBlessing`（均已写入 JSON 并生效）

### ⚠️ 我的批处理计划漏掉了 mystery + unique 层

计划写的是「common+uncommon → rare+epic → legendary → mythic」——
**完全没有分配 mystery(2) 和 unique(1)**。这三个处理器从未被任何批次覆盖：

- `EnchantCelestialBlessingHandler`（mystery，约 530 行 mana 状态机，全项目最重）
- `EnchantHeavenlyPunishmentHandler`（mystery）
- `EnchantReForgeHandler`（unique）

三者的接缝**都已实现**（`AttackEntityEvent`/`LivingDamageEvent`/`LivingEvent`/`TickEvent`/`PlayerEvent`），
所以可以直接移植。**这是漏排，不是被阻塞。**

### 精确的剩余清单

`1.12.2 handlerList` 56 项 − 已注册 38 − `deferred/` 8 = **10**，其中：

| 类别 | 数量 | 说明 |
|---|---|---|
| 声明式，无需处理器（JSON 已承载） | 4 | `AntiKnockback`、`SwiftSneak`、`Unbreakable`、`PrismaticBlessing` ✅ 已完成 |
| 漏排的 mystery + unique | 3 | 见上，接缝齐备 |
| 需要新接缝/设计的 | 3 | `AutoSmelt`（`LootTableEvents.MODIFY`）、`Momentum`（BreakSpeed 缺 `pos`）、`InfinitePower`（hard 组） |

### 第四批（mythic）标记的语义差异

- **🔴 `EnchantEchoShieldHandler` 的 `ignoreArmorAndPotion = true` 现在是惰性的。**
  它的两个反弹调用点**显式传 `true`**（与 EchoShot 的 `false` 不同），
  所以 1.12.2 的"反弹原始伤害"变成"反弹被攻击者护甲减免后的伤害"——**这是真实的行为回退**。
  根因是共享的 `util/PlayerUtils.runPlayerAttack` 无法跳过护甲（1.21 无 `applyPotionDamageCalculations`）。
  要修需改这个共享 helper（影响其他批次），或为反弹单独实现绕过护甲的伤害路径。
- **`isOnSameTeam` → `isTeammate` 丢了"已驯服宠物归主人"那一支**（1.21 的 `isTeammate` 只看记分板队伍）。
  实际影响：Starfall 现在可能打到射手自己的驯服狼。
- **`EnchantKillAuraHandler` 的暴击丢失**（`allowCrit` 惰性，同 crit 接缝）。
- `EnchantKillAuraHandler` 的 `getModifierForCreature` 改用 `EnchantmentHelper.getDamage(...)`：
  **是超集** —— 旧调用只看原版 Sharpness/Smite/Bane（按生物类型），
  新调用会评估所有 `minecraft:damage` 效果（含本模组自己的伤害附魔）。数值等价性未验证。
- `EnchantDeathProtectionHandler`：`getEnchantments` 不可变 → 用 `ItemEnchantmentsComponent.Builder` 重建
  （与 `VoidProtection` 同一模式），等级结果一致。
- 大量粒子/音效常量按 1.13 扁平化改名（`SPELL_WITCH→WITCH`、`CRIT_MAGIC→ENCHANTED_HIT` 等），
  `state.blocksMovement()` 在 1.21.1 已 deprecated（保留，只产生 1 条 `w:`）。
| 3-hard | 5 个被昂贵接缝阻塞的处理器 + `CriticalHitEvent` / `PlayerDropsEvent` | ⏳ |

### 阶段 3 第一批的实际范围（与预估的差异）

预估 42 个不被阻塞，但实际第一批发现在**两个分类阶段看不到的问题**：

1. **`EnchantAoeAttackHandler` 其实是被阻塞的** —— 它调用 `util/PlayerUtils.runPlayerAttack`，
   而后者内部构造 `CriticalHitEvent` / 调 `ForgeHooks.getCriticalHit`。
   分类阶段是**逐处理器**看的，看不到这种**跨文件**依赖。已隔离到 `deferred/`，归入 hard 组。
2. **6 个调用点依赖尚未移植的 util** —— 所以本批必须连带移植
   `util/EntityUtil.kt`（`relativeSpeed`、`fireTime`）与 `util/PlayerUtils.kt`
   （`getRandomArmor`、`tp`、`leggings`/`chestplate`/`boots`/`helmet`）。
   `PlayerUtils.kt` 的其余部分（`runPlayerAttack` 等）故意未移植。

因此第一批净增 **12 个处理器 + 2 个 util 文件**，并**删除** 2 个源文件
（`EnchantAntiKnockbackHandler`、`EnchantSwiftSneakHandler` —— 已由 JSON 声明式表达）。

### 移植中发现的、需要人工确认的语义变化

- **`EnchantCombatMasterHandler`：连击状态的持久性下降。**
  1.12.2 用 Forge 的 `Entity#getEntityData()`（可持久化的 per-entity NBT），1.21 没有对应物。
  当前实现是 `WeakHashMap<Entity, NbtCompound>` —— **状态随实体实例存活，不再落盘**。
  对"连击计数"这种短时状态影响很小，但登出/重登会丢。要恢复持久化应改用
  Fabric 的 `AttachmentRegistry.createPersistent(...)`。**这是本批唯一真正的语义降级。**
- `EnchantVoidProtectionHandler`：1.21 **没有 `EnchantmentHelper.setEnchantments`**，
  且 `getEnchantments` 不再返回可变 map，所以"消耗一级"改为
  `ItemEnchantmentsComponent.Builder` + `stack.set(DataComponentTypes.ENCHANTMENTS, …)` 重建。
- `EnchantAoeAttackHandler`（已隔离）：`canBeAttackedWithItem()` → `isAttackable`，
  是**最接近的近似**而非严格等价（该 flag 由非生物实体覆写为 false，也正是原版 `attack` 检查的）。
- `EnchantSaturationHandler`：`foodStats.saturationLevel` → `hungerManager`，
  注意 setter **改名**为 `setSaturationLevel`（没有 `setFoodSaturationLevel`）。

### 阶段 2 的保真取舍（写进 JSON 时故意这么选的）

1. **权重是每档 2，不是 1.5。** 原代码 `(10 - rarity * 1.5.roundToInt())` 里 Kotlin 的 `.` 优先级高于 `*`，
   所以实际算的是 `10 - rarity * 2`（作者本意应该是 1.5）。**保留了实际行为**而非本意：
   COMMON 10 / UNCOMMON 8 / RARE 6 / EPIC 4 / LEGENDARY 2 / MYTHIC 1 / MYSTERY 1 / UNIQUE 1（JSON 不允许 0）。
2. **`max_cost.base = 65535, per_level = 0`。** 原 `getMaxEnchantability()` 返回 `Int.MAX_VALUE`
   （即无上限）。65535 足以复现"只要满足 min_cost 就永远可选"，又不会在 vanilla 的造价运算里溢出。
3. **`infinite_power` 的 `min_cost` 也是 65535**，因为它原样返回 `Int.MAX_VALUE` ——
   所以它**永远不可能从附魔台滚出**，与原行为一致；且它是宝藏，本来也不进 `in_non_treasure`。
4. **`anvil_cost` 是新值**（`2 + rarity*2`，上限 20）。原工程有自定义 `AnvilCostHandler`，
   vanilla 这个字段在 1.12 没有对应物，所以这是新引入的默认值。
5. **`infinite_power` 不发 `minecraft:damage` 组件**：它的 `itemExtraDamage` 返回 `Int.MAX_VALUE`，
   无法用组件表达（那是"秒杀"语义，由处理器代码负责）。
6. **4 个宝藏附魔**：`celestial_blessing`、`heavenly_punishment`（MYSTERY，基类 `rarity>=6` 规则）、
   `infinite_power`、`reforge`（各自覆写了 `isTreasureEnchantment()`）。
   其余 **52 个**写进 `data/minecraft/tags/enchantment/non_treasure.json` 才会出现在附魔台。
   ⚠️ 注意 `reforge` 的 id 是 **`reforge`**，不是 `re_forge`。

### 暂缓的接缝（含原因，别当成遗漏）

| 事件 | 用量 | 为什么暂缓 |
|---|---|---|
| `CriticalHitEvent` | 5 | 1.21 里暴击判定内联在 `PlayerEntity#attack(Entity)` 中，且 `ForgeHooks.getCriticalHit` 已不存在（`PlayerUtils.kt:145` 还在调它）。要还原 `setCrit`/`damageModifier` 语义需对该方法做逐条字节码注入，风险最高，单独作为一批处理 |
| `BlockEvent.HarvestDropsEvent` | 2 | 处理器直接改 `List<ItemStack>`（`drops.clear()/addAll()`），必须**缓冲**：拦下掉落生成 → 收集成列表 → 发事件 → 生成存活部分。Fabric 无对应钩子 |
| `PlayerInteractEvent.LeftClickEmpty` | 1 | 纯客户端输入事件，Fabric 无对应物，需客户端 mixin 或输入钩子 → 归入客户端批次 |
| `ArrowLooseEvent` | 1 | 仅 `EnchantMultishotHandler` 用；而 1.21 的 `minecraft:projectile_count` 效果组件可能直接cover它 → **见下方"可能不需要移植的处理器"** |
| `FMLServerStartingEvent` | 2 | 它唯一的用途是 `registerServerCommand(ICommand)`，而 `ICommand` 在 1.21 已不存在（改用 Brigadier）。做成 Forge 形状反而是误导 → 归入阶段 7 命令批次 |
| `CommandEvent` / `AnvilUpdateEvent` | 2 | 分别需 `CommandManager` / `AnvilScreenHandler` mixin，与阶段 7 / 阶段 4 的既有 mixin 重定向一起做更省 |
| `RenderWorldLastEvent` / `InputEvent` / `ItemTooltipEvent` | 3 | 纯客户端，归入阶段 6 |
| `PlayerEvent.BreakSpeed` | 2 | **需补**：`ToolHandler` + `EnchantMomentumHandler`。本工程 `compat/event/PlayerEvent.kt` 目前没有这个子类；Fabric 侧需等价挖速钩子（`PlayerEntity.getBlockBreakingSpeed(BlockState)` = `method_7351`，可做 mixin） |
| `PlayerEvent.HarvestCheck` | 1 | **需补**：仅 `ToolHandler`。Forge 里是带 `successfulHarvest` 可写字段的 `GenericEvent`，1.21 无对应钩子 |
| `PlayerEvent.SaveToFile` | 1 | **不需要加**：唯一使用者 `SaveHandler` 已判定 `DROPPABLE`（Fabric Attachment 的 `persistent(codec).copyOnDeath()` 覆盖其职责） |

### 掉落接缝的真实需求面（grep 实测）

| 接缝 | 使用者 | 是否真需要 |
|---|---|---|
| `LivingDropsEvent` | `DropHandler`、`InfiniteContainerHandler` | 仅 infinitepower 模块 |
| `PlayerDropsEvent` | `EnchantSoulBoundHandler`、`SoulBindHandler` | **`EnchantSoulBoundHandler` 确实需要**：已核实 `prevent_equipment_drop` 只"阻止掉落"（原版给消失诅咒用，物品就此消失），**不会保留/归还**物品，语义相反。`SoulBindHandler` 则可用 `PlayerEvent.Clone` 绕开 |
| `BlockEvent.HarvestDropsEvent` | `DropHandler`、`EnchantAutoSmeltHandler` | `DropHandler` 建议放弃；`AutoSmelt` 有 **Fabric 原生替代**：`LootTableEvents.MODIFY` + 自定义 loot function，**完全绕开 mixin** |
| `ArrowLooseEvent` | `EnchantMultishotHandler` | 待 rare+epic 批次定论（`minecraft:projectile_count` 很可能直接覆盖） |
| `CriticalHitEvent` | `EnchantCritHandler`、`EnchantDoubleCritHandler`、`EnchantCritDamageHandler` | **确实需要**：没有任何组件能读取或修改暴击状态/倍率，Fabric 也无对应事件。可降级为声明式（`damage` + `multiply` 1.5 + `random_chance(enchantment_level)`），但**会丢掉暴击粒子与 DoubleCrit 联动**。需你决定要不要保真 |

### ✅ 阶段 2 的机械规则：`itemExtraDamage` → `minecraft:damage`

原 `ModEnchantments.itemExtraDamage`（喂给 1.12 的 `calcDamageByCreature`）对应的正是
1.21.1 的 `minecraft:damage` 组件，公式为：

```
倍率 = (0.2 + category.rarity / 20.0)
"minecraft:damage": [{ "effect": { "type": "minecraft:add", "value": {
      "type": "minecraft:linear", "base": 倍率, "per_level_above_first": 倍率 } } }]
```

`linear` 取 `base == per_level` 时，等级 n 得到 `n × 倍率`，正好等价于原来的 `倍率 × level`。

按分类换算好的倍率：

| 分类 (rarity) | 倍率 | 备注 |
|---|---|---|
| COMMON (0) | 0.2 | 已用于 `acid_attack.json` ✔ |
| UNCOMMON (1) | 0.25 | |
| RARE (2) | 0.3 | |
| EPIC (3) | 0.35 | |
| LEGENDARY (4) | 0.4 | |
| MYTHIC (5) | 0.45 | |
| MYSTERY (6) | 0.5 | |
| UNIQUE (-1) | — | 非 SWORD 类，无此加成 |

**特例覆盖**（不走默认公式）：`EnchantCombo` 0.15×L、`EnchantCelestialBlessing` 2.0×L、
`EnchantHeavenlyPunishment` 5.0×L。

> ⚠️ 注意两处**数值不等价**，写 JSON 时必须留意：
> - `HeavenlyPunishment` 在 1.12 是走 `LivingDamageEvent` **护甲后**乘算，
>   而 `minecraft:damage` 是**护甲前**基础伤害乘算 → 两者数值不同，不能声称等价。
> - `EnchantEchoShield` 不要"代码减伤 + JSON `damage_protection`"叠加，会双减（0.75×level）。

**结论性判断**：这三个最贵的掉落 mixin，其需求**几乎全部集中在 `EnchantInfinitePowerHandler`**（它要 ×(64+looting) 掉落倍率、全背包灵魂绑定）。效果组件里**不存在任何"掉落数量倍率"**。因此建议：**砍掉 `EnchantInfinitePowerHandler` 的掉落倍率功能，或整体延后该模块** —— 一次性省掉 3 个高成本 mixin 加一个高成本网络/GUI 模块。这需要你拍板。

### 阶段 2.5 已确认的语义陷阱（写 JSON 时会踩）

- **`damage_item` / `change_item_damage` 只作用于"附魔物品自身"**，不是任意实体的物品。
  已从原版 `thorns.json` 独立验证：它出现在 `post_attack` 里且 `enchanted: victim`，
  打的是**受害者自己的胸甲**。所以 `EnchantArmorBreakerHandler`（打目标护甲）与
  `EnchantAcidAttackHandler`（打目标武器）**没有声明式替代**，必须保留代码。
- **`damage_immunity` 是常驻免疫**，不能表达"救一次免摔伤"（`EnchantVoidProtectionHandler` 的语义），
  强行用会变成永久免摔伤。
- **`periodic_tick` 的间隔只能是常数**，所以 `EnchantSaturationHandler` 的 `60 - 5·lvl` 间隔
  无法声明式表达。
- **`EnchantEchoShieldHandler` 不要"代码减伤 + JSON `damage_protection`"叠加**，会双减（0.75×level）。
- 判 `KOTLIN` 的**共同根因**：伤害系数依赖实时实体状态（相对速度、已损失生命比例、
  目标药水效果数量）或跨事件持久状态（连击数、等级消耗、上次方块坐标），
  而 `damage`/`damage_protection` 的 `LevelBasedValue` **只吃附魔等级**，谓词只能做布尔筛选。

### 阶段 2.5 得到的可执行结论（infinitepower 批次）

- **`SoulBindHandler` 不需要 `PlayerDropsEvent`**：改用**已实现**的 `PlayerEvent.Clone`
  （桥自 `ServerPlayerEvents.COPY_FROM`）读旧玩家背包再塞回即可 —— 少做一个高成本接缝。
- **`DropHandler` 性价比最差**：效果组件里**没有任何"掉落数量倍率"**（`equipment_drops` 是装备掉落*概率*，
  `*_experience` 只管经验）。如果 1b-2b 的掉落接缝被砍掉，这个文件应当**直接放弃移植**。
- **`InfiniteContainerHandler` 把两件事混在一起**（打开 GUI + 自动拾取），建议拆成两个类；
  其自动拾取与 `DropHandler` 抢同一个掉落事件，应合并为单一掉落入口。
- **`SaveHandler` 可删**：Fabric Attachment 的 `persistent(codec).copyOnDeath()` 同时覆盖它的两个职责，
  且 `PlayerEvent.SaveToFile` 在 1.21 没有对应物、也不需要。
- **`minecraft:prevent_equipment_drop`（消失诅咒的原语）是"销毁物品"**，
  与 `SoulBindHandler` 要的"保留物品"语义**相反**，不能拿它替代。
- **`EnchantInfinitePowerHandler` 必须先拆分**才能分批编译：它持有 `ATTACK_SPEED_MODIFIER` /
  `AOE_RANGE` / `LASER_*` 常量，被 `FlightHandler`、`DropHandler`、`PacketLaser` 共同引用；
  且 `init()` 用了仅 Forge 有的 `Loader.instance().indexedModList` 与 `NetworkRegistry.registerGuiHandler`。

### ⚠️ 重要：一部分处理器可能根本不需要移植

1.21 的附魔效果组件是**声明式**的。下面这些处理器在 1.12.2 里要靠手写事件代码实现，
而在 1.21 用 JSON 效果组件就能表达，**移植它们等于白写代码**：

| 处理器 | 1.21 等价声明 |
|---|---|
| `EnchantMultishotHandler` | `minecraft:projectile_count` |
| `EnchantPiercingArrowHandler` | `minecraft:projectile_piercing` |
| `EnchantUnbreakableHandler` | `minecraft:item_damage` |
| `EnchantVitalityHandler` | `minecraft:attributes`（属性效果组件，主项线性；二次项/阶梯项仍需代码 → 实为 PARTIAL） |
| ~~`EnchantExperienceStealerHandler`~~ | ❌ **此猜测已被推翻**：`mob_experience` 只在**击杀结算**时给经验，而该处理器是「每次命中按伤害 10%×等级抽经验，并从玩家目标身上等量扣除」。1.21.1 **没有任何加/扣经验的原始操作**，必须保留代码（接缝 `LivingHurtEvent` 已有，成本低） |
| `EnchantTrueDamageHandler` | 部分可用 `minecraft:damage`（但 1.12 走护甲后、组件走护甲前，数值不等价） |
| `EnchantFireMasterHandler` | 部分可用 `minecraft:ignite` |
| `EnchantAoeAttackHandler` / `EnchantHealingBladeHandler` | 部分可用 `minecraft:post_attack` + `damage_entity` |

共 **4 个可完全删除代码**：`AntiKnockback`、`SwiftSneak`、`Unbreakable`、`PrismaticBlessing`。
另有 **12 个 PARTIAL**、**1 个 DROPPABLE**（`SaveHandler`）。

**所以阶段 3 之前应该先做一次"处理器 → 声明式 or 保留代码"的分类**，
把所有能声明式表达的挑出来，只写 JSON。这能直接砍掉阶段 3 和 1b-2b 的一部分工作量 ——
也意味着**上面暂缓的接缝里有一些可能永远不需要做**（例如 `ArrowLooseEvent`）。

分类依据：1.21 可用的效果组件清单见 `data/minecraft/enchantment/*.json` 原版样例
（已从缓存的 `minecraft-merged.jar` 抽出过 `sharpness.json`）。
| 2 | 55 个附魔 JSON（含 `non_treasure` 标签、55 条 lang） | ⏳ |
| 3 | 55 个处理器，按稀有度分 6 批 | ⏳ |
| 4 | 19 个既有 Mixin 重定向（风险最高） | ⏳ |
| 5 | 网络层 `PacketCodec` 重写 | ⏳ |
| 6 | 客户端 GUI / 渲染 / 粒子重写 | ⏳ |
| 7 | 命令 / 工具类 / 资源收尾 / 打包 | ⏳ |

### 阶段 4 的 Mixin 重定向清单（待办）

| 旧 | 新目标 | 风险 |
|---|---|---|
| `MixinEnchantmentHelper` | `EnchantmentHelper` | 🔴 极高：`calcItemStackEnchantability`→`calculateRequiredExperienceLevel`、`buildEnchantmentList`→`generateEnchantments`，且权重/最小附魔数逻辑大半作废，需重设计 |
| `MixinContainerEnchantment` | `EnchantmentScreenHandler` | 🟠 |
| `MixinContainerEnchantmentPreview` | `EnchantmentScreenHandler` | 🟠 |
| `MixinContainerRepair` / `MixinGuiRepair` | `AnvilScreenHandler` / `AnvilScreen` | 🟠 |
| `MixinEntityPlayerSP` | `ClientPlayerEntity` | 🟡 |
| `MixinMinecraft` | `MinecraftClient` | 🟡 |
| `MixinEntityRenderer` | `GameRenderer` | 🟡 |
| `MixinNetworkManager` | `ClientConnection` | 🟡 |
| `MixinEntityPlayer` / `MixinEntityPlayerAttack` | `PlayerEntity#attack` | 🟡 |
| `MixinEntitySetDead` | `Entity#remove(Entity.RemovalReason)`（**不是** `setRemoved`，已核实 = `method_5650`） | 🟢 |
| `MixinWorldRemoveEntity` | `World#removeEntity` | 🟢 |
| `EntityArrowPiercingMixin` | `PersistentProjectileEntity` | 🟢 |
| `accessor/EntityArrowAccessor` | — | ⚪ **可直接删除**：1.21 已有公开 `getPierceLevel`/`setPierceLevel` |
| `accessor/EntityFireTimeAccessor` | — | ⚪ **可直接删除**：已核实 `Entity.getFireTicks`/`setFireTicks` 公开（`method_20802`/`method_20803`） |
| `accessor/EntityLivingBaseAccessor` | `LivingEntity` | 🟡 需逐个确认是否已公开 |

---

## 7. 目录结构（阶段 0）

```
fabric-1.21.1/
├─ build.gradle / settings.gradle / gradle.properties
├─ gradle/wrapper/            (jar 与 gradlew 从 1.12.2 工程复制，distributionUrl 改为 8.11.1)
├─ tools/yarn-query.ps1       离线 Yarn 名称查询
└─ src/main/
   ├─ java/dev/firefly/simpletweaks/
   │  ├─ compat/WorldSide.java              isClient 的无歧义封装
   │  └─ mixin/EntityDamageMixin.java       LivingHurtEvent 接缝
   ├─ kotlin/dev/firefly/simpletweaks/
   │  ├─ SimpleTweaks.kt                    入口（ModInitializer）
   │  ├─ compat/ForgeEventBus.kt            Forge 事件总线替身
   │  ├─ compat/EventUtils.kt               扩展函数（invalid/attacker/target/cancel/chance）
   │  ├─ compat/event/{Event,SubscribeEvent,LivingHurtEvent}.kt
   │  ├─ core/Listenable.kt
   │  ├─ enchantments/ModEnchantmentKeys.kt RegistryKey 常量表
   │  ├─ enchantments/handlers/common/EnchantAcidAttackHandler.kt
   │  └─ util/EnchantmentsUtil.kt
   └─ resources/
      ├─ fabric.mod.json / simple_tweaks.mixins.json / pack.mcmeta
      ├─ data/simple_tweaks/enchantment/acid_attack.json
      ├─ data/minecraft/tags/enchantment/non_treasure.json
      └─ assets/simple_tweaks/lang/{en_us,zh_cn}.json
```

---

## 8. 全量迁移的成本账

| 阶段 | 输入 token | 输出 token |
|---|---|---|
| 0 骨架 + 切片 | ~35K | ~30K |
| 1 核心框架 | 90K | 60K |
| 2 附魔 JSON ×55 | 30K | 20K |
| 3 处理器 ×55 | 300K | 180K |
| 4 Mixin ×19 | 300K | 180K |
| 5 网络 | 60K | 45K |
| 6 客户端 | 150K | 150K |
| 7 构建/资源/收尾 | 40K | 35K |
| 构建修复循环 | 500K | 250K |
| **合计** | **~1.5M** | **~0.95M** |

按 `deepseek-flash` 定价（输出 $1.20/1M 高峰、输入未命中 $0.30/1M、命中 $0.006/1M）：

- 期望 **¥6–12**
- 悲观尾部（构建/启动反复失败）**¥25–35**

成本几乎全在**输出/思考 token**（单价是输入 cache-miss 的 4 倍、cache-hit 的 200 倍），
所以"少返工"比"少读代码"重要得多 —— 这也是先把事件接缝和名称核实做扎实的原因。

---

## 9. 阶段 6 / 7 收尾（2026-10-06）

阶段 6（客户端）与阶段 7（构建 / 资源 / 收尾）**已完成**。56 个附魔与客户端功能全部交付，
运行指纹 `build=cleanup1`，产物 `simple_tweaks-2.0.0.jar`。

| 项 | 结果 |
|---|---|
| 附魔名颜色 / 伤害飘字 / 配置 GUI / 附魔图鉴 / 自动冲刺 | ✅ 已交付 |
| 附魔台（覆盖语义、附魔书附魔力、最低条数保底、等级上限） | ✅ 已交付并实测通过 |
| 弓箭三件套 + Starfall | ✅ 已实测通过（Starfall 世界地板 bug 见 `docs/phase6-client-notes.md` §25.1） |
| CelestialBlessing（网络层 / 粒子 / tooltip） | ✅ 已交付，作者确认无问题 |
| InfinitePower | ✅ 核心 + 光环 + 激光；**背包 8 次尝试后舍弃** |
| Velocity | ❌ 作者裁定不移植 |

**四条跨阶段教训**（细节在 `docs/`，此处只留索引）：① refmap 有映射 ≠ 接缝活着（铁律一）；
② Mixin 目标必须用字节码核实（铁律二）；③ **"看起来只做持久化"的方法往往同时承担状态同步的触发职责**
（`markDirty()` 被写成空实现 ⇒ 服务端改了内容却从不通知客户端）；
④ **没有观测量时的改动等于猜** —— 先设计日志，再改代码。

**未做事项**统一记在 `docs/acceptance-checklist.md` 的"已知未做"表，不再散落各处。

---

## 10. 设计决策：为什么是声明式还是 Kotlin

> 阶段 2.5 的分层设计文档（原 8 个 `phase2.5-*` 文件）在收尾时合并进本节后删除。
> 判定规则：**凡能表达成「附魔等级 → 数值」的纯函数 + 条件谓词的，一律写进 `enchantment` JSON
> 并删掉处理器**；只有需要跨事件状态、需要读「本次伤害」或目标实时状态、或需要 1.21 不存在的
> 原语（改物品 NBT、改他人装备、授予飞行、生成追踪投射物、范围选敌…）的，才保留 Kotlin。

### 10.1 四条通用结论（先看这个，再看表）

1. **值效果（`LevelBasedValue`）只能吃附魔等级**，读不到实体状态、更读不到"本次伤害"。
   `linear` / `levels_squared` / `lookup` 都是等级的函数 —— 这是绝大多数 KOTLIN 判定的根源。
2. **条件谓词只能筛，不能缩放**：`entity_properties` / `location` / `damage_source_properties`
   能做布尔判定，无法参与数值计算（生命比例、速度、药水数量都只能筛不能连续缩放）。
3. **若干"看起来该有"的原语在 1.21 不存在**：heal、加经验、修复耐久、改弹射物运动、改暴击倍率、
   范围选敌、删除附魔等级、改写物品 NBT。踩坑清单见 §10.3。
4. **同名不等于同义**：`prevent_equipment_drop` 是**销毁**（消失诅咒）而不是保留；
   `smash_damage_per_fallen_block` 只被 `MaceItem` 消费；`equipment_drops` 只改装备掉落概率而非掉落倍率。

### 10.2 逐附魔判定（完整 55 项）

**DECLARATIVE —— 组件可完整表达，处理器已删**

| 附魔 | 依据 |
|---|---|
| AntiKnockback | `attributes: generic.knockback_resistance`，`add_value` + `linear 0.125/级`，`slots:[armor]`（多件叠加＝按等级求和） |
| SwiftSneak | `attributes: generic.sneaking_speed`，与原生 swift_sneak 同构，仅 max_level 取 6 |
| Unbreakable | `item_damage` 一条 `{"type":"set","value":0}`；原 tick 循环 + `Unbreakable` 标记纯属 1.12.2 的权宜实现 |
| PrismaticBlessing | `attributes` 6 条 × `add_multiplied_base` + `linear 0.04/级`，`slots:["any"]`；多件装备各自结算＝总等级叠加 |

**KOTLIN —— 保留处理器，括号内是卡住声明式的那个原语缺口**

| 附魔 | 关键理由 |
|---|---|
| ArmorBreaker | 无"改**他人**装备耐久"的效果；`damage_item` 只作用于附魔物品自身；"随机一件护甲"无法用条件表达 |
| AcidAttack | 同上 —— `damage_item` 打不到对方手持物 |
| VoidProtection | 无"消耗附魔等级"、无一次性摔伤免除；传送只能 `run_function`，且 y≤-64 会每 tick 重复触发 |
| MotionBonus | 系数取决于**攻守双方相对速度**，值效果只能吃等级 |
| AutoSmelt | 掉落替换无组件；`block_experience` 拿不到"熔炉配方经验×数量" |
| CombatMaster | 需跨事件持久状态（每攻击者的目标 UUID／层数／时间戳），效果与谓词都是无状态的 |
| BloodLust | 系数取决于攻击者**已损失生命比例** |
| EffectBonus | `entity_properties.effects` 只能枚举布尔判断，**不能计数** |
| Healer | 没有 heal 类效果；唯一近似只能给固定治疗量，无法按本次伤害比例 |
| AoeAttack | 无"对范围内所有实体造成伤害"；`explode` 是爆炸伤害/击退，`damage_entity` 只打单个目标 |
| Momentum | 组件里没有可累积的"挖掘速度"项；还需记住上次挖掘的方块坐标 |
| Resilience | 需随**实时生命比例**变化；`damage_protection` 只能给线性固定减伤 |
| Execute / Assassin | 既无"剩余生命阈值"条件，也无"把伤害设为 max_health+absorption"的效果 |
| Immortal | `damage_immunity` 没有"命中计数／每 N 次"；直接配 `random_chance` 0.4 会变成**每次**40% 免伤 |
| ExperienceStealer | 没有"加经验"的实体效果；从玩家身上抽经验更无原语 |
| HuntersMark | 需跨命中状态 + 消耗；`apply_mob_effect` 能挂标记但没有移除原语 |
| TrackingArrow | 没有任何组件能修改弹射物运动 |
| ItemFixer | 无"修复耐久"原语；`repair_with_xp` 只在捡经验时生效，`damage_item` 只增损耗 |
| CritDamage / Crit / DoubleCrit | 谓词里**没有"是否暴击"**，也没有"暴击倍率"组件；`damage` 走的是攻击基伤乘区 |
| ChargedStrike | 需跨命中保存 per-player 数值，组件没有可持久化的数值状态 |
| TrueDamage | 需取"本次伤害的百分比"并跨 LivingHurt→LivingDamage 两阶段搬运 |
| Tunneling | 无组件能在"破坏方块"时做范围破坏；`replace_block` 不产掉落 |
| DelayedRecovery | 需按本次伤害比例分 tick 治疗；`regeneration` 既非等量也不看伤害值 |
| GravityStrike | `smash_damage_per_fallen_block` **只被 `MaceItem` 消费**，且是加算无上限（原语义是乘算+封顶） |
| DamageLimiter | 需"相对**最大生命**的上限截断"；`damage_protection` 不感知最大生命 |
| Flight | 无"授予飞行"组件（`attributes` 里没有 allowFlying）；能力开关与同步必须留代码 |
| SoulBound | `prevent_equipment_drop` 只做"死亡不掉落"，物品就此消失；"保住并归还"需死亡取走 + 重生回填 |
| EchoShot | 需**延迟若干 tick** 后对同一/链式目标再造成伤害；组件没有任何调度能力 |
| HealingBlade | 效果量是"已造成伤害"与"目标当前生命"的函数，还有跨次治疗池与自反馈 `e.amount += healing` |
| GrievousWounds | 1.21 没有削减"受治疗量"的药水效果；按目标计时/过期/刷新属状态机 |
| EchoShield | 反伤量必须等于"本次来袭伤害中被减免的部分"；另有蓄能池、满池免伤、禁疗计时、死亡清账 |
| KillAura | 无组件能在半径内**选出一个敌人并用玩家武器伤害打它** |
| Starfall | 追踪飞行、按 UUID 锁目标、出生高度探测、生命期判定都是逐 tick 状态 |
| DeathProtection | 触发条件是"这一击会打死我"（`health − amount < 0`），谓词无法表达；且**无组件能扣减自身附魔等级** |
| CelestialBlessing | 自定义资源池（存量/消耗/跨死亡保留）+ 自定义伤害源 + 自导引弹幕 + 状态同步 |
| HeavenlyPunishment | "仅对该攻击者减伤"是跨实体**定向**减伤；"定身/攻速归零"需改别的实体属性 |
| ReForge | 30 个组件没有一个能改写物品 NBT（删附魔、删 RepairCost） |
| InfinitePower | 无掉落倍率组件、无飞行/饱食/工具可采/破坏速度语义、无"背包物品死亡保留"、无 GUI |

### 10.3 踩坑清单（这批判定中反复出现的"看着有其实没有"）

| 看着该有 | 实际 |
|---|---|
| `change_item_damage` | **1.21.1 不存在**；`damage_item` 是常量字段且只调 `ItemStack.damage()` |
| `prevent_equipment_drop` | 是**销毁**（消失诅咒用），不是"保留" |
| `equipment_drops` | 只改**装备掉落概率**，不是掉落倍率 → 掉落翻倍永远无法声明式 |
| `smash_damage_per_fallen_block` | 只被 `MaceItem` 的 smash 攻击消费，剑附魔拿不到 |
| `damage_protection` | 只能给 EPF 式百分比，不感知最大生命、没有上限截断 |
| `mob_experience` / `block_experience` | 只在击杀/破坏结算时给经验，与"每次命中加经验"不同 |
| `repair_with_xp` | 只在捡经验时生效，不是"修复耐久"原语 |
| `replace_block` | 替换方块但**不产掉落**，也不能以"玩家破坏"为触发 |
| `damage_immunity` | 无法限定"仅致命"，也不会消耗附魔等级 |
| `random_chance` 近似"每 N 次" | 每次独立判定，语义完全不同（Immortal 的坑） |

---

## 11. 收尾：删除测试指令与 Carpet 附属（`build=cleanup2`）

测试指令与 RCON 假玩家验收都已完成使命（验收改由作者本人客户端进行），三者全部从源码与发布 jar 中删除。

| 删除 | 位置 | 理由 |
|---|---|---|
| `/sttest` 指令 | `core/DevTestCommand.kt` + `SimpleTweaks.onInitialize` | 开发期自测入口；发布 jar 里带测试指令属污染 |
| `invocations` / `vanillaCanEnchant` / `describe*` | `core/EnchantTableGate.kt` | 只有 `/sttest` 读；删除后即死代码。判定谓词 `canEnchant` 与 `maxEnchantmentPower` 保留 |
| Carpet `1.4.147`（`modRuntimeOnly`） | `build.gradle` | 只用于假玩家验收；**从未进过 jar**（§服务端验收基础设施的结论不变） |
| Modrinth maven 仓库 | `build.gradle` | 该仓库**只为** Carpet 添加，删依赖后无引用 |
| `tools/acceptance.ps1`（52 KB） | `tools/` | 100% 依赖 Carpet 假玩家 + `/sttest`，两者删除后无法运行 |
| `tools/rcon.ps1` | `tools/` | 只服务于 `acceptance.ps1` |

**保留**：`/stconfig`、`/enchantinfo` 是正式功能，不是测试指令；`tools/gen-enchantments.ps1`、`tools/yarn-query.ps1` 是代码生成 / 离线名称校验工具。
**不改写**：本文件 §服务端验收基础设施 / §逻辑层级验收结果，以及 `docs/phase4-mixin-notes.md`、`docs/phase6-client-notes.md` 中出现的 `/sttest` 与 Carpet，都是**当时的事实记录**，按原样保留。

> ⚠️ **Kotlin 增量编译不会删除已移除源文件的 class。** 只跑 `build` 时 `DevTestCommand.class` 会残留在 `build/classes` 并被 `jar` 打进发布包（实测发生，529 KB 的包差一点就带着它发出去）。删除源文件后**必须 `gradlew clean build`**。

### 11.1 `yStartFactor` 硬化：乘数从「世界高度」改为「实体高度」

| | 改前（= 1.12.2 原式） | 改后（`build=cleanup2`） |
|---|---|---|
| 锚点公式 | `(entity.y + entity.height) * yStartFactor` | `entityFeetY + entityHeight * yStartFactor` |
| `yStartFactor = 1.0`（默认） | 命中箱顶部 | **完全相同**，默认行为不变 |
| `yStartFactor = 2.0`，站在 y=70 | 锚点 ≈ y143 → 超出 `maxDistance` → **飘字永不渲染** | 锚点 = 脚 + 2 倍身高（≈ y72）→ 正常渲染 |

**为什么必须改**：`yStartFactor` 是**绝对世界高度**的乘数，`2.0` 会把锚点推到离相机 70+ 格处，被 `DamageIndicatorRenderer` 的距离剔除静默丢掉；而 `cancelVanillaDamageIndicator` 默认开启，连原版飘字都没有 ⇒ 表现为该功能**彻底失效**。

**实测复现**：`run/config/simple_tweaks.json` 中 `yStartFactor = 2.0` 时飘字全无。当时已核对代码默认值（1.0）与配置的读、写两条路径**均正确**，唯一问题就在这条公式。改后 `0.5..2.0` 全区间都在渲染范围内，`entityFeetY` / `entityHeight` 仍在构造时缓存，飘字依旧不跟随生物。

---

## 12. 调试日志改为启动开关（`build=cleanup3`）

**问题**：`STLog` 原本"默认开启"（`-Dsimpletweaks.debug=false` 才关）。结果**正常游玩时每个生效的附魔都往日志里写一行 `[ST-*]`** —— 83 个调用点分布在 52 个 handler 里。验收期这是证据，游玩期这是纯噪音。

**做法**：不删除调用点，把 `STLog` 整体翻成**默认关闭**。一处改动静默全部 83 处，且日后要查还能打开 —— 相比删掉 83 行代码，可诊断性完全保留。

| 打开方式 | 写法 | 用途 |
|---|---|---|
| JVM 参数（首选） | `-Dsimpletweaks.debug=true` | 正式客户端：加到启动器 profile 的 `arguments.jvm` |
| 环境变量 | `SIMPLETWEAKS_DEBUG=true`（等价 `=1`） | 开发：`$env:SIMPLETWEAKS_DEBUG='true'; gradlew runClient`，**不用改 `build.gradle`** |

**实现要点**：`enabled` 在**类初始化时读一次**并缓存（启动开关，不是运行时开关；热路径只剩一次字段读取）。`log(enchant) { ... }` 的 lambda 重载本就懒求值，开关关闭时连细节字符串都不会拼。

**同批一并门控**：`EnchantmentScreenHandlerMixin` 的 `[enchant-table] re-enchant ...`（原来无条件 `LOGGER.info`）——改为 `STLog.INSTANCE.getEnabled()` 判定，与其余诊断日志同一开关。

**保持无条件输出**（这些是启动信息与真异常，不该被开关吞掉）：`v2.0.0 loading` / `load complete` / `client ready: ... (build=...)` / `Config loaded from ...` / `Registered N enchantment handler(s)`，以及 `ForgeEventBus` 与配置读写的全部 `warn` / `error`。

> ⚠️ **验收影响**：本文件与 `docs/acceptance-checklist.md` 里所有"日志应出现 `[ST-...]`"的判据，**现在都必须先打开开关**，否则那些行根本不会出现 —— 这是预期，不是功能失效。清单 §M 已加对应检查项。

---

## 14. 铁砧等级上限：配置项存在但从未实现（`build=cleanup4` 修复）

**现象**：`/stconfig` 里「移除铁砧『过于昂贵』」和「铁砧最大花费」能改、能落盘，但铁砧行为不变。

**根因**：**1.21.1 移植里没有任何铁砧 mixin**。1.12.2 这个功能由 `MixinContainerRepair`（服务端闸门）+
`MixinGuiRepair`（客户端红字）实现，**两个都没被移植**；`GeneralConfig`、配置 JSON、配置界面、两份 lang
全都写好了，所以它看起来"已实现" —— 实际没有任何代码读它。与当初 `maxEnchantmentPower` 是同一类错误。

### 14.1 修复：两半缺一不可

| 新增 | 目标 | 作用 |
|---|---|---|
| `mixin/AnvilScreenHandlerMixin`（common） | `AnvilScreenHandler.updateResult` | 服务端闸门：`levelCost >= 40 && !creative → 产物清空` |
| `mixin/AnvilScreenMixin`（client） | `AnvilScreen.drawForeground` | 客户端红字「过于昂贵！」的同一个 40 |

只改服务端 → 产物能拿出来但界面仍标红；只改客户端 → 界面正常但服务端清空产物。

### 14.2 关键坑：`updateResult()` 里有**三处** `40`

字节码实测（`javap -c`，`AnvilScreenHandler.updateResult`，yarn 1.21.1+build.3）：

| 字节码偏移 | ordinal | 含义 |
|---|---|---|
| 723 | 0 | 合并循环内 `if (stack.getCount() > 1) cost = 40;` —— **一个花费值，不是上限** |
| 899 | 1 | `if (j > 0 && i == j && levelCost >= 40) levelCost.set(39);` —— 创造模式显示钳位 |
| **920** | **2** | `if (levelCost >= 40 && !creative) result = EMPTY;` —— **闸门**（唯一读 `creativeMode` 的那处） |

因此**必须写 `ordinal`**：裸 `@ModifyConstant(intValue = 40)` 会同时改掉三处，把"用一叠材料修理"的代价
变成 `maxAnvilCost`（默认 21 亿）。`ordinal` 是 **`@Constant`** 的元素而 `@ModifyConstant` **没有** ——
这一点由 `javap` 查 `sponge-mixin` 0.15.5 的注解定义确认，不是凭记忆。

（1.12.2 那个"把存进 `i` 的 40 改成 0"的 `@ModifyVariable` 钩子在编译器优化后即死代码、从未生效，
所以这里**不**改 ordinal 0 恰好与 1.12.2 的**实际**行为一致。）

### 14.3 仍未移植的铁砧功能（本次未做，勿当 bug 报）

| 功能 | 1.12.2 位置 | 现状 |
|---|---|---|
| **铁砧祛魔**（`anvilDisenchant`） | `MixinContainerRepair#firefly$disenchant` + `DisenchanterLogic` | ❌ **未移植**。配置项/界面/lang 都在，但 `DisenchanterLogic` 整个类不存在；1.21 没有可写经验标签的物品 NBT，需重新设计承载方式 |
| **附魔成本倍率**（稀有度越高越贵） | `AnvilCostHandler`（Forge `AnvilUpdateEvent`） | ❌ **未移植**。Fabric 无对应事件，需另挂 mixin |

> ⚠️ 清单「已知未做」表里原写「`/enchant ... 0` 的替代：**铁砧祛魔**」，而铁砧祛魔本身也不存在 —— 已改正。

### 14.4 验证方式

| 层 | 检查 | 结果 |
|---|---|---|
| 方法解析 | `simple_tweaks-refmap.json` | `AnvilScreenHandlerMixin.updateResult → Lnet/minecraft/class_1706;method_24928()V`；`AnvilScreenMixin.drawForeground → class_471;method_2388(class_332;II)V` |
| 注解取值 | jar 内 class 上跑 `javap -v` | `@Constant(intValue=40, ordinal=2)` / `@Constant(intValue=40)` |
| 行为 | 客户端验收 §N（N6 专验 ordinal 0 未被误改） | **待作者验收** |

> `ordinal` 是唯一脆弱点。若日后铁砧又不生效，第一步是重新 `javap -c` 数 `updateResult` 里的
> `bipush 40` 个数与位置，而不是先怀疑配置读取。

---

## 15. KSP：让「写一个新附魔」回到一个文件（`build=cleanup5`）

> §13 不存在 —— 编号不复用（同验收清单 J 组「保留空缺，不再重排」的约定）。

**目标**（作者原话）：写新附魔回到 1.12.2 那种"一个文件搞定"的体感，其余全由 KSP 生成。

**试点范围**：只做 `fast_bow` **一个**；**现有 56 个附魔一律不动**，仍由 `tools/gen-enchantments.ps1` 生成的表驱动。

### 15.1 一个注解 = 5 份产物

在 handler 上写 `@ModEnchantment(...)`，KSP 生成：

| 产物 | 位置 | 取代了 |
|---|---|---|
| `<id>.json` | `resources/data/simple_tweaks/enchantment/` | 手写数据包定义 |
| `GeneratedEnchantments.<ID>` | `enchantments/generated/GeneratedEnchantments.kt` | `ModEnchantmentKeys` 里的一行 |
| `.KEYS` | 同上 | `ModEnchantmentKeys.ALL` 列表 |
| `.CATEGORY` / `.TYPE` / `.COLOR` / `.MAX_LEVEL` | 同上 | `EnchantmentTiers` 三张表 |
| `.HANDLERS` | 同上 | `EnchantmentManager.handlerList` 手写清单 |

**lang 不生成** —— 附魔名与描述手写（`enchantment.simple_tweaks.<id>` / `.desc`）：文字是注解唯一表达不了的东西。

### 15.2 接线

| 文件 | 作用 |
|---|---|
| `settings.gradle` | `include 'ksp-processor'` |
| `gradle.properties` | `ksp_version=2.0.21-1.0.28`（**必须**与 `kotlin_version` 同线，已核对 Maven Central） |
| `build.gradle` | KSP 插件 + `ksp project(':ksp-processor')` |
| `ksp-processor/` | 纯 Kotlin/JVM 库，只依赖 `symbol-processing-api`；**不应用 Loom**，不依赖 mod 的 source set |
| `enchantments/annotations/ModEnchantment.kt` | 注解本身放在 **mod 里**，这样 handler 文件不必 import 构建期模块（processor 只按全限定名字符串查找它） |
| `enchantments/EnchantmentMeta.kt` | 手写门面：合并「旧表 + KSP 表」，KSP 条目优先 |

**KSP 生成物落点已实测**：`build/generated/ksp/main/kotlin`（Kotlin）与 `build/generated/ksp/main/resources`（**非 kt/java 的扩展名一律进 resources**）。KSP 插件自己把两者挂到 `main` source set，所以 `build.gradle` 里**不需要**额外的 `sourceSets` 接线，jar 里直接就有 `data/simple_tweaks/enchantment/fast_bow.json`。

### 15.3 三个踩过才知道的坑

1. **`validate()` 延后机制在这里会死锁 —— 本批最大的坑。** KSP 的常规写法是把"未解析"的符号 return 出去等下一轮；但被注解的 handler **自己引用了本 processor 即将生成的 `GeneratedEnchantments.<ID>`**，所以 `validate()` 每轮都是 false。后果：第 1 轮 specs 为空、生成空文件并 return deferred，第 2 轮再生成 → `FileAlreadyExistsException`。**结论：不做 deferral**（读注解参数不需要解析类体），真正的问题交给 Kotlin 编译器报。
2. **`process()` 每轮都会被调用**，而"某轮生成过文件"必然触发下一轮。必须防重：本实现用 `done` 标志 + `codeGenerator.generatedFile` 双保险。
3. **`ordinal` 是 `@Constant` 的元素，不是 `@ModifyConstant` 的**（§14.2 同批踩到）。

### 15.4 验证（全部离线可验，不需要开客户端）

| 检查 | 结果 |
|---|---|
| 生成的 `fast_bow.json` vs 被它取代的手写原件 | **逐字节一致**（忽略换行风格差异） |
| jar 内 datapack 附魔 JSON 数 | **57** = 原 56 + `fast_bow` |
| jar 内含 | `GeneratedEnchantments.class`、`EnchantFastBowHandler.class`、`ChargeBoost.class`、`EnchantmentMeta.class` |
| KSP processor / `symbol-processing-api` 泄漏进 mod jar | **无** |

### 15.5 顺带修掉的一个真 bug

`ModEnchantmentKeys.kt` 第 86 行有 `FAST_BOW`，但**没有进 `ALL` 列表** —— 而 `EnchantInfoScreen` 正是用 `ALL` 分组的，所以图鉴里根本看不到它。该文件表头写着"自动生成、勿手改"，这类不一致正是 KSP 要消灭的对象。现已删除该行，键改由 `GeneratedEnchantments.FAST_BOW` 提供。

### 15.6 迁移路径（旧附魔怎么搬）

给旧附魔的 handler 加 `@ModEnchantment`，然后**从 `gen-enchantments.ps1` 的表里删掉它**即可（`EnchantmentMeta` 中 KSP 侧优先）。两代并存不冲突，所以可以一个一个搬，不必一次性全改 —— 这是刻意的：一次性迁移 56 个附魔等于制造 56 个潜在回归。

### 15.7 fast_bow 的效果实现（唯一的新 mixin 改动）

`fast_bow` 在 1.12.2 **不存在**，是全新的。1.21 没有"拉弓更快"的组件，且 `ArrowLooseEvent.charge` 只是快照、原版从不回读，所以事件本身改不了射出的力度。真正的算式在 `BowItem.onStoppedUsing` 里：

```
offset 34:  invokevirtual  getMaxUseTime(ItemStack, LivingEntity)I
offset 44:  invokestatic   getPullProgress(I)F          <-- @ModifyArg 挂在这里
```

实测 `getPullProgress` 是 **static**、且在 `onStoppedUsing` 里**只出现一次** → 不需要 `slice`/`ordinal`（与 §14.2 的铁砧恰成对照）。handler 通过 `compat/ChargeBoost` 把倍率交给这个 `@ModifyArg`；因为 `getPullProgress` 会把结果夹到 `1.0`，**加速只能补足"没拉满"，不可能超过满蓄力**。3 级 = 1.75×，12 tick 即满。

> ⚠️ 这动的是**多个弓附魔共用的** `BowItemArrowLooseMixin`，所以验收 §O3.2 专门回归 `multishot` / `tracking_arrow` / `piercing_arrow` / `starfall`。
