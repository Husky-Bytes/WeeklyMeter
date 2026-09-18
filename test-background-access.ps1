param([string]$SourceRoot=$PSScriptRoot,[string]$JavaBin='', [string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/background-access-tests'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$backgroundSources=@(Get-ChildItem -LiteralPath (Join-Path $SourceRoot 'tests/background-access') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
$backgroundSources+=Join-Path $SourceRoot 'app/src/main/java/dev/yerin/weeklymeter/BackgroundAccess.java'
$backgroundCompiler=if($JavaBin){Join-Path $JavaBin 'javac.exe'}else{'javac'}
$backgroundRuntime=if($JavaBin){Join-Path $JavaBin 'java.exe'}else{'java'}
& $backgroundCompiler --release 8 -encoding UTF-8 -d $OutputDirectory $backgroundSources
if($LASTEXITCODE -ne 0){throw 'Background access compilation failed'}
& $backgroundRuntime -cp $OutputDirectory dev.yerin.weeklymeter.BackgroundAccessTests
if($LASTEXITCODE -ne 0){throw 'Background access tests failed'}
