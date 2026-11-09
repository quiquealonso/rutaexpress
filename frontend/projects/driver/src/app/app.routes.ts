import { Routes } from '@angular/router';
import { authGuard } from '@rutaexpress/shared';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./pages/login.page').then((m) => m.LoginPage) },
  {
    path: '',
    canActivate: [authGuard('DRIVER')],
    loadComponent: () => import('./core/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'today' },
      { path: 'today', title: 'Hoy', loadComponent: () => import('./pages/today.page').then((m) => m.TodayPage) },
      { path: 'scan', title: 'Carga', loadComponent: () => import('./pages/scan.page').then((m) => m.ScanPage) },
      { path: 'route', title: 'Ruta', loadComponent: () => import('./pages/route.page').then((m) => m.RoutePage) },
      { path: 'stops/:id', title: 'Parada', loadComponent: () => import('./pages/stop.page').then((m) => m.StopPage) },
      { path: 'assistant', title: 'Asistente', loadComponent: () => import('./pages/assistant.page').then((m) => m.AssistantPage) },
      { path: 'more', title: 'Más', loadComponent: () => import('./pages/more.page').then((m) => m.MorePage) },
    ],
  },
  { path: '**', redirectTo: '' },
];
