param([string]$SourceRoot=$PSScriptRoot,[string]$JavaBin='', [string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/api-deadline-tests'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$deadlineSources=@('app/src/main/java/dev/yerin/weeklymeter/Json.java','app/src/main/java/dev/yerin/weeklymeter/NetworkPolicy.java','app/src/main/java/dev/yerin/weeklymeter/Api.java','tests/ApiDeadlineTests.java') | ForEach-Object { Join-Path $SourceRoot $_ }
$deadlineCompiler=if($JavaBin){Join-Path $JavaBin 'javac.exe'}else{'javac'}
$deadlineRuntime=if($JavaBin){Join-Path $JavaBin 'java.exe'}else{'java'}
& $deadlineCompiler --release 8 -encoding UTF-8 -d $OutputDirectory $deadlineSources
if($LASTEXITCODE -ne 0){throw 'HTTP deadline test compilation failed'}
& $deadlineRuntime -cp $OutputDirectory dev.yerin.weeklymeter.ApiDeadlineTests
if($LASTEXITCODE -ne 0){throw 'HTTP deadline tests failed'}
