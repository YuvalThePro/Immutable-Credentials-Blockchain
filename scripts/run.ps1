# Run script for Windows PowerShell
# Runs the main application

param(
    [string]$NodeType = "validator",
    [int]$Port = 8080
)

Write-Host "Starting Immutable Credentials Blockchain Node..." -ForegroundColor Cyan
Write-Host "Node Type: $NodeType" -ForegroundColor Yellow
Write-Host "Port: $Port" -ForegroundColor Yellow

# Run the application
java -cp bin com.immutable.credentials.Main

if ($LASTEXITCODE -ne 0) {
    Write-Host "Application exited with error!" -ForegroundColor Red
    exit 1
}
