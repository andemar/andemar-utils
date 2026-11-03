import http from 'http';
import express from 'express';
import cors from 'cors';
import { WebSocketServer, WebSocket } from 'ws';
import { config } from './config';
import { createDynamicProxyMiddleware } from './proxy/dynamic-proxy';
import servicesRouter from './routes/services';
import { logStreamer } from './streaming/log-streamer';
import { serviceScanner } from './discovery/service-scanner';

const app = express();
const server = http.createServer(app);

// Enable Cross-Origin Resource Sharing
app.use(cors());

// Mount Dynamic Reverse Proxy for child web-ui and rest-api services BEFORE body parsers!
// This preserves raw streams for file uploads, chunked downloads, and WebSocket upgrades.
app.use('/proxy', createDynamicProxyMiddleware());

// JSON & URL-encoded parsers for Hub API endpoints
app.use(express.json({ limit: '50mb' }));
app.use(express.urlencoded({ extended: true, limit: '50mb' }));

// Health Check
app.get('/api/health', (req, res) => {
  res.json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    servicesDiscovered: serviceScanner.getAll().length,
    dockerSocket: config.dockerSocket,
    hostWorkspaceDir: config.hostWorkspaceDir
  });
});

// REST API Routes
app.use('/api/services', servicesRouter);

// WebSocket Server for Real-Time Container stdout/stderr Streaming
const wss = new WebSocketServer({ noServer: true });

server.on('upgrade', (request, socket, head) => {
  const url = new URL(request.url || '', `http://${request.headers.host}`);

  if (url.pathname.startsWith('/ws/logs/')) {
    const serviceId = url.pathname.replace(/^\/ws\/logs\//, '').trim();

    wss.handleUpgrade(request, socket, head, (ws: WebSocket) => {
      console.log(`[WebSocket] Client connected to log stream for: ${serviceId}`);
      logStreamer.registerClient(serviceId, ws);
    });
  } else if (!url.pathname.startsWith('/proxy/')) {
    socket.destroy();
  }
});

// Start Orchestrator
server.listen(config.port, () => {
  console.log(`================================================================`);
  console.log(`  🚀 Application Hub Backend Orchestrator Started!`);
  console.log(`  Listening on: http://0.0.0.0:${config.port}`);
  console.log(`  Docker Engine Socket: ${config.dockerSocket}`);
  console.log(`  Services Directory:   ${config.servicesDir}`);
  console.log(`  Host Workspace Path:  ${config.hostWorkspaceDir}`);
  console.log(`  Discovered Services:  ${serviceScanner.getAll().length}`);
  console.log(`================================================================`);
});

