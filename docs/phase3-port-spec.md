# 阶段 3 处理器移植规范

把 1.12.2 的处理器逐份搬到 1.21.1。**这是机械变换，不是重新设计** ——
处理器的方法体逻辑保持不变，只改 import、成员名、附魔引用。

---

## 1. 目标位置

源：`src/main/kotlin/dev/firefly/simpletweaks/enchantments/handlers/<tier>/<Name>.kt`（1.12.2）
目标：`fabric-1.21.1/src/main/kotlin/dev/firefly/simpletweaks/enchantments/handlers/<tier>/<Name>.kt`

包名不变：`dev.firefly.simpletweaks.enchantments.handlers.<tier>`

## 2. import 替换表

| 1.12.2 | 1.21.1 |
|---|---|
| `net.minecraftforge.fml.common.eventhandler.SubscribeEvent` | `dev.firefly.simpletweaks.compat.event.SubscribeEvent` |
| `net.minecraftforge.fml.common.eventhandler.EventPriority` | **删除**（本工程总线不需要；注解参数仍可写 `priority = EventPriority.NORMAL`，需从 compat.event 导入） |
| `net.minecraftforge.event.entity.living.LivingHurtEvent` | `dev.firefly.simpletweaks.compat.event.LivingHurtEvent` |
| ...`LivingDamageEvent` / `LivingDeathEvent` / `LivingHealEvent` / `LivingAttackEvent` / `LivingFallEvent` | 同样改到 `dev.firefly.simpletweaks.compat.event.*` |
| `net.minecraftforge.event.entity.living.LivingEvent` | `dev.firefly.simpletweaks.compat.event.LivingEvent` |
| `net.minecraftforge.fml.common.gameevent.TickEvent` | `dev.firefly.simpletweaks.compat.event.TickEvent` |
| `net.minecraftforge.event.entity.player.PlayerEvent` **和** `net.minecraftforge.fml.common.gameevent.PlayerEvent` | **两者都**改成 `dev.firefly.simpletweaks.compat.event.PlayerEvent`（本项目已合并成一个类，`import ... as Event1` 别名应删除） |
| `net.minecraftforge.event.entity.player.AttackEntityEvent` | `dev.firefly.simpletweaks.compat.event.AttackEntityEvent` |
| `net.minecraftforge.event.world.BlockEvent` | `dev.firefly.simpletweaks.compat.event.BlockEvent` |
| `net.minecraftforge.event.entity.EntityJoinWorldEvent` | `dev.firefly.simpletweaks.compat.event.EntityJoinWorldEvent` |
| `net.minecraftforge.event.entity.player.PlayerInteractEvent` | `dev.firefly.simpletweaks.compat.event.PlayerInteractEvent` |
| `net.minecraftforge.fml.relauncher.Side` / `SideOnly` | **删除**（1.21 用 `@Environment(EnvType.CLIENT)`；处理器一般不需要） |
| `net.minecraft.entity.EntityLivingBase` | `net.minecraft.entity.LivingEntity` |
| `net.minecraft.entity.player.EntityPlayer` | `net.minecraft.entity.player.PlayerEntity` |
| `net.minecraft.entity.player.EntityPlayerMP` | `net.minecraft.server.network.ServerPlayerEntity` |
| `net.minecraft.entity.item.EntityItem` | `net.minecraft.entity.ItemEntity` |
| `net.minecraft.entity.EnumCreatureAttribute` | **已删除**，按语义改用 `net.minecraft.registry.tag.EntityTypeTags` 判断 |
| `net.minecraft.util.EnumParticleTypes` | `net.minecraft.particle.ParticleTypes` |
| `net.minecraft.nbt.NBTTagCompound` / `NBTTagList` | `net.minecraft.nbt.NbtCompound` / `NbtList` |
| `net.minecraft.util.text.TextFormatting` | `net.minecraft.util.Formatting` |
| `net.minecraft.util.text.TextComponentString` | `net.minecraft.text.Text.literal(...)` |
| `net.minecraft.entity.SharedMonsterAttributes` | `net.minecraft.entity.attribute.EntityAttributes` |
| `net.minecraft.entity.ai.attributes.IAttributeInstance` | `net.minecraft.entity.attribute.EntityAttributeInstance` |
| `net.minecraft.entity.ai.attributes.AttributeModifier` | `net.minecraft.entity.attribute.EntityAttributeModifier` |
| `net.minecraft.util.math.BlockPos` / `Vec3d` / `AABB` | 同名（`AABB` 仍在 `net.minecraft.util.math`） |
| `net.minecraft.world.WorldServer` | `net.minecraft.server.world.ServerWorld` |
| `net.minecraft.client.resources.I18n` | 用 `net.minecraft.text.Text.translatable(...)` |

