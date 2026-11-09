# Bloque común a HTTP (local) y HTTPS (producción). Se incluye desde cada server.

client_max_body_size 6m;

gzip on;
gzip_types text/plain text/css application/javascript application/json application/problem+json image/svg+xml application/manifest+json;
gzip_min_length 1024;

include /etc/nginx/snippets/security-headers.inc;

# --- API REST ---
location /api/ {
    proxy_pass http://backend:8080;
    include /etc/nginx/snippets/proxy.inc;
}

location = /api/auth/login {
    limit_req zone=login burst=5 nodelay;
    proxy_pass http://backend:8080;
    include /etc/nginx/snippets/proxy.inc;
}

# Asistente en streaming (SSE): sin buffer
location /api/assistant/ {
    proxy_pass http://backend:8080;
    include /etc/nginx/snippets/proxy.inc;
    proxy_buffering off;
    proxy_read_timeout 120s;
}

# --- Seguimiento público ---
location /public/ {
    limit_req zone=public burst=20 nodelay;
    proxy_pass http://backend:8080;
    include /etc/nginx/snippets/proxy.inc;
}

# --- WebSocket STOMP ---
location /ws {
    proxy_pass http://backend:8080;
    include /etc/nginx/snippets/proxy.inc;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 1h;
}

# --- Servidor MCP (SSE de larga duración) ---
location /mcp/ {
    proxy_pass http://backend:8080;
    include /etc/nginx/snippets/proxy.inc;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 1h;
}

# Documentación de la API (desactivada en el perfil prod del backend)
location ~ ^/(swagger-ui|v3/api-docs) {
    proxy_pass http://backend:8080;
    include /etc/nginx/snippets/proxy.inc;
}

# Actuator no se expone fuera: Prometheus lo lee por la red interna de Docker
location /actuator/ {
    return 404;
}

# --- App del repartidor (PWA) en /app/ ---
location = /app/ngsw-worker.js {
    root /usr/share/nginx/html;
    add_header Cache-Control "no-cache";
    include /etc/nginx/snippets/security-headers.inc;
}
location = /app/ngsw.json {
    root /usr/share/nginx/html;
    add_header Cache-Control "no-cache";
    include /etc/nginx/snippets/security-headers.inc;
}
location /app/ {
    root /usr/share/nginx/html;
    try_files $uri $uri/ /app/index.html;
    location ~* \.(js|css|woff2?|png|svg|ico)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        include /etc/nginx/snippets/security-headers.inc;
    }
}

# --- Panel de administración en / ---
location / {
    root /usr/share/nginx/html/admin;
    try_files $uri $uri/ /index.html;
    location ~* \.(js|css|woff2?|png|svg|ico)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
        include /etc/nginx/snippets/security-headers.inc;
    }
}

location = /index.html {
    root /usr/share/nginx/html/admin;
    add_header Cache-Control "no-cache";
    include /etc/nginx/snippets/security-headers.inc;
}
