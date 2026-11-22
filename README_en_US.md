[![GitHub All Releases](https://img.shields.io/github/downloads/FireFlyUnlegit/FireFly-Simple-Tweaks/total.svg)](https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/releases)
[![GitHub Release](https://img.shields.io/github/downloads/FireFlyUnlegit/FireFly-Simple-Tweaks/latest/total.svg)](https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.12.2%20%7C%201.21.1-blue.svg)]()
[![Loader](https://img.shields.io/badge/Loader-Forge%20%7C%20Fabric-orange.svg)]()

**[English](README_en_US.md) | [简体中文](README.md)**

---

# ⚡ FireFly's Simple Tweaks

A Minecraft mod I build for fun: new enchantments, new mechanics and better combat feedback for vanilla.

Current **1.21.1 / Fabric** release: **v2.4.0** (171 enchantments, 17 elemental reactions, aura gauges, reworked armour curve).

## 🌿 Branches

| Branch                                                                                        | Game version | Loader | Status        |
|-----------------------------------------------------------------------------------------------|--------------|--------|---------------|
| [`forge-1.12.2`](https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/tree/forge-1.12.2)   | 1.12.2       | Forge  | ✅ Stable     |
| [`fabric-1.21.1`](https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/tree/fabric-1.21.1) | 1.21.1       | Fabric | ✅ **v2.4.0** |

- The **1.21.1 / Fabric** port is a full cross-version rewrite — see [`MIGRATION.md`](./MIGRATION.md)
- The two versions are maintained **in parallel**, never merged
- Per-batch changes live in the commit history
- Per-item acceptance notes are kept locally only (`docs/`, `AGENTS.md`, `DEV_GUIDE_1.21.1.md` are local files and not committed)

---

## 📦 Features

### 🧙 Custom enchantments

**171** unique enchantments, split by rarity:

| Rarity | Count | Rarity | Count |
|--------|-------|--------|-------|
| COMMON | 13 | LEGENDARY | 16 |
| UNCOMMON | 13 | MYTHIC | 13 |
| RARE | 28 | MYSTERY | 14 |
| EPIC | 35 | UNIQUE | 5 |
| ADVANCED_COMMON (advanced) | 4 | ADVANCED_EPIC (advanced) | 8 |
| ADVANCED_UNCOMMON (advanced) | 4 | ADVANCED_LEGENDARY (advanced) | 2 |
| ADVANCED_RARE (advanced) | 6 | ADVANCED_MYTHIC (advanced) | 5 |
| ADVANCED_MYSTERY (advanced) | 5 |  |  |

**Advanced enchantments** (ADVANCED_*) never come from the enchanting table: a book has to go onto a piece that already has the maxed base form, which that level is then consumed, and the piece counts as base max + own level from then on (e.g. Advanced Deft I on a weapon behaves like Deft IV).

#### 🌈 Elemental system

Seven elements: **fire / frost / lightning / water / nature / shadow / light**.
Each element has one **conversion** enchantment (EPIC, mutually exclusive) and one **affinity** armour
enchantment (RARE), plus four **cross-element** enchantments (mastery / resonance / penetration / ward).

- **Conversion**: cancels `1.5% × level` (cap **7.5%**) of the hit and re-deals it as that element, plus
  half of it again as a bonus. **Can be put on swords, bows and crossbows** — arrow hits convert and react
  just like melee.
- **Affinity**: per armour piece, −4% damage of that element per level, up to **80%** across four pieces.
  The seven affinities do not exclude each other.
- **Mastery**: elemental **output** +10% per level (cap **+200%**); it never boosts the physical part.
- **Resonance**: **transformative reaction** damage +10% per level (cap **+30%**); it does not scale
  amplifying multipliers.
- **Penetration**: ignores 8% per level (cap **24%**) of the target's elemental reduction.
- **Ward**: −3% per level (cap **9%**) against **any** elemental damage, **multiplied** with affinity.
- **Elemental damage ignores armour** (the seven `element_*` damage types plus `moon` / `star` /
  `true_damage` are in `#minecraft:bypasses_armor`).
- **Non-elemental damage**: anything no element claims (a plain swing, explosions, falling, the void…)
  belongs to no element, never reacts, never eats elemental reduction, and prints grey (`PHYSICAL_COLOR`).
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

- **Accumulation**: a repeat application of the same element **adds up**, but only counts **50%** — eight
  weak hits (1.0 × 0.5 × 8) are needed to reach 4.0, so many small attachments do reach a high tier, just
  **slowly**; a single ultra application is still the fastest route.

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
  `(4.0 + power × 0.55) × coefficient × (1 + mastery × 0.08)`; **no weapon scaling, no crits**.
  **Power = gear strength × 5** (= armour/40 + health above 20/20 + attack damage/20 + enchantment
  score/24, the same source as the mob difficulty curve): naked ≈ 0.5, iron ≈ 6, full build ≈ 35,
  **uncapped** — better gear hits harder, but **experience levels no longer feed it** (a mob farm is
  useless for transformative damage). The same target takes transformative damage at most once per **1 s**
  (status effects are not throttled).
- **Quicken** (nature + lightning): no damage; the target becomes quickened for 6 s and every nature /
  lightning hit afterwards gains flat damage (also power-scaled).

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
| Execute | finishes off low-health targets, threshold scales with level; the bonus is `2% × level × missing health`, but **at most `1.25 × this hit`** (otherwise a target with a health pool in the millions would lose half of it to one swing) |
| AoE Attack | splash the hit onto nearby enemies; pool = original damage × level, one target takes at most 100% |
| Combo | shortens the target's post-hit i-frames (−10% per level), **fully cancelled at level 10** — the only enchantment in this mod that touches i-frames |
| True Damage | part of the damage ignores armour |
| Fire Master | every hit applies burning, stacking up to 10 s; the longer the remaining burn, the faster and harder the extra ticks (down to one per 5 ticks) |
| Advanced Fire Master | **advanced** form: book only, onto a sword that already has Fire Master VI, consuming that level; counts as **6 + own level**. Hits burn at that level, you get the matching fire resistance, and targets already burning take +2% x level |
| Advanced Feather Fall | **Advanced** form: a book on boots that already carry Feather Fall IV, consuming that IV. Counts as **4 + its own level**. Fall damage is reduced by `20% x (4 + level)` (immune from I), and a landing grants Speed II for `2 x level` seconds |
| Finality | **final damage +1% x level** on every hit (up to +3%), multiplying with Arcane Echo's final damage |
| Elemental Surge | **elemental damage +1% x level** you deal (up to +3%), covering conversions, reaction riders and aura splashes |
| Reaction Surge | **reaction damage +1% x level** you trigger (up to +3%), using the weapon that landed the hit (the bow, for arrows) |
| Catalyze | targets you hit resolve damage over time `1 + level` times faster for 5 s: venom ticks keep their value and simply land more often, and a burning target takes `level` extra fire damage every 20 ticks |
| Heavenly Punishment | big final-damage boost; weakens, roots, suppresses attack speed and calls down lightning |
| Starfall | arrows summon a meteor that hunts the target and explodes on impact |
| Gravity Strike | more damage the further you fall, capped, no bonus on flat ground |
| Charged Strike | stores part of each hit and releases it on the next one |
| Motion Bonus | more damage the faster you move relative to the target, max on a sprint collision |
| Combat Master | consecutive hits on the same target stack a bonus, up to 20% × level |
| Assassin | executes low-health enemies |
| Echo Shot | arrows echo on the target for extra damage |
| Fast Bow | shorter draw time, higher level = faster |
| Piercing Arrow | arrows pierce several enemies and stay pickable after hitting a block |
| Tracking Arrow | arrows slowly turn toward the nearest enemy, radius scales with level |
| Multishot | one extra arrow per level, spread horizontally, each consuming ammo |
| Split Arrow | on its first hit an arrow splits into 2 (pierce or not), each keeping 60%/75%/90% of the original damage and inheriting the bow, so the children still pierce and track; if another enemy is nearby, one child turns onto it |
| Volley | after a charged shot another round follows every 3 ticks, 1 + level rounds in total; each round repeats the original fan, so with Multishot it fires 1 + Multishot level arrows, at that shot's speed and no extra ammo |
| Hunter's Mark | arrow hits mark the target for 10 s; the next damage is increased by 20%/level |
| Sniper | more damage the longer the arrow flies (+0.5% per block per level, capped at 40 blocks, doubled at max level and range) |
| Elemental Mastery | +10% elemental conversion and reaction damage per level (cap +200%); elemental part only, no melee bonus, conflicts with no conversion |
| Elemental Resonance | **reaction** damage +10% per level (cap +30%); reactions only, never elemental direct damage |
| Elemental Penetration | ignore 8%/level (cap 24%) of the target's elemental reduction |
| Seven Conversions | fire / frost / lightning / water / nature / shadow / light conversion (1.5%/level, cap 7.5%), mutually exclusive — see the elemental section above |
| Detonate | on hit, if the target's aura is ≥ 2.0, consume all of it and deal `aura × (2 + 0.5 × level)` damage of that element (**no reaction**) |
| Elemental Spread | when a reaction fires, attach `0.5 × level` of that element to other enemies within `2 + 0.5 × level` blocks (**aura only, no damage**) |
| Hamstring | hits have a `40% + 10% × level` chance to apply Slowness I for 3 s (refreshes, never stacks — pure speed control, no damage rider) |
| Shield Bash | **shield only**: blocking a melee hit knocks the attacker back and deals `2 + 2 × level` **true damage** (ignores armour, never crits); projectiles do not trigger it |
| Riposte | after being hit in melee, your next hit within **3 s** deals `+15% × level`, then the charge is spent (no combo multiplier, applies to final damage) |
| First Strike | the first hit on a target that is **still at full health** deals `+12% × level` (IV = +48%); never twice on the same target |
| Deft | attack speed `+8%/level` (III = +24%), shortening the swing interval from 0.625 s to 0.504 s |
| Advanced Deft | **advanced** form: book only, onto a weapon that already has Deft III, consuming that level; the piece then counts as **3 + own level**. Attack speed +24% plus +10% per level, and 5% x level knockback resistance |

#### 🛡️ Defence

| Enchantment | Effect |
|-------------|--------|
| Void Protection | teleports you high up when you fall into the void, consuming one level |
| Flight | chestplate grants flight; costs hunger and durability |
| Echo Shield | each hit is reduced by **3% × level** (total level capped at 20 → 60%) and the reduced part is stored in the echo buffer; when the buffer covers the hit it is fully negated and **the whole buffer is reflected back** (keeping the element that hit you, **ignoring armour**). **The blocked attacker cannot be healed for the next `level` ticks (at most 5 ticks = 0.25 s)** — the 1.12.2 original duration, barely readable in practice; the real heal cut is Grievous Wounds |
| Mirror | a flying arrow / trident that hits you has a `25% × level` chance to be **caught**: ownership flips to you, it flies back at 1.5× speed, its damage is recalculated from your attack damage, and it no longer hurts you (the projectile entity is taken over — not a reflected damage number) |
| Damage Limiter | medium hits are clamped to a fraction of max health, lethal hits pass through |
| Resilience | more reduction the lower your health; four armours at III with 10% health reaches 84% |
| Vitality | increases max health |
| Greater Vitality | the **upgraded** Vitality: only an enchanted book used on armour that already holds Vitality X can add it, consuming that X; the piece then counts as **10 + its own level**. **+8 health** per level plus **+25** per ten levels, then x(1 + 1.5% x ladder levels across the four slots), up to **+1848 health** for a full set |
| Extra Armor | grants armour and toughness |
| Greater Armor | the **upgraded** Extra Armor: only an enchanted book used on armour that already holds Extra Armor VIII can add it, consuming that VIII; the piece then counts as **8 + its own level**. **+3.5 armour and +2.8 toughness** per level, then x(1 + 1.5% x ladder levels across the four slots), up to about **+439 armour and +351 toughness** for a full set |
| Anti Knockback | increases knockback resistance |
| Damage Reduction | percentage damage reduction |
| Death Protection | consumes one level to survive a lethal hit and grants absorption |
| Bulwark | final damage you take is reduced by **1% x level** (up to 3%), **multiplying** with Arcane Echo's final reduction |
| Advanced Saturation | **Advanced** form: a book on a chestplate that already carries Saturation VIII, consuming that VIII. Counts as **8 + its own level**. Hunger and saturation stay full, and the overflow becomes `level` hearts of absorption every five seconds |
| Advanced Aegis | **Advanced**: consumes Aegis III, then counts as **3 + level**. Absorption ceiling `2 x level x (3 + level)`, refilled by `2 x level` hearts every five seconds |
| Advanced Bulwark | **Advanced**: consumes Bulwark III, then counts as **3 + level**. Incoming damage reduced by `1% x (3 + level)`, `0.5% x level` of it healed back |
| Advanced Execute | **Advanced**: consumes Execute V, then counts as **5 + level**. Extra `1% x (5 + level)` of the victim missing health |
| Advanced Finality | **Advanced**: consumes Finality III, then counts as **3 + level**. Final damage `+1% x (3 + level)`, another `+2% x level` against full-health targets |
| Advanced Regeneration | **Advanced**: consumes Regeneration VIII, then counts as **8 + level**. VIII cadence, faster the more health is missing (5% per level) |
| Advanced Venom | **Advanced**: consumes Venom III, then counts as **3 + level**. Hits poison for `60 x (3 + level)` ticks, already poisoned targets take `+5% x level` |
| Immortal | chance to fully ignore a hit, plus several consecutive hits after it triggers |
| Soul Bound | items are kept on death and return to your inventory |
| Saturation | slowly restores hunger and saturation |
| Regeneration | slowly restores health |
| Healer | converts damage dealt into healing for the target |
| Healing Blade | heals you for a share of the damage dealt; overflow becomes absorption |
| Delayed Recovery | part of the damage returns as healing over the next seconds; death cancels it |
| Healing Multiplier | incoming healing increased by +10%/level of that heal plus a flat 0.25/level; never heals past full health |
| Grievous Wounds | reduces the healing the target receives; at level V they cannot heal at all |
| Prismatic Blessing | +4% per level to max health, attack damage, attack speed, movement speed, armour and toughness |
| Celestial Blessing | lifesteal on damage, overflow becomes a light shield, hits add true damage and health shred, summons homing light particles |
| Adopted Armor | repeated damage of the same type stacks reduction (max 6 stacks), 0.9% × level per stack |
| Seven Affinities | fire / frost / lightning / water / nature / shadow / light affinity, −4% per level per armour piece (cap 80%); **none of them exclude each other** |
| Elemental Ward | **all** elemental damage −3%/level (cap −9%); **multiplied** with affinity, so "specialist" and "generalist" both have value |
| Elemental Catalyst | attachment ICD shortened by **0.5 s** per level (2.5 → 2.0 / 1.5 / 1.0 s) — the ceiling on reaction rate at high attack speed |
| Elemental Echo | taking elemental damage attaches `min(0.3 × level, 1.0)` of that element to yourself — getting hit becomes ammo for the next swing |
| Elemental Sight | while worn, the **action bar** shows the element and aura amount of whatever you look at (makes the whole aura system visible) |
| Feather Fall | fall damage `−20%/level` (IV = −80%), **not** immunity, stacks with the enchantments that do cancel falls |
| Fleetfoot | movement speed `+6%/level` while worn (III = +18%), removed the moment the boots come off |

#### 🔧 Tools

| Enchantment | Effect |
|-------------|--------|
| Auto Smelting | mined blocks drop furnace/blast-furnace products (conflicts with Silk Touch, stacks with Fortune) |
| Item Fixer | slowly repairs durability (conflicts with Mending) |
| Momentum | mining the same block type speeds up over time |
| Tunneling | breaking a block also breaks connected same-type blocks (I: up/down, II: cross, III: 3×3) |
| Unbreakable | the item never loses durability |
| ReForge | vanishes immediately after applying, resetting the item's anvil cost |
| Experience Stealer | **every** damage you deal turns into experience at `10% × level` — elemental conversions, reactions, venom, arcs, holy light, shield reflections, not just the physical hit; hitting a player also drains their experience progress |
| Acid Attack | corrodes the target's held weapon on hit |
| Armor Breaker | corrodes a random piece of the target's armour on hit |
| Swift Sneak | faster movement while sneaking |
| Scavenger | killing a **levelled** mob has a level-scaled chance to drop extra vanilla materials; higher mob levels give better materials (iron nuggets below 20 / gold nuggets below 40 / iron ingot below 60 / emerald above) |
| Shield Breaker | **axe only**: hitting a **blocking** target drops the block immediately (that hit lands as if unblocked) and costs `2 × level` extra shield durability |
| Bountiful | **hoe only**: breaking crops or leaves has a `15%/level` chance to double the whole drop list (fortune and silk-touch results included) |
| Gleaner | **pickaxe only**: mining iron/gold/copper ore has a `10%/level` chance to drop one extra nugget-sized item (iron nugget / gold nugget / raw copper), stacks with Fortune |
| Aqua Worker | **helmet only**: underwater mining speed `×(1 + level)` (IV exactly cancels the vanilla ÷5), nothing on dry land |

#### ⭐ Special

| Enchantment | Effect |
|-------------|--------|
| **Infinite Power** | flight, invulnerability, instant mining, infinite food/durability, laser attack, huge drops, soulbound — everything (creative only) |
| **Extraction** | consumed on use: take the item's **highest-level** enchantment and turn it into a book of the same level; the item keeps the rest. **Cannot extract itself, never duplicates** |
| **Elemental Attunement** | consumed on use: replace the conversion enchantment with **another random element** (level kept); nothing is consumed without a conversion |
| **Reroll** | consumed on use: strip every **mod** enchantment and roll 2 fresh **applicable** ones (level 1 to half max), never a conflicting pair |
| **Infusion** | consumed on use: spend **10 experience levels** to raise the highest non-maxed enchantment by **one level**; nothing consumed when maxed or short on levels |
| **Void Collapse** | at 5 stacks, collapse: `min(target max health × (5% + 5% × level), accumulated × 2)` **true damage** (ignores armour, i-frames, no crits), 50% to other enemies within 6 blocks; each stack also makes the target take +12% × level from you. **No downside**, just one collapse per target per 1 s |
| **Elemental Sovereign** | the conversion element **becomes whichever one reacts with the target's current aura** (falls back to your own conversion element when there is none); every reaction you trigger deals **+30% × level** |
| **Midas Touch** | block drops `×(1 + 25% × level)` (**multiplicative with Fortune**), mining speed `+20% × level`, plus bonus experience on a chance. Deliberately ignores mob drops and experience gain (those belong to Scavenger / Experience Stealer) |
| **Reaction Cascade** | if the target **already carries** an aura, immediately resolve a reaction with the **strongest element that reacts with it** (**ignoring the attachment ICD**), feeding `40% + 20% × level` of the hit into it; **0.5 s** internal cooldown |
| **Gale Elytra** | gliding pushes you along your heading (1.2% per level, cap 5%); every 10 s **sneak while gliding** to dash, costing `3 × level` elytra durability. **Elytra only** — vanilla elytra have an enchantability of 0 and take no table enchantments at all; this mod raises it to 15, so this and every other enchantment can be applied at the table |
| **Attunement** | every **distinct** enchantment on the weapon (vanilla included) adds `+2% × level` **final damage**; several levels of one enchantment count once |
| **Skyfall** | holding the mace cancels fall damage; a hit made with fall distance sends a `15% × level` shockwave to every other enemy within `2 + level` blocks |
| **Sunder** | a mace blow goes straight **through a raised shield**: the block is broken at once (the hit lands with full damage) and the shield loses `4 × level` extra durability |
| **Quake** | interrupts a target that is **using an item** (bow draw / shield / potion / food) and puts that item on a 1 s cooldown |
| **Devour** | every kill feeds **this weapon**: **+8% final damage** per stack, up to `5 × level` stacks (III = 15 = +120%); a kill grants `1 + victim max health / 256` stacks (at most 4), so big prey feeds it faster; **dying drops half**. Stacks live in the item's NBT — a new weapon starts over, and the tooltip shows the current count |
| **Multitude** | a reaction you trigger has a `15% + 10% × level` chance to be **replicated** onto every other enemy within `6 + 2 × level` blocks: each of them receives **the trigger element** and **the reaction's own damage** (unlike Elemental Spread — that one only attaches aura) |
| **Karma** | every **attack you land** (elemental conversions / reactions / venom riders do not count) **resets** the karma pool to `20% + 10% × effective level` of that hit. The pool lasts `⌊5 + effective level⌋` s and cancels incoming damage at **1:1** (the value before armour reduction), for at most `⌊effective level⌋` hits, then it is gone. **Max IV per piece**; effective level = sum of all four ÷ 4, capped at 4 (so stuffing 16 into one piece is still worth one set) |
| **Resonance Core** | every elemental reaction you trigger adds one Resonance stack to the target (cap `2 + level`, III = 5 stacks, 10 s). Each stack makes your **elemental damage** against it `+4%` (5 stacks = +20%); once full, your next elemental hit is a **guaranteed crit (×2.4)** and consumes every stack |
| **Blessing of the Moon** | your **water reactions** (Vaporize / Conduct / Bloom / Purify) resolve an extra `20% + 10% × level` (III = 50%) as **lunar damage**: ignores armour, can crit (`15% + 10% × level` chance, ×1.5), and adds a Moonlight stack (max 5, 10 s, +5% lunar damage each). **Amplifying reactions (Vaporize) count too**: the base is the extra damage the multiplier added, and it triggers **Moontide** — the mist lasts twice as long, every other enemy within 3 blocks is misted too, and Moonlight jumps by 2 |
| **Blessing of the Star** | your **frost reactions** (Melt / Vanish / Overload / Eclipse / Freeze / Brittle) resolve an extra `15% + 10% × level` (III = 45%) as **stellar damage**: **always crits** (×1.5) and splashes 50% of it to other enemies within 4 blocks. **Amplifying reactions (Melt / Vanish) count too**: the base is the extra damage the multiplier added, and it triggers **Starprism** — the splash is **not** reduced (100%) and everyone it hits is fractured |
| **Blessing of the Sun** | your **fire / light reactions** (Sanctify / Scorch / Annihilation / Burning / Extinguish / Quicken) resolve an extra `20% + 10% × level` (III = 50%) as **solar damage**: ignores armour, **never crits**, but **+50% against the undead**. This whole family is output: Sanctify → **Radiance** (the same solar instance lands again after 1 s); Scorch → **Sear** (15% per second for 5 s); Annihilation → **Doom** (marked 6 s — if it dies, enemies within 5 blocks take 60% of that instance); Burning → **Scorched Ground** (6 s, 10% per second to enemies within 3 blocks); Extinguish → **Embers** (three bursts in half a second, 50% each, 150% total); Quicken → **Corona** (all solar damage ×1.75 for 6 s, this one included). **Quicken deals no damage of its own and is still allowed through** (Corona opens anyway); amplifying reactions are scaled from the extra damage they added |
| **Elemental Supremacy** | (1) your **highest** elemental affinity also defends against **all seven** elements (maximum, never summed). (2) its **own mitigation zone**: `4.5% × total level` (**90%** at 20 levels), applied separately from the affinity and multiplied with it — 80% × 90% = **98%**, never 100%. (3) every attack carries an extra `5%` of its damage as an elemental conversion (the hit itself is not reduced), once per 10 total levels (2 at a full set), random element, can start a reaction. (4) every elemental reaction that hits you is remembered, capacity = total level ÷ 2 (max 10); your **next attack** replays all of them (each re-based on 40% of that hit). (5) attachment and reaction ICDs are shortened by `1 − total level ÷ 20` — both collapse to **1 tick** at a full set. (6) **Counter**: every elemental hit you take is answered by the element that counters it, attached to the attacker at **0.5 units** (an active hit applies 1), reflecting `4% × total level` of that hit (80% at a full set) as that element, and locking the attacker's healing for `total level` ticks (**1 s** at a full set) — the reflection runs on the auxiliary channel, so it can react and ignores armour but never re-enters on-hit; not triggered when the attacker also wears Elemental Supremacy |
| **Arcane Echo** | turns the **total enchantment levels of your six slots (both hands + four armour pieces)** into a block of bonuses. Strength = `total x level x 0.6`, multiplied by each coefficient: max health `0.24`, armour `0.32`, toughness `0.16`, attack damage `0.05`, attack speed `0.12%`, movement speed `0.08%` (**the only capped one: 20%**), knockback resistance `0.15%`, luck `0.015`, **flat damage reduction `0.05`**, **final damage `0.05%`**, **final reduction via `rating/(rating+0.5)`** (rating = `0.1% x strength`; approaches 100% but never reaches it), **elemental damage `0.25%`**, **reaction damage `0.35%`**, **ranged (bow/crossbow) damage `0.15%`**, **healing received `0.25%`** and **mining speed `0.1%`** — everything except move speed is **uncapped**. Max **level 10**: a "60 total + level X" set gives +86.4 health, +18 attack, +18% final damage, +90% elemental and +126% reaction. The **effective level is the lowest** Arcane Echo you wear (chest I + sword II = level I) and its **own level does not count** towards the total. Fits **swords / axes / maces / bows / crossbows / any armour**; the tooltip lists the live numbers and `/fst stats` shows the same figures |

### 🎮 Client features

- **Damage indicator** — floating numbers on hits and heals; percentage mode, two-line display (damage + health)
    - **Final health change only**: it shows what the health bar actually lost, never "hit for 150, lost 48"
    - **Per-element colours**: each of the seven elements has its own colour; non-elemental damage is grey
    - **Split by element**: one hit becomes **one number per element** (non-elemental / water / lightning / reaction),
      fanned horizontally and **staggered vertically** so they never overlap (random position, not attached to the entity)
    - **Reaction labels**: a reaction prints its name and the damage it dealt above the target, e.g. `Conduct (-28.30)`
- **Advanced enchanting**: a toggle in the enchanting table removes the one-enchantment cap and adds advanced forms to the pool; prices follow the displayed power (XP = power, lapis = power / 3, capped at 64).
- **HUD bars** — health and hunger drawn as bars instead of vanilla icons; the food bar prints
  `food (saturation)` and tints the saturated share of the fill gold
    - Bar numbers take decimals; the maximum number of places is configurable
    - **Compact numbers**: an opt-in `k`/`M` form (`1.09k`) that climbs through `B / T / P / E / Z / Y` and falls back to `1.2e27` past that (so ten billion health reads `10.0B`, never a wall of digits); floating damage numbers have a **separate** switch
    - **Attribute bounds**: page 6 of the config screen sets a min/max per attribute — **the default ceiling is "no ceiling"** (a value like `1.0E10` really is in force, not blocked at 2.1 billion), a blank min keeps vanilla; typing a number wins, `∞` = 3.4E38, `↺` clears that row. Applies immediately, no restart, and AttributeFix can no longer override it
- **Enchantment index** — `/simple_tweaks enchantinfo`, browsable **by rarity** or **by applicable item**; the book in the first slot flips it to the advanced catalogue
- **Config screen** — `/simple_tweaks stconfig`, applied live, no restart
- **Coloured enchantment names** — by rarity; `Infinite Power` cycles a rainbow
- **Hit particle suppression / anvil cost removal / auto sprint / no hurt camera shake** — all toggleable

### 👹 Mob enhancement

Difficulty is no longer "spawn more mobs": every mob gets a level **at spawn time** based on the current
difficulty and is enhanced by this mod. Four switches on the "Mobs" tab of `/simple_tweaks stconfig`
(config keys `mobEnhancement.*`): `enabled` / `levelsEnabled` / `enchantmentsEnabled` / `elementsEnabled`;
the same tab holds the three 0–100 **level-up chances** (see "levelling in combat" below).

- **Levels** — base stats grow with level (health, attack, armour, **knockback resistance**), **no level cap**.
  Level = chunk "age/difficulty" **+ the player's overall power** (armour, max health, attack damage,
  **enchantment score**; the score is `Σ(level × quality weight) + a breadth bonus`, where the quality
  weight is `√(10 / weight)` and lands between 1.0 and 2.2), and power below 0.4 **does not count**
  (bad starting gear never inflates the level), so a **new world starts at Lv1** and climbs as you gear
  up; a group spawned in one place spreads over a small range and shows a **`[Lv.N]`** name tag
  (computed on the server, synced to clients). **Every mob has a level** — unarmed spiders, creepers and
  slimes gain health and damage too, not just the zombies holding a sword
- **Levelling in combat** — mobs are not frozen at spawn: hitting a player, killing something and being hit
  by a player each roll an independent chance to **gain one level** (default 3% / 10% / 1%, all three are
  0–100 boxes on the mobs tab). A level-up heals to full and grants the new stats at once. The ceiling is
  "spawn level at this position × 1.5" (clamped to Lv10–100), so growth follows your gear instead of
  flat-lining
- **Knockback resistance** — `+3%` per level, up to `95%`: high-level mobs barely budge, though a crit
  knockback still moves them — and pushing them into lava still works
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

1. Download `simple_tweaks-2.3.0.jar` from [Releases](https://github.com/FireFlyUnlegit/FireFly-Simple-Tweaks/releases)
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
| `/fst stats` | open the stats overview: power breakdown, live attributes, and the damage-modifier block (final damage / final reduction / elemental mastery / conversion / penetration / reaction and ranged bonuses, with Finality, Bulwark, Elemental Surge, Reaction Surge and Heavenly Punishment folded in — shown even without Arcane Echo) (`st` also works) | none |
| `/fst enchant <target> <enchantment> <level> [slot]` | set an enchantment, level `0` removes it | OP 2 |
| `/fst difficulty` | show the session offset and the persistent offset | OP 2 |
| `/fst difficulty <0-100>` | temporarily raise the base difficulty (**session only**) | OP 2 |
| `/fst difficulty reset` | clear the session offset | OP 2 |
| `/fst difficulty modify <±value>` | add to the **computed** difficulty, **saved to config and kept across restarts** | OP 2 |
| `/fst difficulty modify clear` | reset the persistent offset | OP 2 |
| `/fst difficulty check` | print `localDifficulty / base / offset / modify / power (+term) → effective → level` | OP 2 |
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
