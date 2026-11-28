# Changelog

Player-facing notes in English, newest first. `DEVLOG.md` stays the Chinese engineering log
(phenomenon / evidence / fix / acceptance); this file only says what changed for players.

Cut a release by adding a section headed `## [<mod_version>] - <date>`. `release.yml` looks up
the section whose header contains `[<mod_version>]`, so the heading has to match `mod_version`
in `gradle.properties`.

## [2.5.0] - 2026-11-28

Minecraft 1.21.1 · Fabric · requires Fabric API and Fabric Language Kotlin

175 custom enchantments (33 advanced forms), 7 elements with reactions, levelled mobs, and a full
HUD and damage feedback suite.

### Added

**Floating info bar**
Every levelled mob now shows a compact bar above its head: name (with `[Lv.N]`), health bar, health
text, and armor value. The bar keeps a white ghost segment when health drops and stacks absorption
as a golden segment. Below 30% it switches to red. Inside the visible range it takes over the
vanilla name tag so the name is not drawn twice.

- Rendered entirely client side: players without the mod see nothing and are not affected
- New config page: enable, health text (off / absolute / percent), show armor, also show over other
  players, hide the vanilla name tag, background opacity, scale, and visible distance

**Sword blocking**
Three new enchantments: Blocking, Advanced Blocking and Auto Block. Holding a sword reduces incoming
damage by the block value; advanced blocking additionally reflects a share of it; auto block rolls a
per-level chance to raise the sword for a single tick on hit. A master switch turns the whole family
off.

**Bow enchantments**
Windbreaker (cancels air drag), Weightless (cancels gravity), Split Arrow and Burst. Quality of the
multi-arrow enchantments is now derived from the number of arrows actually fired, and each Burst
volley counts the arrows produced by Multishot.

**Damage feedback**
Critical hits are marked with `!!`; elemental reaction popups split into two lines, one for the
reaction and one for the value.

### Changed

- Mob projectiles gain damage from mob level
- Non-melee mob damage (projectiles, explosions, sonic boom) is scaled up across the board
- Creepers only gain explosion damage, no longer explosion radius
- Levelled mobs regenerate 2% of max health per second after 10 seconds out of combat
- Mob level grants knockback resistance, so high level mobs are no longer pushed around easily

### Fixed

- First person sword model disappearing while blocking
- Damage numbers and the info bar flickering in some viewing angles, and the background darkening
  sprites drawn on top of it
- The info bar occasionally crashing with a `BufferBuilder` error
- Tracking arrows staying stuck inside a body they pierced but did not leave
- Windbreaker and Weightless overwriting other enchantments' speed changes (now reverted
  incrementally)

### Removed

- `noHurtCam`: conflicted with more camera mods than it helped
- Elemental Sight: the effect was invisible and permanently occupied the action bar

### Internal

- Element damage math moved into a pure JVM module with 11 unit tests, run during the build
- One rewrite entry point for enchantment snapshots, so in-place rewrites no longer read stale levels
- Build-time checks grew from 17 to 20: every subscribed event must have a post site, every config
  key must have a reader, hand-written enchantment JSON must agree with its annotation
- Client packet receivers split out of the initializer; a best-effort protocol handshake warns on
  version mismatch instead of refusing the connection
- Feature counts in the startup log are derived at runtime instead of hardcoded
