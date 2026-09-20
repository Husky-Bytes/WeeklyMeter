param([string]$SourceRoot=$PSScriptRoot,[string]$JavaBin='', [string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/preview-tests'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$previewSources=@((Join-Path $SourceRoot 'tests/PreviewTests.java'))
foreach($previewClass in @('PreviewGeometry','HomeWidgetPreviewSizes')){$previewSources+=Join-Path $SourceRoot ('app/src/main/java/dev/yerin/weeklymeter/'+$previewClass+'.java')}
$previewCompiler=if($JavaBin){Join-Path $JavaBin 'javac.exe'}else{'javac'}
$previewRuntime=if($JavaBin){Join-Path $JavaBin 'java.exe'}else{'java'}
& $previewCompiler --release 8 -encoding UTF-8 -d $OutputDirectory $previewSources
if($LASTEXITCODE -ne 0){throw 'Preview geometry compilation failed'}
& $previewRuntime -cp $OutputDirectory dev.yerin.weeklymeter.PreviewTests
if($LASTEXITCODE -ne 0){throw 'Preview geometry tests failed'}
