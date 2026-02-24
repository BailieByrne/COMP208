# Profiler to run exe multiple times and track execution times
$exePath = "build\bin\Release\stock_sim.exe"
$logFolder = "Logs"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logFile = "$logFolder\profiler_log_$timestamp.txt"

#Change runs here 500 is a sufficient number
$numRuns = 500

# Create Logs folder if it doesn't exist
if (-not (Test-Path $logFolder)) {
    New-Item -ItemType Directory -Path $logFolder | Out-Null
}

# Clear log file
"Profiler Log - $(Get-Date)" | Out-File -FilePath $logFile
"" | Add-Content -Path $logFile

$times = @()

for ($i = 1; $i -le $numRuns; $i++) {
    # Measure execution time
    $measurement = Measure-Command {
        & $exePath
    }
    
    $timeMs = [int]$measurement.TotalMilliseconds
    $times += $timeMs
    
    "$timeMs" | Add-Content -Path $logFile
    Write-Host "Run $i - ${timeMs}ms"
}

# Calculate statistics
$total = ($times | Measure-Object -Sum).Sum
$avg = ($times | Measure-Object -Average).Average
$min = ($times | Measure-Object -Minimum).Minimum
$max = ($times | Measure-Object -Maximum).Maximum

# Display and log results
Write-Host ""
Write-Host "========== STATISTICS =========="
Write-Host "Total Runs: $($times.Count)"
Write-Host "Total Time: ${total}ms"
Write-Host "Average: $([int]$avg)ms"
Write-Host "Minimum: ${min}ms"
Write-Host "Maximum: ${max}ms"
Write-Host "================================"

"" | Add-Content -Path $logFile
"========== STATISTICS ==========" | Add-Content -Path $logFile
"Total Runs: $($times.Count)" | Add-Content -Path $logFile
"Total Time: ${total}ms" | Add-Content -Path $logFile
"Average: $([int]$avg)ms" | Add-Content -Path $logFile
"Minimum: ${min}ms" | Add-Content -Path $logFile
"Maximum: ${max}ms" | Add-Content -Path $logFile
"================================" | Add-Content -Path $logFile

Write-Host ""
Write-Host "Results saved to $logFile"
