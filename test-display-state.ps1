param([string]$SourceRoot=$PSScriptRoot,[string]$JavaBin='', [string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/display-state-tests'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$displaySource=Join-Path $SourceRoot 'app/src/main/java/dev/yerin/weeklymeter'
$displayCompiler=if($JavaBin){Join-Path $JavaBin 'javac.exe'}else{'javac'}
$displayRuntime=if($JavaBin){Join-Path $JavaBin 'java.exe'}else{'java'}
& $displayCompiler --release 8 -encoding UTF-8 -d $OutputDirectory "$displaySource/DisplayExpiry.java" "$displaySource/AppSignals.java" "$SourceRoot/tests/DisplayStateTests.java"
if($LASTEXITCODE -ne 0){throw 'Display state compilation failed'}
& $displayRuntime -cp $OutputDirectory dev.yerin.weeklymeter.DisplayStateTests $displaySource
if($LASTEXITCODE -ne 0){throw 'Display state tests failed'}
