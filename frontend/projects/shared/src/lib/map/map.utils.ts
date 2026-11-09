import * as L from 'leaflet';
import { GeoPoint, StopStatus } from '../models';

export const VALENCIA: GeoPoint = { lat: 39.4699, lon: -0.3763 };

export const STOP_COLORS: Record<StopStatus, string> = {
  PENDING: '#1e3a5f',
  SKIPPED: '#b54708',
  DELIVERED: '#1f7a4d',
  FAILED: '#b42318',
};

export function toLatLng(p: GeoPoint): L.LatLngExpression {
  return [p.lat, p.lon];
}

/** Mapa con teselas de OpenStreetMap. Se llama dentro de afterNextRender (el contenedor ya existe). */
export function createMap(element: HTMLElement, center: GeoPoint = VALENCIA, zoom = 13): L.Map {
  const map = L.map(element, { zoomControl: true, attributionControl: true }).setView(toLatLng(center), zoom);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; colaboradores de OpenStreetMap',
  }).addTo(map);
  return map;
}

/** Marcador numerado con el color del estado de la parada. */
export function stopMarker(p: GeoPoint, label: string | number, status: StopStatus, selected = false): L.Marker {
  const color = STOP_COLORS[status];
  const size = selected ? 38 : 28;
  const icon = L.divIcon({
    className: selected ? 'rx-stop-marker selected' : 'rx-stop-marker',
    html: `<span style="background:${color}">${label}</span>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  });
  return L.marker(toLatLng(p), { icon });
}

export function depotMarker(p: GeoPoint): L.Marker {
  const icon = L.divIcon({
    className: 'rx-depot-marker',
    html: '<span title="Almacén">warehouse</span>',
    iconSize: [34, 34],
    iconAnchor: [17, 17],
  });
  return L.marker(toLatLng(p), { icon, zIndexOffset: 1000 });
}

export function driverMarker(p: GeoPoint, name: string): L.Marker {
  const icon = L.divIcon({
    className: 'rx-driver-marker',
    html: `<span title="${escapeHtml(name)}">local_shipping</span>`,
    iconSize: [34, 34],
    iconAnchor: [17, 17],
  });
  return L.marker(toLatLng(p), { icon, zIndexOffset: 2000 });
}

export function routeLine(points: GeoPoint[], color = '#3b5f8a', dashed = false): L.Polyline {
  return L.polyline(points.map(toLatLng), { color, weight: 5, opacity: 0.8, dashArray: dashed ? '8 8' : undefined });
}

export function fitTo(map: L.Map, points: GeoPoint[]): void {
  if (points.length > 0) {
    map.fitBounds(L.latLngBounds(points.map(toLatLng)), { padding: [30, 30], maxZoom: 16 });
  }
}

export function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]!);
}
