#PowerShell script for Windows)
$PROJECT_NAME = "ChronicleConquest"
$MAIN_CLASS = "my2Dgame.Main"
$MAVEN_VERSION = (Select-String -Path "pom.xml" -Pattern "<version>(\d+\.\d+\.\d+)</version>" | ForEach-Object {$_.Matches.Groups[1].Value})[0]
$JAR_NAME = "my2Dgame-$MAVEN_VERSION.jar"
$DISPLAY_VERSION = $args[1] ? $args[1] : $MAVEN_VERSION

Write-Host ">>> Building JAR with Maven..."
mvn clean package -DskipTests

Write-Host ">>> Building Windows install

# Prepare input directory with JAR + nat
$INPUT_DIR = "build/jpackage-input-win"
Remove-Item $INPUT_DIR -Recurse -ErrorAc
New-Item $INPUT_DIR -ItemType Directory
Copy-Item "target/$JAR_NAME" "$INPUT_DIR

if (Test-Path "natives/windows") {
    New-Item "$INPUT_DIR/natives" -ItemType Directory
    Copy-Item "natives/windows/*" "$INPU
    Write-Host "  Bundled Windows native libraries"
}

jpackage.exe `
    --type exe `
    --name $PROJECT_NAME `
    --app-version $DISPLAY_VERSION `
    --main-jar $JAR_NAME `
    --main-class $MAIN_CLASS `
    --input $INPUT_DIR `
    --dest "dist/" `
    --java-options "-Djava.library.path=
    --verbose

Write-Host "Windows installer (.exe) created in dist/"