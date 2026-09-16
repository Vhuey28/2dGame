$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "Building with Maven..."
mvn -q clean package

Write-Host "Starting game..."
java -Djava.library.path="natives\windows" -jar target\my2Dgame-1.0.0.jar
