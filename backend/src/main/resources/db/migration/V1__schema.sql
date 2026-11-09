-- RutaExpress: esquema inicial (PostgreSQL 16 + PostGIS)
CREATE EXTENSION IF NOT EXISTS postgis;

-- ============ auth ============
CREATE TABLE app_user (
    id               UUID PRIMARY KEY,
    email            VARCHAR(255) NOT NULL UNIQUE,
    password_hash    VARCHAR(100) NOT NULL,
    role             VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'DISPATCHER', 'DRIVER')),
    enabled          BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_attempts  INT          NOT NULL DEFAULT 0,
    locked_until     TIMESTAMPTZ,
    last_login_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    last_modified_by VARCHAR(255)
);

CREATE TABLE refresh_token (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    family_id   UUID         NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID
);
CREATE INDEX idx_refresh_token_user ON refresh_token (user_id);
CREATE INDEX idx_refresh_token_family ON refresh_token (family_id);

CREATE TABLE personal_access_token (
    id           UUID PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name         VARCHAR(80)  NOT NULL,
    prefix       VARCHAR(20)  NOT NULL,
    token_hash   VARCHAR(64)  NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ  NOT NULL,
    expires_at   TIMESTAMPTZ,
    last_used_at TIMESTAMPTZ,
    revoked_at   TIMESTAMPTZ
);
CREATE INDEX idx_pat_user ON personal_access_token (user_id);

-- ============ catalog ============
CREATE TABLE depot (
    id               UUID PRIMARY KEY,
    name             VARCHAR(120)           NOT NULL,
    address          VARCHAR(255)           NOT NULL,
    location         geography(Point, 4326) NOT NULL,
    created_at       TIMESTAMPTZ            NOT NULL,
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    last_modified_by VARCHAR(255)
);

CREATE TABLE vehicle (
    id               UUID PRIMARY KEY,
    plate            VARCHAR(15)    NOT NULL UNIQUE,
    capacity_kg      NUMERIC(10, 2) NOT NULL,
    capacity_m3      NUMERIC(10, 2) NOT NULL,
    capacity_parcels INT            NOT NULL,
    active           BOOLEAN        NOT NULL DEFAULT TRUE,
    depot_id         UUID           NOT NULL REFERENCES depot (id),
    created_at       TIMESTAMPTZ    NOT NULL,
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    last_modified_by VARCHAR(255)
);

CREATE TABLE zone (
    id               UUID PRIMARY KEY,
    name             VARCHAR(120)             NOT NULL,
    area             geography(Polygon, 4326) NOT NULL,
    color            VARCHAR(7)               NOT NULL DEFAULT '#1976d2',
    depot_id         UUID                     NOT NULL REFERENCES depot (id),
    created_at       TIMESTAMPTZ              NOT NULL,
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    last_modified_by VARCHAR(255)
);
CREATE INDEX idx_zone_area ON zone USING GIST (area);

