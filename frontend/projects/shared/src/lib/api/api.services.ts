import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config';
import {
  ComparisonResponse,
  Dashboard,
  Depot,
  Directions,
  Driver,
  FailReason,
  GeoPoint,
  ImportResult,
  Incident,
  IncidentType,
  LiveDriver,
  LoadSummary,
  Page,
  Parcel,
  ParcelEvent,
  ParcelListItem,
  ParcelRequest,
  PersonalToken,
  PersonalTokenCreated,
  PositionSample,
  ProofType,
  PublicTracking,
  Route,
  RouteListItem,
  RouteSummary,
  RouteVersion,
  ScanResponse,
  StatusCount,
  Stop,
  UserResponse,
  Uuid,
  Vehicle,
  Zone,
  DriverPosition,
} from '../models';

function params(values: Record<string, string | number | boolean | null | undefined>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(values)) {
    if (v !== null && v !== undefined && v !== '') {
      p = p.set(k, String(v));
    }
  }
  return p;
}

abstract class BaseApi {
  protected readonly http = inject(HttpClient);
  protected readonly base = inject(API_BASE_URL);

  protected url(path: string): string {
    return `${this.base}${path}`;
  }
}

@Injectable({ providedIn: 'root' })
export class AccountApi extends BaseApi {
  me(): Observable<UserResponse> {
    return this.http.get<UserResponse>(this.url('/api/auth/me'));
  }

  tokens(): Observable<PersonalToken[]> {
    return this.http.get<PersonalToken[]>(this.url('/api/auth/tokens'));
  }

  createToken(name: string, expiresInDays?: number): Observable<PersonalTokenCreated> {
    return this.http.post<PersonalTokenCreated>(this.url('/api/auth/tokens'), { name, expiresInDays });
  }

  revokeToken(id: Uuid): Observable<void> {
    return this.http.delete<void>(this.url(`/api/auth/tokens/${id}`));
  }
}

@Injectable({ providedIn: 'root' })
export class CatalogApi extends BaseApi {
  depots(): Observable<Depot[]> {
    return this.http.get<Depot[]>(this.url('/api/depots'));
  }

  saveDepot(depot: Omit<Depot, 'id'>, id?: Uuid): Observable<Depot> {
    return id
      ? this.http.put<Depot>(this.url(`/api/depots/${id}`), depot)
      : this.http.post<Depot>(this.url('/api/depots'), depot);
  }

  deleteDepot(id: Uuid): Observable<void> {
    return this.http.delete<void>(this.url(`/api/depots/${id}`));
  }

  vehicles(page = 0, size = 100): Observable<Page<Vehicle>> {
    return this.http.get<Page<Vehicle>>(this.url('/api/vehicles'), { params: params({ page, size }) });
  }

  saveVehicle(v: Partial<Vehicle>, id?: Uuid): Observable<Vehicle> {
    return id
      ? this.http.put<Vehicle>(this.url(`/api/vehicles/${id}`), v)
      : this.http.post<Vehicle>(this.url('/api/vehicles'), v);
  }

  deleteVehicle(id: Uuid): Observable<void> {
    return this.http.delete<void>(this.url(`/api/vehicles/${id}`));
  }

  zones(depotId?: Uuid): Observable<Zone[]> {
    return this.http.get<Zone[]>(this.url('/api/zones'), { params: params({ depotId }) });
  }

  saveZone(z: { name: string; depotId: Uuid; area: GeoPoint[]; color?: string }, id?: Uuid): Observable<Zone> {
    return id
      ? this.http.put<Zone>(this.url(`/api/zones/${id}`), z)
      : this.http.post<Zone>(this.url('/api/zones'), z);
  }

  deleteZone(id: Uuid): Observable<void> {
    return this.http.delete<void>(this.url(`/api/zones/${id}`));
  }

  drivers(page = 0, size = 100): Observable<Page<Driver>> {
    return this.http.get<Page<Driver>>(this.url('/api/drivers'), { params: params({ page, size }) });
  }

  driverMe(): Observable<Driver> {
    return this.http.get<Driver>(this.url('/api/drivers/me'));
  }

