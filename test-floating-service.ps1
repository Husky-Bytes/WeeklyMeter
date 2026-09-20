param([string]$SourceRoot=$PSScriptRoot,[string]$JavaBin='', [string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/floating-service-tests'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$floatingSource=Join-Path $SourceRoot 'app/src/main/java/dev/yerin/weeklymeter'
$floatingCompiler=if($JavaBin){Join-Path $JavaBin 'javac.exe'}else{'javac'}
$floatingRuntime=if($JavaBin){Join-Path $JavaBin 'java.exe'}else{'java'}
& $floatingCompiler --release 8 -encoding UTF-8 -d $OutputDirectory "$floatingSource/FloatingGeometry.java" "$floatingSource/FloatingGesture.java" (Join-Path $SourceRoot 'tests/FloatingServiceTests.java')
if($LASTEXITCODE -ne 0){throw 'Floating overlay tests compilation failed'}
& $floatingRuntime -cp $OutputDirectory dev.yerin.weeklymeter.FloatingServiceTests "$floatingSource/FloatingWidgetService.java"
if($LASTEXITCODE -ne 0){throw 'Floating overlay tests failed'}
