import { WebSocket } from 'ws';

export class LogStreamer {
  private clientPools: Map<string, Set<WebSocket>> = new Map();
  private logBuffers: Map<string, string[]> = new Map();
  private readonly maxBufferSize = 2500;

  public registerClient(serviceId: string, ws: WebSocket): void {
    if (!this.clientPools.has(serviceId)) {
      this.clientPools.set(serviceId, new Set());
    }
    const pool = this.clientPools.get(serviceId)!;
    pool.add(ws);

    // Replay log history to new client
    const history = this.logBuffers.get(serviceId) || [];
    for (const chunk of history) {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'log', data: chunk }));
      }
    }

    ws.on('close', () => {
      pool.delete(ws);
      if (pool.size === 0) {
        this.clientPools.delete(serviceId);
      }
    });

    ws.on('error', (err) => {
      console.error(`[LogStreamer] WebSocket error for ${serviceId}:`, err);
      pool.delete(ws);
    });
  }

  public broadcastLog(serviceId: string, data: string): void {
    if (!this.logBuffers.has(serviceId)) {
      this.logBuffers.set(serviceId, []);
    }
    const buffer = this.logBuffers.get(serviceId)!;
    buffer.push(data);
    if (buffer.length > this.maxBufferSize) {
      buffer.shift();
    }

    const pool = this.clientPools.get(serviceId);
    if (!pool || pool.size === 0) return;

    const payload = JSON.stringify({ type: 'log', data });
    for (const client of pool) {
      if (client.readyState === WebSocket.OPEN) {
        client.send(payload);
      }
    }
  }

  public broadcastStatus(serviceId: string, status: string, details?: any): void {
    const pool = this.clientPools.get(serviceId);
    if (!pool || pool.size === 0) return;

    const payload = JSON.stringify({ type: 'status', status, details });
    for (const client of pool) {
      if (client.readyState === WebSocket.OPEN) {
        client.send(payload);
      }
    }
  }

  public clearBuffer(serviceId: string): void {
    this.logBuffers.set(serviceId, []);
    const pool = this.clientPools.get(serviceId);
    if (!pool) return;
    const payload = JSON.stringify({ type: 'clear' });
    for (const client of pool) {
      if (client.readyState === WebSocket.OPEN) {
        client.send(payload);
      }
    }
  }
}

export const logStreamer = new LogStreamer();

