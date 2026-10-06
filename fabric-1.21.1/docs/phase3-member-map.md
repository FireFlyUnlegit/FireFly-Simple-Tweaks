# 阶段 3 成员名对照表（1.12.2 → 1.21.1 Yarn）

全部用 `tools/yarn-query.ps1` 从本地缓存映射核实过。**别凭直觉写名字** —— 下表里带 ⚠️ 的
都是直觉会写错、或"不存在但在父类/接口上"的情况。

## 类型名

| 1.12.2 | 1.21.1 Yarn | 备注 |
|---|---|---|
| `EntityLivingBase` | `net.minecraft.entity.LivingEntity` | |
| `EntityPlayer` | `net.minecraft.entity.player.PlayerEntity` | |
| `EntityPlayerMP` | `net.minecraft.server.network.ServerPlayerEntity` | ⚠️ 不在 `entity.player` 包下 |
| `EntityItem` | `net.minecraft.entity.ItemEntity` | |
| `EntityLightningBolt` | `net.minecraft.entity.LightningEntity` | `class_1538` |
| `EnumParticleTypes` | `net.minecraft.particle.ParticleTypes` | `class_2398` |
| `EntityEquipmentSlot` | `net.minecraft.entity.EquipmentSlot` | |
| `NBTTagCompound` / `NBTTagList` | `net.minecraft.nbt.NbtCompound` / `NbtList` | |
| `TextFormatting` | `net.minecraft.util.Formatting` | |
| `EnumCreatureAttribute` | — **已删除** | 1.21 用 `EntityType` 标签表达 |
| `SharedMonsterAttributes` | `net.minecraft.entity.attribute.EntityAttributes` | ⚠️ 1.21.1 **仍带** `GENERIC_` 前缀 |
| `AttributeModifier` | `net.minecraft.entity.attribute.EntityAttributeModifier` | ⚠️ 构造需 `Identifier` |
| `IAttributeInstance` | `net.minecraft.entity.attribute.EntityAttributeInstance` | |
| `I18n.format(...)` | `Text.translatable(...)` | |
| `Minecraft.getMinecraft()` | `MinecraftClient.getInstance()` | `method_1551` |

## 实体 / 玩家成员

| 1.12.2 | 1.21.1 Yarn | intermediary |
|---|---|---|
| `getHeldItemMainhand()` | `getMainHandStack()` | `method_6047` |
| `isItemStackDamageable()` | `isDamageable()` | `method_7963` |
| `damageItem(n, entity)` | `damage(n, entity, EquipmentSlot.MAINHAND)` | `method_7970` |
| `getUniqueID()` | `getUuid()` | ⚠️ 声明在**接口** `net.minecraft.world.entity.EntityLike` 上（`Entity` 继承），所以查 `Entity` 的成员列表**查不到** |
| `posX` / `posY` / `posZ` | `x` / `y` / `z`（`getX()/getY()/getZ()`） | 都在 `Entity` 上 |
| `world.isRemote` | `world.isClient` | ⚠️ 是**public 字段**（`field_9236`），不是方法 |
| `world.totalWorldTime` | `world.time`（`getTime()`） | |
| `maxHealth` | `maxHealth`（`getMaxHealth()`） | `method_6063` |
| `absorptionAmount` | `getAbsorptionAmount()` | `method_6067`；`setAbsorptionAmount` = `method_6073` |
| `fireTime` | `getFireTicks()` / `setFireTicks(int)` | `method_20802` / `method_20803`，**均为公开**（原 `EntityFireTimeAccessor` 可直接删） |
| `activePotionEffects` | `getStatusEffects()` | `Collection<StatusEffectInstance>` |
| `getEntityBoundingBox()` | `getBoundingBox()` | ⚠️ 声明在 `EntityLike` 上 |
| `getLookVec()` | `getRotationVec(float)` | |
| `getPositionVector()` | `getPos()` | 返回 `Vec3d` |

## 世界 / 查询 / 粒子

| 1.12.2 | 1.21.1 Yarn | 备注 |
|---|---|---|
| `world.getEntitiesWithinAABB(Class, AABB)` | `world.getEntitiesByClass(Class, Box, Predicate)` | ⚠️ `method_8390` 声明在**接口** `net.minecraft.world.EntityView` 上，`World` 继承 |
| `world.getEntitiesWithinAABBExcludingEntity` | `getOtherEntities(Entity, Box, Predicate)` | |
| `world.spawnParticle(EnumParticleTypes, x,y,z, ...)` 服务端 | `ServerWorld.spawnParticles(ParticleEffect, x,y,z, count, ...)` | `method_14199`（另有一个带 `ServerPlayerEntity` 的重载 `method_14166`） |
| `worldServer.spawnEntity(entity)` | `ServerWorld.spawnEntity(entity)` | |
| `world.playSound(...)` | `world.playSound(...)` | 签名变化：需 `RegistryEntry<SoundEvent>` |

