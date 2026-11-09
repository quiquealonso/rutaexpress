# Versión para Windows (PowerShell) de prepare.sh
#   .\infra\osrm\prepare.ps1
#   .\infra\osrm\prepare.ps1 -Region spain
param(
    [string]$Region = "spain/valencia",
    [string]$OsrmImage = "ghcr.io/project-osrm/osrm-backend:v5.27.1"
)
$ErrorActionPreference = "Stop"
$dir = Join-Path $PSScriptRoot "data"
New-Item -ItemType Directory -Force $dir | Out-Null
$pbf = Join-Path $dir "region.osm.pbf"
if (-not (Test-Path $pbf)) {
    $url = "https://download.geofabrik.de/europe/$Region-latest.osm.pbf"
    Write-Host "Descargando $url ..."
    Invoke-WebRequest -Uri $url -OutFile $pbf
}
docker run --rm -t -v "${dir}:/data" $OsrmImage osrm-extract -p /opt/car.lua /data/region.osm.pbf
docker run --rm -t -v "${dir}:/data" $OsrmImage osrm-partition /data/region.osrm
docker run --rm -t -v "${dir}:/data" $OsrmImage osrm-customize /data/region.osrm
Write-Host "Listo. Arranca el servidor con: docker compose up -d osrm"