CREATE TABLE driver (
    id                 UUID PRIMARY KEY,
    name               VARCHAR(120) NOT NULL,
    phone              VARCHAR(30),
    license_number     VARCHAR(30),
    status             VARCHAR(20)  NOT NULL,
    user_id            UUID UNIQUE REFERENCES app_user (id),
    depot_id           UUID         NOT NULL REFERENCES depot (id),
    zone_id            UUID REFERENCES zone (id) ON DELETE SET NULL,
    default_vehicle_id UUID REFERENCES vehicle (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ  NOT NULL,
    updated_at         TIMESTAMPTZ,
    created_by         VARCHAR(255),
    last_modified_by   VARCHAR(255)
);

-- ============ parcel ============
CREATE TABLE recipient (
    id               UUID PRIMARY KEY,
    name             VARCHAR(120) NOT NULL,
    phone            VARCHAR(30),
    email            VARCHAR(120),
    anonymized_at    TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    last_modified_by VARCHAR(255)
);

CREATE TABLE address (
    id               UUID PRIMARY KEY,
    street           VARCHAR(200) NOT NULL,
    number           VARCHAR(20),
    extra            VARCHAR(120),
    postal_code      VARCHAR(10)  NOT NULL,
    city             VARCHAR(120) NOT NULL,
    country          VARCHAR(60)  NOT NULL,
    normalized_hash  VARCHAR(64)  NOT NULL UNIQUE,
    location         geography(Point, 4326),
    geocode_quality  DOUBLE PRECISION,
    geocode_provider VARCHAR(30),
    needs_review     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    last_modified_by VARCHAR(255)
);
CREATE INDEX idx_address_location ON address USING GIST (location);
CREATE INDEX idx_address_review ON address (needs_review) WHERE needs_review;

CREATE TABLE parcel (
    id                 UUID PRIMARY KEY,
    tracking_code      VARCHAR(20)    NOT NULL UNIQUE,
    weight_kg          NUMERIC(10, 3) NOT NULL,
    length_cm          INT,
    width_cm           INT,
    height_cm          INT,
    status             VARCHAR(20)    NOT NULL,
    window_start       TIME,
    window_end         TIME,
    notes              VARCHAR(500),
    depot_id           UUID           NOT NULL REFERENCES depot (id),
    assigned_driver_id UUID REFERENCES driver (id) ON DELETE SET NULL,
    delivery_attempts  INT            NOT NULL DEFAULT 0,
    recipient_id       UUID           NOT NULL REFERENCES recipient (id),
    address_id         UUID           NOT NULL REFERENCES address (id),
    version            BIGINT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ    NOT NULL,
    updated_at         TIMESTAMPTZ,
    created_by         VARCHAR(255),
    last_modified_by   VARCHAR(255)
);
CREATE INDEX idx_parcel_status ON parcel (status);
CREATE INDEX idx_parcel_depot_status ON parcel (depot_id, status);
CREATE INDEX idx_parcel_assigned ON parcel (assigned_driver_id) WHERE assigned_driver_id IS NOT NULL;
CREATE INDEX idx_parcel_address ON parcel (address_id);
CREATE INDEX idx_parcel_created ON parcel (created_at DESC);

CREATE TABLE parcel_event (
    id          UUID PRIMARY KEY,
    parcel_id   UUID         NOT NULL REFERENCES parcel (id) ON DELETE CASCADE,
    type        VARCHAR(40)  NOT NULL,
    occurred_at TIMESTAMPTZ  NOT NULL,
    actor       VARCHAR(255),
    source      VARCHAR(20)  NOT NULL,
    data        JSONB
);
CREATE INDEX idx_parcel_event_parcel ON parcel_event (parcel_id, occurred_at);

-- ============ scanning ============
CREATE TABLE load_session (
    id               UUID PRIMARY KEY,
    driver_id        UUID        NOT NULL REFERENCES driver (id),
    vehicle_id       UUID REFERENCES vehicle (id),
    depot_id         UUID        NOT NULL REFERENCES depot (id),
    status           VARCHAR(10) NOT NULL,
    opened_at        TIMESTAMPTZ NOT NULL,
    closed_at        TIMESTAMPTZ,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ,
    created_by       VARCHAR(255),
    last_modified_by VARCHAR(255)
);
CREATE INDEX idx_load_driver ON load_session (driver_id, opened_at DESC);
-- Un repartidor solo puede tener una carga abierta a la vez
CREATE UNIQUE INDEX uq_load_open_per_driver ON load_session (driver_id) WHERE status = 'OPEN';

CREATE TABLE load_expected_parcel (
    load_id   UUID NOT NULL REFERENCES load_session (id) ON DELETE CASCADE,
    parcel_id UUID NOT NULL,
    PRIMARY KEY (load_id, parcel_id)
);

CREATE TABLE scan (
    id             UUID PRIMARY KEY,
    load_id        UUID        NOT NULL REFERENCES load_session (id) ON DELETE CASCADE,
    client_scan_id UUID        NOT NULL UNIQUE,
    code           VARCHAR(64) NOT NULL,
    parcel_id      UUID REFERENCES parcel (id),
    result         VARCHAR(20) NOT NULL,
    scanned_at     TIMESTAMPTZ NOT NULL,
    received_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_scan_load ON scan (load_id, scanned_at DESC);
CREATE INDEX idx_scan_load_parcel ON scan (load_id, parcel_id) WHERE result = 'OK';

-- ============ routing ============
CREATE TABLE route (
    id                  UUID PRIMARY KEY,
    load_id             UUID                   NOT NULL UNIQUE REFERENCES load_session (id),
    driver_id           UUID                   NOT NULL REFERENCES driver (id),
    depot_id            UUID                   NOT NULL REFERENCES depot (id),
    depot_location      geography(Point, 4326) NOT NULL,
    status              VARCHAR(20)            NOT NULL,
    current_version     INT                    NOT NULL DEFAULT 0,
    algorithm           VARCHAR(30)            NOT NULL,
    return_to_depot     BOOLEAN                NOT NULL,
    total_distance_m    BIGINT                 NOT NULL DEFAULT 0,
    total_duration_s    BIGINT                 NOT NULL DEFAULT 0,
    geometry            geography(LineString, 4326),
    estimated_end       TIMESTAMPTZ,
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    last_reoptimized_at TIMESTAMPTZ,
    lock_version        BIGINT                 NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ            NOT NULL,
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    last_modified_by    VARCHAR(255)
);
CREATE INDEX idx_route_driver ON route (driver_id, created_at DESC);
CREATE INDEX idx_route_status ON route (status);

CREATE TABLE stop (
    id              UUID PRIMARY KEY,
    route_id        UUID                   NOT NULL REFERENCES route (id) ON DELETE CASCADE,
    sequence        INT                    NOT NULL,
    scan_order      INT                    NOT NULL,
    status          VARCHAR(20)            NOT NULL,
    address_id      UUID                   NOT NULL REFERENCES address (id),
    address_line    VARCHAR(400)           NOT NULL,
    location        geography(Point, 4326) NOT NULL,
    service_seconds BIGINT                 NOT NULL,
    window_start    TIME,
    window_end      TIME,
    eta             TIMESTAMPTZ,
    planned_eta     TIMESTAMPTZ,
    leg_duration_s  BIGINT                 NOT NULL DEFAULT 0,
    leg_distance_m  BIGINT                 NOT NULL DEFAULT 0,
    fail_reason     VARCHAR(400),
    attempts        INT                    NOT NULL DEFAULT 0,
    completed_at    TIMESTAMPTZ,
    lock_version    BIGINT                 NOT NULL DEFAULT 0
);
CREATE INDEX idx_stop_route_sequence ON stop (route_id, sequence);
CREATE INDEX idx_stop_location ON stop USING GIST (location);

CREATE TABLE stop_parcel (
    stop_id   UUID NOT NULL REFERENCES stop (id) ON DELETE CASCADE,
    parcel_id UUID NOT NULL REFERENCES parcel (id),
    PRIMARY KEY (stop_id, parcel_id)
);
CREATE INDEX idx_stop_parcel_parcel ON stop_parcel (parcel_id);

CREATE TABLE route_version (
    id               UUID PRIMARY KEY,
    route_id         UUID        NOT NULL REFERENCES route (id) ON DELETE CASCADE,
    version          INT         NOT NULL,
    algorithm        VARCHAR(30) NOT NULL,
    reason           VARCHAR(40) NOT NULL,
    stop_order       JSONB       NOT NULL,
    total_distance_m BIGINT      NOT NULL,
    total_duration_s BIGINT      NOT NULL,
    compute_millis   BIGINT      NOT NULL,
    matrix_source    VARCHAR(20) NOT NULL,
    geometry         geography(LineString, 4326),
    created_at       TIMESTAMPTZ NOT NULL,
    UNIQUE (route_id, version)
);

-- ============ delivery ============
CREATE TABLE proof_of_delivery (
    id            UUID PRIMARY KEY,
    stop_id       UUID        NOT NULL UNIQUE REFERENCES stop (id) ON DELETE CASCADE,
    type          VARCHAR(20) NOT NULL,
    file_key      VARCHAR(255),
    content_type  VARCHAR(50),
    receiver_name VARCHAR(120),
    location      geography(Point, 4326),
    created_by    VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE TABLE incident (
    id          UUID PRIMARY KEY,
    driver_id   UUID          NOT NULL REFERENCES driver (id),
    route_id    UUID REFERENCES route (id) ON DELETE SET NULL,
    type        VARCHAR(30)   NOT NULL,
    description VARCHAR(1000) NOT NULL,
    location    geography(Point, 4326),
    source      VARCHAR(20)   NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolved_by VARCHAR(255)
);
CREATE INDEX idx_incident_open ON incident (created_at DESC) WHERE resolved_at IS NULL;

-- ============ tracking (particionada por día) ============
CREATE TABLE driver_position (
    id          UUID                   NOT NULL,
    driver_id   UUID                   NOT NULL,
    location    geography(Point, 4326) NOT NULL,
    speed_kmh   DOUBLE PRECISION,
    heading     DOUBLE PRECISION,
    accuracy_m  DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ            NOT NULL,
    received_at TIMESTAMPTZ            NOT NULL,
    PRIMARY KEY (id, recorded_at)
) PARTITION BY RANGE (recorded_at);
-- Las particiones diarias las crea PartitionMaintenanceJob; la DEFAULT recoge lo que llegue fuera de rango
CREATE TABLE driver_position_default PARTITION OF driver_position DEFAULT;
CREATE INDEX idx_driver_position_driver ON driver_position (driver_id, recorded_at DESC);

-- ============ geo (cachés) ============
CREATE TABLE geocode_cache (
    address_hash       VARCHAR(64) PRIMARY KEY,
    normalized_address VARCHAR(500)     NOT NULL,
    location           geography(Point, 4326),
    confidence         DOUBLE PRECISION NOT NULL,
    display_name       VARCHAR(500),
    provider           VARCHAR(30)      NOT NULL,
    created_at         TIMESTAMPTZ      NOT NULL
);

CREATE TABLE distance_cache (
    origin_key      VARCHAR(40) NOT NULL,
    destination_key VARCHAR(40) NOT NULL,
    seconds         BIGINT      NOT NULL,
    meters          BIGINT      NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (origin_key, destination_key)
);
CREATE INDEX idx_distance_cache_created ON distance_cache (created_at);
