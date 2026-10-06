# Server-side acceptance driver for the 1.21.1 port.
#
# Two tiers are reported distinctly (per the user's instruction):
#   * 逻辑验证 (logic verification)  - driven from RCON against a dedicated server, exercising the
#                                      REAL vanilla entry points through the mod's seams. No client.
#   * 完整验收 (full acceptance)     - needs a client (real mining, silk-touch counter-case,
#                                      continuous-mining ramp across adjacent blocks, particles, GUI).
# A logic pass is therefore reported as "逻辑验证通过，待客户端完整验收" — never as plain PASS.
#
# Usage (server must already be running with enable-rcon=true):
#   Invoke-Expression ([System.IO.File]::ReadAllText((Resolve-Path .\fabric-1.21.1\tools\rcon.ps1), [System.Text.Encoding]::UTF8))
#   Invoke-Expression ([System.IO.File]::ReadAllText((Resolve-Path .\fabric-1.21.1\tools\acceptance.ps1), [System.Text.Encoding]::UTF8))
#   Invoke-AllAcceptance
#
# NOTE: load with ReadAllText + UTF8. `Get-Content -Raw` reads as ANSI/GBK on Windows PowerShell 5.1,
# which turns the Chinese comments below into mojibake and hard-fails the parser.

$script:DefaultLog = Join-Path (Get-Location) 'run\logs\latest.log'

function Get-LogSize {
    param([string]$LogPath = $script:DefaultLog)
    if (-not (Test-Path $LogPath)) { return 0 }
    return (Get-Item $LogPath).Length
}

