# Offline Yarn mappings query helper for the 1.12.2 -> 1.21.1 migration.
#
# NOTE: .ps1 files are blocked by ExecutionPolicy on this machine, so this file is
# meant to be loaded as an expression, not executed:
#
#   Invoke-Expression (Get-Content .\fabric-1.21.1\tools\yarn-query.ps1 -Raw)
#   Get-YarnClass -Class 'net/minecraft/entity/LivingEntity' -Filter '\tdamage$'
#   Get-YarnFind  -Pattern '^\tm\t.*\tdamage$'
#   Get-YarnClass -Class 'net/minecraft/item/ItemStack' -Version '1.21.4'
#
# It resolves exact Yarn names + descriptors from the locally cached tiny v2
# mappings, so API names can be verified without compiling anything.

$script:YarnLoomRoot = 'E:\gradle\caches\fabric-loom'

function Get-YarnMappingsPath {
    param([string]$Version = '1.21.1')
    $f = Get-ChildItem $script:YarnLoomRoot -Recurse -File -Filter 'mappings.tiny' -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -match [regex]::Escape("\$Version\") } |
        Select-Object -First 1
    if (-not $f) {
        $avail = (Get-ChildItem $script:YarnLoomRoot -Directory | Select-Object -ExpandProperty Name) -join ', '
        throw "No mappings.tiny for $Version. Cached: $avail"
    }
    return $f.FullName
}

# Dump a Yarn class declaration plus its members (fields/methods).
function Get-YarnClass {
    param(
        [Parameter(Mandatory)][string]$Class,
        [string]$Filter,
        [string]$Version = '1.21.1'
    )
    $path = Get-YarnMappingsPath -Version $Version
    $lines = Get-Content $path

    $start = $null
    for ($i = 0; $i -lt $lines.Count; $i++) {
        # A real class declaration is exactly: c<TAB>obf<TAB>intermediary<TAB>named
        # (no spaces). The file also contains javadoc entries of the form
        # "c<TAB>some prose with spaces" which must NOT terminate a block walk.
        if ($lines[$i] -match "^c\t\S+\t\S+\t\S+$" -and $lines[$i] -match "\t$([regex]::Escape($Class))$") {
            $start = $i; break
        }
    }
    if ($null -eq $start) {
        Write-Output "# NOT FOUND as class: $Class"
        $lines | Select-String -Pattern ([regex]::Escape($Class)) | Select-Object -First 8 |
            ForEach-Object { "  $($_.Line)" }
        return
    }

    Write-Output $lines[$start]
    for ($i = $start + 1; $i -lt $lines.Count; $i++) {
        $l = $lines[$i]
        # stop only at a real class declaration, not at a javadoc "c\t<prose>" line
        if ($l -match "^c\t\S+\t\S+\t\S+$") { break }
        if ($l -match "^\t[mpf]\t") {
            if (-not $Filter -or $l -match $Filter) { Write-Output $l }
        }
    }
}

# Ad-hoc regex scan over the whole mappings file.
function Get-YarnFind {
    param(
        [Parameter(Mandatory)][string]$Pattern,
        [string]$Version = '1.21.1',
        [int]$Limit = 60
    )
    $path = Get-YarnMappingsPath -Version $Version
    $hits = Select-String -Path $path -Pattern $Pattern
    Write-Output "# '$Pattern' -> $($hits.Count) hits"
    $hits | Select-Object -First $Limit | ForEach-Object { $_.Line }
}
