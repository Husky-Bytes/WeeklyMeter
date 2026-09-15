param([string]$SourceRoot=$PSScriptRoot,[string]$JavaBin='', [string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/widget-refresh-service-tests'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$manualSources=@(Get-ChildItem -LiteralPath (Join-Path $SourceRoot 'tests/widget-refresh-service') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
foreach($name in @('WidgetRefreshService','Scheduler','UsageJob','RefreshFeedback','RefreshFeedbackModel')){$manualSources+=Join-Path $SourceRoot ('app/src/main/java/dev/yerin/weeklymeter/'+$name+'.java')}
$manualCompiler=if($JavaBin){Join-Path $JavaBin 'javac.exe'}else{'javac'}
$manualRuntime=if($JavaBin){Join-Path $JavaBin 'java.exe'}else{'java'}
& $manualCompiler --release 8 -encoding UTF-8 -d $OutputDirectory $manualSources
if($LASTEXITCODE -ne 0){throw 'Widget refresh service compilation failed'}
& $manualRuntime -cp $OutputDirectory dev.yerin.weeklymeter.WidgetRefreshServiceTests
if($LASTEXITCODE -ne 0){throw 'Widget refresh service tests failed'}
