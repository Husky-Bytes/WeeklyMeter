param([string]$SourceRoot=$PSScriptRoot,[string]$JavaBin='', [string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/floating-lifecycle-tests'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$floatingSources=@(Get-ChildItem -LiteralPath (Join-Path $SourceRoot 'tests/floating-lifecycle') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
foreach($name in @('FloatingWidgetService','FloatingGeometry','FloatingGesture','DisplayExpiry','AppSignals')){$floatingSources+=Join-Path $SourceRoot ('app/src/main/java/dev/yerin/weeklymeter/'+$name+'.java')}
$floatingCompiler=if($JavaBin){Join-Path $JavaBin 'javac.exe'}else{'javac'}
$floatingRuntime=if($JavaBin){Join-Path $JavaBin 'java.exe'}else{'java'}
& $floatingCompiler --release 8 -encoding UTF-8 -d $OutputDirectory $floatingSources
if($LASTEXITCODE -ne 0){throw 'Floating lifecycle compilation failed'}
& $floatingRuntime -cp $OutputDirectory dev.yerin.weeklymeter.FloatingLifecycleTests
if($LASTEXITCODE -ne 0){throw 'Floating lifecycle tests failed'}
