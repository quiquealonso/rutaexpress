import { Pipe, PipeTransform } from '@angular/core';

const STATUS_LABELS: Record<string, string> = {
  REGISTERED: 'Registrado',
  LOADED: 'Cargado',
  OUT_FOR_DELIVERY: 'En reparto',
  DELIVERED: 'Entregado',
  FAILED_ATTEMPT: 'Intento fallido',
  RETURNED: 'Devuelto',
  CANCELLED: 'Cancelado',
  PENDING: 'Pendiente',
  FAILED: 'Fallida',
  SKIPPED: 'Aplazada',
  PLANNED: 'Planificada',
  IN_PROGRESS: 'En curso',
  COMPLETED: 'Terminada',
  ACTIVE: 'Activo',
  ON_ROUTE: 'En ruta',
  OFF_DUTY: 'Fuera de servicio',
  INACTIVE: 'Baja',
  OPEN: 'Abierta',
  CLOSED: 'Cerrada',
  OK: 'Cargado',
  DUPLICATE: 'Duplicado',
  UNKNOWN: 'Desconocido',
  NOT_EXPECTED: 'No es tuyo',
  ABSENT: 'Ausente',
  WRONG_ADDRESS: 'Dirección incorrecta',
  REFUSED: 'Rechazado',
  DAMAGED: 'Dañado',
  INACCESSIBLE: 'Acceso imposible',
  OTHER: 'Otro',
  BREAKDOWN: 'Avería',
  ACCIDENT: 'Accidente',
  DELAY: 'Retraso',
  TRAFFIC: 'Tráfico',
  ROAD_CLOSED: 'Calle cortada',
  DAMAGED_PARCEL: 'Paquete dañado',
  NEAREST_NEIGHBOR: 'Vecino más cercano',
  TWO_OPT: '2-opt + Or-opt',
  OR_TOOLS: 'OR-Tools',
  INITIAL: 'Inicial',
  REOPTIMIZE: 'Recálculo manual',
  SKIP: 'Parada aplazada',
  RETRY_LATER: 'Reintento posterior',
  DEVIATION: 'Desvío detectado',
  // Roles
  ADMIN: 'Administración',
  DISPATCHER: 'Tráfico',
  DRIVER: 'Repartidor',
  // Origen de incidencias y eventos
  API: 'App repartidor',
  MCP: 'Integración externa',
  ASSISTANT: 'Asistente de voz',
  SYSTEM: 'Sistema',
};

@Pipe({ name: 'rxLabel' })
export class LabelPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return value ? (STATUS_LABELS[value] ?? value) : '';
  }
}

@Pipe({ name: 'rxDistance' })
export class DistancePipe implements PipeTransform {
  transform(meters: number | null | undefined): string {
    if (meters == null) return '';
    return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(1)} km`;
  }
}

@Pipe({ name: 'rxDuration' })
export class DurationPipe implements PipeTransform {
  transform(seconds: number | null | undefined): string {
    if (seconds == null) return '';
    const minutes = Math.round(seconds / 60);
    if (minutes < 60) return `${minutes} min`;
    return `${Math.floor(minutes / 60)} h ${minutes % 60} min`;
  }
}

export function labelOf(value: string): string {
  return STATUS_LABELS[value] ?? value;
}
