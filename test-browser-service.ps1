param([string]$SourceRoot=$PSScriptRoot,[string]$JavaBin='', [string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/browser-service-tests'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$serviceSources=@(Get-ChildItem -LiteralPath (Join-Path $SourceRoot 'tests/browser-service') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
$serviceSources+=Join-Path $SourceRoot 'app/src/main/java/dev/yerin/weeklymeter/BrowserLoginService.java'
$serviceSources+=Join-Path $SourceRoot 'app/src/main/java/dev/yerin/weeklymeter/Messages.java'
$serviceCompiler=if($JavaBin){Join-Path $JavaBin 'javac.exe'}else{'javac'}
$serviceRuntime=if($JavaBin){Join-Path $JavaBin 'java.exe'}else{'java'}
& $serviceCompiler --release 8 -encoding UTF-8 -d $OutputDirectory $serviceSources
if($LASTEXITCODE -ne 0){throw 'Browser service compilation failed'}
& $serviceRuntime -cp $OutputDirectory dev.yerin.weeklymeter.BrowserServiceTests
if($LASTEXITCODE -ne 0){throw 'Browser service tests failed'}
