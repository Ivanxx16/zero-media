# 0Media Digital Signage — Dockerfile Multi-Módulo para Producción
FROM node:22-alpine

WORKDIR /app

# Copiar configuración y código del backend
COPY backend/package.json ./backend/
COPY backend/servidor-local.js ./backend/
COPY backend/signage_state.json* ./backend/
COPY backend/.env.example ./backend/.env.example

# Copiar interfaz frontend web
COPY frontend-web/ ./frontend-web/

# Copiar instalador APK para descarga remota
COPY aplicacion-tv/0Media_Signage_TV.apk* ./aplicacion-tv/

# Crear carpetas de almacenamiento persistente
RUN mkdir -p /app/backend/uploads /app/backend/data

WORKDIR /app/backend

ENV NODE_ENV=production
ENV PORT=3000
ENV HOST=0.0.0.0

EXPOSE 3000

VOLUME ["/app/backend/uploads"]

CMD ["node", "servidor-local.js"]
