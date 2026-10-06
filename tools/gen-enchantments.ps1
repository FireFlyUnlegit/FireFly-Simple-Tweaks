# Generates all 56 enchantment JSON definitions + tags + lang JSON for the 1.21.1 port.
#
# .ps1 execution is blocked on this machine, so load it as an expression:
#   Invoke-Expression (Get-Content .\fabric-1.21.1\tools\gen-enchantments.ps1 -Raw)
#   Invoke-GenEnchantments -SrcRoot 'F:\Codes\Mod\FireFly''s Simple Tweaks-1.12.2' -OutRoot 'F:\Codes\Mod\FireFly''s Simple Tweaks-1.12.2\fabric-1.21.1'

function Invoke-GenEnchantments {
    param(
        [Parameter(Mandatory)][string]$SrcRoot,
        [Parameter(Mandatory)][string]$OutRoot
    )

    $tiers = 'common','uncommon','rare','epic','legendary','mythic','mystery','unique'

    # rarity index mirrors the 1.12.2 EnchantmentCategories enum
    $rarity = @{ UNIQUE = -1; COMMON = 0; UNCOMMON = 1; RARE = 2; EPIC = 3; LEGENDARY = 4; MYTHIC = 5; MYSTERY = 6 }
    # 1.12.2 `EnchantmentCategories.color`, copied verbatim from
    # src/main/kotlin/.../enchantments/baseclass/EnchantmentCategories.kt. Used for the enchantment
    # name colour on the tooltip; the port needs it as a table because the 1.12.2 `ModEnchantments`
    # base class (which carried `textColor`) no longer exists -- 1.21 enchantments are data-driven.
    $catColors = @{
        UNIQUE = 'WHITE'; COMMON = 'GRAY'; UNCOMMON = 'GREEN'; RARE = 'BLUE'
        EPIC = 'LIGHT_PURPLE'; LEGENDARY = 'GOLD'; MYTHIC = 'DARK_RED'; MYSTERY = 'AQUA'
    }

    # ModEnchantmentType -> (supported_items, primary_items, slots)
    $typeMap = @{
        SWORD      = @('#minecraft:enchantable/sword',        '#minecraft:enchantable/sword',        @('mainhand'))
        WEAPON     = @('#minecraft:enchantable/weapon',       '#minecraft:enchantable/sword',        @('mainhand'))
        HELD       = @('#minecraft:enchantable/weapon',       '#minecraft:enchantable/weapon',       @('mainhand','offhand'))
        ARMOR      = @('#minecraft:enchantable/armor',        '#minecraft:enchantable/armor',        @('armor'))
        HELMET     = @('#minecraft:enchantable/head_armor',   '#minecraft:enchantable/head_armor',   @('head'))
        CHESTPLATE = @('#minecraft:enchantable/chest_armor',  '#minecraft:enchantable/chest_armor',  @('chest'))
        LEGGINGS   = @('#minecraft:enchantable/leg_armor',    '#minecraft:enchantable/leg_armor',    @('legs'))
        BOOTS      = @('#minecraft:enchantable/foot_armor',   '#minecraft:enchantable/foot_armor',   @('feet'))
        BREAKABLE  = @('#minecraft:enchantable/durability',   '#minecraft:enchantable/durability',   @('any'))
        TOOL       = @('#minecraft:enchantable/mining',       '#minecraft:enchantable/mining',       @('mainhand'))
        BOW        = @('#minecraft:enchantable/bow',          '#minecraft:enchantable/bow',          @('mainhand','offhand'))
    }

    # overrides of ModEnchantments.itemExtraDamage, keyed by enchantment id (verified from source)
    $dmgOverride = @{
        fire_master       = 0.25
        combo             = 0.15
        healing_blade     = 1.0
        celestial_blessing= 2.0
        heavenly_punishment = 5.0
        # infinite_power returns Int.MAX_VALUE -> not expressible; emitted without a damage component
    }
    $noDamageComponent = @('infinite_power')

    # Enchantments whose 1.12.2 handler was DELETED because the behaviour is fully declarative.
    # Without these entries the enchantment would do nothing at all, so they are mandatory, not
    # optional polish. Sourced from docs/phase2.5-SUMMARY.md.
    function New-AttributesBlock([array]$specs, [string]$op = 'add_value') {
        $items = foreach ($s in $specs) {
            $amt = $s[1]
            @"
      {
        "amount": {
          "type": "minecraft:linear",
          "base": $amt,
          "per_level_above_first": $amt
        },
        "attribute": "minecraft:$($s[0])",
        "id": "simple_tweaks:enchantment.$($s[2])",
        "operation": "$op"
      }
"@
        }
        return "    `"minecraft:attributes`": [`n" + (($items -join ",`n").TrimEnd()) + "`n    ]"
    }

    $declarativeEffects = @{}
    $declarativeEffects['anti_knockback'] = New-AttributesBlock @(,@('generic.knockback_resistance', 0.125, 'anti_knockback'))
    $declarativeEffects['swift_sneak']    = New-AttributesBlock @(,@('player.sneaking_speed', 0.15, 'swift_sneak'))
    $declarativeEffects['prismatic_blessing'] = New-AttributesBlock @(
        @('generic.max_health', 0.04, 'prismatic_blessing.max_health'),
        @('generic.movement_speed', 0.04, 'prismatic_blessing.movement_speed'),
        @('generic.attack_damage', 0.04, 'prismatic_blessing.attack_damage'),
        @('generic.attack_speed', 0.04, 'prismatic_blessing.attack_speed'),
        @('generic.armor', 0.04, 'prismatic_blessing.armor'),
        @('generic.armor_toughness', 0.04, 'prismatic_blessing.armor_toughness')
    ) 'add_multiplied_base'
    # Unbreakable = never lose durability: set the per-use durability loss to 0.
    $declarativeEffects['unbreakable'] = @"
    "minecraft:item_damage": [
      {
        "effect": {
          "type": "minecraft:set",
          "value": 0.0
        }
      }
    ]
