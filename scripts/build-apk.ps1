param([string]$AndroidSdk = $env:ANDROID_HOME, [string]$Jdk = $env:JAVA_HOME)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Set-Location $projectRoot
if (-not $AndroidSdk -or -not $Jdk) {
    $player = Get-ChildItem 'C:\Program Files\Unity\Hub\Editor\*\Editor\Data\PlaybackEngines\AndroidPlayer' -Directory -ErrorAction SilentlyContinue | Select-Object -Last 1
    if (-not $AndroidSdk -and $player) { $AndroidSdk = Join-Path $player.FullName 'SDK' }
    if (-not $Jdk -and $player) { $Jdk = Join-Path $player.FullName 'OpenJDK' }
}
if (-not $AndroidSdk -or -not $Jdk) { throw 'Provide -AndroidSdk and -Jdk, or set ANDROID_HOME and JAVA_HOME.' }
$platform = Join-Path $AndroidSdk 'platforms\android-36\android.jar'
$buildTools = Join-Path $AndroidSdk 'build-tools\36.0.0'
foreach ($required in @($platform, "$buildTools\aapt2.exe", "$Jdk\bin\javac.exe")) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Missing build tool: $required" }
}
function Invoke-Checked([string]$Exe, [string[]]$Arguments) {
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed with exit code $LASTEXITCODE" }
}
Invoke-Checked 'node' @('scripts/build-web.cjs')
# Unique build directories avoid stale classes and require no recursive deletion.
$work = Join-Path $projectRoot ('build\apk-' + [Guid]::NewGuid().ToString('N'))
$classes = Join-Path $work 'classes'
$dex = Join-Path $work 'dex'
New-Item -ItemType Directory -Force $classes,$dex,(Join-Path $projectRoot 'dist') | Out-Null
$sources = @(Get-ChildItem android\src -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' })
$sourceList = Join-Path $work 'sources.txt'
[IO.File]::WriteAllLines($sourceList, $sources)
Invoke-Checked "$Jdk\bin\javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',$platform,'-classpath',"$buildTools\core-lambda-stubs.jar",'-d',$classes,"@$sourceList")
$jar = Join-Path $work 'classes.jar'
Invoke-Checked "$Jdk\bin\jar.exe" @('cf',$jar,'-C',$classes,'.')
Invoke-Checked "$Jdk\bin\java.exe" @('-cp',"$buildTools\lib\d8.jar",'com.android.tools.r8.D8','--lib',$platform,'--min-api','26','--output',$dex,$jar)
$unsigned = Join-Path $work 'unsigned.apk'
Invoke-Checked "$buildTools\aapt2.exe" @('link','-I',$platform,'--manifest',"$projectRoot\android\AndroidManifest.xml",'-o',$unsigned)
# This Windows aapt2 emits backslashes for nested -A paths. jar normalises ZIP entry separators.
Invoke-Checked "$Jdk\bin\jar.exe" @('uf',$unsigned,'-C',"$projectRoot\android",'assets')
Invoke-Checked "$Jdk\bin\jar.exe" @('uf',$unsigned,'-C',$dex,'classes.dex')
$entries = & "$Jdk\bin\jar.exe" tf $unsigned
if ($entries -notcontains 'assets/www/index.html' -or @($entries | Where-Object { $_.Contains('\') }).Count -gt 0) {
    throw 'APK asset paths are invalid.'
}
$aligned = Join-Path $work 'aligned.apk'
Invoke-Checked "$buildTools\zipalign.exe" @('-f','4',$unsigned,$aligned)
$key = Join-Path $projectRoot 'build\debug.keystore'
if (-not (Test-Path -LiteralPath $key)) {
    Invoke-Checked "$Jdk\bin\keytool.exe" @('-genkeypair','-keystore',$key,'-storepass','android','-alias','androiddebugkey','-keypass','android','-dname','CN=Android Debug,O=Android,C=US','-keyalg','RSA','-keysize','2048','-validity','10000')
}
$apk = Join-Path $projectRoot 'dist\SwayCommand-debug.apk'
Invoke-Checked "$Jdk\bin\java.exe" @('-jar',"$buildTools\lib\apksigner.jar",'sign','--ks',$key,'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,$aligned)
Invoke-Checked "$Jdk\bin\java.exe" @('-jar',"$buildTools\lib\apksigner.jar",'verify','--verbose',$apk)
Write-Host "Built $apk"
