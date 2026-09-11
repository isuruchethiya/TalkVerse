# Helper script: Compile + Run DbConnectionTest
# Execute from PowerShell with:
#   powershell -NoProfile -ExecutionPolicy Bypass -File .\02_Run_DbConnectionTest.ps1

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$srcDir     = "$projectRoot\src\java"
$libDir     = "$projectRoot\web\WEB-INF\lib"
$buildDir   = "$projectRoot\build\classes"
$logDir     = "$env:TEMP"

Write-Host "============================================================"
Write-Host "TalkVerse -- Compile + Run DbConnectionTest (JDK 17 friendly)"
Write-Host "============================================================"
Write-Host "Project root: $projectRoot"

# -- prep dirs --
New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
Copy-Item "$srcDir\hibernate.cfg.xml" -Destination "$buildDir\hibernate.cfg.xml" -Force
Write-Host "PREP: build\classes dir ready, hibernate.cfg.xml copied."

# -- build classpath string using short (8.3) paths so Start-Process -ArgumentList never breaks on spaces --
$fs = New-Object -ComObject Scripting.FileSystemObject
$jarFiles = Get-ChildItem $libDir -Filter *.jar | Sort-Object Name
$cpItems = @()
$cpItems += $fs.GetFolder($buildDir).ShortPath
foreach ($j in $jarFiles) {
    $cpItems += $fs.GetFile($j.FullName).ShortPath
}
$cp = $cpItems -join ";"
$cpCount = $jarFiles.Count + 1
Write-Host "PREP: Classpath has $cpCount entries (using 8.3 short paths)."
Write-Host "CP: $cp"
Write-Host ""

# -- Step 1: compile --
Write-Host "=== Step 1/3: Compile entities + HibernateUtil + DbConnectionTest ==="
$entityFiles = Get-ChildItem "$srcDir\entity" -Filter *.java | ForEach-Object { $fs.GetFile($_.FullName).ShortPath }
$sourceFiles = $entityFiles + @(
    $fs.GetFile("$srcDir\model\HibernateUtil.java").ShortPath,
    $fs.GetFile("$srcDir\model\DbConnectionTest.java").ShortPath
)
Write-Host "Source files count: $($sourceFiles.Count)"
Write-Host "  (using 8.3 short paths for source files too, to avoid space-in-path issues)"

$compileArgs = @(
    "-encoding", "UTF-8",
    "-source", "11",
    "-target", "11",
    "-cp", $cp,
    "-d", $fs.GetFolder($buildDir).ShortPath
) + $sourceFiles

$cOut = "$logDir\tv_javac_stdout.log"
$cErr = "$logDir\tv_javac_stderr.log"
$cpInfo = Start-Process -FilePath javac -ArgumentList $compileArgs `
    -RedirectStandardOutput $cOut -RedirectStandardError $cErr `
    -Wait -PassThru -NoNewWindow
Write-Host "javac exit code: $($cpInfo.ExitCode)"
if (Test-Path $cOut) {
    $co = Get-Content $cOut
    if ($co) { $co | ForEach-Object { Write-Host "  out: $_" } }
}
if (Test-Path $cErr) {
    $ce = Get-Content $cErr
    if ($ce) { $ce | ForEach-Object { Write-Host "  err: $_" } }
}
Write-Host ""

Write-Host "=== Step 2/3: Verify compiled classes ==="
$classes = Get-ChildItem $buildDir -Recurse -Filter *.class
Write-Host "Class file count: $($classes.Count)"
$classes | ForEach-Object {
    $rel = $_.FullName.Substring($buildDir.Length + 1)
    Write-Host "  - $rel"
}
Write-Host ""

if ($cpInfo.ExitCode -ne 0) {
    Write-Host "STOP -- compile exited non-zero. DbConnectionTest not run."
    exit 2
}

# -- Step 3: run --
Write-Host "=== Step 3/3: Run DbConnectionTest ==="
$runArgs = @(
    "-Dfile.encoding=UTF-8",
    "-cp", $cp,
    "model.DbConnectionTest"
)
$rOut = "$logDir\tv_run_stdout.log"
$rErr = "$logDir\tv_run_stderr.log"
$rp = Start-Process -FilePath java -ArgumentList $runArgs `
    -RedirectStandardOutput $rOut -RedirectStandardError $rErr `
    -Wait -PassThru -NoNewWindow
Write-Host "java exit code: $($rp.ExitCode)"
Write-Host ""
if (Test-Path $rOut) { Get-Content $rOut }
if (Test-Path $rErr) {
    $re = Get-Content $rErr
    if ($re) {
        Write-Host ""
        Write-Host "--- STDERR ---"
        $re | ForEach-Object { Write-Host "  [stderr] $_" }
    }
}

Write-Host ""
if ($rp.ExitCode -eq 0) {
    Write-Host "RESULT: PASSED OK"
} else {
    Write-Host "RESULT: FAILED (exit $($rp.ExitCode))"
}
exit $rp.ExitCode
