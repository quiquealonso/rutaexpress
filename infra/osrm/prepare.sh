#!/usr/bin/env bash
# Descarga el mapa de OpenStreetMap y lo prepara para OSRM (algoritmo MLD). Se ejecuta una sola vez
# (o cuando quieras actualizar el mapa). Por defecto, la Comunidad Valenciana: toda España necesita
# bastante más memoria (unos 8-10 GB) al procesar el mapa.
#
#   ./infra/osrm/prepare.sh                      # Comunidad Valenciana
#   REGION=spain ./infra/osrm/prepare.sh         # España completa
set -euo pipefail

REGION="${REGION:-spain/valencia}"
OSRM_IMAGE="${OSRM_IMAGE:-ghcr.io/project-osrm/osrm-backend:v5.27.1}"
DIR="$(cd "$(dirname "$0")" && pwd)/data"
URL="https://download.geofabrik.de/europe/${REGION}-latest.osm.pbf"

mkdir -p "$DIR"
cd "$DIR"

if [ ! -f region.osm.pbf ]; then
  echo "Descargando $URL ..."
  curl -fL --retry 3 -o region.osm.pbf "$URL"
fi

echo "1/3 osrm-extract (perfil coche)"
docker run --rm -t -v "$DIR:/data" "$OSRM_IMAGE" osrm-extract -p /opt/car.lua /data/region.osm.pbf
echo "2/3 osrm-partition"
docker run --rm -t -v "$DIR:/data" "$OSRM_IMAGE" osrm-partition /data/region.osrm
echo "3/3 osrm-customize"
docker run --rm -t -v "$DIR:/data" "$OSRM_IMAGE" osrm-customize /data/region.osrm

echo "Listo. Arranca el servidor con: docker compose up -d osrm"
