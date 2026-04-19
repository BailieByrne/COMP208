# Copy Fin_Stuff assets to client directory
# Run this script from the COMP208 directory

$source = "Fin_Stuff\Cycle_two_testing\assets"
$destination = "client\Fin_Stuff\Cycle_two_testing\assets"

Write-Host "Copying Fin_Stuff assets..."
Write-Host "Source: $source"
Write-Host "Destination: $destination"

# Create destination if it doesn't exist
if (!(Test-Path $destination)) {
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    Write-Host "Created destination directory"
}

# Copy all files
Copy-Item -Path "$source\*" -Destination $destination -Recurse -Force

Write-Host "Copy complete!"
Get-ChildItem $destination | Select-Object Name | Write-Host
