param([string]$SourceRoot=$PSScriptRoot,[string]$JavaBin='', [string]$OutputDirectory=(Join-Path $PSScriptRoot 'build/language-tests'))
$ErrorActionPreference='Stop'
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$languageSources=@(Get-ChildItem -LiteralPath (Join-Path $SourceRoot 'tests/language') -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
foreach($name in @('LanguagePolicy','AppLanguage','Texts','BootReceiver')){$languageSources+=Join-Path $SourceRoot "app/src/main/java/dev/yerin/weeklymeter/$name.java"}
$languageSources+=Join-Path $SourceRoot 'tests/LanguagePolicyTests.java'
$languageCompiler=if($JavaBin){Join-Path $JavaBin 'javac.exe'}else{'javac'}
$languageRuntime=if($JavaBin){Join-Path $JavaBin 'java.exe'}else{'java'}
& $languageCompiler --release 8 -encoding UTF-8 -d $OutputDirectory $languageSources
if($LASTEXITCODE -ne 0){throw 'Language compilation failed'}
foreach($test in @('LanguagePolicyTests','AppLanguageTests')){
    & $languageRuntime -cp $OutputDirectory "dev.yerin.weeklymeter.$test"
    if($LASTEXITCODE -ne 0){throw "$test failed"}
}
