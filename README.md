# RutaExpress

Plataforma de reparto de última milla: el repartidor escanea los paquetes al cargar la furgoneta, el sistema
calcula la ruta óptima **por carretera** y un asistente de IA conectado por **MCP** le guía parada a parada.

| Pieza | Tecnología |
|---|---|
| Backend | Java 21 · Spring Boot 3.5 · Spring Modulith · Spring Security (JWT) · Spring AI (MCP + Claude) |
| Datos | PostgreSQL 16 + PostGIS · Hibernate 6 + hibernate-spatial · Flyway · Redis |
| Rutas | OSRM propio (mapa OSM) · Google OR-Tools · vecino más cercano y 2-opt + Or-opt propios |
| Frontend | Angular 21 (standalone, signals, zoneless) · Angular Material · Leaflet · ZXing · PWA |
| Infra | Docker Compose · Nginx · MinIO · Prometheus + Grafana · GitHub Actions |

## Estructura

```
rutaexpress/
├── backend/                 Spring Boot (monolito modular)
│   └── src/main/java/com/rutaexpress/
│       ├── auth/            login, JWT, refresh rotatorio, tokens personales (MCP)
│       ├── catalog/         almacenes, vehículos, repartidores, zonas
│       ├── parcel/          paquetes, destinatarios, direcciones, historial, etiquetas QR, CSV, RGPD
│       ├── scanning/        sesiones de carga y escaneos idempotentes
│       ├── geo/             geocodificación (Nominatim) y matriz/rutas (OSRM) con caché
│       ├── routing/         planificación, versiones, recálculo; optimizer/ = algoritmos (Strategy)
│       ├── delivery/        entregas con prueba, fallos, incidencias, MinIO, dashboard
│       ├── tracking/        GPS (tabla particionada), WebSocket STOMP, seguimiento público
│       ├── mcp/             servidor MCP, herramientas del conductor, asistente, límite de peticiones
│       ├── demo/            datos de demostración (Valencia)
│       └── shared/          errores RFC 7807, eventos de dominio, geometría, seguridad, config
├── frontend/                workspace Angular
│   ├── projects/admin/      panel de tráfico
│   ├── projects/driver/     PWA del repartidor
│   ├── projects/shared/     modelos, API, auth, tiempo real, mapas
│   └── e2e/                 Playwright
├── infra/                   nginx, osrm, prometheus, grafana
├── load-tests/k6/           prueba de carga
└── .github/workflows/       CI y despliegue
```

Los módulos se comunican por **eventos de dominio** (`shared/events/DomainEvents`): por ejemplo, cerrar la carga
publica `LoadClosedEvent` y `routing` lo escucha de forma asíncrona, sin que `scanning` conozca `routing`.
`ArchitectureTest` verifica con Spring Modulith que no haya dependencias circulares ni accesos a tipos internos,
y genera los diagramas de módulos en `backend/target/spring-modulith-docs`.

## Arranque rápido (Docker)

Requisitos: Docker con Compose 2.24 o superior.

```bash
cp .env.example .env
```

Edita `.env` y cambia todas las contraseñas y `JWT_SECRET`. Después, prepara el mapa (una sola vez, unos minutos):

```bash
./infra/osrm/prepare.sh
```

En Windows: `.\infra\osrm\prepare.ps1`. Por defecto descarga la Comunidad Valenciana; toda España necesita 8–10 GB
de RAM al procesar el mapa (`REGION=spain ./infra/osrm/prepare.sh`).

```bash
docker compose up -d --build
```

| URL | Qué es |
|---|---|
| http://localhost | Panel de tráfico |
| http://localhost/app/ | App del repartidor (instalable como PWA) |
| http://localhost/seguimiento/{código} | Seguimiento público |
| http://localhost/swagger-ui.html | Documentación de la API |
| http://localhost:3000 | Grafana (dashboard «RutaExpress») |

### Usuarios de demostración

Se crean al arrancar si la base de datos está vacía (`DEMO_DATA=true`). Son solo para entornos locales.