## 3. 成员名替换

**看 `phase3-member-map.md`**，那里是已用本地映射逐个核实过的对照表。
最容易写错的几个：

- `heldItemMainhand` → `mainHandStack`
- `world.isRemote` → **`WorldSide.isClient(world)`**（`World.isClient` 是字段，Kotlin 里和 `WorldView.isClient()` 有歧义，统一走 `WorldSide`）
- `uniqueID` → `uuid`（`getUuid()` 继承自接口 `EntityLike`）
- `posX/posY/posZ` → `x/y/z`
- `totalWorldTime` → `world.time`
- `isItemStackDamageable` → `isDamageable`
- `damageItem(n, e)` → `damage(n, e, EquipmentSlot.MAINHAND)`
- `activePotionEffects` → `statusEffects`
- `getEntitiesWithinAABB(C, aabb)` → `world.getEntitiesByClass(C::class.java, box) { true }`

## 4. 附魔引用：对象 → RegistryKey

1.12.2 直接引用 `EnchantFoo` 这个 `Enchantment` 对象；1.21 只能持有 `RegistryKey`。

```diff
-import dev.firefly.simpletweaks.enchantments.common.EnchantAcidAttack
+import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
...
-val lvl = getItemSpecificEnchantLevel(stack, EnchantAcidAttack)
+val lvl = getItemSpecificEnchantLevel(stack, ModEnchantmentKeys.ACID_ATTACK)
```

**常量名 = id 的大写形式**，但**不要靠类名推断** —— 去
`fabric-1.21.1/src/main/kotlin/dev/firefly/simpletweaks/enchantments/ModEnchantmentKeys.kt`
查确切常量名（已知陷阱：`EnchantReForge` 的 id 是 `reforge`，不是 `re_forge`）。

## 5. 可用的 compat 事件（**只有这些，不要发明**）

```
LivingAttackEvent(entityLiving, source, amount)             可取消
LivingHurtEvent(entityLiving, source, amount)               可取消，amount 可写
LivingDamageEvent(entityLiving, source, amount)             可取消，amount 可写
LivingDeathEvent(entityLiving, source)                      可取消
LivingHealEvent(entityLiving, amount)                       可取消，amount 可写
LivingFallEvent(entityLiving, distance, damageMultiplier)   可取消，字段只读
LivingEvent.LivingUpdateEvent(entityLiving)
TickEvent.ServerTickEvent(phase)
TickEvent.ClientTickEvent(phase)
TickEvent.WorldTickEvent(phase, world)
TickEvent.PlayerTickEvent(phase, player)
TickEvent.RenderTickEvent(phase, partialTicks)
TickEvent.Phase.START / .END
PlayerEvent.Clone(player, original, wasDeath)               player=新的, original=旧的
PlayerEvent.PlayerLoggedInEvent(player)
PlayerEvent.PlayerLoggedOutEvent(player)
PlayerEvent.PlayerRespawnEvent(player, endConquered)
PlayerEvent.PlayerChangedDimensionEvent(player)
PlayerEvent.BreakSpeed(player, state, pos, newSpeed)        ⚠️ pos 目前恒为 null
PlayerEvent.HarvestCheck(player, state, canHarvest)
AttackEntityEvent(player, target)                           可取消
EntityJoinWorldEvent(entity, world)                         可取消
BlockEvent.BreakEvent(world, pos, state, player)            可取消
PlayerInteractEvent.RightClickItem(player, world, hand, itemStack)  可取消
```

**尚未实现、本批不得使用**：`CriticalHitEvent`、`LivingDropsEvent`、`PlayerDropsEvent`、
`BlockEvent.HarvestDropsEvent`、`PlayerInteractEvent.LeftClickEmpty`、`ArrowLooseEvent`、
`PlayerEvent.SaveToFile`、`ItemTooltipEvent`、`RenderWorldLastEvent`、`InputEvent`、
`CommandEvent`、`AnvilUpdateEvent`、`FMLServerStartingEvent`、`RegistryEvent`。

## 6. 可用的扩展函数（`compat/EventUtils.kt`）

`LivingAttackEvent` / `LivingHurtEvent` / `LivingDamageEvent` / `LivingDeathEvent` / `LivingHealEvent`：
`.isClientSide`、`.attacker`（Forge 的 `trueSource`）、`.target`、`.invalid`、`.cancel()`

`AttackEntityEvent`：`.isClientSide`、`.invalid`
`TickEvent.PlayerTickEvent`：`.invalid`、`.isClientSide`
全局：`chance(Float)`、`chance(Double)`

