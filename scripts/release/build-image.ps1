param(
    [Parameter(Mandatory = $true)][string]$Version,
    [Parameter(Mandatory = $true)][string]$WebDist
)

$ErrorActionPreference = 'Stop'
$git = Get-Command git -ErrorAction Stop
$gitRoot = Split-Path (Split-Path $git.Source -Parent) -Parent
$bashPath = Join-Path $gitRoot 'bin\bash.exe'
$cygpath = Join-Path $gitRoot 'usr\bin\cygpath.exe'
if (-not (Test-Path $bashPath) -or -not (Test-Path $cygpath)) {
    throw 'Git for Windows bash.exe was not found.'
}
$script = Join-Path $PSScriptRoot 'build-image.sh'
$unixScript = & $cygpath -u $script
$resolvedWebDist = (Resolve-Path $WebDist).Path
$unixWebDist = & $cygpath -u $resolvedWebDist
& $bashPath $unixScript $Version $unixWebDist
if ($LASTEXITCODE -ne 0) {
    throw "Yakmogo image build failed with exit code $LASTEXITCODE"
}
