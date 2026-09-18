param([string]$SourceRoot=$PSScriptRoot,[string]$JavaBin='', [string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/widget-publish-tests'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$publishSources=@(Get-ChildItem -LiteralPath (Join-Path $SourceRoot 'tests/widget-publish') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
foreach($name in @('WeeklyWidget','Store','Json','Usage','WidgetStyle','Messages')){$publishSources+=Join-Path $SourceRoot ('app/src/main/java/dev/yerin/weeklymeter/'+$name+'.java')}
$publishCompiler=if($JavaBin){Join-Path $JavaBin 'javac.exe'}else{'javac'}
$publishRuntime=if($JavaBin){Join-Path $JavaBin 'java.exe'}else{'java'}
& $publishCompiler --release 8 -encoding UTF-8 -d $OutputDirectory $publishSources
if($LASTEXITCODE -ne 0){throw 'Widget publication compilation failed'}
& $publishRuntime -cp $OutputDirectory dev.yerin.weeklymeter.WidgetPublishTests
if($LASTEXITCODE -ne 0){throw 'Widget publication tests failed'}
