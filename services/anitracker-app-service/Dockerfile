FROM node:20-slim

WORKDIR /app

# Pre-create application and cache directories with proper permissions for unprivileged node user
RUN mkdir -p /app/.next /app/node_modules && chown -R node:node /app

USER node

# Copy manifests first for optimized layer caching
COPY --chown=node:node package.json package-lock.json ./

# Install dependencies using clean install
RUN npm ci

# Copy application source code
COPY --chown=node:node . .

# Expose Next.js development server port
EXPOSE 3000

# Configure Next.js to bind to all network interfaces inside container
ENV PORT=3000 \
    HOSTNAME="0.0.0.0"

# Run Next.js in development mode with hot reload
CMD ["npm", "run", "dev", "--", "-H", "0.0.0.0"]
