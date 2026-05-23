# build-and-load.ps1 — Сборка образа и загрузка в Rancher Desktop VM
#
# Использование:
#   .\rancher\build-and-load.ps1              # только сборка + загрузка
#   .\rancher\build-and-load.ps1 -Restart     # + перезапуск деплоя
#
# Почему нужна загрузка в VM:
#   docker build кладёт образ в Docker Desktop.
#   k3s (Rancher Desktop) использует отдельную Linux VM с отдельным Docker daemon.
#   Без rdctl shell -- docker load образ не найдётся → ErrImageNeverPull.

param(
    [switch]$Restart
)

$Image = "pet-hospital:1.0.0"
$Tar   = "$env:TEMP\pet-hospital.tar"

Write-Host "[1/3] Building $Image (--provenance=false required for k3s)..." -ForegroundColor Cyan
docker build --provenance=false -t $Image .
if (-not $?) { Write-Host "Build failed" -ForegroundColor Red; exit 1 }

Write-Host "[2/3] Saving to $Tar..." -ForegroundColor Cyan
docker save $Image -o $Tar
if (-not $?) { Write-Host "Save failed" -ForegroundColor Red; exit 1 }

Write-Host "[3/3] Loading into Rancher Desktop VM..." -ForegroundColor Cyan
rdctl shell -- sh -c "docker load < /mnt/c/Users/$env:USERNAME/AppData/Local/Temp/pet-hospital.tar"
if (-not $?) { Write-Host "Load failed" -ForegroundColor Red; exit 1 }

if ($Restart) {
    Write-Host "Restarting deployment..." -ForegroundColor Cyan
    kubectl rollout restart deployment/hospital-app -n pet-hospital
    kubectl rollout status deployment/hospital-app -n pet-hospital --timeout=300s
}

Write-Host "Done: $Image is ready in Rancher Desktop VM" -ForegroundColor Green