  createDriver(d: Record<string, unknown>): Observable<Driver> {
    return this.http.post<Driver>(this.url('/api/drivers'), d);
  }

  updateDriver(id: Uuid, d: Record<string, unknown>): Observable<Driver> {
    return this.http.put<Driver>(this.url(`/api/drivers/${id}`), d);
  }

  deactivateDriver(id: Uuid): Observable<void> {
    return this.http.delete<void>(this.url(`/api/drivers/${id}`));
  }
}

export interface ParcelQuery {
  status?: string;
  depotId?: Uuid;
  q?: string;
  needsReview?: boolean;
  driverId?: Uuid;
  page?: number;
  size?: number;
  sort?: string;
}

@Injectable({ providedIn: 'root' })
export class ParcelApi extends BaseApi {
  list(query: ParcelQuery): Observable<Page<ParcelListItem>> {
    return this.http.get<Page<ParcelListItem>>(this.url('/api/parcels'), {
      params: params({ ...query }),
    });
  }

  get(id: Uuid): Observable<Parcel> {
    return this.http.get<Parcel>(this.url(`/api/parcels/${id}`));
  }

  events(id: Uuid): Observable<ParcelEvent[]> {
    return this.http.get<ParcelEvent[]>(this.url(`/api/parcels/${id}/events`));
  }

  stats(): Observable<StatusCount[]> {
    return this.http.get<StatusCount[]>(this.url('/api/parcels/stats'));
  }

  create(p: ParcelRequest): Observable<Parcel> {
    return this.http.post<Parcel>(this.url('/api/parcels'), p);
  }

  update(id: Uuid, p: ParcelRequest): Observable<Parcel> {
    return this.http.put<Parcel>(this.url(`/api/parcels/${id}`), p);
  }

  cancel(id: Uuid): Observable<void> {
    return this.http.delete<void>(this.url(`/api/parcels/${id}`));
  }

  importCsv(file: File, depotId: Uuid): Observable<ImportResult> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportResult>(this.url('/api/parcels/import'), form, { params: params({ depotId }) });
  }

  label(id: Uuid): Observable<Blob> {
    return this.http.post(this.url(`/api/parcels/${id}/label`), null, { responseType: 'blob' });
  }

  labels(ids: Uuid[]): Observable<Blob> {
    return this.http.post(this.url('/api/parcels/labels'), { parcelIds: ids }, { responseType: 'blob' });
  }
}

@Injectable({ providedIn: 'root' })
export class LoadApi extends BaseApi {
  open(driverId?: Uuid, vehicleId?: Uuid): Observable<LoadSummary> {
    return this.http.post<LoadSummary>(this.url('/api/loads'), { driverId, vehicleId });
  }

  current(): Observable<LoadSummary> {
    return this.http.get<LoadSummary>(this.url('/api/loads/current'));
  }

  get(id: Uuid): Observable<LoadSummary> {
    return this.http.get<LoadSummary>(this.url(`/api/loads/${id}`));
  }

  scan(loadId: Uuid, code: string, clientScanId: Uuid, scannedAt: string): Observable<ScanResponse> {
    return this.http.post<ScanResponse>(this.url(`/api/loads/${loadId}/scans`), { code, clientScanId, scannedAt });
  }

  scanBatch(loadId: Uuid, scans: { code: string; clientScanId: Uuid; scannedAt: string }[]): Observable<ScanResponse[]> {
    return this.http.post<ScanResponse[]>(this.url(`/api/loads/${loadId}/scans/batch`), { scans });
  }

  close(loadId: Uuid): Observable<LoadSummary> {
    return this.http.post<LoadSummary>(this.url(`/api/loads/${loadId}/close`), null);
  }
}

@Injectable({ providedIn: 'root' })
export class RouteApi extends BaseApi {
  today(): Observable<RouteListItem[]> {
    return this.http.get<RouteListItem[]>(this.url('/api/routes'));
  }

  current(): Observable<Route> {
    return this.http.get<Route>(this.url('/api/routes/current'));
  }