## 附魔读取

| 1.12.2 | 1.21.1 Yarn |
|---|---|
| `EnchantmentHelper.getEnchantmentLevel(Enchantment, ItemStack)` | `EnchantmentHelper.getLevel(RegistryEntry<Enchantment>, ItemStack)`，`method_8225` |
| `Enchantment` 对象直接引用 | 只能持有 `RegistryKey<Enchantment>`；本工程用 `RegistryEntry.matchesKey`（`method_40225`）在 `ItemEnchantmentsComponent` 内比对，免注册表查询 |
| `Enchantment.REGISTRY` | `RegistryKeys.ENCHANTMENT`（`field_41265`）+ 数据包 JSON |

## 物品栈

`getCount()` / `setCount(int)` / `getItem()` / `copy()` / `split(int)` / `decrement(int)` / `isEmpty()` /
`isDamageable()` —— `ItemStack`（`class_1799`）上均存在。

## 查询技巧备忘

- 查不到某个成员时，**先怀疑它声明在父类或接口上**（`getUuid` → `EntityLike`、
  `getBoundingBox` → `EntityLike`、`getEntitiesByClass` → `EntityView`）。
  用「扫描全文件并跟踪当前类」的方式查出真正声明者，别只查目标类。
- `Entity` 与 `world/entity/EntityLike` **两个包同时存在**，1.21 新引入的接口在
  `net/minecraft/world/entity/` 下。

### ⚠️ 要按 Yarn 名读 Minecraft 类，必须用**这个** jar

Loom 缓存里那两个 `minecraft-merged.jar` / `minecraft-client.jar` 是
**混淆过的（Mojang 官方名）** —— 里面是 `a.class` / `fgi$a.class`，
**没有** `net/minecraft/entity/LivingEntity.class`，也没有 `class_1309`。
按 Yarn 名去里面找类会全部落空，别在这里浪费时间。

**Yarn 命名版的 jar 在 `minecraftMaven` 下**（实测路径）：

```
E:\gradle\caches\fabric-loom\minecraftMaven\net\minecraft\minecraft-merged\
  1.21.1-net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2\
  minecraft-merged-1.21.1-net.fabricmc.yarn.1_21_1.1.21.1+build.3-v2.jar
```

用它可以直接做**离线引用分析**（谁调用了某方法），这是验证"某组件在 1.21.1 是否真的被消费"
这类问题的决定性手段。做法：把 zip 里每个 `.class` 读成字节，在常量池里搜方法名的 ASCII 字节。

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = '<上面那个 yarn jar 的完整路径>'
$z = [System.IO.Compression.ZipFile]::OpenRead($jar)
$needle = [System.Text.Encoding]::ASCII.GetBytes('getProjectileCount')
foreach ($e in $z.Entries) {
  if ($e.FullName -notmatch '^net/minecraft/.*\.class$') { continue }
  $ms = New-Object System.IO.MemoryStream; $s = $e.Open(); $s.CopyTo($ms); $s.Close()
  $b = $ms.ToArray()
  for ($i = 0; $i -le $b.Length - $needle.Length; $i++) {
    if ($b[$i] -eq $needle[0]) {
      $ok = $true
      for ($j = 1; $j -lt $needle.Length; $j++) { if ($b[$i+$j] -ne $needle[$j]) { $ok = $false; break } }
      if ($ok) { Write-Output (($e.FullName -replace '\.class$','') -replace '/','.'); break }
    }
  }
}
$z.Dispose()
```

> ⚠️ PowerShell 里 `$list.Add($x -replace 'a','b' -replace 'c','d')` 会被解析成多参数调用而报
> `Cannot find an overload for "Add"`。**必须加括号**：
> `$list.Add((($x -replace 'a','b') -replace 'c','d'))`。

已用此法验证过的实例：`getProjectileCount` / `getProjectileSpread` 只被
`EnchantmentHelper`（定义）与 `RangedWeaponItem`（消费）引用，而 `BowItem extends RangedWeaponItem`
—— 所以 `minecraft:projectile_count` / `projectile_spread` **在弓上直接生效**，
`EnchantMultishotHandler` 不需要 `ArrowLooseEvent`。