"@

    function Eval-MinAbility([string]$expr, [string]$name) {
        $e = $expr -replace '\s',''
        if ($e -match '^Int\.MAX_VALUE$') { return @{ base = 65535; per = 0; special = 'MAX' } }
        if ($e -match '^(\d+)$')                       { return @{ base = [int]$Matches[1]; per = 0; special = $null } }
        if ($e -match '^(\d+)\+(\d+)\*it$')            { $a=[int]$Matches[1]; $b=[int]$Matches[2]; return @{ base=$a+$b; per=$b; special=$null } }
        if ($e -match '^(\d+)\+it\*(\d+)$')            { $a=[int]$Matches[1]; $b=[int]$Matches[2]; return @{ base=$a+$b; per=$b; special=$null } }
        if ($e -match '^it\*(\d+)\+(\d+)$')            { $b=[int]$Matches[1]; $a=[int]$Matches[2]; return @{ base=$a+$b; per=$b; special=$null } }
        if ($e -match '^it\*(\d+)$')                   { $b=[int]$Matches[1]; return @{ base=$b;   per=$b; special=$null } }
        if ($e -match '^(\d+)\*it$')                   { $b=[int]$Matches[1]; return @{ base=$b;   per=$b; special=$null } }
        if ($e -match '^it$')                          { return @{ base = 1; per = 1; special = $null } }
        throw "unhandled minAbility form for ${name}: [$e]"
    }

    $rows = @()
    foreach ($d in $tiers) {
        $dir = Join-Path $SrcRoot "src\main\kotlin\dev\firefly\simpletweaks\enchantments\$d"
        foreach ($f in Get-ChildItem $dir -File -Filter *.kt) {
            $t = Get-Content $f.FullName -Raw
            $body = [regex]::Match($t, 'ModEnchantments\((?<b>.*?)\n\s*\)', 'Singleline').Groups['b'].Value
            $id  = [regex]::Match($body, 'id\s*=\s*"([a-z_0-9]+)"').Groups[1].Value
            if (-not $id) { $id = [regex]::Match($body, '"([a-z_0-9]+)"').Groups[1].Value }
            $ty  = [regex]::Match($body, 'ModEnchantmentType\.(\w+)').Groups[1].Value
            $mx  = [regex]::Match($body, 'enchantmentMaxLevel\s*=\s*(\d+)').Groups[1].Value
            if (-not $mx) { $mx = [regex]::Match($body, "ModEnchantmentType\.\w+\s*,\s*(\d+)").Groups[1].Value }
            $lam = [regex]::Match($body, 'minAbility\s*=\s*\{([^}]*)\}').Groups[1].Value
            if (-not $lam) { $lam = [regex]::Match($body, '\{([^}]*)\}').Groups[1].Value }
            $cat = [regex]::Match($body, 'EnchantmentCategories\.(\w+)').Groups[1].Value

            # base class: isTreasureEnchantment() = category.rarity >= 6; two classes override it to true.
            # NOTE: the id is "reforge" (not "re_forge") -- taken from unique/EnchantReForge.kt.
            $treasure = ($rarity[$cat] -ge 6) -or ($id -in @('infinite_power','reforge'))

            $rows += [pscustomobject]@{
                Tier=$d; Id=$id; Type=$ty; Max=[int]$mx; Lam=$lam; Cat=$cat
                Rarity=$rarity[$cat]; Treasure=$treasure
            }
        }
    }

    # ---- lang ---------------------------------------------------------------
    $langDir = Join-Path $SrcRoot 'src\main\resources\assets\simple_tweaks\lang'
    function Read-Lang([string]$file) {
        $m = [ordered]@{}
        # MUST read as explicit UTF-8. `Get-Content` with no -Encoding decodes as ANSI/GBK on Windows
        # PowerShell 5.1, which turned the 1.12.2 UTF-8 zh_cn.lang into mojibake and then wrote that
        # mojibake back out as UTF-8 -- i.e. the corruption was baked into the generated file at build
        # time, not introduced when the client read it. en_us was unaffected only because it is ASCII.
        # (Same trap as tools/acceptance.ps1, which is why that file carries a UTF-8 BOM.)
        foreach ($line in [System.IO.File]::ReadAllLines($file, [System.Text.Encoding]::UTF8)) {
            if ($line -match '^\s*#' -or $line -notmatch '=') { continue }
            $i = $line.IndexOf('=')
            $k = $line.Substring(0, $i).Trim()
            $v = $line.Substring($i + 1)
            if ($k) { $m[$k] = $v }
        }
        return $m
    }
    $en = Read-Lang (Join-Path $langDir 'en_us.lang')
    $zh = Read-Lang (Join-Path $langDir 'zh_cn.lang')

    # ---- port-only lang keys ------------------------------------------------
    # The 1.12.2 `.lang` files are the reference tree and are kept verbatim, so they cannot be edited.
    # Labels for features that exist ONLY in the 1.21 port -- currently the in-game config screen,
    # which is a vanilla-Screen replacement for 1.12.2's 673-line GUI -- are therefore declared here.
    #
    # The collision check is deliberate: a silent overwrite would either hide a 1.12.2 string behind a
    # new one, or hide the new one behind an old translation, and neither would be visible in a diff of
    # the generated file.
    $extraLang = [ordered]@{
        'gui.simple_tweaks.config.page' = @('%s（第 %s/%s 页）', '%s (page %s/%s)')
        'gui.simple_tweaks.config.section.general'    = @('通用', 'General')
        'gui.simple_tweaks.config.section.damage'     = @('伤害飘字', 'Damage indicator')
        'gui.simple_tweaks.config.section.movement'   = @('移动', 'Movement')
        'gui.simple_tweaks.config.toggle'             = @('%s: %s', '%s: %s')
        'gui.simple_tweaks.config.on'                 = @('开', 'On')
        'gui.simple_tweaks.config.off'                = @('关', 'Off')
        'gui.simple_tweaks.config.next_page'          = @('下一页 ▶', 'Next page ▶')
        'gui.simple_tweaks.config.done'               = @('完成', 'Done')
        'gui.simple_tweaks.config.unlimited'          = @('无限', 'Unlimited')
        'gui.simple_tweaks.config.save_hint'          = @('改动即时保存到 config/simple_tweaks.json', 'Changes are saved to config/simple_tweaks.json immediately')

        'gui.simple_tweaks.config.cancelVanillaDamageIndicator' = @('取消原版伤害粒子', 'Cancel the vanilla damage particle')
        'gui.simple_tweaks.config.disableAnvilCostLimit'        = @('移除铁砧「过于昂贵」', 'Remove the anvil "Too Expensive" cap')
        'gui.simple_tweaks.config.maxAnvilCost'                 = @('铁砧最大花费', 'Max anvil cost')
        'gui.simple_tweaks.config.disableEnchantmentTableLimit' = @('移除附魔台等级上限', 'Remove the enchanting table level cap')
        'gui.simple_tweaks.config.maxEnchantmentPower'          = @('附魔台最大等级', 'Max enchanting power')
        'gui.simple_tweaks.config.enabledEnchantmentColor'      = @('附魔名彩色', 'Coloured enchantment names')
        'gui.simple_tweaks.config.enabledSpecialParticles'      = @('附魔特效粒子', 'Enchantment particles')
        'gui.simple_tweaks.config.anvilDisenchant'              = @('铁砧祛魔', 'Anvil disenchanting')

        'gui.simple_tweaks.config.damageIndicator.enabled'        = @('伤害飘字', 'Damage numbers')
        'gui.simple_tweaks.config.damageIndicator.symbol'         = @('显示 +/- 符号', 'Show +/- sign')
        'gui.simple_tweaks.config.damageIndicator.percentageMode' = @('按百分比显示', 'Show as percentage')
        'gui.simple_tweaks.config.damageIndicator.showShadow'     = @('显示阴影', 'Show shadow')
        'gui.simple_tweaks.config.damageIndicator.duration'       = @('存活帧数', 'Lifetime (frames)')
        'gui.simple_tweaks.config.damageIndicator.riseDuration'   = @('上升时长', 'Rise duration')
        'gui.simple_tweaks.config.damageIndicator.maxDistance'    = @('可见距离', 'Visible distance')
        'gui.simple_tweaks.config.damageIndicator.scale'          = @('字号', 'Text scale')
        'gui.simple_tweaks.config.damageIndicator.yStartFactor'   = @('起始高度系数', 'Start height factor')

        'gui.simple_tweaks.config.autoSprintEnabled'    = @('自动冲刺', 'Auto sprint')
        'gui.simple_tweaks.config.autoSprintOmniSprint' = @('全向冲刺（任意方向都冲刺）', 'Omni sprint (any direction)')
    }
    foreach ($k in $extraLang.Keys) {
        if ($en.ContainsKey($k) -or $zh.ContainsKey($k)) { throw "port-only lang key collides with 1.12.2: $k" }
        $zh[$k] = $extraLang[$k][0]
        $en[$k] = $extraLang[$k][1]
    }

    # ---- emit ---------------------------------------------------------------
    $encDir = Join-Path $OutRoot 'src\main\resources\data\simple_tweaks\enchantment'
    New-Item -ItemType Directory -Force -Path $encDir | Out-Null

    $utf8 = New-Object System.Text.UTF8Encoding($false)
    function Write-Text([string]$path, [string]$text) {
        [System.IO.File]::WriteAllText($path, $text, $utf8)
    }

    $report = @()
    foreach ($r in ($rows | Sort-Object Tier, Id)) {
        $ab = Eval-MinAbility $r.Lam $r.Id
        $map = $typeMap[$r.Type]
        if (-not $map) { throw "no tag mapping for type $($r.Type) ($($r.Id))" }
        $supported, $primary, $slots = $map

        # weight: 1.12.2 computed (10 - rarity * 1.5.roundToInt()).coerceAtLeast(1)
        # note: in Kotlin `.` binds tighter than `*`, so 1.5.roundToInt() == 2 -> 10 - rarity*2
        $w = if ($r.Rarity -lt 0) { 1 } else { [Math]::Max(1, 10 - $r.Rarity * 2) }

        $anvil = if ($r.Rarity -lt 0) { 2 } else { [Math]::Min(20, 2 + $r.Rarity * 2) }

        $eff = @()
        # `ModEnchantments.itemExtraDamage` is SWORD-only in 1.12.2:
        #   `return if (this.modType == ModEnchantmentType.SWORD) (0.2 + category.rarity / 20.0) * level else 0.0`
        # so ARMOR / BOW / BREAKABLE / TOOL / CHESTPLATE / LEGGINGS / BOOTS enchantments granted **no**
        # damage bonus at all. Emitting `minecraft:damage` for every type -- which this generator did
        # until now -- was therefore a behavioural EXPANSION over the original rather than a
        # translation of it. Found while porting the bow enchantments; see
        # docs/phase6-client-notes.md §21.
        #
        # Note the `$dmgOverride` entries are all sword enchantments, so they remain reachable.
        if ($r.Type -eq 'SWORD' -and $r.Id -notin $noDamageComponent) {
            $k = if ($dmgOverride.ContainsKey($r.Id)) { $dmgOverride[$r.Id] } else { 0.2 + $r.Rarity / 20.0 }
            # round to kill float noise: 0.2 + 2/20.0 would otherwise emit 0.30000000000000004
            $k = [Math]::Round([double]$k, 4)
            $eff += @"
    "minecraft:damage": [
      {
        "effect": {
          "type": "minecraft:add",
          "value": {
            "type": "minecraft:linear",
            "base": $k,
            "per_level_above_first": $k
          }
        }
      }
    ]
"@
        }

        if ($declarativeEffects.ContainsKey($r.Id)) { $eff += $declarativeEffects[$r.Id] }

        $slotJson = ($slots | ForEach-Object { '"' + $_ + '"' }) -join ', '
        $primaryLine = if ($r.Treasure) { '' } else { "`n  `"primary_items`": `"$primary`"," }

        $json = @"
{
  "anvil_cost": $anvil,
  "description": {
    "translate": "enchantment.simple_tweaks.$($r.Id)"
  },
"@
        if ($eff.Count -gt 0) {
            $json += "`n  `"effects`": {`n$($eff -join ",`n")`n  },"
        } else {
            $json += "`n  `"effects`": {},"
        }
        # max_cost: 1.12.2 returned Int.MAX_VALUE, i.e. no upper bound. 65535 reproduces "always eligible
        # once min_cost is met" without risking overflow in vanilla's cost arithmetic.
        $json += @"

  "max_cost": {
    "base": 65535,
    "per_level_above_first": 0
  },
  "max_level": $($r.Max),
  "min_cost": {
    "base": $($ab.base),
    "per_level_above_first": $($ab.per)
  },$primaryLine
  "slots": [$slotJson],
  "supported_items": "$supported",
  "weight": $w
}
"@
        Write-Text (Join-Path $encDir "$($r.Id).json") $json

        $report += [pscustomobject]@{
            Tier=$r.Tier; Id=$r.Id; Type=$r.Type; Cat=$r.Cat; Max=$r.Max
            MinBase=$ab.base; Per=$ab.per; Weight=$w; Anvil=$anvil; Treasure=$r.Treasure
        }
    }

    # ---- non_treasure tag (=> appears in the enchanting table) --------------
    $tagDir = Join-Path $OutRoot 'src\main\resources\data\minecraft\tags\enchantment'
    New-Item -ItemType Directory -Force -Path $tagDir | Out-Null
    $nonTreasure = ($report | Where-Object { -not $_.Treasure } | Sort-Object Id |
        ForEach-Object { '    "simple_tweaks:' + $_.Id + '"' }) -join ",`n"
    Write-Text (Join-Path $tagDir 'non_treasure.json') @"
{
  "replace": false,
  "values": [
$nonTreasure
  ]
}
"@

    # ---- lang JSON (all keys, not just enchantment ones) --------------------
    $assetsLang = Join-Path $OutRoot 'src\main\resources\assets\simple_tweaks\lang'
    New-Item -ItemType Directory -Force -Path $assetsLang | Out-Null
    foreach ($pair in @(@{n='en_us';m=$en}, @{n='zh_cn';m=$zh})) {
        $lines = $pair.m.Keys | ForEach-Object {
            $v = ($pair.m[$_] | ConvertTo-Json -Compress)
            '  "' + $_ + '": ' + $v
        }
        Write-Text (Join-Path $assetsLang "$($pair.n).json") ("{`n" + ($lines -join ",`n") + "`n}`n")
    }

    # ---- RegistryKey table -------------------------------------------------
    # Every ported handler references its enchantment through a RegistryKey (the 1.21 way), so this
    # is generated rather than hand-written 56 times.
    $keyDir = Join-Path $OutRoot 'src\main\kotlin\dev\firefly\simpletweaks\enchantments'
    New-Item -ItemType Directory -Force -Path $keyDir | Out-Null
    $keyLines = ($report | Sort-Object Id | ForEach-Object {
        $const = $_.Id.ToUpperInvariant()
        '    val ' + $const + ': RegistryKey<Enchantment> = of("' + $_.Id + '")'
    }) -join "`n"
    $allList = ($report | Sort-Object Id | ForEach-Object { $_.Id.ToUpperInvariant() }) -join ', '
    $keysFile = @"
package dev.firefly.simpletweaks.enchantments

import dev.firefly.simpletweaks.SimpleTweaks
import net.minecraft.enchantment.Enchantment
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier

/**
 * RegistryKeys for all $($report.Count) enchantments.
 *
 * Since 1.21 the enchantment *definitions* live in the `data/simple_tweaks/enchantment/` folder
 * (one JSON per enchantment), so code only ever holds a [RegistryKey]. This replaces the 1.12.2
 * `object EnchantX : ModEnchantments(...)` classes, which no longer exist.
 *
 * To read a level:
 * ```
 * getItemSpecificEnchantLevel(stack, ModEnchantmentKeys.ACID_ATTACK)
 * ```
 * (see `util/EnchantmentsUtil.kt` -- it matches by key inside the stack's own enchantment component,
 * so no registry lookup or world access is needed).
 *
 * AUTO-GENERATED by tools/gen-enchantments.ps1 -- do not edit by hand.
 */
object ModEnchantmentKeys {

    private fun of(id: String): RegistryKey<Enchantment> =
        RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of(SimpleTweaks.MOD_ID, id))

$keyLines

    /** All ${($report.Count)} keys, for diagnostics/iteration. */
    val ALL: List<RegistryKey<Enchantment>> = listOf($allList)
}
"@
    Write-Text (Join-Path $keyDir 'ModEnchantmentKeys.kt') $keysFile

    # ---- enchantment name colours ------------------------------------------
    # 1.12.2 coloured the name in `ModEnchantments.decorateName(rawName(level))` from
    # `EnchantmentCategories.color`. The colour is deliberately NOT baked into the enchantment JSON
    # `description`: the feature is gated by `GeneralConfig.enabledEnchantmentColor`, and a static
    # text component cannot honour a runtime switch. So the table is generated here and applied at
    # render time by `mixin/EnchantmentNameColorMixin.java`.
    $colorLines = ($report | Sort-Object Id | ForEach-Object {
        if (-not $catColors.ContainsKey($_.Cat)) { throw "no colour for category [$($_.Cat)] (id $($_.Id))" }
        '        "' + $_.Id + '" to Formatting.' + $catColors[$_.Cat]
    }) -join ",`n"
    $colorFile = @"
