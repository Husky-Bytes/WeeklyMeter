param([string]$SourceRoot = $PSScriptRoot, [string]$JavaBin = '', [string]$OutputDirectory = (Join-Path $PSScriptRoot 'build/browser-auth-tests'), [switch]$UnitOnly)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$browserSources = @((Join-Path $SourceRoot 'app/src/main/java/dev/yerin/weeklymeter/BrowserAuth.java'), (Join-Path $SourceRoot 'tests/BrowserAuthTests.java'))
$browserCompiler = if ($JavaBin) { Join-Path $JavaBin 'javac.exe' } else { 'javac' }
$browserRuntime = if ($JavaBin) { Join-Path $JavaBin 'java.exe' } else { 'java' }
& $browserCompiler --release 8 -encoding UTF-8 -d $OutputDirectory $browserSources
if ($LASTEXITCODE -ne 0) { throw 'Browser auth test compilation failed' }
$browserArgs = @('-cp', $OutputDirectory, 'dev.yerin.weeklymeter.BrowserAuthTests')
if ($UnitOnly) { $browserArgs += '--unit-only' }
& $browserRuntime $browserArgs
if ($LASTEXITCODE -ne 0) { throw 'Browser auth tests failed' }
