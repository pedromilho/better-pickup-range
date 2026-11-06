param(
    [string]$ServerJar = 'D:\Games\Hytale\install\release\package\game\latest\Server\HytaleServer.jar',
    [string]$ModsDirectory = 'D:\Games\Hytale\UserData\Mods'
)

$ErrorActionPreference = 'Stop'
$server = (Resolve-Path -LiteralPath $ServerJar).Path
$build = Join-Path $PSScriptRoot 'build'
$resources = Join-Path $PSScriptRoot 'src/main/resources'
$sources = Get-ChildItem (Join-Path $PSScriptRoot 'src/main/java') -Recurse -Filter '*.java'
$classes = Join-Path $build ([guid]::NewGuid().ToString())

New-Item -ItemType Directory -Path $classes -Force | Out-Null
try {
    & javac --release 25 -encoding UTF-8 -cp $server -d $classes $sources.FullName
    if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

    $output = Join-Path $build 'BetterPickupRange.jar'
    & jar --create --file $output -C $classes . -C $resources .
    if ($LASTEXITCODE -ne 0) { throw 'jar failed' }
    $installed = Join-Path (Resolve-Path -LiteralPath $ModsDirectory).Path 'BetterPickupRange.jar'
    Copy-Item -LiteralPath $output -Destination $installed -Force
    Write-Output $installed
} finally {
    $resolved = (Resolve-Path -LiteralPath $classes).Path
    if ((Split-Path -Parent $resolved) -eq (Resolve-Path -LiteralPath $build).Path) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
