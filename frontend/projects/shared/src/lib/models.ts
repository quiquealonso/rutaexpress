// Tipos que reflejan los DTO del backend. Se pueden regenerar desde el OpenAPI con `npm run generate:api`
// (genera src/lib/api/schema.d.ts) para detectar en compilación cualquier cambio de contrato.

export type Uuid = string;
export type IsoInstant = string;

export interface GeoPoint {
  lat: number;
  lon: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  code?: string;
  errors?: Record<string, string>;
}

// ---------- auth ----------
export type Role = 'ADMIN' | 'DISPATCHER' | 'DRIVER';

export interface UserResponse {
  id: Uuid;
  email: string;
  role: Role;
  driverId?: Uuid;
  enabled: boolean;
  lastLoginAt?: IsoInstant;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
  user: UserResponse;
}

export interface PersonalToken {
  id: Uuid;
  name: string;
  prefix: string;
  createdAt: IsoInstant;
  expiresAt?: IsoInstant;
  lastUsedAt?: IsoInstant;
  active: boolean;
}

export interface PersonalTokenCreated {
  id: Uuid;
  name: string;
  token: string;
  expiresAt: IsoInstant;
}

// ---------- catalog ----------
export interface Depot {
  id: Uuid;
  name: string;
  address: string;
  location: GeoPoint;
}

export interface Vehicle {
  id: Uuid;
  plate: string;
  capacityKg: number;
  capacityM3: number;
  capacityParcels: number;
  active: boolean;
  depotId: Uuid;
  depotName: string;
}

export interface Zone {
  id: Uuid;
  name: string;
  depotId: Uuid;
  depotName: string;
  area: GeoPoint[];
  color: string;
}

export type DriverStatus = 'ACTIVE' | 'ON_ROUTE' | 'OFF_DUTY' | 'INACTIVE';

export interface Driver {
  id: Uuid;
  name: string;
  phone?: string;
  licenseNumber?: string;
  status: DriverStatus;
  userId?: Uuid;
  depotId: Uuid;
  depotName: string;
  zoneId?: Uuid;
  zoneName?: string;
  defaultVehicleId?: Uuid;
  defaultVehiclePlate?: string;
}

// ---------- parcel ----------
export type ParcelStatus =
  | 'REGISTERED'
  | 'LOADED'
  | 'OUT_FOR_DELIVERY'
  | 'DELIVERED'
  | 'FAILED_ATTEMPT'
  | 'RETURNED'
  | 'CANCELLED';

export interface Recipient {
  name: string;
  phone?: string;
  email?: string;
}

export interface AddressInput {
  street: string;
  number?: string;
  extra?: string;
  postalCode: string;
  city: string;
  country?: string;
  location?: GeoPoint;
}

export interface AddressResponse extends AddressInput {
  id: Uuid;
  geocodeQuality?: number;
  geocodeProvider?: string;
  needsReview: boolean;
  oneLine: string;
}

export interface ParcelListItem {
  id: Uuid;
  trackingCode: string;
  status: ParcelStatus;
  recipientName: string;
  addressLine: string;
  city: string;
  needsReview: boolean;
  weightKg: number;
  assignedDriverId?: Uuid;
  createdAt: IsoInstant;
}

export interface Parcel {
  id: Uuid;
  trackingCode: string;
  status: ParcelStatus;
  weightKg: number;
  lengthCm?: number;
  widthCm?: number;
  heightCm?: number;
  windowStart?: string;
  windowEnd?: string;
  notes?: string;
  depotId: Uuid;
  assignedDriverId?: Uuid;
  deliveryAttempts: number;
  recipient: Recipient;
  address: AddressResponse;
  createdAt: IsoInstant;
  updatedAt?: IsoInstant;
}

export interface ParcelRequest {
  trackingCode?: string;
  recipient: Recipient;
  address: AddressInput;
  weightKg: number;
  lengthCm?: number;
  widthCm?: number;
  heightCm?: number;
  windowStart?: string;
  windowEnd?: string;
  notes?: string;
  depotId?: Uuid;
  assignedDriverId?: Uuid;
}

export interface ParcelEvent {
  id: Uuid;
  type: string;
  occurredAt: IsoInstant;
  actor?: string;
  source: string;
  data?: Record<string, unknown>;
}

export interface ImportResult {
  created: number;
  failed: number;
  needsReview: number;
  errors: { row: number; message: string }[];
}

export interface StatusCount {
  status: ParcelStatus;
  total: number;
}

// ---------- scanning ----------
export type ScanResult = 'OK' | 'DUPLICATE' | 'UNKNOWN' | 'NOT_EXPECTED';

export interface ScanResponse {
  id: Uuid;
  clientScanId: Uuid;
  code: string;
  result: ScanResult;
  parcelId?: Uuid;
  message: string;
  scannedAt: IsoInstant;
  scannedCount: number;
  expectedCount: number;
}

export interface LoadSummary {
  id: Uuid;
  driverId: Uuid;
  vehicleId?: Uuid;
  depotId: Uuid;
  status: 'OPEN' | 'CLOSED';
  openedAt: IsoInstant;
  closedAt?: IsoInstant;
  expectedCount: number;
  scannedCount: number;
  duplicateCount: number;
  unknownCount: number;
  notExpectedCount: number;
  missing: { parcelId: Uuid; trackingCode: string; addressLine: string }[];
  recentScans: ScanResponse[];
}

