param([string]$SourceRoot = $PSScriptRoot, [string]$JavaBin = '', [string]$OutputDirectory = (Join-Path $PSScriptRoot 'build/lifecycle-tests'))
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$testSources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'tests/lifecycle') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$testSources += Join-Path $SourceRoot 'app/src/main/java/dev/yerin/weeklymeter/Scheduler.java'
$testSources += Join-Path $SourceRoot 'app/src/main/java/dev/yerin/weeklymeter/UsageJob.java'
$testSources += Join-Path $SourceRoot 'app/src/main/java/dev/yerin/weeklymeter/AutoRefreshDiagnostics.java'
$testSources += Join-Path $SourceRoot 'app/src/main/java/dev/yerin/weeklymeter/RefreshFeedback.java'
$testSources += Join-Path $SourceRoot 'app/src/main/java/dev/yerin/weeklymeter/RefreshFeedbackModel.java'
$compiler = if ($JavaBin) { Join-Path $JavaBin 'javac.exe' } else { 'javac' }
$runtime = if ($JavaBin) { Join-Path $JavaBin 'java.exe' } else { 'java' }
& $compiler --release 8 -encoding UTF-8 -d $OutputDirectory $testSources
if ($LASTEXITCODE -ne 0) { throw 'Lifecycle test compilation failed' }
& $runtime -cp $OutputDirectory dev.yerin.weeklymeter.LifecycleTests
if ($LASTEXITCODE -ne 0) { throw 'Lifecycle tests failed' }
