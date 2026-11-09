"""
Simula un día de reparto en curso sobre los datos demo, usando solo la API pública del backend:
  - Lucía (centro): carga escaneada, ruta optimizada, reparto iniciado, 4 entregas, 1 ausente con reintento.
  - Javier (este): carga escaneada, ruta optimizada, reparto iniciado, 2 entregas y una incidencia.
  - Posiciones GPS de ambos cerca de su siguiente parada.

  python scripts/seed-scenario.py [http://localhost:8082]
"""
import json
import sys
import time
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone

BASE = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8082"
PASSWORD = "RutaExpress-demo-2026"


def call(method, path, token=None, body=None, raw=None, content_type="application/json"):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", content_type)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            text = r.read().decode()
            return r.status, (json.loads(text) if text else None)
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def login(email):
    status, body = call("POST", "/api/auth/login", body={"email": email, "password": PASSWORD})
    assert status == 200, body
    return body["accessToken"]


def deliver(token, stop_id, receiver):
    boundary = uuid.uuid4().hex
    parts = [("proofType", "CODE"), ("receiverName", receiver)]
    raw = b"".join(
        f'--{boundary}\r\nContent-Disposition: form-data; name="{k}"\r\n\r\n{v}\r\n'.encode() for k, v in parts
    ) + f"--{boundary}--\r\n".encode()
    return call("POST", f"/api/stops/{stop_id}/deliver", token, raw=raw,
                content_type=f"multipart/form-data; boundary={boundary}")


def now():
    return datetime.now(timezone.utc).isoformat()


def run_driver(email, deliveries, fail_one):
    token = login(email)
    status, route = call("GET", "/api/routes/current", token)
    if status != 200:
        status, load = call("POST", "/api/loads", token)
        assert status == 201, load
        print(f"{email}: carga abierta, {load['expectedCount']} paquetes esperados")
        for m in load["missing"]:
            call("POST", f"/api/loads/{load['id']}/scans", token,
                 {"code": m["trackingCode"], "clientScanId": str(uuid.uuid4()), "scannedAt": now()})
        # Un código desconocido para que se vea el aviso
        call("POST", f"/api/loads/{load['id']}/scans", token,
             {"code": "RXNOEXISTE01", "clientScanId": str(uuid.uuid4()), "scannedAt": now()})
        status, closed = call("POST", f"/api/loads/{load['id']}/close", token)
        assert status == 202, closed
        print(f"{email}: carga cerrada, esperando la optimización…")
        for _ in range(60):
            status, route = call("GET", "/api/routes/current", token)
            if status == 200:
                break
            time.sleep(1)
        assert status == 200, "la ruta no se calculó"
    print(f"{email}: ruta v{route['version']} ({route['algorithm']}), {len(route['stops'])} paradas")

    if route["status"] == "PLANNED":
        call("POST", f"/api/routes/{route['id']}/start", token)

    done = sum(1 for s in route["stops"] if s["status"] in ("DELIVERED", "FAILED"))
    for _ in range(max(0, deliveries - done)):
        status, nxt = call("GET", f"/api/routes/{route['id']}/next-stop", token)
        if status != 200:
            break
        receiver = nxt["parcels"][0]["recipientName"] if nxt["parcels"] else "Destinatario"
        deliver(token, nxt["id"], receiver)
        print(f"  entregada {nxt['sequence']}. {nxt['addressLine']}")

    if fail_one:
        status, nxt = call("GET", f"/api/routes/{route['id']}/next-stop", token)
        if status == 200:
            call("POST", f"/api/stops/{nxt['id']}/fail", token,
                 {"reason": "ABSENT", "note": "No contesta al timbre", "retryLater": True})
            print(f"  ausente (se reintenta al final): {nxt['addressLine']}")

    # GPS: unos metros antes de la siguiente parada
    status, nxt = call("GET", f"/api/routes/{route['id']}/next-stop", token)
    if status == 200:
        lat, lon = nxt["location"]["lat"], nxt["location"]["lon"]
        positions = [{"lat": lat - 0.002 + i * 0.0004, "lon": lon - 0.001 + i * 0.0002, "speedKmh": 22.0,
                      "accuracyMeters": 8.0, "recordedAt": now()} for i in range(5)]
        call("POST", "/api/tracking/positions", token, {"positions": positions})
    return token


lucia = run_driver("lucia@rutaexpress.local", deliveries=4, fail_one=True)
javier = run_driver("javier@rutaexpress.local", deliveries=2, fail_one=False)
call("POST", "/api/incidents", javier,
     {"type": "TRAFFIC", "description": "Atasco en la avenida del Puerto, unos 15 minutos de retraso"})
print("Incidencia de tráfico registrada. Escenario listo.")
