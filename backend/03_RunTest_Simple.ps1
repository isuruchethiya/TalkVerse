$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$srcDir     = "$projectRoot\src\java"
$libDir     = "$projectRoot\web\WEB-INF\lib"
$buildDir   = "$projectRoot\build\classes"
$logDir     = "$env:TEMP"

Write-Host "============================================================"
Write-Host "TalkVerse -- DB Connection Tester (Call operator & variant)"
Write-Host "============================================================"
Write-Host "Project: $projectRoot"
Write-Host ""

New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
Copy-Item "$srcDir\hibernate.cfg.xml" "$buildDir\hibernate.cfg.xml" -Force
Write-Host "Prep OK. build\classes ready."

# Build classpath (simple string, no fancy tricks)
$jarList = Get-ChildItem $libDir -Filter *.jar | ForEach-Object { $_.FullName }
$cp = "$buildDir;" + ($jarList -join ";")

Write-Host "Classpath entries: $($jarList.Count + 1)"
Write-Host ""

Write-Host "=== Step 1/3: Compile sources ==="
$entityFiles = @((Get-ChildItem "$srcDir\entity" -Filter *.java).FullName)
$modelFiles  = @("$srcDir\model\HibernateUtil.java", "$srcDir\model\DbConnectionTest.java")
$allSrc = @()
foreach ($f in $entityFiles) { $allSrc += $f }
foreach ($f in $modelFiles)  { $allSrc += $f }
Write-Host "Source file count: $($allSrc.Count)"
Write-Host ""

& javac -encoding UTF-8 -source 11 -target 11 -cp $cp -d $buildDir $allSrc
$javacExit = $LASTEXITCODE
Write-Host ""
Write-Host "javac exit code: $javacExit"
Write-Host ""

Write-Host "=== Step 2/3: Class file count ==="
$cls = Get-ChildItem $buildDir -Recurse -Filter *.class
Write-Host "Classes found: $($cls.Count)"
foreach ($c in $cls) {
    $r = $c.FullName.Substring($buildDir.Length + 1)
    Write-Host "  - $r"
}
Write-Host ""

if ($javacExit -ne 0) {
    Write-Host "Compile failed -- test NOT run."
    exit 2
}

Write-Host "=== Step 3/3: Run DbConnectionTest ==="
Write-Host ""
$jvmArgs = @(
    '--add-opens',
    'java.base/java.lang=ALL-UNNAMED',
    '--add-opens',
    'java.base/java.lang.reflect=ALL-UNNAMED',
    '--add-opens',
    'java.base/java.util=ALL-UNNAMED',
    '-Dfile.encoding=UTF-8',
    '-cp',
    $cp,
    'model.DbConnectionTest'
)
& java @jvmArgs
$runExit = $LASTEXITCODE
Write-Host ""
Write-Host "java exit code: $runExit"
Write-Host ""
if ($runExit -eq 0) { Write-Host "RESULT: OK" } else { Write-Host "RESULT: FAIL" }
exit $runExit
