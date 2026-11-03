import { Request, Response, NextFunction } from 'express';
import { createProxyMiddleware } from 'http-proxy-middleware';
import { serviceScanner } from '../discovery/service-scanner';
import { config } from '../config';

export function createDynamicProxyMiddleware() {
  return createProxyMiddleware({
    router: async (req: Request) => {
      // Extract serviceId from /proxy/:serviceId/*
      const match = req.url.match(/^\/proxy\/([^/?#]+)/);
      if (!match) return undefined;

      const serviceId = match[1];
      const manifest = serviceScanner.get(serviceId);
      if (!manifest || !manifest.ports) return undefined;

      // In Docker Compose network, use container name; otherwise fallback to localhost
      const containerName = `hub-service-${serviceId}`;
      const port = manifest.ports.container;

      // If running inside container with hub-network:
      if (process.env.DOCKER_NETWORK) {
        return `http://${containerName}:${port}`;
      }

      // If running locally, check if host port is mapped:
      if (manifest.ports.host) {
        return `http://127.0.0.1:${manifest.ports.host}`;
      }

      return `http://${containerName}:${port}`;
    },
    pathRewrite: (pathStr: string, req: Request) => {
      // Strip /proxy/:serviceId from path
      const rewritten = pathStr.replace(/^\/proxy\/[^/?#]+/, '') || '/';
      return rewritten;
    },
    changeOrigin: true,
    ws: true,
    on: {
      proxyRes: (proxyRes, req, res) => {
        // Strip X-Frame-Options to permit embedding in Angular SPA <iframe>
        delete proxyRes.headers['x-frame-options'];
        // Remove frame-ancestors restriction in CSP
        if (proxyRes.headers['content-security-policy']) {
          const csp = String(proxyRes.headers['content-security-policy']);
          proxyRes.headers['content-security-policy'] = csp.replace(/frame-ancestors\s+[^;]+;?/gi, '');
        }
      },
      error: (err, req, res) => {
        console.error(`[DynamicProxy] Proxy routing error:`, err);
        if (!('headersSent' in res) || !(res as Response).headersSent) {
          (res as Response).status(502).json({
            error: 'Bad Gateway',
            message: 'Target service container is not reachable. Ensure the container has been started.',
            details: err.message
          });
        }
      }
    }
  });
}

