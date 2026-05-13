# Compile script for Windows PowerShell
# Compiles all Java source files

Write-Host "Compiling Immutable Credentials Blockchain..." -ForegroundColor Cyan

# Create bin directory if it doesn't exist
if (-not (Test-Path "bin")) {
    New-Item -ItemType Directory -Path "bin" | Out-Null
}

# Compile all Java files
javac -d bin -sourcepath src/main/java (Get-ChildItem -Recurse -Path src/main/java -Filter "*.java" | ForEach-Object { $_.FullName })

if ($LASTEXITCODE -eq 0) {
    Write-Host "Compilation successful!" -ForegroundColor Green
} else {
    Write-Host "Compilation failed!" -ForegroundColor Red
    exit 1
}