package dev.firefly.simpletweaks.enchantments

import net.minecraft.util.Formatting

/**
 * Tooltip colour per enchantment, ported from 1.12.2 ``EnchantmentCategories.color``.
 *
 * AUTO-GENERATED by tools/gen-enchantments.ps1 -- do not edit by hand.
 *
 * 1.12.2 expressed this through ``ModEnchantments.decorateName`` / ``getTranslatedName``. Since 1.21
 * enchantments are data-driven and their displayed name comes from
 * ``Enchantment.getName(RegistryEntry, int)``, the colour is applied there instead -- see
 * ``mixin/EnchantmentNameColorMixin.java``.
 *
 * This is an explicit table rather than something derived from the JSON because the 1.12.2 tier
 * (``EnchantmentCategories``) has no 1.21 equivalent: 1.21 enchantments have no rarity, and the
 * ``weight`` field that the generator does write is ambiguous -- 1.12.2's ``weight`` is
 * ``10 - rarity*2`` clamped at 1, so MYTHIC and MYSTERY both come out as 1.
 */
object EnchantmentNameColors {

    /** Enchantment id (path only) -> 1.12.2 ``EnchantmentCategories.color``. */
    val COLORS: Map<String, Formatting> = mapOf(
$colorLines
    )

    fun of(id: String): Formatting? = COLORS[id]
}
"@
    Write-Text (Join-Path $keyDir 'EnchantmentNameColors.kt') $colorFile

    # ---- tier (category) and applicability (type) tables --------------------
    # The in-game enchant index (client/EnchantInfoScreen) groups enchantments by 1.12.2
    # `EnchantmentCategories` and prints the 1.12.2 `ModEnchantmentType` as "applies to". Neither
    # concept exists in 1.21 code -- enchantments are data-driven and applicability is an item tag --
    # so both tables are generated from the same parse that writes the JSON definitions.
    #
    # CATEGORY values are lowercased because they are used directly as the lang key suffix
    # (`gui.simple_tweaks.category.<name>`), which is how the 1.12.2 screen did it.
    # TYPE values keep the 1.12.2 spelling (`WEAPON`, not the lang key `sword_bow`); the caller maps
    # that one exception, so this table stays a faithful copy of the original enum.
    $catLines = ($report | Sort-Object Id | ForEach-Object {
        '        "' + $_.Id + '" to "' + $_.Cat.ToLowerInvariant() + '"'
    }) -join ",`n"
    $typeLines = ($report | Sort-Object Id | ForEach-Object {
        '        "' + $_.Id + '" to "' + $_.Type.ToLowerInvariant() + '"'
    }) -join ",`n"
    $maxLines = ($report | Sort-Object Id | ForEach-Object {
        '        "' + $_.Id + '" to ' + $_.Max
    }) -join ",`n"
    $tiersFile = @"