  get(id: Uuid): Observable<Route> {
    return this.http.get<Route>(this.url(`/api/routes/${id}`));
  }

  nextStop(id: Uuid): Observable<Stop> {
    return this.http.get<Stop>(this.url(`/api/routes/${id}/next-stop`));
  }

  summary(id: Uuid): Observable<RouteSummary> {
    return this.http.get<RouteSummary>(this.url(`/api/routes/${id}/summary`));
  }

  versions(id: Uuid): Observable<RouteVersion[]> {
    return this.http.get<RouteVersion[]>(this.url(`/api/routes/${id}/versions`));
  }

  compare(id: Uuid): Observable<ComparisonResponse> {
    return this.http.get<ComparisonResponse>(this.url(`/api/routes/${id}/compare`));
  }

  start(id: Uuid): Observable<Route> {
    return this.http.post<Route>(this.url(`/api/routes/${id}/start`), null);
  }

  reoptimize(id: Uuid, position?: GeoPoint): Observable<Route> {
    return this.http.post<Route>(this.url(`/api/routes/${id}/reoptimize`), position ? { position } : null);
  }

  directions(id: Uuid, stopId?: Uuid): Observable<Directions> {
    return this.http.get<Directions>(this.url(`/api/routes/${id}/directions`), { params: params({ stopId }) });
  }
}

@Injectable({ providedIn: 'root' })
export class DeliveryApi extends BaseApi {
  deliver(stopId: Uuid, proofType: ProofType, receiverName?: string, file?: Blob, position?: GeoPoint): Observable<Stop> {
    const form = new FormData();
    form.append('proofType', proofType);
    if (receiverName) form.append('receiverName', receiverName);
    if (position) {
      form.append('lat', String(position.lat));
      form.append('lon', String(position.lon));
    }
    if (file) form.append('file', file, proofType === 'SIGNATURE' ? 'firma.png' : 'foto.jpg');
    return this.http.post<Stop>(this.url(`/api/stops/${stopId}/deliver`), form);
  }

  fail(stopId: Uuid, reason: FailReason, note: string | undefined, retryLater: boolean, position?: GeoPoint): Observable<Stop> {
    return this.http.post<Stop>(this.url(`/api/stops/${stopId}/fail`), { reason, note, retryLater, position });
  }

  skip(stopId: Uuid, position?: GeoPoint): Observable<Route> {
    return this.http.post<Route>(this.url(`/api/stops/${stopId}/skip`), position ? { position } : null);
  }

  proofFile(stopId: Uuid): Observable<Blob> {
    return this.http.get(this.url(`/api/stops/${stopId}/proof/file`), { responseType: 'blob' });
  }

  reportIncident(type: IncidentType, description: string, position?: GeoPoint): Observable<Incident> {
    return this.http.post<Incident>(this.url('/api/incidents'), { type, description, position });
  }

  incidents(open = false): Observable<Incident[]> {
    return this.http.get<Incident[]>(this.url('/api/incidents'), { params: params({ open }) });
  }

  resolveIncident(id: Uuid): Observable<Incident> {
    return this.http.post<Incident>(this.url(`/api/incidents/${id}/resolve`), null);
  }

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>(this.url('/api/dashboard'));
  }
}

@Injectable({ providedIn: 'root' })
export class TrackingApi extends BaseApi {
  sendPositions(positions: PositionSample[]): Observable<{ accepted: number }> {
    return this.http.post<{ accepted: number }>(this.url('/api/tracking/positions'), { positions });
  }

  liveDrivers(): Observable<LiveDriver[]> {
    return this.http.get<LiveDriver[]>(this.url('/api/tracking/drivers'));
  }

  trail(driverId: Uuid, since?: string): Observable<DriverPosition[]> {
    return this.http.get<DriverPosition[]>(this.url(`/api/tracking/drivers/${driverId}/trail`), {
      params: params({ since }),
    });
  }

  publicTrack(code: string): Observable<PublicTracking> {
    return this.http.get<PublicTracking>(this.url(`/public/track/${encodeURIComponent(code)}`));
  }
}
