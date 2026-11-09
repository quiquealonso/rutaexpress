import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  output,
  untracked,
} from '@angular/core';
import * as L from 'leaflet';
import { GeoPoint, Route } from '../models';
import { createMap, depotMarker, driverMarker, escapeHtml, fitTo, routeLine, stopMarker } from './map.utils';

/**
 * Mapa de una ruta: almacén, paradas numeradas con el color de su estado y la polilínea por carretera.
 * Opcionalmente, una segunda geometría (por ejemplo, la versión planificada) en discontinua.
 */
@Component({
  selector: 'rx-route-map',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
  styles: ':host { display: block; min-height: 160px; border-radius: var(--rx-radius, 12px); overflow: hidden; }',
})
export class RouteMapComponent {
  readonly route = input<Route | null>(null);
  readonly comparison = input<GeoPoint[] | null>(null);
  readonly driverPosition = input<GeoPoint | null>(null);
  readonly selectedStopId = input<string | null>(null);
  /** Si es true, el mapa se centra en la parada seleccionada en vez de encuadrar toda la ruta. */
  readonly focusSelected = input(false);
  readonly stopClicked = output<string>();

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private map?: L.Map;
  private layer = L.layerGroup();
  private driverLayer = L.layerGroup();
  private fitted = false;

  constructor() {
    afterNextRender(() => {
      this.map = createMap(this.host.nativeElement);
      this.layer.addTo(this.map);
      this.driverLayer.addTo(this.map);
      this.draw();
      this.drawDriver();
      // Leaflet necesita recalcular el tamaño si el contenedor cambia (paneles, pestañas)
      new ResizeObserver(() => this.map?.invalidateSize()).observe(this.host.nativeElement);
    });
    effect(() => {
      this.route();
      this.comparison();
      this.selectedStopId();
      untracked(() => this.draw());
    });
    effect(() => {
      this.driverPosition();
      untracked(() => this.drawDriver());
    });
  }

  private draw(): void {
    const map = this.map;
    const route = this.route();
    if (!map) return;
    this.layer.clearLayers();
    if (!route) return;
    const comparison = this.comparison();
    if (comparison?.length) {
      routeLine(comparison, '#9e9e9e', true).addTo(this.layer);
    }
    if (route.geometry.length > 1) {
      routeLine(route.geometry).addTo(this.layer);
    }
    depotMarker(route.depotLocation).bindTooltip('Almacén').addTo(this.layer);
    for (const stop of route.stops) {
      const selected = stop.id === this.selectedStopId();
      const marker = stopMarker(stop.location, stop.sequence, stop.status, selected)
        .bindTooltip(`${stop.sequence}. ${escapeHtml(stop.addressLine)}`)
        .on('click', () => this.stopClicked.emit(stop.id));
      if (selected) {
        marker.setZIndexOffset(500);
      }
      marker.addTo(this.layer);
    }
    const focused = this.focusSelected() ? route.stops.find((s) => s.id === this.selectedStopId()) : undefined;
    if (focused) {
      map.setView([focused.location.lat, focused.location.lon], 16);
      this.fitted = true;
    } else if (!this.fitted) {
      fitTo(map, [route.depotLocation, ...route.stops.map((s) => s.location)]);
      this.fitted = true;
    }
  }

  private drawDriver(): void {
    this.driverLayer.clearLayers();
    const p = this.driverPosition();
    if (this.map && p) {
      driverMarker(p, 'Repartidor').addTo(this.driverLayer);
    }
  }

  /** Centra el mapa en un punto (p. ej. la siguiente parada). */
  focus(point: GeoPoint, zoom = 16): void {
    this.map?.setView([point.lat, point.lon], zoom);
  }
}
