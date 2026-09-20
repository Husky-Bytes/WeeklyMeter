param([string]$SourceRoot=$PSScriptRoot,[string]$JavaBin='', [string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/floating-style-tests'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$floatingStyleSources=@(Get-ChildItem -LiteralPath (Join-Path $SourceRoot 'tests/floating-style') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
foreach($floatingClass in @('Json','WidgetStyle','WidgetAppearance','FloatingPreferences')){$floatingStyleSources+=Join-Path $SourceRoot ('app/src/main/java/dev/yerin/weeklymeter/'+$floatingClass+'.java')}
$floatingStyleCompiler=if($JavaBin){Join-Path $JavaBin 'javac.exe'}else{'javac'}
$floatingStyleRuntime=if($JavaBin){Join-Path $JavaBin 'java.exe'}else{'java'}
& $floatingStyleCompiler --release 8 -encoding UTF-8 -d $OutputDirectory $floatingStyleSources
if($LASTEXITCODE -ne 0){throw 'Floating style compilation failed'}
& $floatingStyleRuntime -cp $OutputDirectory dev.yerin.weeklymeter.FloatingStyleTests
if($LASTEXITCODE -ne 0){throw 'Floating style tests failed'}
