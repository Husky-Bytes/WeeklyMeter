param(
    [string]$SourceRoot=$PSScriptRoot,
    [Parameter(Mandatory=$true)][string]$AppBuildDirectory,
    [Parameter(Mandatory=$true)][string]$SigningDirectory,
    [Parameter(Mandatory=$true)][string]$OutputDirectory,
    [string]$Sdk='D:\Android\Sdk',
    [string]$Jdk='C:\Program Files\Android\Android Studio\jbr',
    [string]$Serial='emulator-5580',
    [switch]$Run
)
$ErrorActionPreference='Stop'
if($Serial -ne 'emulator-5580'){throw 'This destructive synthetic-preference test is restricted to the isolated emulator-5580.'}
$runtimeSource=(Resolve-Path -LiteralPath (Join-Path $SourceRoot 'tests/android-runtime')).Path
$runtimeAppBuild=(Resolve-Path -LiteralPath $AppBuildDirectory).Path
$runtimeSigning=(Resolve-Path -LiteralPath $SigningDirectory).Path
$runtimeOutput=[IO.Path]::GetFullPath($OutputDirectory)
$runtimeTools=Join-Path $Sdk 'build-tools/35.0.0'
$runtimeAndroid=Join-Path $Sdk 'platforms/android-36/android.jar'
$runtimeAdb=Join-Path $Sdk 'platform-tools/adb.exe'
$env:JAVA_HOME=$Jdk
$env:PATH="$Jdk\bin;$env:PATH"
function Runtime-Native([string]$Exe,[string[]]$Arguments){& $Exe @Arguments;if($LASTEXITCODE -ne 0){throw "Runtime test command failed: $Exe"}}
if(Test-Path -LiteralPath $runtimeOutput){throw 'Use a new runtime test output directory.'}
New-Item -ItemType Directory -Path $runtimeOutput | Out-Null
foreach($runtimePart in @('classes','dex')){New-Item -ItemType Directory -Path (Join-Path $runtimeOutput $runtimePart) | Out-Null}
Copy-Item -LiteralPath "$runtimeAppBuild/classes.jar" -Destination "$runtimeOutput/app-classes.jar"
Runtime-Native "$Jdk/bin/javac.exe" @('-source','8','-target','8','-encoding','UTF-8','-bootclasspath',"$runtimeTools/core-lambda-stubs.jar;$runtimeAndroid",'-classpath',"$runtimeOutput/app-classes.jar",'-d',"$runtimeOutput/classes","$runtimeSource/RuntimeSmokeInstrumentation.java")
Runtime-Native "$Jdk/bin/jar.exe" @('cf',"$runtimeOutput/tests.jar",'-C',"$runtimeOutput/classes",'.')
Runtime-Native "$runtimeTools/d8.bat" @('--release','--min-api','26','--lib',$runtimeAndroid,'--classpath',"$runtimeOutput/app-classes.jar",'--output',"$runtimeOutput/dex","$runtimeOutput/tests.jar")
Runtime-Native "$runtimeTools/aapt2.exe" @('link','-I',$runtimeAndroid,'--manifest',"$runtimeSource/AndroidManifest.xml",'--min-sdk-version','26','--target-sdk-version','35','-o',"$runtimeOutput/base.apk")
Add-Type -AssemblyName System.IO.Compression.FileSystem
$runtimeZip=[IO.Compression.ZipFile]::Open("$runtimeOutput/base.apk",[IO.Compression.ZipArchiveMode]::Update)
try{foreach($runtimeDex in Get-ChildItem -LiteralPath "$runtimeOutput/dex" -Filter '*.dex'){[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($runtimeZip,$runtimeDex.FullName,$runtimeDex.Name,[IO.Compression.CompressionLevel]::Optimal)|Out-Null}}finally{$runtimeZip.Dispose()}
Runtime-Native "$runtimeTools/zipalign.exe" @('-f','4',"$runtimeOutput/base.apk","$runtimeOutput/aligned.apk")
Runtime-Native "$runtimeTools/apksigner.bat" @('sign','--ks',"$runtimeSigning/key.p12",'--ks-key-alias','weeklymeter','--ks-pass',"file:$runtimeSigning/password",'--out',"$runtimeOutput/WeeklyMeter-runtime-tests.apk","$runtimeOutput/aligned.apk")
Runtime-Native "$runtimeTools/apksigner.bat" @('verify','--verbose',"$runtimeOutput/WeeklyMeter-runtime-tests.apk")
if($Run){
    $runtimeQemu=(& $runtimeAdb -s $Serial shell getprop ro.kernel.qemu | Out-String).Trim()
    if($LASTEXITCODE -ne 0 -or $runtimeQemu -ne '1'){throw 'Isolated running Android emulator was not verified; no install performed.'}
    $runtimeAvd=@(& $runtimeAdb -s $Serial emu avd name)
    if($LASTEXITCODE -ne 0 -or $runtimeAvd.Count -lt 1 -or $runtimeAvd[0].Trim() -ne 'WeeklyMeter060'){throw 'Expected isolated WeeklyMeter060 AVD; no install performed.'}
    $runtimePackage=(& $runtimeAdb -s $Serial shell pm path dev.yerin.weeklymeter | Out-String).Trim()
    if($LASTEXITCODE -ne 0 -or -not $runtimePackage.StartsWith('package:')){throw 'Install the release APK on isolated emulator-5580 first.'}
    Runtime-Native $runtimeAdb @('-s',$Serial,'install','-r',"$runtimeOutput/WeeklyMeter-runtime-tests.apk")
    $runtimeResult=& $runtimeAdb -s $Serial shell am instrument -w dev.yerin.weeklymeter.runtime/dev.yerin.weeklymeter.RuntimeSmokeInstrumentation 2>&1
    $runtimeResult | Tee-Object -FilePath "$runtimeOutput/instrumentation-result.txt"
    if($LASTEXITCODE -ne 0 -or ($runtimeResult -join "`n") -notmatch 'RUNTIME_SMOKE_PASS checks='){throw 'Android runtime smoke tests failed; inspect the generated instrumentation result.'}
}