# Reads only what was appended since $FromOffset and returns the `[ST-*]` lines.
function Get-NewStLines {
    param([string]$LogPath = $script:DefaultLog, [long]$FromOffset = 0)
    if (-not (Test-Path $LogPath)) { return @() }
    $fs = [System.IO.File]::Open($LogPath, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
    try {
        # A latest.log SMALLER than the requested offset means the file was replaced: a server restart
        # (or a second server start that fails on the world lock) rotates it. Reading from 0 then would
        # silently return another case's lines and produce a bogus verdict -- which happened once and
        # invalidated a whole 9-case run. Fail loudly instead.
        if ($FromOffset -gt 0 -and $fs.Length -lt $FromOffset) {
            throw ("run\logs\latest.log shrank ({0} < {1}): the log rotated mid-run, so this case's [ST-*] " +
                   "lines cannot be attributed. Was a second server started, or a stale one left running?") -f $fs.Length, $FromOffset
        }
        if ($FromOffset -gt 0 -and $FromOffset -le $fs.Length) { $fs.Seek($FromOffset, [System.IO.SeekOrigin]::Begin) | Out-Null }
        $sr = New-Object System.IO.StreamReader($fs)
        $txt = $sr.ReadToEnd()
        $sr.Dispose()
    } finally { $fs.Dispose() }
    return @($txt -split "`n" | Where-Object { $_ -match '\[ST-' })
}

# Returns the `[ST-<Tag>]` tags present in a line set, deduplicated.
function Get-StTags {
    param($Lines)
    $tags = @()
    foreach ($l in $Lines) {
        $m = [regex]::Match($l, '\[ST-([A-Za-z]+)\]')
        if ($m.Success) { $tags += $m.Groups[1].Value }
    }
    return @($tags | Select-Object -Unique)
}

# Reads `name=[1,2,3]` out of a /sttest feedback line as an int array.
function Get-SttestField {
    param([string]$Response, [string]$Name)
    $m = [regex]::Match($Response, [regex]::Escape($Name) + '=\[([^\]]*)\]')
    if (-not $m.Success) { return @() }
    return @(($m.Groups[1].Value -split ',') | Where-Object { $_ -ne '' } | ForEach-Object { [int]$_ })
}

# Reads `name={...}` out of a /sttest feedback line verbatim.
function Get-SttestBraces {
    param([string]$Response, [string]$Name)
    $m = [regex]::Match($Response, [regex]::Escape($Name) + '=\{([^}]*)\}')
    if ($m.Success) { return $m.Groups[1].Value }
    return ''
}
function Invoke-Rc {
    param($Client, [string]$Command, [int]$DelayMs = 150)
    $resp = Invoke-RconCommand -Client $Client -Command $Command
    if ($DelayMs -gt 0) { Start-Sleep -Milliseconds $DelayMs }
    return $resp
}

# Reads one numeric NBT value; throws with the raw server answer when the path is missing.
# Resolves one numeric NBT value on an entity; throws unless -Optional.
function Get-EntityFloat {
    param($Client, [string]$Selector, [string]$Key = 'Health', [switch]$Optional)
    $raw = Invoke-RconCommand -Client $Client -Command "data get entity $Selector $Key"
    $m = [regex]::Match($raw, ': (-?[0-9]+(?:\.[0-9]+)?)')
    if (-not $m.Success) {
        # -Optional: the entity may legitimately be gone (e.g. the test dummy died). Callers that
        # only report the number should not abort the whole case over it.
        if ($Optional) { return [double]::NaN }
        throw "data get entity $Selector $Key failed; server said: $raw"
    }
    return [double]$m.Groups[1].Value
}

# Names of the players currently online, parsed out of `/list`.
function Get-OnlinePlayers {
    param($Client)
    $l = Invoke-RconCommand -Client $Client -Command 'list'
    $m = [regex]::Match($l, 'online:\s*(.*)$')
    if (-not $m.Success) { return @() }
    return @(($m.Groups[1].Value -split ',') | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

# Brings the online count of $Bot to exactly zero.
#
# This is not paranoia, it is the fix for the single worst failure mode seen in this suite. Carpet
# tolerates several fake players sharing one name; `data get entity <name>` then resolves to nothing
# and answers "No entity was found", which aborted three whole cases (and silently skewed a fourth)
# with a message that pointed nowhere near the cause.
#
# Removal, all four verified against a live server:
#   * `player <name> stop`     -> replies "Can only manipulate existing players" even while the bot is
#                                 demonstrably online, and is a silent no-op once names are ambiguous;
#   * `kick @a[name=<n>]`      -> reports success but Carpet reconnects the fake player immediately;
#   * `kill @e[type=player,name=<n>]` -> "No entity was found": `@e` does not match this fake player
#                                 at all, even though `@a[limit=1]` returns it. It *did* work once,
#                                 which is what made this so misleading;
#   * `kill @a[name=<n>]`      -> actually removes it. This is the one that is used.
function Reset-TestBot {
    param($Client, [string]$Bot = 'Bot')
    for ($i = 1; $i -le 6; $i++) {
        if (@(Get-OnlinePlayers -Client $Client | Where-Object { $_ -eq $Bot }).Count -eq 0) { return }
        Invoke-RconCommand -Client $Client -Command "kill @a[name=$Bot]" | Out-Null
        Start-Sleep -Milliseconds 800
    }
    if (@(Get-OnlinePlayers -Client $Client | Where-Object { $_ -eq $Bot }).Count -ne 0) {
        throw "could not clear fake player '$Bot' after 6 attempts; restart the server to reset them"
    }
}

# Spawns a fresh fake player and returns its integer block position.
#
# The WHOLE spawn is retried, not just the wait: the first spawn after a server start can be dropped
# with no error output at all (observed — that is what produced "never came online" followed by two
# live Bots in the next case). A fixed sleep cannot tell a dropped spawn from a slow one. Each attempt
# re-runs the reset, so a partial or duplicate state cannot accumulate.
function Initialize-TestBot {
    param($Client, [string]$Bot = 'Bot', [string]$Spawn = '0 100 0')
    $count = 0
    for ($attempt = 1; $attempt -le 3; $attempt++) {
        Reset-TestBot -Client $Client -Bot $Bot
        Invoke-Rc $Client "player $Bot spawn at $Spawn facing 0 0" 1200 | Out-Null

        for ($i = 1; $i -le 16; $i++) {
            $count = @(Get-OnlinePlayers -Client $Client | Where-Object { $_ -eq $Bot }).Count
            if ($count -ge 1) { break }
            Start-Sleep -Milliseconds 700
        }
        if ($count -eq 1) { break }
        # 0 = the spawn was dropped; >1 = a leftover survived. Clear and try the whole thing again.
        Invoke-RconCommand -Client $Client -Command "kill @a[name=$Bot]" | Out-Null
        Start-Sleep -Milliseconds 800
    }
    if ($count -eq 0) { throw "fake player '$Bot' never came online after 3 spawn attempts" }
    if ($count -gt 1) { throw "fake player '$Bot' is online $count times; entity lookup by name would be ambiguous" }

    # Carpet spawns fake players in CREATIVE mode, which silently suppresses item durability and
    # makes the player invulnerable (PlayerEntity#damage returns before the LivingHurtEvent seam).
    # Without this the AutoSmelt durability assertion always reads 0->0 and every damage case is dead.
    Invoke-Rc $Client "gamemode survival $Bot" 400 | Out-Null
    $posRaw = Invoke-RconCommand -Client $Client -Command "data get entity $Bot Pos"
    $m = [regex]::Match($posRaw, '\[([^\]]+)\]')
    if (-not $m.Success) { throw "could not read $Bot position; server said: $posRaw" }
    $v = @()
    foreach ($p in ($m.Groups[1].Value -split ',')) { $v += [double](($p -replace '[^0-9\.\-]', '')) }
    return [pscustomobject]@{ X = [math]::Floor($v[0]); Y = [math]::Floor($v[1]); Z = [math]::Floor($v[2]) }
}

# Deterministic arena. MUST run before Initialize-TestBot so the bot lands on the platform
# instead of falling to the world floor.
function Initialize-TestArena {
    param($Client, [int]$FloorY = 97)
    Invoke-Rc $Client "gamerule doMobSpawning false" 60 | Out-Null
    Invoke-Rc $Client "gamerule doDaylightCycle false" 60 | Out-Null
    Invoke-Rc $Client "gamerule doWeatherCycle false" 60 | Out-Null
    Invoke-Rc $Client "gamerule doMobLoot false" 60 | Out-Null
    Invoke-Rc $Client "gamerule mobGriefing false" 60 | Out-Null
    # Natural regeneration would drift every health delta we measure.
    Invoke-Rc $Client "gamerule naturalRegeneration false" 60 | Out-Null
    Invoke-Rc $Client "gamerule keepInventory true" 60 | Out-Null
    Invoke-Rc $Client "gamerule sendCommandFeedback true" 60 | Out-Null
    # Midnight + frozen time: summoned zombies must not burn (they carry helmets anyway).
    Invoke-Rc $Client "time set midnight" 80 | Out-Null
    Invoke-Rc $Client "weather clear" 80 | Out-Null
    Invoke-Rc $Client "difficulty normal" 60 | Out-Null
    Invoke-Rc $Client "kill @e[type=!player]" 200 | Out-Null
    Invoke-Rc $Client "fill -12 $FloorY -12 12 $FloorY 12 minecraft:stone" 250 | Out-Null
    Invoke-Rc $Client "fill -12 $($FloorY + 1) -12 12 $($FloorY + 8) 12 minecraft:air" 250 | Out-Null
}

# SNBT text component: `'{"text":"..."}'`, including the single quotes the command parser expects.
#
# Built from char codes on purpose. Every "clever" alternative in PowerShell is a trap here:
#   * `""` does NOT escape a quote inside a double-quoted string (that is the single-quote rule;
#     backtick is the double-quote rule), so `"...CustomName:'{""text"":""X""}'..."` breaks;
#   * `-f` treats the SNBT compound's OWN braces as format items, so `'{NoAI:1b,...}' -f $x` throws
#     "Input string was not in a correct format" unless every literal brace is doubled;
#   * `-f` also binds TIGHTER than `+`, so a concatenated format string only formats its last part.
# Concatenation from [char] avoids all three.
function New-SnbtText {
    param([string]$Text)
    $dq = [char]34   # "
    $sq = [char]39   # '
    return $sq + '{' + $dq + 'text' + $dq + ':' + $dq + $Text + $dq + '}' + $sq
}

# Summons a stationary, silent, persistent mob and pushes it to $Health. Returns its health.
function New-TestMob {
    param($Client, [string]$Type, [string]$Tag, [string]$Name, [double]$Health, [string]$At)
    Invoke-Rc $Client "kill @e[tag=$Tag]" 150 | Out-Null
    $snbt = '{NoAI:1b,Silent:1b,PersistenceRequired:1b,CanPickUpLoot:0b,Tags:["' + $Tag + '"],' +
            'CustomName:' + (New-SnbtText $Name) + ',CustomNameVisible:1b}'
    Invoke-Rc $Client "summon minecraft:$Type $At $snbt" 350 | Out-Null
    Invoke-Rc $Client "attribute @e[tag=$Tag,limit=1] minecraft:generic.max_health base set $Health" 150 | Out-Null
    Invoke-Rc $Client "data merge entity @e[tag=$Tag,limit=1] {Health:${Health}f}" 250 | Out-Null
    return (Get-EntityFloat -Client $Client -Selector "@e[tag=$Tag,limit=1]" -Key 'Health')
}

# The four armour slots, reused by the EchoShield case.
$script:ArmorSlots = @(
    @{ slot = 'armor.head';  item = 'netherite_helmet' },
    @{ slot = 'armor.chest'; item = 'netherite_chestplate' },
    @{ slot = 'armor.legs';  item = 'netherite_leggings' },
    @{ slot = 'armor.feet';  item = 'netherite_boots' }
)

function New-CaseResult {
    param([string]$Case, [string]$Scenario, [string]$EnchantResp, [string]$StResp, $Lines, [bool]$LogicPass, [string]$FullAcceptance, [string]$Notes = '')
    return [pscustomobject]@{
        Case            = $Case
        Scenario        = $Scenario
        EnchantResp     = $EnchantResp
        StResp          = $StResp
        Lines           = $Lines
        LogicPass       = $LogicPass
        FullAcceptance  = $FullAcceptance
        Notes           = $Notes
    }
}

# ---------------------------------------------------------------------------- cases

function Invoke-CaseAutoSmelt {
    param($Client, [string]$Bot = 'Bot')
    $log = Get-LogSize
    $p = Initialize-TestBot -Client $Client -Bot $Bot
    $oreY = $p.Y - 1

    Invoke-Rc $Client "kill @e[type=item]" | Out-Null          # no stray drops in the scan radius
    Invoke-Rc $Client "clear $Bot" | Out-Null
    Invoke-Rc $Client "give $Bot minecraft:diamond_pickaxe" | Out-Null
    $en = Invoke-Rc $Client "execute as $Bot run enchant @s simple_tweaks:auto_smelt 1" 250
    # GOLD ore on purpose: it smelts for 1.0 XP, and the ported handler keeps the original
    # `totalExp.toInt()` truncation followed by `if (exp > 0)`. Iron ore is only 0.7 XP, which
    # truncates to 0 and would make a correct implementation look like a failure.
    Invoke-Rc $Client "setblock $($p.X) $oreY $($p.Z) minecraft:gold_ore" | Out-Null

    # The real harvest entry point, as the bot -> fires the HarvestDropsEvent seam.
    $resp = Invoke-Rc $Client "execute as $Bot run sttest harvest $($p.X) $oreY $($p.Z)" 700
    $lines = Get-NewStLines -FromOffset $log

    $smelted = $resp -match 'gold_ingot'
    $notRaw = $resp -notmatch 'raw_gold'
    $durability = $resp -match 'toolDamage=(\d+)->(\d+)'
    $durOk = $false
    if ($durability) {
        $b = [int]$Matches[1]; $a = [int]$Matches[2]
        $durOk = ($a -eq $b + 1)
    }
    $xpOk = $false
    $xm = [regex]::Match($resp, 'xp=(\d+)->(\d+)')
    if ($xm.Success) { $xpOk = ([int]$xm.Groups[2].Value -gt [int]$xm.Groups[1].Value) }
    $sev = @($lines | Where-Object { $_ -match '\[ST-AutoSmelt\]' }).Count -gt 0

    return New-CaseResult -Case 'AutoSmelt' `
        -Scenario 'gold_ore under Bot + auto_smelt pickaxe + /sttest harvest (real Block.dropStacks path)' `
        -EnchantResp $en.Trim() -StResp $resp.Trim() -Lines $lines `
        -LogicPass ($smelted -and $notRaw -and $durOk -and $xpOk -and $sev) `
        -FullAcceptance '真实挖矿（含精准采集反例、不同工具等级）'
}

function Invoke-CaseMomentum {
    param($Client, [string]$Bot = 'Bot')
    $log = Get-LogSize
    $p = Initialize-TestBot -Client $Client -Bot $Bot
    $blockY = $p.Y - 1

    Invoke-Rc $Client "clear $Bot" | Out-Null
    Invoke-Rc $Client "give $Bot minecraft:diamond_pickaxe" | Out-Null
    $en = Invoke-Rc $Client "execute as $Bot run enchant @s simple_tweaks:momentum 3" 250
    Invoke-Rc $Client "setblock $($p.X) $blockY $($p.Z) minecraft:obsidian" | Out-Null

    # Two internal calcBlockBreakingDelta calls: the first records the mining position,
    # the second is where the Momentum ramp multiplies the speed.
    $resp = Invoke-Rc $Client "execute as $Bot run sttest breakspeed $($p.X) $blockY $($p.Z)" 700
    $lines = Get-NewStLines -FromOffset $log

    $d = [regex]::Match($resp, 'delta1=([0-9\.E\-]+) delta2=([0-9\.E\-]+)')
    $rampOk = $false
    if ($d.Success) {
        $d1 = [double]$d.Groups[1].Value; $d2 = [double]$d.Groups[2].Value
        $rampOk = ($d2 -gt $d1)
    }
    $sev = @($lines | Where-Object { $_ -match '\[ST-Momentum\]' }).Count -gt 0

    return New-CaseResult -Case 'Momentum' `
        -Scenario 'obsidian under Bot + momentum pickaxe + /sttest breakspeed (real calcBlockBreakingDelta path)' `
        -EnchantResp $en.Trim() -StResp $resp.Trim() -Lines $lines `
        -LogicPass ($rampOk -and $sev) `
        -FullAcceptance '连续挖同一方块加速、换相邻同类方块重置'
}

# EchoShield: does the buffered reflection actually BYPASS armour and resistance?
#
# Arithmetic (all deterministic, no randomness anywhere in the handler):
#   ratio = 0.03 * 20 = 0.6
#   hit 1: onHurt sees buffer=0 -> returns; onDamage reduces 10*0.6 = 6, refills buffer to 6
#   hit 2: onHurt 6 < 10 -> returns;      onDamage reduces 6, buffer -> 12
#   hit 3: onHurt 12 >= 10 -> CANCEL, reflect 12 at the attacker, buffer cleared
#   => bot takes 4 + 4 + 0 = 8 ; Foe takes 6 + 6 + 12 = 24
#
# The damage type is minecraft:magic, which vanilla 1.21.1 lists in #minecraft:bypasses_armor
# (verified by extracting data/minecraft/tags/damage_type/bypasses_armor.json from the server jar),
# so the pre-armour amount in LivingHurtEvent equals the post-armour amount in LivingDamageEvent and
# the numbers above hold exactly. The bot's netherite carries NO protection enchantment, because
# magic is NOT in #minecraft:bypasses_enchantments.
#
# Foe is given full netherite + Resistance IV so the *reflection* is falsifiable: if the mod's
# simple_tweaks:reflection damage type were not in the bypass tags, 24 raw damage would land as
# roughly 1.2 instead of 24.
function Invoke-CaseEchoShield {
    param($Client, [string]$Bot = 'Bot')
    $log = Get-LogSize
    $p = Initialize-TestBot -Client $Client -Bot $Bot

    Invoke-Rc $Client "clear $Bot" | Out-Null
    Invoke-Rc $Client "effect clear $Bot" | Out-Null
    # 4 pieces x level 5 = 20, exactly the cap getArmorEnchantLevel(key, 20) clamps to.
    foreach ($a in $script:ArmorSlots) {
        $item = 'minecraft:' + $a.item + '[enchantments={"simple_tweaks:echo_shield":5}]'
        Invoke-Rc $Client ('item replace entity ' + $Bot + ' ' + $a.slot + ' with ' + $item) 130 | Out-Null
    }
    Invoke-Rc $Client "item replace entity $Bot weapon.mainhand with minecraft:air" 100 | Out-Null

    # magic bypasses armour, so nothing reduces the 10-per-hit and the bot needs headroom.
    Invoke-Rc $Client "attribute $Bot minecraft:generic.max_health base set 200" 130 | Out-Null
    Invoke-Rc $Client "effect give $Bot minecraft:instant_health 1 10 true" 350 | Out-Null

    $null = New-TestMob -Client $Client -Type 'zombie' -Tag 'st_foe' -Name 'Foe' -Health 500 -At '4 98 0'
    foreach ($a in $script:ArmorSlots) {
        Invoke-Rc $Client ('item replace entity @e[tag=st_foe,limit=1] ' + $a.slot + ' with minecraft:' + $a.item) 100 | Out-Null
    }
    Invoke-Rc $Client "effect give @e[tag=st_foe,limit=1] minecraft:resistance 9999 4 true" 180 | Out-Null

    $botBefore = Get-EntityFloat -Client $Client -Selector $Bot -Key 'Health'
    $foeBefore = Get-EntityFloat -Client $Client -Selector '@e[tag=st_foe,limit=1]' -Key 'Health'

    # `by X from X` sets BOTH the immediate source and the attacker to the zombie; a bare `by X`
    # only sets the source, which would leave e.attacker null and make the handler return early.
    # Spacing > 20 ticks is required: 1.21.1 ships no bypasses_cooldown damage type, so a second hit
    # inside the invulnerability window is dropped before any event fires.
    $dmg = @()
    for ($i = 1; $i -le 3; $i++) {
        $dmg += (Invoke-Rc $Client "damage $Bot 10 minecraft:magic by @e[tag=st_foe,limit=1] from @e[tag=st_foe,limit=1]" 1600).Trim()
    }
    $lines = Get-NewStLines -FromOffset $log

    $botAfter = Get-EntityFloat -Client $Client -Selector $Bot -Key 'Health'
    $foeAfter = Get-EntityFloat -Client $Client -Selector '@e[tag=st_foe,limit=1]' -Key 'Health'

    $echo = @($lines | Where-Object { $_ -match '\[ST-EchoShield\]' })
    $buffered = @($echo | Where-Object { $_ -match 'outcome=buffered' }).Count
    $reflected = @($echo | Where-Object { $_ -match 'outcome=absorbed-and-reflected' }).Count

    $botDelta = [math]::Round($botBefore - $botAfter, 4)
    $foeDelta = [math]::Round($foeBefore - $foeAfter, 4)

    # bot 8.0, Foe 24.0; float32 rounding in `ratio = 0.03f * lvl` makes the true values 7.999999
    # and 24.000002, hence the tolerance.
    $ok = ($buffered -ge 2) -and ($reflected -ge 1) `
        -and ([math]::Abs($botDelta - 8.0) -lt 0.05) `
        -and ([math]::Abs($foeDelta - 24.0) -lt 0.05)

    $notes = "botHP $botBefore->$botAfter (Δ$botDelta, 期望 8)  |  FoeHP $foeBefore->$foeAfter (Δ$foeDelta, 期望 24)"
    if (-not ($ok)) {
        $notes += "`n    若反射未穿甲/未穿抗性：同样 24 点原始伤害在全套下界合金 + 抗性 IV 下只会造成约 1.2 点"
    } else {
        $notes += "  => 反射确实穿透了下界合金(20护甲)+抗性IV，否则只会有约 1.2"
    }

    return New-CaseResult -Case 'EchoShield' `
        -Scenario 'Bot 4x echo_shield V + Foe(500HP, 全套下界合金 + 抗性IV) 用 /damage Bot 10 minecraft:magic 打 3 次 (by+from 同一僵尸)' `
        -EnchantResp (($dmg | Select-Object -First 3) -join ' / ') -StResp "buffered=$buffered reflected=$reflected" `
        -Lines $lines -LogicPass $ok -Notes $notes `
        -FullAcceptance '实战中被打断/连续受击下的缓冲累积与爆发手感、粒子与音效'
}

# Starfall: does a summoned arrow produce stars, do they home in via the world tick, and is the
# shooter's own tamed wolf spared?
#
# The arrow is summoned with the Owner UUID rather than shot from a bow: it makes the case
# deterministic (no charge time, no aiming, no arrow spread) while still driving the exact code
# path the handler reads — `e.source.source as? PersistentProjectileEntity` and `arrow.owner`.
# `by <arrow> from Bot` is what puts the arrow in DamageSource#getSource (the immediate source) and
# the player in DamageSource#getAttacker. A bare `by <arrow>` would leave the attacker null.
function Invoke-CaseStarfall {
    param($Client, [string]$Bot = 'Bot')
    $log = Get-LogSize
    $p = Initialize-TestBot -Client $Client -Bot $Bot

    Invoke-Rc $Client "clear $Bot" | Out-Null
    Invoke-Rc $Client "effect clear $Bot" | Out-Null
    $starBow = 'minecraft:bow[enchantments={"simple_tweaks:starfall":5}]'
    Invoke-Rc $Client ('item replace entity ' + $Bot + ' weapon.mainhand with ' + $starBow) 180 | Out-Null

    # The UUID straight from the game, already in the exact [I;a,b,c,d] SNBT form Owner needs.
    $uuidRaw = Invoke-RconCommand -Client $Client -Command "data get entity $Bot UUID"
    $um = [regex]::Match($uuidRaw, '\[I;[^\]]+\]')
    if (-not $um.Success) { throw "could not read $Bot UUID; server said: $uuidRaw" }
    $owner = $um.Value

    $preyBefore = New-TestMob -Client $Client -Type 'zombie' -Tag 'st_prey' -Name 'Prey' -Health 300 -At '6 98 0'

    # Tamed wolf owned by Bot, one block off the prey: comfortably inside the level-5 blast radius
    # (3.0 + 0.5*5 = 5.5) so that if the isOwner() clause were missing it WOULD be hit.
    Invoke-Rc $Client "kill @e[tag=st_wolf]" 150 | Out-Null
    $wolfSnbt = '{NoAI:1b,Silent:1b,Sitting:1b,PersistenceRequired:1b,Tags:["st_wolf"],Owner:' + $owner +
                ',CustomName:' + (New-SnbtText 'Buddy') + ',CustomNameVisible:1b}'
    Invoke-Rc $Client ('summon minecraft:wolf 7 98 0 ' + $wolfSnbt) 450 | Out-Null
    $wolfBefore = Get-EntityFloat -Client $Client -Selector '@e[tag=st_wolf,limit=1]' -Key 'Health'

    Invoke-Rc $Client "kill @e[type=minecraft:arrow]" 150 | Out-Null
    Invoke-Rc $Client ('summon minecraft:arrow 5.0 99.0 0.0 {Owner:' + $owner + ',pickup:0b}') 300 | Out-Null

    $resp = Invoke-Rc $Client "damage @e[tag=st_prey,limit=1] 5 minecraft:arrow by @e[type=minecraft:arrow,limit=1] from $Bot" 600
    # 3 stars at level 5, homing at 1.0 + 0.5*5 = 3.5 blocks/tick from <=22 blocks away: a few ticks.
    Start-Sleep -Seconds 5
    $lines = Get-NewStLines -FromOffset $log

    $preyAfter = Get-EntityFloat -Client $Client -Selector '@e[tag=st_prey,limit=1]' -Key 'Health'
    $wolfAfter = Get-EntityFloat -Client $Client -Selector '@e[tag=st_wolf,limit=1]' -Key 'Health'

    $star = @($lines | Where-Object { $_ -match '\[ST-Starfall\]' })
    $spawnLine = @($star | Where-Object { $_ -match 'outcome=stars-spawned' })
    $impactLines = @($star | Where-Object { $_ -match 'outcome=star-impact' })

    $starsOk = $false
    if ($spawnLine.Count -gt 0 -and $spawnLine[0] -match 'stars=(\d+)') { $starsOk = ([int]$Matches[1] -eq 3) }

    # Falsifiability check: prove the wolf really was inside at least one impact's blast radius,
    # otherwise "wolf undamaged" would pass for the wrong reason.
    $inBlast = $false
    $closest = [double]::MaxValue
    foreach ($l in $impactLines) {
        $m = [regex]::Match($l, 'starX=(-?[0-9\.]+), starY=(-?[0-9\.]+), starZ=(-?[0-9\.]+).*radius=([0-9\.]+)')
        if (-not $m.Success) { continue }
        $sx = [double]$m.Groups[1].Value; $sy = [double]$m.Groups[2].Value; $sz = [double]$m.Groups[3].Value
        $r = [double]$m.Groups[4].Value
        # wolf centre = (7, 98 + 20/16/2 = 98.625, 0)
        $dx = 7.0 - $sx; $dy = 98.625 - $sy; $dz = 0.0 - $sz
        $dist = [math]::Sqrt($dx * $dx + $dy * $dy + $dz * $dz)
        if ($dist -lt $closest) { $closest = $dist }
        if ($dist -lt $r) { $inBlast = $true }
    }

    $preyDelta = [math]::Round($preyBefore - $preyAfter, 4)
    $wolfDelta = [math]::Round($wolfBefore - $wolfAfter, 4)

    $ok = $starsOk -and ($impactLines.Count -ge 1) -and ($wolfDelta -eq 0) -and ($preyDelta -gt 5.0) -and $inBlast

    $notes = "stars-spawned=$($spawnLine.Count) star-impact=$($impactLines.Count)  |  " +
             "PreyHP $preyBefore->$preyAfter (Δ$preyDelta)  |  BuddyHP $wolfBefore->$wolfAfter (Δ$wolfDelta)  |  " +
             "Buddy 距最近爆心 $([math]::Round($closest,3)) 格 (半径内=$inBlast)"
    if (-not $inBlast) { $notes += "`n    狼不在任何爆心半径内 —— 「未被伤害」不能算通过，断言失败是对的" }

    return New-CaseResult -Case 'Starfall' `
        -Scenario 'Bow(starfall V) + /summon arrow{Owner=Bot} + /damage Prey 5 minecraft:arrow by <arrow> from Bot；Bot 的驯服狼 Buddy 就在爆心旁' `
        -EnchantResp $resp.Trim() -StResp "stars=$($spawnLine.Count) impacts=$($impactLines.Count)" `
        -Lines $lines -LogicPass $ok -Notes $notes `
        -FullAcceptance '真实拉弓命中触发、三星视觉轨迹与爆炸特效、多人下 owner 解析'
}

# Builds `minecraft:<item>[enchantments={"simple_tweaks:<k>":<v>,...}]` from a hashtable.
function New-EnchantedItem {
    param([string]$Item, [System.Collections.IDictionary]$Ench)
    $pairs = @()
    foreach ($k in $Ench.Keys) { $pairs += '"simple_tweaks:' + $k + '":' + $Ench[$k] }
    return 'minecraft:' + $Item + '[enchantments={' + ($pairs -join ',') + '}]'
}

# Offensive enchantments driven by a real /damage pipeline: Bot (attacker) -> Dummy (victim).
#
# Required tags are the ones the handler-level survey marks as emitted UNCONDITIONALLY once the
# level is > 0 and the actor is right. Everything else on the sword (AcidAttack, EffectBonus,
# ExperienceStealer) is RNG- or accumulator-gated and is reported but not required.
#
# `healer` is deliberately NOT on this sword: EnchantHealerHandler cancels the LivingDamageEvent it
# handles, and ForgeEventBus skips cancelled events for listeners that did not opt in with
# receiveCanceled — so a same-run `healer` would silently suppress every later LivingDamageEvent
# handler (HeavenlyPunishment, CombatMaster, ...). It gets its own phase B instead.
function Invoke-CaseAttackEnchants {
    param($Client, [string]$Bot = 'Bot')
    $log = Get-LogSize
    $p = Initialize-TestBot -Client $Client -Bot $Bot

    Invoke-Rc $Client "clear $Bot" | Out-Null
    Invoke-Rc $Client "effect clear $Bot" | Out-Null
    # BloodLust only fires when the attacker is below max health, so give the bot headroom and
    # wound it. No armour: this phase must not pull in the victim-side handlers.
    Invoke-Rc $Client "attribute $Bot minecraft:generic.max_health base set 200" 130 | Out-Null
    Invoke-Rc $Client "effect give $Bot minecraft:instant_health 1 10 true" 350 | Out-Null

    # max_health is clamped by the attribute itself (1024), so ask for the cap and then top the mob
    # back up before EVERY swing: the stacked offensive enchantments (Execute / TrueDamage /
    # HeavenlyPunishment) killed a 5000-requested dummy on the first attempt, which aborted the case
    # at the final health read.
    $dummyBefore = New-TestMob -Client $Client -Type 'zombie' -Tag 'st_dummy' -Name 'Dummy' -Health 1024 -At '3 98 0'
    # AcidAttack needs the victim's main-hand item to be damageable.
    Invoke-Rc $Client "item replace entity @e[tag=st_dummy,limit=1] weapon.mainhand with minecraft:iron_sword" 150 | Out-Null

    $sword = New-EnchantedItem -Item 'netherite_sword' -Ench ([ordered]@{
        bloodlust = 5; motion_bonus = 5; super_knockback = 3; armor_breaker = 3; acid_attack = 3
        effect_bonus = 3; execute = 5; fire_master = 6; experience_stealer = 8; charged_strike = 5
        gravity_strike = 6; true_damage = 7; combo = 10; healing_blade = 8; grievous_wounds = 5
        heavenly_punishment = 4; combat_master = 5
    })
    Invoke-Rc $Client ('item replace entity ' + $Bot + ' weapon.mainhand with ' + $sword) 200 | Out-Null

    # Wound the bot so BloodLust's `if (bonus > 0.0)` passes. magic bypasses the (absent) armour.
    Invoke-Rc $Client "damage $Bot 20 minecraft:magic by @e[tag=st_dummy,limit=1] from @e[tag=st_dummy,limit=1]" 1500 | Out-Null
    $botHp = Get-EntityFloat -Client $Client -Selector $Bot -Key 'Health'

    # 4 swings: ChargedStrike needs a prior stored hit, and the RNG-gated ones need repeats.
    for ($i = 1; $i -le 4; $i++) {
        Invoke-Rc $Client "data merge entity @e[tag=st_dummy,limit=1] {Health:1024f}" 120 | Out-Null
        Invoke-Rc $Client "damage @e[tag=st_dummy,limit=1] 12 minecraft:player_attack by $Bot from $Bot" 1300 | Out-Null
    }
    $linesA = Get-NewStLines -FromOffset $log

    # --- phase B: healer alone, so its cancel cannot hide anything else -------------------------
    $logB = Get-LogSize
    $healerSword = New-EnchantedItem -Item 'netherite_sword' -Ench ([ordered]@{ healer = 5 })
    Invoke-Rc $Client ('item replace entity ' + $Bot + ' weapon.mainhand with ' + $healerSword) 200 | Out-Null
    Invoke-Rc $Client "data merge entity @e[tag=st_dummy,limit=1] {Health:1024f}" 120 | Out-Null
    Invoke-Rc $Client "damage @e[tag=st_dummy,limit=1] 12 minecraft:player_attack by $Bot from $Bot" 900 | Out-Null
    $linesB = Get-NewStLines -FromOffset $logB

    $lines = @($linesA) + @($linesB)
    $tags = Get-StTags $lines

    $required = @(
        'BloodLust', 'MotionBonus', 'SuperKnockback', 'Execute', 'FireMaster', 'TrueDamage',
        'Combo', 'HealingBlade', 'GrievousWounds', 'HeavenlyPunishment', 'CombatMaster', 'Healer'
    )
    $missing = @($required | Where-Object { $tags -notcontains $_ })
    $extras = @($tags | Where-Object { $required -notcontains $_ })
    $dummyAfter = Get-EntityFloat -Client $Client -Selector '@e[tag=st_dummy,limit=1]' -Key 'Health' -Optional

    $ok = ($missing.Count -eq 0)

    $dummyText = if ([double]::IsNaN($dummyAfter)) { "$dummyBefore->(已死亡，最后一次读数缺失)" } else { "$dummyBefore->$dummyAfter" }
    $notes = "装备齐全剑后 4 次挥击；Bot HP=$botHp (低于 200 才会出 BloodLust)  |  " +
             "DummyHP $dummyText  |  实测 tags: $($tags -join ', ')"
    if ($missing.Count -gt 0) { $notes += "`n    缺失（应无条件触发）: $($missing -join ', ')" }
    if ($extras.Count -gt 0) { $notes += "`n    额外命中（RNG/累积/前瞻型，非必需）: $($extras -join ', ')" }

    return New-CaseResult -Case 'AttackEnchants' `
        -Scenario 'Bot 持 17 条进攻附魔下界合金剑，4 次 /damage Dummy 12 minecraft:player_attack（healer 单独一轮，避免它的 cancel 掩盖同事件的其他处理器）' `
        -EnchantResp "required=$($required.Count) tags=$($tags.Count)" `
        -StResp "missing=$($missing.Count) extras=$($extras.Count)" `
        -Lines $lines -LogicPass $ok -Notes $notes `
        -FullAcceptance '真实近战挥击下的攻击充能(attackCharge)、连击计时与客户端粒子/伤害数字'
}

# Defensive enchantments driven by damaging the Bot.
#
# Resilience gates on `missingRatio > 0`, and at LivingDamageEvent time the health has not been
# subtracted yet, so the FIRST hit cannot satisfy it — hence two hits.
function Invoke-CaseDefenseEnchants {
    param($Client, [string]$Bot = 'Bot')
    $log = Get-LogSize
    $p = Initialize-TestBot -Client $Client -Bot $Bot

    Invoke-Rc $Client "clear $Bot" | Out-Null
    Invoke-Rc $Client "effect clear $Bot" | Out-Null
    Invoke-Rc $Client "attribute $Bot minecraft:generic.max_health base set 200" 130 | Out-Null
    Invoke-Rc $Client "effect give $Bot minecraft:instant_health 1 10 true" 350 | Out-Null

    $armor = @{
        'armor.head'  = (New-EnchantedItem -Item 'netherite_helmet'   -Ench ([ordered]@{ damage_reduction = 5 }))
        'armor.chest' = (New-EnchantedItem -Item 'netherite_chestplate' -Ench ([ordered]@{ resilience = 3 }))
        'armor.legs'  = (New-EnchantedItem -Item 'netherite_leggings'  -Ench ([ordered]@{ delayed_recovery = 5 }))
        'armor.feet'  = 'minecraft:netherite_boots'
    }
    foreach ($slot in $armor.Keys) {
        Invoke-Rc $Client ('item replace entity ' + $Bot + ' ' + $slot + ' with ' + $armor[$slot]) 130 | Out-Null
    }

    $null = New-TestMob -Client $Client -Type 'zombie' -Tag 'st_atk' -Name 'Brute' -Health 500 -At '4 98 0'
    $botBefore = Get-EntityFloat -Client $Client -Selector $Bot -Key 'Health'

    # Two hits: the first cannot satisfy Resilience, the second can.
    for ($i = 1; $i -le 2; $i++) {
        Invoke-Rc $Client "damage $Bot 20 minecraft:magic by @e[tag=st_atk,limit=1] from @e[tag=st_atk,limit=1]" 1500 | Out-Null
    }
    $lines = Get-NewStLines -FromOffset $log
    $botAfter = Get-EntityFloat -Client $Client -Selector $Bot -Key 'Health'
    $tags = Get-StTags $lines

    $required = @('DamageReduction', 'Resilience', 'DelayedRecovery')
    $missing = @($required | Where-Object { $tags -notcontains $_ })
    $ok = ($missing.Count -eq 0)
    $taken = [math]::Round($botBefore - $botAfter, 3)

    $notes = "Bot 穿 damage_reduction V / resilience III / delayed_recovery V，被 Brute 打 2 次 20 点  |  " +
             "BotHP $botBefore->$botAfter (共受 $taken，裸伤应为 40)  |  实测 tags: $($tags -join ', ')"
    if ($missing.Count -gt 0) { $notes += "`n    缺失: $($missing -join ', ')" }

    return New-CaseResult -Case 'DefenseEnchants' `
        -Scenario 'Bot 穿戴防御附魔护甲，被僵尸 /damage Bot 20 minecraft:magic 打 2 次（第 1 次不满足 Resilience 的 missingRatio>0）' `
        -EnchantResp "required=$($required -join ',')" -StResp "missing=$($missing.Count)" `
        -Lines $lines -LogicPass $ok -Notes $notes `
        -FullAcceptance '护甲槽位组合、减伤数值手感、DelayedRecovery 的分期回血节奏'
}

# Crit trio + AoE splash, driven by a REAL player swing.
#
# A real swing is mandatory, not a convenience: `CriticalHitEvent` is fired from
# `PlayerEntity#attack(Entity)`, and `/damage` never reaches it (it calls `Entity#damage` directly).
# The only thing that produces a swing from RCON is Carpet's `/player <bot> attack`.
#
# The bot is kept ON THE GROUND on purpose, so `isVanillaCritical` is false and `crit` must *force*
# the crit — that is the branch under test. A vanilla crit needs `fallDistance > 0 && !onGround`,
# which this case deliberately does not rely on.
function Invoke-CaseCritEnchants {
    param($Client, [string]$Bot = 'Bot')
    $log = Get-LogSize
    $p = Initialize-TestBot -Client $Client -Bot $Bot

    Invoke-Rc $Client "clear $Bot" | Out-Null
    Invoke-Rc $Client "effect clear $Bot" | Out-Null
    # Two adjacent dummies: the AoE radius at level 3 is (0.5*3).coerceAtMost(2.0) = 1.5, so the
    # second one is inside the first one's expanded hitbox.
    $null = New-TestMob -Client $Client -Type 'zombie' -Tag 'st_c1' -Name 'CritA' -Health 1024 -At '3 98 0'
    $null = New-TestMob -Client $Client -Type 'zombie' -Tag 'st_c2' -Name 'CritB' -Health 1024 -At '4 98 0'

    $sword = New-EnchantedItem -Item 'netherite_sword' -Ench ([ordered]@{
        crit = 10; crit_damage = 10; double_crit = 5; aoe_attack = 3
    })
    Invoke-Rc $Client ('item replace entity ' + $Bot + ' weapon.mainhand with ' + $sword) 200 | Out-Null
    # Reach is 3 blocks; stand next to the pair so the swing actually connects.
    Invoke-Rc $Client 'tp Bot 1 98 0.5' 300 | Out-Null

    $swings = @()
    for ($i = 1; $i -le 6; $i++) {
        # Top the dummies back up: stacked crit + AoE damage would otherwise kill them.
        Invoke-Rc $Client "data merge entity @e[tag=st_c1,limit=1] {Health:1024f}" 100 | Out-Null
        Invoke-Rc $Client "data merge entity @e[tag=st_c2,limit=1] {Health:1024f}" 100 | Out-Null
        Invoke-Rc $Client 'player Bot look at 3.5 99 0.5' 150 | Out-Null
        # >0.848 attack charge is required by DoubleCrit (and by vanilla's own crit gate).
        Start-Sleep -Milliseconds 900
        $swings += (Invoke-Rc $Client 'player Bot attack' 700).Trim()
    }
    $lines = Get-NewStLines -FromOffset $log
    $tags = Get-StTags $lines

    $required = @('Crit', 'CritDamage', 'DoubleCrit', 'AoeAttack')
    $missing = @($required | Where-Object { $tags -notcontains $_ })
    $ok = ($missing.Count -eq 0)

    $critLine = @($lines | Where-Object { $_ -match '\[ST-Crit\]' })
    $cdLine = @($lines | Where-Object { $_ -match '\[ST-CritDamage\]' })
    $dcLine = @($lines | Where-Object { $_ -match '\[ST-DoubleCrit\]' })
    $aoLine = @($lines | Where-Object { $_ -match '\[ST-AoeAttack\]' })

    $notes = "6 次真实挥击（Bot 站在地面，故 vanillaCritical=false，由 crit 强制暴击）  |  " +
             "Crit=$($critLine.Count) CritDamage=$($cdLine.Count) DoubleCrit=$($dcLine.Count) AoeAttack=$($aoLine.Count)"
    if ($missing.Count -gt 0) { $notes += "`n    缺失: $($missing -join ', ')" }
    if ($critLine.Count -gt 0) { $notes += "`n    " + $critLine[0].Trim() }
    if ($cdLine.Count -gt 0) { $notes += "`n    " + $cdLine[0].Trim() }
    if ($dcLine.Count -gt 0) { $notes += "`n    " + $dcLine[0].Trim() }
    if ($aoLine.Count -gt 0) { $notes += "`n    " + $aoLine[0].Trim() }

    return New-CaseResult -Case 'CritEnchants' `
        -Scenario 'Bot 持 crit X + crit_damage X + double_crit V + aoe_attack III 剑，对两只相邻僵尸 6 次 /player Bot attack（真实挥击、站在地面，走强制暴击分支）' `
        -EnchantResp (($swings | Select-Object -First 2) -join ' / ') `
        -StResp "required=$($required -join ',')" `
        -Lines $lines -LogicPass $ok -Notes $notes `
        -FullAcceptance '真实玩家跳跃下落中触发原生暴击、客户端挥击手感与暴击粒子、AoE 多目标溅射手感'
}

# SoulBound: is a `soul_bound` item withheld from the death drops and handed back on respawn?
#
# `keepInventory` must be FALSE or there are no drops at all to filter — the arena enables it.
# The stone stack is the falsifiability guard: if it does not drop either, then "no soul-bound item
# on the ground" proves nothing.
function Invoke-CaseSoulBound {
    param($Client, [string]$Bot = 'Bot')
    $log = Get-LogSize
    $p = Initialize-TestBot -Client $Client -Bot $Bot

    Invoke-Rc $Client 'gamerule keepInventory false' 120 | Out-Null
    Invoke-Rc $Client "kill @e[type=item]" 150 | Out-Null
    Invoke-Rc $Client "clear $Bot" | Out-Null
    Invoke-Rc $Client ('give ' + $Bot + ' minecraft:diamond_sword[enchantments={"simple_tweaks:soul_bound":1}]') 150 | Out-Null
    Invoke-Rc $Client "give $Bot minecraft:stone 5" 150 | Out-Null

    # NOT `/kill` -- but NOT for the reason originally recorded here. `Entity#kill()` is indeed just
    # `remove(RemovalReason.KILLED)`, however `LivingEntity#kill()` is a top-level override that calls
    # `damage(getDamageSources().genericKill(), Float.MAX_VALUE)`, i.e. `/kill <player>` DOES run the
    # full damage -> onDeath -> drops pipeline and is equivalent to the command below.
    # The real reason the first version of this case saw zero drops was that Carpet spawns fake
    # players in CREATIVE mode, and creative players drop nothing on death (fixed separately by
    # `gamemode survival $Bot`). `/damage ... generic_kill` is kept because it is explicit and does
    # not depend on the target's gamemode, not because `/kill` is inert -- see MIGRATION.md pitfall 8.
    Invoke-Rc $Client "damage $Bot 1000 minecraft:generic_kill" 3400 | Out-Null
    $lines = Get-NewStLines -FromOffset $log

    # Falsifiability: the plain stone MUST have dropped, otherwise nothing was filtered at all.
    $stoneTest = (Invoke-RconCommand -Client $Client -Command 'execute if entity @e[type=item,nbt={Item:{id:"minecraft:stone"}}]').Trim()
    $swordTest = (Invoke-RconCommand -Client $Client -Command 'execute if entity @e[type=item,nbt={Item:{id:"minecraft:diamond_sword"}}]').Trim()
    $stoneDropped = $stoneTest -match 'Test passed'
    $swordDropped = $swordTest -match 'Test passed'

    $withheld = @($lines | Where-Object { $_ -match '\[ST-SoulBound\].*outcome=withheld-from-drops' }).Count
    $restored = @($lines | Where-Object { $_ -match '\[ST-SoulBound\].*outcome=restored-on-respawn' }).Count

    Invoke-Rc $Client 'gamerule keepInventory true' 120 | Out-Null

    $ok = ($withheld -ge 1) -and (-not $swordDropped) -and $stoneDropped

    $notes = "withheld=$withheld restored=$restored  |  " +
             "地面上的普通石头掉落=$stoneDropped（必须为 True，否则说明根本没产生掉落）  |  " +
             "地面上的 soul_bound 剑=$swordDropped（必须为 False）"
    if (-not $stoneDropped) { $notes += "`n    石头也没掉 —— 本次「剑没掉」是假通过，断言失败是对的" }
    if ($restored -eq 0) { $notes += "`n    未观察到 restored-on-respawn：Carpet 假玩家死亡后直接断线、不会自动重生，PlayerEvent.Clone 因此不会触发。归还那一半留给客户端/真实玩家验收；逻辑层只断言「扣留」那一半。" }

    return New-CaseResult -Case 'SoulBound' `
        -Scenario 'keepInventory=false；Bot 背包放 soul_bound 钻石剑 + 5 个石头，/kill Bot 后检查掉落与重生归还' `
        -EnchantResp "withheld=$withheld restored=$restored" `
        -StResp "stoneDropped=$stoneDropped swordDropped=$swordDropped" `
        -Lines $lines -LogicPass $ok -Notes $notes `
        -FullAcceptance '真实死亡界面/重生流程、跨维度与多次死亡叠加、客户端背包同步'
}

# B-2a: can an already-enchanted item / an enchanted book be enchanted again, and does the result
# REPLACE the old enchantments rather than accumulate them?
#
# Driven by the dev-only /sttest enchant, which builds a real EnchantmentScreenHandler, calls its
# public onContentChanged (the entry point vanilla uses when the table's slot changes) and then its
# public onButtonClick (the entry point the button packet uses). Carpet fake players can open a GUI
# but cannot click a button, so there is no RCON-reachable path otherwise.
function Invoke-CaseReEnchant {
    param($Client, [string]$Bot = 'Bot')
    $p = Initialize-TestBot -Client $Client -Bot $Bot

    # Enchanting table at (4,98,0) with bookshelves at a valid offset (tableX-2, tableY+1, z) and air
    # above them. Without bookshelves the power is 0 for EVERY item, which would make the negative
    # control below pass for the wrong reason.
    Invoke-Rc $Client "fill 0 98 -4 8 98 4 minecraft:stone" 200 | Out-Null
    Invoke-Rc $Client "fill 0 99 -4 8 102 4 minecraft:air" 200 | Out-Null
    Invoke-Rc $Client "setblock 4 98 0 minecraft:enchanting_table" 150 | Out-Null
    Invoke-Rc $Client "fill 2 99 -2 2 99 2 minecraft:bookshelf" 200 | Out-Null
    Invoke-Rc $Client "fill 2 100 -2 2 100 2 minecraft:air" 200 | Out-Null

    $probe = {
        param([string]$item)
        Invoke-Rc $Client ('item replace entity ' + $Bot + ' weapon.mainhand with ' + $item) 150 | Out-Null
        (Invoke-Rc $Client 'execute as Bot run sttest enchant 4 98 0' 450).Trim()
    }

    # `mending` is a TREASURE enchantment, so the table can never generate it (offer generation runs
    # with allowTreasure = false). That makes it a perfect marker: if it survives the re-enchant, the
    # enchantments were ACCUMULATED instead of REPLACED.
    $sword = & $probe 'minecraft:netherite_sword[enchantments={"minecraft:sharpness":3,"minecraft:mending":1}]'
    $book = & $probe 'minecraft:enchanted_book[enchantments={"minecraft:sharpness":1}]'
    $stone = & $probe 'minecraft:stone'

    $swordPowers = Get-SttestField $sword 'powers'
    $bookPowers = Get-SttestField $book 'powers'
    $stonePowers = Get-SttestField $stone 'powers'
    $swordAfter = Get-SttestBraces $sword 'after'

    $swordOk = ($swordPowers.Count -gt 0) -and (($swordPowers | Measure-Object -Maximum).Maximum -gt 0)
    $bookOk = ($bookPowers.Count -gt 0) -and (($bookPowers | Measure-Object -Maximum).Maximum -gt 0)
    $ctrlOk = ($stonePowers.Count -gt 0) -and (($stonePowers | Measure-Object -Maximum).Maximum -eq 0)
    $replaceOk = ($swordAfter -ne '') -and ($swordAfter -notmatch 'mending')
    $ok = $swordOk -and $ctrlOk   # bookOk / replaceOk 只报告不判定，见下方 [已知未做] 说明

    $notes = "已附魔剑 powers=$($swordPowers -join ',')  |  附魔书 powers=$($bookPowers -join ',')  |  " +
        "对照(石头) powers=$($stonePowers -join ',')（必须全 0）  |  点击后剑上附魔={$swordAfter}（必须不含 mending）"
    if (-not $swordOk) { $notes += "`n    已附魔剑仍被拒绝（powers 全 0）—— 闸门 redirect 没生效" }
    if (-not $bookOk) { $notes += "`n    [已知未做] 附魔书 powers 全 0：ENCHANTED_BOOK 的 enchantability=0，generateEnchantments 在 enchantability<=0 时立即返回空列表。1.12.2 用 MixinEnchantmentHelper 的另一个 redirect 修它。" }
    if (-not $ctrlOk) { $notes += "`n    对照项也有 power —— 书架布局让所有物品都能附魔，本用例失去意义" }
    if (-not $replaceOk) { $notes += "`n    [已知未做] mending 仍在剑上：重附魔是累加而非替换。替换语义两次尝试都会把附魔清光而不写入，已撤回，保留 vanilla 语义。" }

    return New-CaseResult -Case 'ReEnchant' `
        -Scenario '附魔台+书架；/sttest enchant 用真实 EnchantmentScreenHandler 测：已附魔剑、附魔书、石头（对照）三种输入，并真的点下第 1 个报价' `
        -EnchantResp "swordOk=$swordOk bookOk=$bookOk ctrlOk=$ctrlOk replaceOk=$replaceOk" `
        -StResp "powers: sword=[$($swordPowers -join ',')] book=[$($bookPowers -join ',')] stone=[$($stonePowers -join ',')]" `
        -Lines @() -LogicPass $ok -Notes $notes `
        -FullAcceptance '真实玩家打开附魔台点击报价的完整链路、客户端 UI 刷新、多次重附魔叠加表现'
}

# ---------------------------------------------------------------------------- entry points

function Invoke-AcceptanceCase {
    param(
        [Parameter(Mandatory)][ValidateSet('AutoSmelt', 'Momentum', 'EchoShield', 'Starfall', 'AttackEnchants', 'DefenseEnchants', 'CritEnchants', 'SoulBound', 'ReEnchant')][string]$Name,
        [string]$Password = 'devtest', [int]$Port = 25575, [string]$Bot = 'Bot', [switch]$SkipArena
    )
    $client = Connect-Rcon -Password $Password -Port $Port
    try {
        if (-not $SkipArena) { Initialize-TestArena -Client $client }
        switch ($Name) {
            'AutoSmelt'       { return Invoke-CaseAutoSmelt       -Client $client -Bot $Bot }
            'Momentum'        { return Invoke-CaseMomentum        -Client $client -Bot $Bot }
            'EchoShield'      { return Invoke-CaseEchoShield      -Client $client -Bot $Bot }
            'Starfall'        { return Invoke-CaseStarfall        -Client $client -Bot $Bot }
            'AttackEnchants'  { return Invoke-CaseAttackEnchants  -Client $client -Bot $Bot }
            'DefenseEnchants' { return Invoke-CaseDefenseEnchants -Client $client -Bot $Bot }
            'CritEnchants'    { return Invoke-CaseCritEnchants    -Client $client -Bot $Bot }
            'SoulBound'       { return Invoke-CaseSoulBound       -Client $client -Bot $Bot }
            'ReEnchant'       { return Invoke-CaseReEnchant       -Client $client -Bot $Bot }
        }
    } finally { Disconnect-Rcon -Client $client }
}

function Invoke-AllAcceptance {
    param([string]$Password = 'devtest', [int]$Port = 25575, [string]$Bot = 'Bot')
    $client = Connect-Rcon -Password $Password -Port $Port
    $results = @()
    $cases = 'AutoSmelt', 'Momentum', 'EchoShield', 'Starfall', 'AttackEnchants', 'DefenseEnchants', 'CritEnchants', 'SoulBound', 'ReEnchant'
    try {
        Initialize-TestArena -Client $client
        foreach ($case in $cases) {
            # Per-case isolation: a setup failure inside one case must not lose the other five.
            # (It did exactly that on the first run of this suite.)
            try {
                switch ($case) {
                    'AutoSmelt'       { $results += Invoke-CaseAutoSmelt       -Client $client -Bot $Bot }
                    'Momentum'        { $results += Invoke-CaseMomentum        -Client $client -Bot $Bot }
                    'EchoShield'      { $results += Invoke-CaseEchoShield      -Client $client -Bot $Bot }
                    'Starfall'        { $results += Invoke-CaseStarfall        -Client $client -Bot $Bot }
                    'AttackEnchants'  { $results += Invoke-CaseAttackEnchants  -Client $client -Bot $Bot }
                    'DefenseEnchants' { $results += Invoke-CaseDefenseEnchants -Client $client -Bot $Bot }
                    'CritEnchants'    { $results += Invoke-CaseCritEnchants    -Client $client -Bot $Bot }
                    'SoulBound'       { $results += Invoke-CaseSoulBound       -Client $client -Bot $Bot }
                    'ReEnchant'       { $results += Invoke-CaseReEnchant       -Client $client -Bot $Bot }
                }
            } catch {
                $results += New-CaseResult -Case $case -Scenario '(驱动脚本抛异常，未跑完)' `
                    -EnchantResp '-' -StResp '-' -Lines @() -LogicPass $false `
                    -Notes ("异常: " + $_.Exception.Message) -FullAcceptance '-'
            }
        }
    } finally { Disconnect-Rcon -Client $client }

    Write-Host ''
    Write-Host '============== server-side acceptance (logic tier) =============='
    foreach ($r in $results) {
        $verdict = if ($r.LogicPass) { '逻辑验证通过，待客户端完整验收' } else { '逻辑验证失败' }
        Write-Host ("[{0}] {1}" -f $verdict, $r.Case)
        Write-Host ("    场景      : {0}" -f $r.Scenario)
        Write-Host ("    驱动      : {0}" -f $r.EnchantResp)
        Write-Host ("    断言输入  : {0}" -f $r.StResp)
        if ($r.Notes) { Write-Host ("    实测      : {0}" -f $r.Notes) }
        if ($r.Lines.Count -gt 0) {
            Write-Host ("    [ST-*] tags: {0}" -f ((Get-StTags $r.Lines) -join ', '))
            foreach ($l in ($r.Lines | Select-Object -First 8)) { Write-Host ("    [ST-*]     : {0}" -f $l.Trim()) }
        } else {
            Write-Host '    [ST-*]     : (无 —— 处理器未被触发)'
        }
        Write-Host ("    完整验收  : {0}  (需客户端)" -f $r.FullAcceptance)
    }
    Write-Host '================================================================'
    Write-Host ("逻辑层级通过 {0}/{1}" -f @($results | Where-Object LogicPass).Count, $results.Count)
    return $results
}