package dev.firefly.simpletweaks.enchantments

/**
 * 1.12.2 tier and applicability, per enchantment.
 *
 * AUTO-GENERATED by tools/gen-enchantments.ps1 -- do not edit by hand.
 *
 * 1.21 has neither concept in code: enchantments are data-driven, and "what may this go on" is an
 * item tag. The 1.12.2 tier is still needed by the in-game enchant index
 * (``client/EnchantInfoScreen``) to group entries, and ``ModEnchantmentType`` is what that screen
 * prints as "applies to", so both are generated from the same parse as the JSON definitions.
 */
object EnchantmentTiers {

    /** Enchantment id -> 1.12.2 ``EnchantmentCategories`` name, lowercased for direct lang-key use. */
    val CATEGORY: Map<String, String> = mapOf(
$catLines
    )

    /** Enchantment id -> 1.12.2 ``ModEnchantmentType`` name, lowercased. */
    val TYPE: Map<String, String> = mapOf(
$typeLines
    )

    /**
     * Enchantment id -> max level (1.12.2 ``Enchantment#getMaxLevel``).
     *
     * Generated rather than read from the enchantment registry so the in-game index depends only on
     * static data plus rendering -- no registry lookup, hence no client-registry-sync assumptions.
     */
    val MAX_LEVEL: Map<String, Int> = mapOf(
$maxLines
    )
}
"@
    Write-Text (Join-Path $keyDir 'EnchantmentTiers.kt') $tiersFile

    # Write-Host (not Write-Output): anything on the output stream would be appended to the
    # returned $report array and show up as phantom empty rows.
    Write-Host "generated enchantment JSONs : $($report.Count)"
    Write-Host "non_treasure entries        : $(($report | Where-Object { -not $_.Treasure }).Count)"
    Write-Host "treasure (excluded from tag): $(($report | Where-Object { $_.Treasure } | ForEach-Object { $_.Id }) -join ', ')"
    Write-Host "lang keys en_us / zh_cn     : $($en.Count) / $($zh.Count)"
    return ,$report
}
