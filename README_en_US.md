[![GitHub All Releases](https://img.shields.io/github/downloads/FireFlyUnlegit/FireFly-Simple-Tweaks/total.svg)](https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/releases)
[![GitHub Release](https://img.shields.io/github/downloads/FireFlyUnlegit/FireFly-Simple-Tweaks/latest/total.svg)](https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.12.2%20%7C%201.21.1-blue.svg)]()
[![Loader](https://img.shields.io/badge/Loader-Forge%20%7C%20Fabric-orange.svg)]()

**[English](README_en_US.md) | [简体中文](README.md)**

---

# ⚡ FireFly's Simple Tweaks

A Minecraft mod I build for fun: new enchantments, new mechanics and better combat feedback for vanilla.

Current **1.21.1 / Fabric** release: **v2.2.5** (95 enchantments, 19 elemental reactions, aura gauges, reworked armour curve).

## 🌿 Branches

| Branch                                                                                        | Game version | Loader | Status        |
|-----------------------------------------------------------------------------------------------|--------------|--------|---------------|
| [`forge-1.12.2`](https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/tree/forge-1.12.2)   | 1.12.2       | Forge  | ✅ Stable     |
| [`fabric-1.21.1`](https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/tree/fabric-1.21.1) | 1.21.1       | Fabric | ✅ **v2.2.5** |

- The **1.21.1 / Fabric** port is a full cross-version rewrite — see [`MIGRATION.md`](./MIGRATION.md)
- The two versions are maintained **in parallel**, never merged
- Per-batch changes live in the commit history

---

## 📦 Features

### 🧙 Custom enchantments

**95** unique enchantments, split by rarity:

| Rarity | Count | Rarity | Count |
|--------|-------|--------|-------|
| COMMON | 7 | LEGENDARY | 16 |
| UNCOMMON | 9 | MYTHIC | 9 |
| RARE | 27 | MYSTERY | 2 |
| EPIC | 24 | UNIQUE | 1 |

#### 🌈 Elemental system

Eight elements: **physical / fire / frost / lightning / water / nature / shadow / light**.
Each element has one **conversion** enchantment (EPIC, mutually exclusive) and one **affinity** armour
enchantment (RARE), plus four **cross-element** enchantments (mastery / resonance / penetration / ward).

- **Conversion**: cancels `1.5% × level` (cap **7.5%**) of the hit and re-deals it as that element, plus
  half of it again as a bonus. **Can be put on swords, bows and crossbows** — arrow hits convert and react
  just like melee.
- **Affinity**: per armour piece, −4% damage of that element per level, up to **80%** across four pieces.
  The eight affinities do not exclude each other.
- **Mastery**: elemental **output** +10% per level (cap **+200%**); it never boosts the physical part.
- **Resonance**: **transformative reaction** damage +10% per level (cap **+30%**); it does not scale
  amplifying multipliers.
- **Penetration**: ignores 8% per level (cap **24%**) of the target's elemental reduction.
- **Ward**: −3% per level (cap **9%**) against **any** elemental damage, **multiplied** with affinity.
- **Elemental damage ignores armour** (the eight `element_*` damage types are in `#minecraft:bypasses_armor`).
- **I-frames**: elemental damage does **not** bypass invulnerability frames — it is part of that swing.
  The only enchantment that shortens/cancels i-frames is **Combo** (−10% per level, fully cancelled at 10).
- **What counts as elemental**: chain lightning arcs = lightning, venom ticks = nature, celestial light =
  light, starfall meteors = fire, echo shield's retaliation = whatever hit you. All of them leave an aura.

#### 🧪 Advanced elemental theory

- **Aura gauge**: elements have an amount and **decay linearly**:

| Tier | Gauge | Lifetime | Source |
|------|-------|----------|--------|
| Weak | 1.0 | 9.5 s | most elemental hits, lightning arcs |
| Strong | 2.0 | 12 s | conversion riders, starfall, celestial light |
| Ultra | 4.0 | 17 s | heavenly punishment |

- **Attachment ICD (two counters)**: against the same target, only the 1st, 4th, 7th… hit from the same
  attacker actually attaches an element within a **2.5 s** window; the window resets afterwards. Venom
  ticks and the mod's own damage riders **bypass** the ICD.
- **Counter consumption**: water beats fire, fire beats frost, frost beats water, and water/fire/frost/
  lightning beat nature — a winning element consumes **twice** the amount (1 unit of water eats 2 of fire).
  After a reaction the **first** element keeps its remainder and keeps decaying; the **second** leaves
  nothing behind.
- **Amplifying reactions** (melt / vaporize): no separate damage, they **multiply the triggering hit**
  (×2.0 / ×1.5), so weapon, crit and mastery are all included.
- **Transformative reactions** (conduct / overload / burning / annihilation …): deal **separate damage**,
  `(4.0 + level × 0.55) × coefficient × (1 + mastery × 0.08)`; **weapon does not apply, no crits**.
  The same target takes transformative damage at most once per **1 s** (status effects are not throttled).
- **Quicken** (nature + lightning): no damage; the target becomes quickened for 6 s and every nature /
  lightning hit afterwards gains flat damage.

| Reaction | Pair | Type | Value |
|----------|------|------|-------|
| **Melt** | frost → fire | amplify | **×2.0** |
| **Melt (reverse)** | fire → frost | amplify | **×1.5** |
| **Vaporize** | fire → water | amplify | **×2.0** |
| **Vaporize (reverse)** | water → fire | amplify | **×1.5** |
| **Conduct** | water ↔ lightning | transformative | 1.25 / 1.15, **armour −40%** |
| **Overload** | frost ↔ lightning | transformative | 1.60 / 1.50, armour shred / slow |
| **Burning** | nature → fire | transformative | 1.35 |
| **Extinguish** | fire → nature | transformative | 0.90, slow |
| **Annihilation** | shadow ↔ light | transformative | 1.70, blind, attacker heals 25% |
| **Sanctify** | fire ↔ light | transformative | 1.20, attacker heals 10% |
| **Purify** | water ↔ light | transformative | 0.60, attacker heals **30%** |
| **Soulburn** | fire ↔ shadow | transformative | 1.45, blind |
| **Wither** | nature ↔ shadow | transformative | 1.10, slow |
| **Eclipse** | frost ↔ light | transformative | 0.80, blind + slow |
| **Backstab** | shadow → physical | transformative | 1.75 (highest in the table) |
| **Blunt** | light → physical | transformative | 0.55, blind |
| **Freeze** | frost → water | control | frozen for 10 s |
| **Brittle** | water → frost | transformative | 0.50, armour shred |
| **Bloom** | water / light ↔ nature | control | heavy entanglement 7 s (light version also heals) |
| **Quicken** | nature ↔ lightning | state | quickened 6 s; nature/lightning hits gain flat damage |

#### 🛡️ Armour curve (no more wasted points past 20)

Vanilla hard-caps armour at 20 points (`clamp(…, 20.0)` inside `DamageUtil#getDamageLeft`, 80% reduction).
This mod **switches to a different kind of reduction** instead:

| Armour | Behaviour |
|--------|-----------|
| `0 … 20` | vanilla formula, **byte-for-byte unchanged** (up to 80%) |
| `> 20` | percentage stays at 80%, **each extra point gives 0.5 flat reduction per hit**, capped at 75% of the remaining damage |

Example (20-damage hit): armour 20 → 4.0 taken; armour 26 → 1.0; armour 40 → 1.0 (capped).
A 100-damage hit: armour 26 → 17 taken; armour 40 → 10 taken.
**Applies to players and mobs alike** — high-level mobs no longer get softer because their armour overflowed.

#### ⚔️ Combat

| Enchantment | Effect |
|-------------|--------|
| Crit | chance to crit on hit (no jump needed), higher level = higher chance |
| Crit Damage | increases the extra damage a crit deals |
| Double Crit | chance to deal a large bonus on a crit |
| Bloodlust | more damage the more health the attacker has lost |
| Execute | finish off low-health targets, threshold scales with level |
| Area Attack | splash the hit onto nearby enemies; pool = original damage × level, one target takes at most 100% |
| Combo | shortens the target's post-hit i-frames (−10% per level), **fully cancelled at level 10** — the only enchantment in this mod that touches i-frames |
| True Damage | part of the damage ignores armour |
| Fire Master | more damage the longer the target burns; extends burning and grants fire resistance |
| Heavenly Punishment | big final-damage boost; weakens, roots, suppresses attack speed and calls down lightning |
| Starfall | arrows summon a meteor that hunts the target and explodes on impact |
| Gravity Strike | more damage the further you fall, capped, no bonus on flat ground |
| Charged Strike | stores part of each hit and releases it on the next one |
| Momentum | more damage the faster you move relative to the target, max on a sprint collision |
| Combat Master | consecutive hits on the same target stack a bonus, up to 20% × level |
| Assassin | executes low-health enemies |
| Echo Shot | arrows echo on the target for extra damage |
| Fast Bow | shorter draw time, higher level = faster |
| Piercing Arrow | arrows pierce several enemies and stay pickable after hitting a block |
| Homing Arrow | arrows slowly turn toward the nearest enemy, radius scales with level |
| Multishot | one extra arrow per level, spread horizontally, each consuming ammo |
| Hunter's Mark | arrow hits mark the target for 10 s; the next damage is increased by 20%/level |
| Sniper | more damage the longer the arrow flies (+0.5% per block per level, capped at 40 blocks, doubled at max level and range) |
| Elemental Mastery | +10% elemental conversion and reaction damage per level (cap +200%); elemental part only, no melee bonus, conflicts with no conversion |
| Elemental Resonance | **reaction** damage +10% per level (cap +30%); reactions only, never elemental direct damage |
| Elemental Penetration | ignore 8%/level (cap 24%) of the target's elemental reduction |
| Eight Conversions | physical / fire / frost / lightning / water / nature / shadow / light conversion (1.5%/level, cap 7.5%), mutually exclusive — see the elemental section above |
| Detonate | on hit, if the target's aura is ≥ 2.0, consume all of it and deal `aura × (2 + 0.5 × level)` damage of that element (**no reaction**) |
| Elemental Spread | when a reaction fires, attach `0.5 × level` of that element to other enemies within `2 + 0.5 × level` blocks (**aura only, no damage**) |

#### 🛡️ Defence

| Enchantment | Effect |
|-------------|--------|
| Void Protection | teleports you high up when you fall into the void, consuming one level |
| Flight | chestplate grants flight; costs hunger and durability |
| Echo Shield | each hit is partly reduced and stored; when the store is full the hit is negated and reflected |
| Damage Limiter | medium hits are clamped to a fraction of max health, lethal hits pass through |
| Resilience | more reduction the lower your health; four armours at III with 10% health reaches 84% |
| Vitality | increases max health |
| Extra Armour | grants armour and toughness |
| Knockback Resistance | increases knockback resistance |
| Extra Reduction | percentage damage reduction |
| Death Protection | consumes one level to survive a lethal hit and grants absorption |
| Immortal | chance to fully ignore a hit, plus several consecutive hits after it triggers |
| Soulbound | items are kept on death and return to your inventory |
| Saturation | slowly restores hunger and saturation |
| Regeneration | slowly restores health |
| Healer | converts damage dealt into healing for the target |
| Life Siphon | heals you for a share of the damage dealt; overflow becomes absorption |
| Delayed Recovery | part of the damage returns as healing over the next seconds; death cancels it |
| Healing Multiplier | incoming healing increased by +10%/level of that heal plus a flat 0.25/level; never heals past full health |
| Grievous Wounds | reduces the healing the target receives; at level V they cannot heal at all |
| Prismatic Blessing | +4% per level to max health, attack damage, attack speed, movement speed, armour and toughness |
| Celestial Blessing | lifesteal on damage, overflow becomes a light shield, hits add true damage and health shred, summons homing light particles |
| Adaptive Armour | repeated damage of the same type stacks reduction (max 6 stacks), 0.9% × level per stack |
| Eight Affinities | physical / fire / frost / lightning / water / nature / shadow / light affinity, −4% per level per armour piece (cap 80%); **none of them exclude each other** |
| Elemental Ward | **all** elemental damage −3%/level (cap −9%); **multiplied** with affinity, so "specialist" and "generalist" both have value |
| Elemental Catalyst | attachment ICD shortened by **0.5 s** per level (2.5 → 2.0 / 1.5 / 1.0 s) — the ceiling on reaction rate at high attack speed |
| Elemental Echo | taking elemental damage attaches `min(0.3 × level, 1.0)` of that element to yourself — getting hit becomes ammo for the next swing |
| Elemental Sight | while worn, the **action bar** shows the element and aura amount of whatever you look at (makes the whole aura system visible) |

#### 🔧 Tools

| Enchantment | Effect |
|-------------|--------|
| Auto Smelt | mined blocks drop furnace/blast-furnace products (conflicts with Silk Touch, stacks with Fortune) |
| Item Fixer | slowly repairs durability (conflicts with Mending) |
| Momentum (mining) | mining the same block type speeds up over time |
| Tunneling | breaking a block also breaks connected same-type blocks (I: up/down, II: cross, III: 3×3) |
| Unbreakable | the item never loses durability |
| Reforge | vanishes immediately after applying, resetting the item's anvil cost |
| Experience Stealer | extra XP based on damage dealt |
| Acid Attack | corrodes the target's held weapon on hit |
| Armour Breaker | corrodes a random piece of the target's armour on hit |
| Swift Sneak | faster movement while sneaking |
| Scavenger | killing a **levelled** mob has a level-scaled chance to drop extra vanilla materials; higher mob levels give better materials (iron nuggets below 20 / gold nuggets below 40 / iron ingot below 60 / emerald above) |

#### ⭐ Special

| Enchantment | Effect |
|-------------|--------|
| **Infinite Power** | flight, invulnerability, instant mining, infinite food/durability, laser attack, huge drops, soulbound — everything (creative only) |

### 🎮 Client features

- **Damage indicator** — floating numbers on hits and heals; percentage mode, two-line display (damage + health)
    - **Final health change only**: it shows what the health bar actually lost, never "hit for 150, lost 48"
    - **Per-element colours**: each of the eight elements has its own colour
    - **Split by element**: one hit becomes **one number per element** (physical / water / lightning / reaction),
      fanned horizontally and **staggered vertically** so they never overlap (random position, not attached to the entity)
    - **Reaction labels**: a reaction prints its name and the damage it dealt above the target, e.g. `Conduct (-28.30)`
- **Enchantment index** — `/simple_tweaks enchantinfo`, browsable **by rarity** or **by applicable item**
- **Config screen** — `/simple_tweaks stconfig`, applied live, no restart
- **Coloured enchantment names** — by rarity; `Infinite Power` cycles a rainbow
- **Hit particle suppression / anvil cost removal / auto sprint / no hurt camera shake** — all toggleable

### 👹 Mob enhancement

Difficulty is no longer "spawn more mobs": every mob gets a level **at spawn time** based on the current
difficulty and is enhanced by this mod. Four switches on the "Mobs" tab of `/simple_tweaks stconfig`
(config keys `mobEnhancement.*`): `enabled` / `levelsEnabled` / `enchantmentsEnabled` / `elementsEnabled`.

- **Levels** — base stats grow with level (health, attack, armour), **no level cap**. Level = chunk
  "age/difficulty" **+ the player's overall power** (armour, max health, attack damage, total enchantment
  levels), so **early game is Lv1–3** and it climbs as you gear up; a group spawned in one place spreads
  over a small range and shows a **`[Lv.N]`** name tag (computed on the server, synced to clients).
  **Every mob has a level** — unarmed spiders, creepers and slimes gain health and damage too
- **Modded equipment** — zombies/skeletons can spawn with **this mod's enchantments** and mixed-material
  armour. Enchantment strength comes from the **mob's total level budget**: `1 + level/10` levels
  (Lv1–9 → 1, Lv10–19 → 2, Lv20–29 → 3 …), spread across its gear, at most 3 enchantments per item.
  **Those enchantments are stripped from drops**, so modded enchantments never leak to players
- **Elemental attacks and resistances** — mobs can carry an attack element (extra elemental damage on hit)
  and elemental resistances that scale with level
- **Creeper enhancement** — explosion power scales with level (capped)
- **Difficulty offset** — `/simple_tweaks difficulty <0-100>` temporarily raises the base difficulty so you
  can watch the level/enchantment/element probabilities change; `reset` clears it (**session only**, never saved)

---

## 🚀 Installation

### 1.21.1 / Fabric

**Requirements**:

- Minecraft **1.21.1**
- [Fabric Loader](https://fabricmc.net/use/) ≥ **0.16**
- [Fabric API](https://modrinth.com/mod/fabric-api) **0.116.17+1.21.1** or newer
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) **1.12.3+kotlin.2.0.21** or newer

**Steps**:

1. Download `simple_tweaks-2.2.5.jar` from [Releases](https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/releases)
2. Drop it into `.minecraft/mods/`
3. Launch the game

> The jar must be **byte-identical** to the CI / local `clean build` artefact (do not repackage), otherwise
> PCL / Modrinth / CurseForge fingerprinting will not recognise it.

### 1.12.2 / Forge

**Requirements**:

- Minecraft **1.12.2**
- Forge **14.23.5.2847** or newer

**Steps**:

1. Download the latest release from [Releases](https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/releases)
2. Drop it into `.minecraft/mods/`
3. Launch the game

---

## ⌨️ Commands

Every command lives under **`/simple_tweaks`**; `/fst` is an exact alias.

| Command | Purpose | Permission |
|---------|---------|------------|
| `/fst enchantinfo` | open the enchantment index (`ei` / `enchinfo` also work) | none |
| `/fst stconfig` | open the config screen | none |
| `/fst enchant <target> <enchantment> <level> [slot]` | set an enchantment, level `0` removes it | OP 2 |
| `/fst difficulty` | show the session offset and the persistent offset | OP 2 |
| `/fst difficulty <0-100>` | temporarily raise the base difficulty (**session only**) | OP 2 |
| `/fst difficulty reset` | clear the session offset | OP 2 |
| `/fst difficulty modify <±value>` | add to the **computed** difficulty, **saved to config and kept across restarts** | OP 2 |
| `/fst difficulty modify clear` | reset the persistent offset | OP 2 |
| `/fst difficulty check` | print `localDifficulty / base / offset / modify / playerPower → effective → level` | OP 2 |
| `/fst debugItem` | summary of the held item (id / count / durability / name / component count) | OP 2 |
| `/fst debugItem nbt show\|set\|add\|remove\|clear` | edit `minecraft:custom_data` (values parsed as SNBT) | OP 2 |
| `/fst debugItem component list\|get\|set\|remove` | inspect/edit any data component (through its own codec) | OP 2 |
| `/fst debugItem enchants show\|copy\|paste\|merge\|set\|remove\|clear` | **copy enchantments onto another item**: hold A and `copy`, hold B and `paste`; B's other components are untouched | OP 2 |
| `/fst debugItem name\|damage\|unbreakable` | quick name / durability / unbreakable edits | OP 2 |

> You can also edit `config/simple_tweaks.json` directly; `/fst stconfig` is its GUI and applies changes live.

---

## ⚠️ Known 1.21.1 limitations

| Item | Status | Reason |
|------|--------|--------|
| `Velocity` module | ❌ not ported | not needed |
| Infinite Power backpack GUI | ❌ dropped | 1.21 slot syncing is incompatible; abandoned after 8 attempts |
| `/attribute` command integration | ❌ not ported | low priority, vanilla has it |
| Anvil disenchanting | ❌ not ported | grindstone replaces it |

---

## 🤝 Contributing

Issues and PRs are welcome.

- **1.12.2** — based on the `master` branch
- **1.21.1** — based on the `fabric-1.21.1` branch
- Please run a build before submitting

---

## 📄 License

[MIT](LICENSE)

---

## 👥 Contributors

Thanks to everyone who contributed to this project.

<a href="https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/graphs/contributors">
<img alt="Contributors" src="https://contrib.rocks/image?repo=FireFlyUnlegit/FireFly-Simple-Tweaks" />
</a>

---

*A mod built for practice.*