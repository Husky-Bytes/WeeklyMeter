param([string]$SourceRoot = $PSScriptRoot, [string]$JavaBin = '', [string]$OutputDirectory = (Join-Path $PSScriptRoot 'build/localization-tests'))
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$localeCompiler = if ($JavaBin) { Join-Path $JavaBin 'javac.exe' } else { 'javac' }
$localeRuntime = if ($JavaBin) { Join-Path $JavaBin 'java.exe' } else { 'java' }
$localeSources = @('app/src/main/java/dev/yerin/weeklymeter/Messages.java', 'tests/MessageLocalizationTests.java') | ForEach-Object { Join-Path $SourceRoot $_ }
& $localeCompiler --release 8 -encoding UTF-8 -d $OutputDirectory $localeSources
if ($LASTEXITCODE -ne 0) { throw 'Localization test compilation failed' }
& $localeRuntime -cp $OutputDirectory dev.yerin.weeklymeter.MessageLocalizationTests $SourceRoot
if ($LASTEXITCODE -ne 0) { throw 'Message localization tests failed' }
