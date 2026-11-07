# Minimal Minecraft RCON client for the 1.21.1 port's server-side acceptance runs.
#
# .ps1 execution is blocked on this machine, so load it as an expression:
#   Invoke-Expression (Get-Content .\fabric-1.21.1\tools\rcon.ps1 -Raw)
#   $r = Connect-Rcon -Password 'devtest'
#   Invoke-RconCommand -Client $r -Command 'list'
#   Disconnect-Rcon -Client $r
#
# Protocol (Minecraft RCON, all integers little-endian):
#   [len:4][requestId:4][type:4][body:ASCII][0x00][0x00]     len = 8 + body.Length + 2
#   types: 3 = login, 2 = command, 0 = response
# Login failure is reported by a response with requestId = -1.

function Read-RconExact {
    param([System.IO.Stream]$Stream, [int]$Count)
    $buf = New-Object byte[] $Count
    $off = 0
    while ($off -lt $Count) {
        $n = $Stream.Read($buf, $off, $Count - $off)
        if ($n -le 0) { throw "RCON connection closed while reading" }
        $off += $n
    }
    return $buf
}

function Send-RconPacket {
    param([System.IO.Stream]$Stream, [int]$Id, [int]$Type, [string]$Body)
    $bodyBytes = [System.Text.Encoding]::ASCII.GetBytes($Body)
    $len = 8 + $bodyBytes.Length + 2
    $ms = New-Object System.IO.MemoryStream
    $bw = New-Object System.IO.BinaryWriter($ms)
    $bw.Write([int]$len)
    $bw.Write([int]$Id)
    $bw.Write([int]$Type)
    $bw.Write($bodyBytes)
    $bw.Write([byte]0)
    $bw.Write([byte]0)
    $bw.Flush()
    $bytes = $ms.ToArray()
    $bw.Dispose(); $ms.Dispose()
    $Stream.Write($bytes, 0, $bytes.Length)
    $Stream.Flush()
}

function Receive-RconPacket {
    param([System.IO.Stream]$Stream)
    $lenBuf = Read-RconExact -Stream $Stream -Count 4
    $len = [BitConverter]::ToInt32($lenBuf, 0)
    if ($len -lt 10) { return $null }
    $data = Read-RconExact -Stream $Stream -Count $len
    $id = [BitConverter]::ToInt32($data, 0)
    $type = [BitConverter]::ToInt32($data, 4)
    $bodyLen = $len - 10
    $body = if ($bodyLen -gt 0) { [System.Text.Encoding]::ASCII.GetString($data, 8, $bodyLen) } else { '' }
    return [pscustomobject]@{ Id = $id; Type = $type; Body = $body }
}

function Connect-Rcon {
    param(
        [string]$ServerHost = '127.0.0.1',
        [int]$Port = 25575,
        [string]$Password = 'devtest'
    )
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect($ServerHost, $Port)
    $stream = $tcp.GetStream()
    Send-RconPacket -Stream $stream -Id 1 -Type 3 -Body $Password
    $resp = Receive-RconPacket -Stream $stream
    if ($null -eq $resp -or $resp.Id -eq -1) {
        $tcp.Close()
        throw "RCON auth failed (wrong rcon.password?)"
    }
    return [pscustomobject]@{ Tcp = $tcp; Stream = $stream; Host = $ServerHost; Port = $Port }
}

# Sends one command and returns the concatenated response body.
# The server may answer with several type-0 packets; we drain until the socket goes quiet.
function Invoke-RconCommand {
    param(
        [Parameter(Mandatory)]$Client,
        [Parameter(Mandatory)][string]$Command
    )
    $stream = $Client.Stream
    Send-RconPacket -Stream $stream -Id 2 -Type 2 -Body $Command
    $out = ''
    # Give the server a moment before the first poll, then require several consecutive quiet
    # polls before concluding the answer is complete. Polling immediately returns an empty
    # string for commands whose response has not been flushed yet.
    Start-Sleep -Milliseconds 150
    $quiet = 0
    while ($true) {
        if (-not $stream.DataAvailable) {
            Start-Sleep -Milliseconds 60
            $quiet++
            if ($quiet -ge 3) { break }
            continue
        }
        $quiet = 0
        $p = Receive-RconPacket -Stream $stream
        if ($null -eq $p) { break }
        $out += $p.Body
    }
    return $out
}

function Disconnect-Rcon {
    param($Client)
    if ($null -eq $Client) { return }
    try { $Client.Stream.Close() } catch { }
    try { $Client.Tcp.Close() } catch { }
}
