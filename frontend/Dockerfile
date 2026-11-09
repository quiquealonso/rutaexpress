# --- Compilación de las dos aplicaciones Angular ---
FROM node:22-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# El panel se sirve en "/" y la PWA del repartidor en "/app/"
RUN npx ng build admin \
    && npx ng build driver --base-href /app/

# --- Nginx con los estáticos compilados ---
FROM nginx:1.27-alpine
COPY --from=build /workspace/dist/admin/browser /usr/share/nginx/html/admin
COPY --from=build /workspace/dist/driver/browser /usr/share/nginx/html/app
EXPOSE 80 443