| Usuario | Rol |
|---|---|
| `admin@rutaexpress.local` | Administrador |
| `trafico@rutaexpress.local` | Jefe de tráfico |
| `lucia@rutaexpress.local` | Repartidora, zona Valencia Centro |
| `javier@rutaexpress.local` | Repartidor, zona Valencia Este |

Contraseña de todos: `RutaExpress-demo-2026` (se cambia con la propiedad `rutaexpress.demo.password`).
Hay 29 paquetes repartidos en las dos zonas, dos de ellos al mismo portal (se agrupan en una sola parada).

## Desarrollo en local

```bash
docker compose up -d postgres redis osrm minio
```

```bash
cd backend && ./mvnw spring-boot:run
```

El perfil por defecto (`dev`) usa caché en memoria, ficheros en disco local, datos de demo y, si OSRM no responde,
una estimación en línea recta (para poder trabajar sin el mapa procesado).

```bash
cd frontend && npm install && npm run start:admin
```

```bash
cd frontend && npm run start:driver
```

Panel en http://localhost:4200 y app del repartidor en http://localhost:4201 (el proxy de `ng serve` reenvía
`/api`, `/ws`, `/mcp` y `/public` al backend en el puerto 8080). Con el backend en marcha,
`npm run generate:api` genera los tipos TypeScript desde el OpenAPI.

## Flujo del reparto

1. Tráfico da de alta paquetes (uno a uno o importando CSV) y repartidores con su zona.
2. El repartidor abre la carga en la PWA: esperados = paquetes asignados + los de su zona (consulta PostGIS).
3. Escanea con la cámara (BarcodeDetector nativo o ZXing). Cada escaneo lleva un `clientScanId`:
   reenviarlo desde el modo sin conexión no lo duplica.
4. Al cerrar la carga (202 Accepted) se agrupan los paquetes por dirección, se geocodifica lo que falte,
   se pide la matriz a OSRM, se optimiza y se guarda la versión 1 de la ruta. Llega por WebSocket.
5. Durante el reparto: navegación (Google Maps / Waze), entrega con firma o foto, ausencias con o sin
   reintento, saltar parada, incidencias. Saltar, reintentar o desviarse más de 350 m recalcula la ruta
   desde la posición GPS (nueva versión; las paradas cerradas no se tocan).
6. El panel ve en directo posiciones y estados, compara lo planificado con lo real y los algoritmos entre sí.

## Algoritmos de ruta

`routing/optimizer` implementa el patrón Strategy (`RouteOptimizer`). El algoritmo se elige con
`ROUTING_ALGORITHM` y, si falla o se agota el tiempo, se usa 2-opt como respaldo.

| Algoritmo | Idea | Notas |
|---|---|---|
| `NEAREST_NEIGHBOR` | Ir siempre a la parada más cercana | Solución inicial, O(n²) |
| `TWO_OPT` | Vecino más cercano + 2-opt (con coste asimétrico) + Or-opt (segmentos de 1–3) | Implementación propia |
| `OR_TOOLS` | Google OR-Tools, búsqueda local guiada con límite de tiempo | Franjas horarias como límites blandos |

Se minimiza el tiempo total por carretera: `T = t(p0, p1) + Σ t(pi, pi+1) [+ t(pn, p0)]`.
La pestaña «Comparativa de algoritmos» del detalle de ruta (`GET /api/routes/{id}/compare`) ejecuta los tres
sobre las paradas pendientes y mide el ahorro frente al orden de escaneo.

## Servidor MCP y asistente

El módulo `mcp` usa el starter de servidor MCP de Spring AI dentro del propio backend.

- **Herramientas**: `get_next_stop`, `list_pending_stops`, `get_route_summary`, `get_directions`, `find_parcel`,
  `mark_delivered`, `mark_failed`, `skip_stop`, `reoptimize_route`, `report_incident`.
- **Recursos**: `route://current`, `stop://next`. **Prompt**: `modo_conductor`.
- **Seguridad**: cada petición va autenticada; la identidad sale siempre del token, nunca de un parámetro.
  Las acciones quedan auditadas en el historial del paquete con origen `MCP` o `ASSISTANT`.
  Límite de 30 peticiones por minuto y usuario.

