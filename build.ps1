param(
    [string]$ServerJar = 'D:\Games\Hytale\install\release\package\game\latest\Server\HytaleServer.jar',
    [string]$ModsDirectory = 'D:\Games\Hytale\UserData\Mods'
)

$ErrorActionPreference = 'Stop'
$server = (Resolve-Path -LiteralPath $ServerJar).Path
$build = Join-Path $PSScriptRoot 'build'
$resources = Join-Path $PSScriptRoot 'src/main/resources'
$manifest = Get-Content -LiteralPath (Join-Path $resources 'manifest.json') -Raw | ConvertFrom-Json
$jarName = "BetterPickupRange-$($manifest.Version).jar"
$sources = Get-ChildItem (Join-Path $PSScriptRoot 'src/main/java') -Recurse -Filter '*.java'
$classes = Join-Path $build ([guid]::NewGuid().ToString())

New-Item -ItemType Directory -Path $classes -Force | Out-Null
try {
    & javac --release 25 -encoding UTF-8 -cp $server -d $classes $sources.FullName
    if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

    $output = Join-Path $build $jarName
    & jar --create --file $output -C $classes . -C $resources .
    if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
    $mods = (Resolve-Path -LiteralPath $ModsDirectory).Path
    $installed = Join-Path $mods $jarName
    Copy-Item -LiteralPath $output -Destination $installed -Force
    # Keep only one installed copy of the mod.
    Get-ChildItem -LiteralPath $mods -File -Filter 'BetterPickupRange*.jar' |
        Where-Object { $_.Name -ne $jarName -and $_.Name -match '^BetterPickupRange(?:-\d+\.\d+\.\d+)?\.jar$' } |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName }
    Write-Output $installed
} finally {
    $resolved = (Resolve-Path -LiteralPath $classes).Path
    if ((Split-Path -Parent $resolved) -eq (Resolve-Path -LiteralPath $build).Path) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
