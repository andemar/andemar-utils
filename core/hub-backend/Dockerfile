# Multi-stage Dockerfile for Hub Backend Orchestrator
FROM node:22-slim AS builder

WORKDIR /app

COPY package*.json tsconfig.json ./
RUN npm install

COPY src/ ./src/
RUN npm run build

# Production runtime stage
FROM node:22-slim AS runner

WORKDIR /app

ENV NODE_ENV=production \
    PORT=4000 \
    DOCKER_SOCKET=/var/run/docker.sock \
    SERVICES_DIR=/services

# Install docker CLI if needed for host socket communication diagnostics
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY package*.json ./
RUN npm install --omit=dev

COPY --from=builder /app/dist ./dist

# Create mount points
RUN mkdir -p /services

EXPOSE 4000

CMD ["node", "dist/index.js"]