## 7. 可用的工具函数（`util/EnchantmentsUtil.kt`）

```kotlin
getItemSpecificEnchantLevel(stack: ItemStack, key: RegistryKey<Enchantment>): Int
getItemSpecificEnchantsLevel(stack: ItemStack, keys: List<RegistryKey<Enchantment>>): Int
getItemsSpecificEnchantLevel(stacks: List<ItemStack>, key: RegistryKey<Enchantment>): Int
hasEnchantment(stack: ItemStack, key: RegistryKey<Enchantment>): Boolean
LivingEntity.getMainHandEnchantLevel(key): Int
LivingEntity.getArmorEnchantLevel(key, maxTotal: Int = Int.MAX_VALUE): Int
WorldSide.isClient(world) / WorldSide.isServer(world)
```

> 注意：1.12.2 的 `getSpecificEnchantLevel(enchantment)`（读客户端玩家主手）已删除，
> 改用 `player.getMainHandEnchantLevel(key)`。
> `getArmorEnchantLevel` 的 `maxTotal` 默认值从 `Int.MAX_VALUE` 保留。

## 8. 处理器骨架

```kotlin
package dev.firefly.simpletweaks.enchantments.handlers.<tier>

import dev.firefly.simpletweaks.compat.event.LivingHurtEvent
import dev.firefly.simpletweaks.compat.event.SubscribeEvent
import dev.firefly.simpletweaks.compat.invalid
import dev.firefly.simpletweaks.core.Listenable
import dev.firefly.simpletweaks.enchantments.ModEnchantmentKeys
import dev.firefly.simpletweaks.util.getItemSpecificEnchantLevel

object EnchantXxxHandler : Listenable {
    @SubscribeEvent
    fun onLivingHurt(e: LivingHurtEvent) {
        // 方法体逻辑照搬
    }
}
```

- `object` + `Listenable` 保持不变（`Listenable.registerEvents()` 由 `EnchantmentManager` 调用）
- `@SubscribeEvent` 方法签名保持不变（参数类型改成本项目的对应事件）
- **不要**自己调用 `registerEvents()` / `ForgeEventBus.register(...)`
- `Listenable.init()` 如果原文件有 `override fun init()`，保留

## 9. 本批的删除项

- `EnchantAntiKnockbackHandler` — 已由 JSON 的 `minecraft:attributes` 表达，**删除文件**
- `EnchantSwiftSneakHandler` — 同上，**删除文件**

## 10. 完成标准

- 12 个文件写到目标目录，**方法体逻辑与源文件一一对应**（不要顺手"优化"或重构）
- 不引用任何 `net.minecraftforge.*`
- 不引用第 5 节「尚未实现」列表里的任何事件
- 附魔引用一律走 `ModEnchantmentKeys.*`
- 每个文件顶部保留原有中文注释；新增注释说明 1:1 映射关系即可

## 11. 两个已踩过的坑（务必避免）

### 11.1 KDoc 里绝不能出现 `/*`

Kotlin 的块注释**可以嵌套**。所以下面这种写法是致命的：

```kotlin
/**
 * ... 数据在 `data/simple_tweaks/enchantment/*.json` ...
 */
```

`enchantment/*.json` 里的 `/*` 会**开启一个嵌套块注释**，而后面没有对应的 `*/`，
于是报 `Syntax error: Unclosed comment`，并且**连锁导致该文件所有导出符号 Unresolved**
（本次实测：一个文件里的这一个字符，引发了 ~30 条 "Unresolved reference 'ModEnchantmentKeys'"）。

要写通配路径就写成 `` `data/simple_tweaks/enchantment/` 目录下 `` 或 `<id>.json`。

自检：`Get-ChildItem src -Recurse -Filter *.kt | % { $t=Get-Content $_ -Raw; ([regex]::Matches($t,'/\*')).Count -ne ([regex]::Matches($t,'\*/')).Count }`

### 11.2 扩展函数必须显式 import

`invalid` / `isClientSide` / `cancel()` / `chance()` 这些是 **`compat` 包里的扩展函数**，
不会自动可见，必须写：

```kotlin
import dev.firefly.simpletweaks.compat.invalid
```

同理 `util` 包的扩展（`getItemSpecificEnchantLevel`、`leggings`、`relativeSpeed`、`tp` …）
也要各自 import。

> 注意区分：`e.source.attacker` 是**原版** `DamageSource.getAttacker()`（`method_5529`），不需要 import；
> 只有直接写在事件上的 `e.attacker`（Forge 的 `trueSource` 助记）才是本工程的扩展。