**Cliente MCP externo**: en la PWA, «Más» → «Conectar un cliente MCP» crea un token personal (`rx_pat_…`).
El servidor está en `http://localhost/mcp/sse` (transporte SSE) con la cabecera `Authorization: Bearer <token>`.

**Asistente en la PWA**: pon `ASSISTANT_ENABLED=true` y `ANTHROPIC_API_KEY` en `.env`. Admite voz (Web Speech API)
y lee las respuestas en voz alta. Con el vehículo en marcha (velocidad GPS > 5 km/h) solo se muestra el modo voz.

> Detalle técnico: el SDK MCP ejecuta las herramientas en hilos de Reactor, donde se perdería el contexto de
> seguridad de la petición. `ReactorSecurityPropagation` lo propaga y `ToolIdentity` falla cerrado si no hay
> usuario autenticado.

## Pruebas

| Comando | Qué ejecuta |
|---|---|
| `cd backend && ./mvnw test` | Algoritmos, reglas de negocio, utilidades geo, Spring Modulith y ArchUnit |
| `cd backend && ./mvnw verify` | Lo anterior y el flujo completo con Testcontainers (PostgreSQL + PostGIS reales; necesita Docker) |
| `cd frontend && npm test` | Tests de la librería compartida (Vitest) |
| `cd frontend && npm run e2e` | Playwright contra el sistema levantado (`npx playwright install chromium` antes) |
| `k6 run load-tests/k6/drivers.js` | 50 repartidores enviando posiciones y consultando la ruta |

## Flujo de trabajo e integración continua

- `main` está protegida: no admite `push` directo, `force push` ni borrado. Todo entra por pull request.
- Se trabaja en `dev` (o en una rama que sale de `dev`) y se abre un pull request de `dev` a `main`.
- El pull request solo se puede fusionar si pasan los cuatro checks obligatorios:

| Check | Workflow | Qué comprueba |
|---|---|---|
| Backend (build, tests, calidad) | `ci.yml` | `./mvnw verify`: compilación, tests unitarios, Spring Modulith/ArchUnit e integración con Testcontainers |
| Frontend (tests y build) | `ci.yml` | `npm ci`, tests de `shared` y build de producción de `admin` y `driver` |
| Secretos en el historial de Git | `security.yml` | Gitleaks sobre todo el historial (excepciones revisadas en `.gitleaks.toml`) |
| Dependencias con vulnerabilidades | `security.yml` | `npm audit` (altas y críticas) y OSV-Scanner sobre `package-lock.json` y `pom.xml` |

Al fusionar en `main`, CI publica las imágenes en GHCR. Dependabot abre cada semana pull requests contra `dev`.

## Producción (VM de Google Cloud)

1. Clona el repositorio en la VM, crea `.env` y define `DOMAIN`, `LETSENCRYPT_EMAIL` y `REGISTRY`.
2. Primer certificado: arranca con la configuración HTTP (`docker compose up -d`) y ejecuta el `certbot certonly`
   que aparece en la cabecera de `docker-compose.prod.yml`.
3. Después, siempre con los dos ficheros:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

El workflow `deploy.yml` hace `pull` y `up -d` por SSH tras cada CI correcto en `main`
(secretos `VM_HOST`, `VM_USER` y `VM_SSH_KEY`, y la variable de repositorio `DEPLOY_ENABLED=true`; sin ella
no despliega). El perfil `prod` exige `JWT_SECRET`, desactiva Swagger
y la estimación en línea recta.

## Seguridad y datos personales

- JWT de 15 minutos y refresh token rotatorio guardado como hash; si se reutiliza uno ya rotado, se revoca toda
  la familia. Bloqueo temporal tras 5 intentos de login fallidos.
- Autorización por rol y por propiedad: un repartidor solo accede a sus cargas, rutas y paradas.
- CORS restringido, cabeceras de seguridad y CSP en Nginx, HTTPS obligatorio en producción, límite de peticiones
  en login y seguimiento público.
- Ficheros de entrega limitados a 5 MB y PNG/JPEG/WebP.
- Los destinatarios se anonimizan a los 90 días de cerrar sus paquetes (`DataRetentionJob`). El seguimiento
  público no muestra nombre, dirección ni posición del repartidor, solo una franja de ±15 minutos.
