# PowerShell build script for the Windows Chronicle Conquest installer.
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$DisplayVersion
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

$PROJECT_NAME = "ChronicleConquest"
$MAIN_CLASS = "my2Dgame.Main"

function Require-Command {
    param([string]$Name)

    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Required command '$Name' was not found on PATH."
    }
    return $command
}

$maven = Require-Command "mvn"
$jpackage = Get-Command "jpackage.exe" -ErrorAction SilentlyContinue
if (-not $jpackage) {
    $jpackage = Require-Command "jpackage"
}

$versionMatch = Select-String -Path "pom.xml" -Pattern "<version>(\d+\.\d+\.\d+)</version>" |
    Select-Object -First 1
if (-not $versionMatch) {
    throw "Could not read the Maven project version from pom.xml."
}

$MAVEN_VERSION = $versionMatch.Matches[0].Groups[1].Value
$JAR_NAME = "my2Dgame-$MAVEN_VERSION.jar"

if ([string]::IsNullOrWhiteSpace($DisplayVersion)) {
    $DisplayVersion = $MAVEN_VERSION
}
if ($DisplayVersion -notmatch '^\d+(\.\d+){0,2}$') {
    throw "Display version '$DisplayVersion' must be numeric, for example 1.0.0."
}

Write-Host ">>> Building JAR with Maven..."
& $maven.Source clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE."
}

$jarPath = Join-Path "target" $JAR_NAME
if (-not (Test-Path $jarPath)) {
    throw "Built JAR was not found at $jarPath."
}

Write-Host ">>> Building Windows installer..."

# Prepare an input directory containing the shaded JAR and Windows JInput DLLs.
$INPUT_DIR = "build/jpackage-input-win"
if (Test-Path $INPUT_DIR) {
    Remove-Item -LiteralPath $INPUT_DIR -Recurse -Force
}
New-Item -ItemType Directory -Path $INPUT_DIR -Force | Out-Null
Copy-Item -LiteralPath $jarPath -Destination $INPUT_DIR

$nativeSource = $null
if (Test-Path "natives/windows") {
    $nativeSource = "natives/windows"
} elseif (Test-Path "jinput-natives") {
    $nativeSource = "jinput-natives"
}

if ($nativeSource) {
    $nativeLibraries = @(Get-ChildItem -LiteralPath $nativeSource -Filter "*.dll" -File)
    if ($nativeLibraries.Count -gt 0) {
        $nativeDestination = Join-Path $INPUT_DIR "natives"
        New-Item -ItemType Directory -Path $nativeDestination -Force | Out-Null
        $nativeLibraries | Copy-Item -Destination $nativeDestination
        Write-Host "  Bundled $($nativeLibraries.Count) Windows native libraries"
    }
}

New-Item -ItemType Directory -Path "dist" -Force | Out-Null
$installerPath = "dist/$PROJECT_NAME-$DisplayVersion.exe"
if (Test-Path $installerPath) {
    Remove-Item -LiteralPath $installerPath -Force
}

$jpackageArgs = @(
    "--type", "exe",
    "--name", $PROJECT_NAME,
    "--app-version", $DisplayVersion,
    "--main-jar", $JAR_NAME,
    "--main-class", $MAIN_CLASS,
    "--input", $INPUT_DIR,
    "--dest", "dist/",
    "--java-options", '-Djava.library.path=$APPDIR\natives',
    "--win-shortcut",
    "--win-menu",
    "--win-dir-chooser",
    "--verbose"
)

& $jpackage.Source @jpackageArgs
if ($LASTEXITCODE -ne 0) {
    throw "jpackage failed with exit code $LASTEXITCODE."
}

if (-not (Test-Path $installerPath)) {
    throw "jpackage completed, but the expected installer was not found at $installerPath."
}

Write-Host "Windows installer (.exe) created at $installerPath"