// ---------- routing ----------
export type StopStatus = 'PENDING' | 'DELIVERED' | 'FAILED' | 'SKIPPED';
export type RouteStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';

export interface ParcelBrief {
  id: Uuid;
  trackingCode: string;
  recipientName: string;
  recipientPhone?: string;
  notes?: string;
}

export interface Stop {
  id: Uuid;
  routeId: Uuid;
  sequence: number;
  status: StopStatus;
  eta?: IsoInstant;
  plannedEta?: IsoInstant;
  addressLine: string;
  location: GeoPoint;
  parcels: ParcelBrief[];
  windowStart?: string;
  windowEnd?: string;
  failReason?: string;
  attempts: number;
  completedAt?: IsoInstant;
  legDistanceMeters: number;
  legDurationSeconds: number;
}

export interface Route {
  id: Uuid;
  loadId: Uuid;
  driverId: Uuid;
  depotId: Uuid;
  depotLocation: GeoPoint;
  status: RouteStatus;
  version: number;
  algorithm: string;
  totalDistanceMeters: number;
  totalDurationSeconds: number;
  estimatedEnd?: IsoInstant;
  startedAt?: IsoInstant;
  completedAt?: IsoInstant;
  geometry: GeoPoint[];
  stops: Stop[];
}

export interface RouteListItem {
  id: Uuid;
  driverId: Uuid;
  driverName: string;
  status: RouteStatus;
  version: number;
  algorithm: string;
  totalStops: number;
  delivered: number;
  failed: number;
  pending: number;
  totalDistanceMeters: number;
  estimatedEnd?: IsoInstant;
  startedAt?: IsoInstant;
}

export interface RouteSummary {
  routeId: Uuid;
  status: RouteStatus;
  totalStops: number;
  delivered: number;
  failed: number;
  pending: number;
  parcelsPending: number;
  remainingDistanceMeters: number;
  remainingDurationSeconds: number;
  estimatedEnd?: IsoInstant;
}

export interface RouteVersion {
  id: Uuid;
  version: number;
  algorithm: string;
  reason: string;
  totalDistanceMeters: number;
  totalDurationSeconds: number;
  computeMillis: number;
  matrixSource: string;
  createdAt: IsoInstant;
  stopOrder: Uuid[];
  geometry: GeoPoint[];
}

export interface AlgorithmComparison {
  algorithm: string;
  totalDurationSeconds: number;
  computeMillis: number;
  savedSecondsVsScanOrder: number;
  savedPercentVsScanOrder: number;
}

export interface ComparisonResponse {
  routeId: Uuid;
  stops: number;
  matrixSource: string;
  scanOrderDurationSeconds: number;
  results: AlgorithmComparison[];
}

// ---------- delivery ----------
export type FailReason = 'ABSENT' | 'WRONG_ADDRESS' | 'REFUSED' | 'DAMAGED' | 'INACCESSIBLE' | 'OTHER';
export type ProofType = 'SIGNATURE' | 'PHOTO' | 'CODE';
export type IncidentType =
  | 'BREAKDOWN'
  | 'ACCIDENT'
  | 'DELAY'
  | 'TRAFFIC'
  | 'ROAD_CLOSED'
  | 'DAMAGED_PARCEL'
  | 'OTHER';

export interface Incident {
  id: Uuid;
  driverId: Uuid;
  routeId?: Uuid;
  type: IncidentType;
  description: string;
  location?: GeoPoint;
  source: string;
  createdAt: IsoInstant;
  resolvedAt?: IsoInstant;
  resolvedBy?: string;
}

export interface Directions {
  stopId: Uuid;
  addressLine: string;
  distanceMeters: number;
  durationSeconds: number;
  steps: { instruction: string; distanceMeters: number; durationSeconds: number }[];
  geometry: GeoPoint[];
  googleMapsUrl: string;
  wazeUrl: string;
  source: string;
}

export interface Dashboard {
  routesToday: number;
  routesInProgress: number;
  routesCompleted: number;
  parcelsByStatus: StatusCount[];
  failedStopsToday: number;
  deliveredStopsToday: number;
  averageSecondsPerStop: number;
  openIncidents: number;
}

// ---------- tracking ----------
export interface DriverPosition {
  driverId: Uuid;
  location: GeoPoint;
  speedKmh?: number;
  heading?: number;
  accuracyMeters?: number;
  recordedAt: IsoInstant;
}

export interface LiveDriver {
  driverId: Uuid;
  driverName: string;
  location: GeoPoint;
  speedKmh?: number;
  recordedAt: IsoInstant;
  routeId?: Uuid;
}

export interface PositionSample {
  lat: number;
  lon: number;
  speedKmh?: number;
  heading?: number;
  accuracyMeters?: number;
  recordedAt: IsoInstant;
}

export interface RealtimeMessage<T = unknown> {
  type:
    | 'POSITION'
    | 'ROUTE_OPTIMIZED'
    | 'ROUTE_OPTIMIZATION_FAILED'
    | 'ROUTE_STARTED'
    | 'ROUTE_COMPLETED'
    | 'STOP_STATUS_CHANGED'
    | 'INCIDENT_REPORTED';
  payload: T;
  at: IsoInstant;
}

export interface PublicTracking {
  trackingCode: string;
  status: ParcelStatus;
  statusLabel: string;
  city: string;
  etaFrom?: IsoInstant;
  etaTo?: IsoInstant;
  stopsBefore?: number;
  lastUpdate?: IsoInstant;
  events: { type: string; label: string; at: IsoInstant }[];
}
