param(
    [Parameter(Mandatory=$true)][string]$Project,
    [Parameter(Mandatory=$true)][string]$BuildDirectory,
    [Parameter(Mandatory=$true)][string]$SigningDirectory,
    [string]$Sdk = 'D:\Android\Sdk',
    [string]$Jdk = 'C:\Program Files\Android\Android Studio\jbr'
)
$ErrorActionPreference = 'Stop'
$projectPath = (Resolve-Path -LiteralPath $Project).Path
$buildPath = [IO.Path]::GetFullPath($BuildDirectory)
$signPath = [IO.Path]::GetFullPath($SigningDirectory)
$toolsPath = Join-Path $Sdk 'build-tools\35.0.0'
$androidJar = Join-Path $Sdk 'platforms\android-36\android.jar'
$env:JAVA_HOME = $Jdk
$env:PATH = "$Jdk\bin;$env:PATH"
function Run-Native([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments 2>&1 | ForEach-Object { "$_" }
    if ($LASTEXITCODE -ne 0) { throw "Build tool failed ($LASTEXITCODE): $Exe" }
}
if (Test-Path -LiteralPath $buildPath) { throw 'Use a new build directory for each run.' }
New-Item -ItemType Directory -Path $buildPath -Force | Out-Null
foreach ($part in @('gen','classes','dex','test-classes','auth-test-classes','dist')) {
    New-Item -ItemType Directory -Path (Join-Path $buildPath $part) | Out-Null
}
Start-Transcript -LiteralPath (Join-Path $buildPath 'build-log.txt') | Out-Null
try {
    Run-Native "$Jdk\bin\java.exe" @('-version')
    Run-Native "$toolsPath\aapt2.exe" @('version')
    $src = Join-Path $projectPath 'app\src\main\java\dev\yerin\weeklymeter'
    $testClasses = Join-Path $buildPath 'test-classes'
    Run-Native "$Jdk\bin\javac.exe" @('--release','8','-encoding','UTF-8','-d',$testClasses,"$src\Json.java","$src\Usage.java","$src\NetworkPolicy.java","$src\WidgetStyle.java","$src\RefreshFeedbackModel.java","$projectPath\tests\CoreTests.java","$projectPath\tests\UsageRegressionTests.java","$projectPath\tests\WidgetStyleTests.java","$projectPath\tests\RefreshFeedbackTests.java")
    Run-Native "$Jdk\bin\java.exe" @('-cp',$testClasses,'dev.yerin.weeklymeter.CoreTests')
    Run-Native "$Jdk\bin\java.exe" @('-cp',$testClasses,'dev.yerin.weeklymeter.UsageRegressionTests')
    Run-Native "$Jdk\bin\java.exe" @('-cp',$testClasses,'dev.yerin.weeklymeter.WidgetStyleTests')
    Run-Native "$Jdk\bin\java.exe" @('-cp',$testClasses,'dev.yerin.weeklymeter.RefreshFeedbackTests')
    Run-Native "$Jdk\bin\javac.exe" @('--release','8','-encoding','UTF-8','-d',$testClasses,"$projectPath\tests\FontAssetTests.java")
    Run-Native "$Jdk\bin\java.exe" @('-Djava.awt.headless=true','-cp',$testClasses,'FontAssetTests',"$projectPath\app\src\main\assets\fonts")
    $authStubs = @(Get-ChildItem -LiteralPath "$projectPath\tests\auth-stubs" -Recurse -Filter '*.java' | ForEach-Object FullName)
    Run-Native "$Jdk\bin\javac.exe" (@('--release','8','-encoding','UTF-8','-d',"$buildPath\auth-test-classes","$src\Json.java","$src\Usage.java","$src\NetworkPolicy.java","$src\Api.java","$src\Repo.java","$src\BrowserAuth.java","$projectPath\tests\AuthRegressionTests.java") + $authStubs)
    Run-Native "$Jdk\bin\java.exe" @('-cp',"$buildPath\auth-test-classes",'dev.yerin.weeklymeter.AuthRegressionTests')
    & "$projectPath\test-lifecycle.ps1" -SourceRoot $projectPath -JavaBin "$Jdk\bin" -OutputDirectory "$buildPath\lifecycle-test-classes"
    & "$projectPath\test-browser-auth.ps1" -SourceRoot $projectPath -JavaBin "$Jdk\bin" -OutputDirectory "$buildPath\browser-test-classes"
    & "$projectPath\test-browser-service.ps1" -SourceRoot $projectPath -JavaBin "$Jdk\bin" -OutputDirectory "$buildPath\browser-service-test-classes"
    & "$projectPath\test-widget-refresh-service.ps1" -SourceRoot $projectPath -JavaBin "$Jdk\bin" -OutputDirectory "$buildPath\widget-refresh-test-classes"
    & "$projectPath\test-localization.ps1" -SourceRoot $projectPath -JavaBin "$Jdk\bin" -OutputDirectory "$buildPath\localization-test-classes"
    & "$projectPath\test-language.ps1" -SourceRoot $projectPath -JavaBin "$Jdk\bin" -OutputDirectory "$buildPath\language-test-classes"
    & "$projectPath\test-widget-publish.ps1" -SourceRoot $projectPath -JavaBin "$Jdk\bin" -OutputDirectory "$buildPath\widget-publish-test-classes"
    & "$projectPath\test-background-access.ps1" -SourceRoot $projectPath -JavaBin "$Jdk\bin" -OutputDirectory "$buildPath\background-access-test-classes"
    & "$projectPath\test-floating-style.ps1" -SourceRoot $projectPath -JavaBin "$Jdk\bin" -OutputDirectory "$buildPath\floating-style-test-classes"
    & "$projectPath\test-floating-service.ps1" -SourceRoot $projectPath -JavaBin "$Jdk\bin" -OutputDirectory "$buildPath\floating-service-test-classes"
    Run-Native 'python' @("$projectPath\check-project.py")
    Run-Native 'python' @("$projectPath\tests\check-adaptive-icon.py")
    Run-Native "$toolsPath\aapt2.exe" @('compile','--dir',"$projectPath\app\src\main\res",'-o',"$buildPath\resources.zip")
    # Windows aapt2 -A can emit backslashes in nested asset ZIP entry names.
    # Insert assets ourselves below using canonical Android '/' paths, before signing.
    Run-Native "$toolsPath\aapt2.exe" @('link','-I',$androidJar,'--manifest',"$projectPath\app\src\main\AndroidManifest.xml",'--java',"$buildPath\gen",'--min-sdk-version','26','--target-sdk-version','35','--version-code','10','--version-name','0.6.0','-o',"$buildPath\base.apk","$buildPath\resources.zip")
    $sources = @(Get-ChildItem -LiteralPath "$projectPath\app\src\main\java","$buildPath\gen" -Recurse -Filter '*.java' | ForEach-Object FullName)
    Run-Native "$Jdk\bin\javac.exe" (@('-source','8','-target','8','-encoding','UTF-8','-bootclasspath',"$toolsPath\core-lambda-stubs.jar;$androidJar",'-d',"$buildPath\classes") + $sources)
    Run-Native "$Jdk\bin\jar.exe" @('cf',"$buildPath\classes.jar",'-C',"$buildPath\classes",'.')
    Run-Native "$toolsPath\d8.bat" @('--release','--min-api','26','--lib',$androidJar,'--output',"$buildPath\dex","$buildPath\classes.jar")
    Copy-Item -LiteralPath "$buildPath\base.apk" -Destination "$buildPath\unaligned.apk"
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $apkZip = [IO.Compression.ZipFile]::Open("$buildPath\unaligned.apk", [IO.Compression.ZipArchiveMode]::Update)
    try {
        foreach ($dex in Get-ChildItem -LiteralPath "$buildPath\dex" -Filter '*.dex') {
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apkZip,$dex.FullName,$dex.Name,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
        $assetsRoot = (Resolve-Path -LiteralPath "$projectPath\app\src\main\assets").Path
        foreach ($asset in Get-ChildItem -LiteralPath $assetsRoot -Recurse -File) {
            if ($asset.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Asset links are not allowed.' }
            $relative = $asset.FullName.Substring($assetsRoot.Length + 1).Replace('\','/')
            $entryName = 'assets/' + $relative
            if ($relative.StartsWith('/') -or $relative.Split('/') -contains '..' -or $apkZip.GetEntry($entryName)) { throw 'Invalid or duplicate asset path.' }
            [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apkZip,$asset.FullName,$entryName,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
        }
    } finally { $apkZip.Dispose() }
    Run-Native "$Jdk\bin\javac.exe" @('--release','8','-encoding','UTF-8','-d',$testClasses,"$projectPath\tests\ApkAssetTests.java")
    Run-Native "$Jdk\bin\java.exe" @('-cp',$testClasses,'ApkAssetTests',"$buildPath\unaligned.apk","$projectPath\app\src\main\assets")
    Run-Native "$toolsPath\zipalign.exe" @('-f','4',"$buildPath\unaligned.apk","$buildPath\aligned.apk")
    New-Item -ItemType Directory -Path $signPath -Force | Out-Null
    if (-not (Test-Path -LiteralPath "$signPath\key.p12")) {
        if (Test-Path -LiteralPath "$signPath\password") { throw 'Orphan signing password exists; choose a fresh signing directory.' }
        $keyBytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rng.GetBytes($keyBytes) } finally { $rng.Dispose() }
        [IO.File]::WriteAllText("$signPath\password",[Convert]::ToBase64String($keyBytes),[Text.UTF8Encoding]::new($false))
        Run-Native "$Jdk\bin\keytool.exe" @('-genkeypair','-keystore',"$signPath\key.p12",'-storetype','PKCS12','-storepass:file',"$signPath\password",'-alias','weeklymeter','-keyalg','RSA','-keysize','3072','-validity','3650','-dname','CN=WeeklyMeter Personal Build')
    }
    if (-not (Test-Path -LiteralPath "$signPath\password")) { throw 'Signing password file missing; existing key preserved.' }
    Run-Native "$toolsPath\apksigner.bat" @('sign','--ks',"$signPath\key.p12",'--ks-key-alias','weeklymeter','--ks-pass',"file:$signPath\password",'--out',"$buildPath\dist\WeeklyMeter.apk","$buildPath\aligned.apk")
    Run-Native "$Jdk\bin\java.exe" @('-cp',$testClasses,'ApkAssetTests',"$buildPath\dist\WeeklyMeter.apk","$projectPath\app\src\main\assets")
    & "$toolsPath\apksigner.bat" verify --verbose --print-certs "$buildPath\dist\WeeklyMeter.apk" | Tee-Object -FilePath "$buildPath\dist\APK-VERIFICATION.txt"
    if ($LASTEXITCODE -ne 0) { throw 'Signature verification failed' }
    & "$toolsPath\aapt2.exe" dump badging "$buildPath\dist\WeeklyMeter.apk" | Tee-Object -FilePath "$buildPath\dist\APK-METADATA.txt"
    if ($LASTEXITCODE -ne 0) { throw 'APK metadata validation failed' }
    Get-FileHash -LiteralPath "$buildPath\dist\WeeklyMeter.apk" -Algorithm SHA256 | Format-List
} finally { Stop-Transcript | Out-Null }
