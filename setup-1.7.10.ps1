[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'

function Test-Java8Jdk([string]$Candidate) {
    if (-not $Candidate -or -not (Test-Path -LiteralPath "$Candidate\bin\javac.exe")) {
        return $false
    }
    # Windows PowerShell treats Java's version output on stderr as an error.
    $savedPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $version = & "$Candidate\bin\java.exe" -version 2>&1 | Out-String
        return $LASTEXITCODE -eq 0 -and $version -match 'version "1\.8\.'
    } finally {
        $ErrorActionPreference = $savedPreference
    }
}

if (-not (Test-Java8Jdk $JavaHome)) {
    $JavaHome = $null
    $searchRoots = @(
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Java",
        "$env:ProgramFiles\Amazon Corretto",
        "$env:ProgramFiles\Zulu",
        "$env:USERPROFILE\.jdks"
    )
    foreach ($searchRoot in $searchRoots) {
        if (-not (Test-Path -LiteralPath $searchRoot)) { continue }
        foreach ($candidate in Get-ChildItem -LiteralPath $searchRoot -Directory) {
            if (Test-Java8Jdk $candidate.FullName) {
                $JavaHome = $candidate.FullName
                break
            }
        }
        if ($JavaHome) { break }
    }
}

if (-not $JavaHome) {
    throw 'A Java 8 JDK is required. Install one and run .\setup-1.7.10.ps1 -JavaHome "C:\path\to\jdk8".'
}

$savedJavaHome = $env:JAVA_HOME
$savedPath = $env:PATH
Push-Location $PSScriptRoot
try {
    $env:JAVA_HOME = $JavaHome
    $env:PATH = "$JavaHome\bin;$env:PATH"
    Write-Host "Using Java 8 JDK: $JavaHome"

    & .\gradlew.bat --no-daemon setupDecompWorkspace idea
    if ($LASTEXITCODE -ne 0) { throw "Workspace setup failed (exit $LASTEXITCODE)." }

    & .\gradlew.bat --no-daemon clean build
    if ($LASTEXITCODE -ne 0) { throw "Build failed (exit $LASTEXITCODE)." }

    Write-Host ''
    Write-Host 'Ready. Open this folder in IntelliJ IDEA as a Gradle project.'
    Write-Host "Set Project SDK and Gradle JVM to Java 8 ($JavaHome), and use the Gradle wrapper."
    Write-Host 'Select the Build ReplayMod run configuration to create the release JAR in build\libs.'
} finally {
    $env:JAVA_HOME = $savedJavaHome
    $env:PATH = $savedPath
    Pop-Location
}
