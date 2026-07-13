# PowerShell script to build and run my2Dgame on Windows

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $projectRoot

$jdkDir = "C:\Users\vhuey\.vscode\extensions\redhat.java-1.55.0-win32-x64\jre\21.0.11-win32-x86_64\bin"
$javac = Join-Path $jdkDir "javac.exe"
$java = Join-Path $jdkDir "java.exe"
$binDir = Join-Path $projectRoot "bin"
$srcDir = Join-Path $projectRoot "src"
$resDir = Join-Path $projectRoot "res"

if (-not (Test-Path $javac)) {
    # Fallback to system PATH if VS Code JDK isn't found
    $javac = "javac"
    $java = "java"
}

Write-Host "Cleaning bin directory..."
if (Test-Path $binDir) {
    try {
        Remove-Item -LiteralPath $binDir -Recurse -Force -ErrorAction Stop
    } catch {
        cmd /c "rmdir /s /q \"$binDir\""
    }
}
New-Item -ItemType Directory -Force -Path $binDir | Out-Null

Write-Host "Compiling Java source files..."
$javaFiles = Get-ChildItem -LiteralPath $srcDir -Recurse -Filter "*.java" | Select-Object -ExpandProperty FullName
& $javac -encoding UTF-8 -cp $srcDir -d $binDir $javaFiles

if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed!"
    exit $LASTEXITCODE
}

Write-Host "Copying resource files..."
if (Test-Path $resDir) {
    Copy-Item -Path (Join-Path $resDir "*") -Destination $binDir -Recurse -Force
}

Write-Host "Build complete! Starting game..."
& $java -cp $binDir my2Dgame.Main
