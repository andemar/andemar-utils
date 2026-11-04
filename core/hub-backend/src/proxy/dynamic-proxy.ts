import { Request, Response } from 'express';
import { createProxyMiddleware } from 'http-proxy-middleware';
import { serviceScanner } from '../discovery/service-scanner';
import { config } from '../config';

export function createDynamicProxyMiddleware() {
  return createProxyMiddleware({
    // Provide a fallback target to prevent http-proxy-middleware v3 from throwing
    target: 'http://127.0.0.1:4000',
    router: async (req: Request) => {
      // Determine full path from originalUrl or url
      const fullPath = req.originalUrl || req.url;
      // Match serviceId from /proxy/:serviceId or :serviceId
      const cleanPath = fullPath.replace(/^\/proxy\/?/, '');
      const serviceId = cleanPath.split(/[\/?#]/)[0];

      if (!serviceId) return undefined;

      const manifest = serviceScanner.get(serviceId);
      if (!manifest || !manifest.ports) return undefined;

      const containerName = `hub-service-${serviceId}`;
      const port = manifest.ports.container;

      // In Docker Compose network, reach container by its DNS alias
      if (process.env.DOCKER_NETWORK) {
        return `http://${containerName}:${port}`;
      }

      // If running locally, route to mapped host port if available
      if (manifest.ports.host) {
        return `http://127.0.0.1:${manifest.ports.host}`;
      }

      return `http://${containerName}:${port}`;
    },
    pathRewrite: (pathStr: string) => {
      // Strip both /proxy/:serviceId and /:serviceId from the start of pathStr
      const rewritten = pathStr.replace(/^(\/proxy)?\/[^/?#]+/, '');
      return rewritten.startsWith('/') ? rewritten : '/' + rewritten;
    },
    changeOrigin: true,
    ws: true,
    on: {
      proxyRes: (proxyRes) => {
        // Strip X-Frame-Options to permit embedding in Angular SPA <iframe>
        delete proxyRes.headers['x-frame-options'];
        // Remove frame-ancestors restriction in CSP
        if (proxyRes.headers['content-security-policy']) {
          const csp = String(proxyRes.headers['content-security-policy']);
          proxyRes.headers['content-security-policy'] = csp.replace(/frame-ancestors\s+[^;]+;?/gi, '');
        }
      },
      error: (err, req, res) => {
        console.error(`[DynamicProxy] Proxy routing error:`, err.message);
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
