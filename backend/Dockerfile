# 0Media Digital Signage — Dockerfile para despliegue aislado de backend
FROM node:22-alpine

WORKDIR /app

COPY package.json ./
COPY servidor-local.js ./
COPY signage_state.json* ./
COPY .env.example ./.env.example

RUN mkdir -p /app/uploads /app/data

ENV NODE_ENV=production
ENV PORT=3000
ENV HOST=0.0.0.0

EXPOSE 3000

VOLUME ["/app/uploads"]

CMD ["node", "servidor-local.js"]
