import { Routes } from '@angular/router';
import { authGuard } from '@rutaexpress/shared';

// Carga diferida por ruta: cada pantalla es su propio chunk
export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login.page').then((m) => m.LoginPage) },
  // Seguimiento público (sin sesión) para el destinatario
  { path: 'seguimiento', title: 'Seguimiento', loadComponent: () => import('./pages/public-tracking.page').then((m) => m.PublicTrackingPage) },
  { path: 'seguimiento/:code', title: 'Seguimiento', loadComponent: () => import('./pages/public-tracking.page').then((m) => m.PublicTrackingPage) },
  {
    path: '',
    canActivate: [authGuard('ADMIN', 'DISPATCHER')],
    loadComponent: () => import('./core/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', title: 'Dashboard', loadComponent: () => import('./pages/dashboard.page').then((m) => m.DashboardPage) },
      { path: 'parcels', title: 'Paquetes', loadComponent: () => import('./pages/parcels/parcel-list.page').then((m) => m.ParcelListPage) },
      { path: 'parcels/new', title: 'Nuevo paquete', loadComponent: () => import('./pages/parcels/parcel-form.page').then((m) => m.ParcelFormPage) },
      { path: 'parcels/:id', title: 'Paquete', loadComponent: () => import('./pages/parcels/parcel-detail.page').then((m) => m.ParcelDetailPage) },
      { path: 'parcels/:id/edit', title: 'Editar paquete', loadComponent: () => import('./pages/parcels/parcel-form.page').then((m) => m.ParcelFormPage) },
      { path: 'live', title: 'Mapa en directo', loadComponent: () => import('./pages/live-map.page').then((m) => m.LiveMapPage) },
      { path: 'routes', title: 'Rutas', loadComponent: () => import('./pages/routes/route-list.page').then((m) => m.RouteListPage) },
      { path: 'routes/:id', title: 'Detalle de ruta', loadComponent: () => import('./pages/routes/route-detail.page').then((m) => m.RouteDetailPage) },
      { path: 'drivers', title: 'Repartidores', loadComponent: () => import('./pages/catalog/drivers.page').then((m) => m.DriversPage) },
      { path: 'vehicles', title: 'Vehículos', loadComponent: () => import('./pages/catalog/vehicles.page').then((m) => m.VehiclesPage) },
      { path: 'depots', title: 'Almacenes', loadComponent: () => import('./pages/catalog/depots.page').then((m) => m.DepotsPage) },
      { path: 'zones', title: 'Zonas', loadComponent: () => import('./pages/catalog/zones.page').then((m) => m.ZonesPage) },
      { path: 'incidents', title: 'Incidencias', loadComponent: () => import('./pages/incidents.page').then((m) => m.IncidentsPage) },
    ],
  },
  { path: '**', redirectTo: '' },
];
