# Installs VarunKulkarni.cer into CurrentUser\TrustedPeople for instant MSIX double-click installation
$cerPath = Join-Path $PSScriptRoot "VarunKulkarni.cer"
if (Test-Path $cerPath) {
    Import-Certificate -FilePath $cerPath -CertStoreLocation "Cert:\CurrentUser\TrustedPeople"
    Write-Host "[SUCCESS] Varun Kulkarni Code Signing Certificate installed to CurrentUser\TrustedPeople." -ForegroundColor Green
    Write-Host "You can now double-click VarunKulkarni.HorizonMobileController.msix to install directly via Windows App Installer!" -ForegroundColor Cyan
} else {
    Write-Warning "VarunKulkarni.cer not found in $PSScriptRoot."
}
